/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.run.deployment.liveedit

import com.android.annotations.Trace
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.internalErrorCodeGenException
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.internalErrorCompileCommandException
import com.android.tools.idea.run.deployment.liveedit.desugaring.LiveEditDesugar
import com.android.tools.idea.run.deployment.liveedit.desugaring.LiveEditDesugarRequest
import com.android.tools.idea.run.deployment.liveedit.desugaring.LiveEditDesugarResponse
import com.android.tools.idea.run.deployment.liveedit.desugaring.MinApiLevel
import com.android.tools.idea.run.deployment.liveedit.k2.LiveEditCompilerForK2
import com.google.common.collect.HashMultimap
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.psi.KtFile

import java.util.Optional

class LiveEditCompiler(val project: Project,
                       private val irClassCache: IrClassCache,
                       apkClassProvider: ApkClassProvider = DefaultApkClassProvider()) {

  private val LOGGER = LogWrapper(Logger.getInstance(LiveEditCompiler::class.java))

  // Cache of fully-qualified class name to inlineable bytecode on disk or in memory
  var inlineCandidateCache = SourceInlineCandidateCache()

  private var desugarer = LiveEditDesugar()
  private val outputBuilder = LiveEditOutputBuilder(apkClassProvider)
  private val logger = LiveEditLogger("LE Compiler")

  /**
   * Compile a given set of MethodReferences to Java .class files and populates the output list with the compiled code.
   * The compilation is wrapped in a cancelable read action, and will be interrupted by a PSI write action.
   *
   * Returns true if the compilation is successful, and false if the compilation was interrupted and did not complete.
   * If compilation fails due to issues with invalid syntax or other compiler-specific errors, throws a
   * LiveEditException detailing the failure.
   */
  @Trace
  fun compile(inputs: List<LiveEditCompilerInput>,
              giveWritePriority: Boolean = true,
              apiVersions: Set<MinApiLevel> = emptySet()): Optional<LiveEditDesugarResponse> {
    // Bundle changes per-file to prevent wasted recompilation of the same file. The most common
    // scenario is multiple pending changes in the same file, so this is somewhat important.
    val changedFiles = HashMultimap.create<KtFile, LiveEditCompilerInput>()
    for (input in inputs) {
      if (input.file is KtFile) {
        changedFiles.put(input.file, input)
      }
    }

    // Wrap compilation in a read action that can be interrupted by any other read or write action,
    // which prevents the UI from freezing during compilation if the user continues typing.
    val progressManager = ProgressManager.getInstance()

    var desugaredOutputs : LiveEditDesugarResponse? = null
    val compileCmd = {
      var outputBuilder = LiveEditCompilerOutput.Builder()
      for ((file, input) in changedFiles.asMap()) {
        // Ignore script files. This check must be done in a read action.
        if (file.isScript()) {
          continue
        }
        try {
          // Compiler pass
          if (KotlinPluginModeProvider.isK2Mode()) {
            LiveEditCompilerForK2(project, inlineCandidateCache, irClassCache, this.outputBuilder).compile(file, input, outputBuilder)
          } else {
            compileKtFile(file, input, outputBuilder)
          }
          val outputs = outputBuilder.build()
          logger.dumpCompilerOutputs(outputs.classes)

          // Desugaring pass
          val request = LiveEditDesugarRequest(outputs, apiVersions)
          desugaredOutputs = desugarer.desugar(request)
          logger.dumpDesugarOutputs(desugaredOutputs!!.classes)

        } catch (e: ProcessCanceledException) {
          throw e
        } catch (e: LiveEditUpdateException) {
          throw e
        } catch (e : Exception) {
          // Unlike the other exception where it is temporary errors or setup failures. These type of internal error should be
          // rare and probably worth logging for bug reports.
          LOGGER.warning("Internal error during compilation command: %s\n%s", e.message, e.stackTraceToString().prependIndent("\t"))
          throw internalErrorCompileCommandException(file, e)
        }
      }
    }

    // In manual mode, we trigger when SaveAll action shortcut is detected. Which means we run concurrently with SaveAllAction.
    // Therefore, we cannot use runInReadActionWithWriteActionPriority (otherwise we would be continuously interrupted because
    // save is happening, upon testing on a small file we were interrupted 1000 times by save writes).
    // Instead, we run with runReadAction.
    //
    // In automatic mode, we want to be interrupted on each keystroke, so we only run when the user is done typing.
    // A keystroke writes the PSI trees so running with runInReadActionWithWriteActionPriority yield exactly the interrupt policy we need.

    var success = true
    if (giveWritePriority) {
      success = progressManager.runInReadActionWithWriteActionPriority(compileCmd, progressManager.progressIndicator)
    } else {
      ApplicationManager.getApplication().runReadAction(toComputable(compileCmd))?.let { throw it }
    }
    return if (success) Optional.of(desugaredOutputs!!) else Optional.empty()
  }

  private fun toComputable(cmd : () -> Unit) = Computable<Exception?> {
    try {
      cmd()
    } catch (e : Exception) {
      return@Computable e
    }
    return@Computable null
  }

  private fun compileKtFile(file: KtFile,
                            inputs: Collection<LiveEditCompilerInput>,
                            output: LiveEditCompilerOutput.Builder) {
    val tracker = PerformanceTracker()
    var inputFiles = listOf(file)

    runWithCompileLock {
      ReadActionPrebuildChecks(project, file)

      // This is a three-step process:
      // 1) Compute binding context based on any previous cached analysis results.
      //    On small edits of previous analyzed project, this operation should be below 30ms or so.
      ProgressManager.checkCanceled()
      val resolution = tracker.record("resolution_fetch") { fetchResolution(project, inputFiles) }

      ProgressManager.checkCanceled()
      val analysisResult = tracker.record("analysis") { analyze(inputFiles, resolution) }
      val inlineCandidates = analyzeSingleDepthInlinedFunctions(file, analysisResult.bindingContext, inlineCandidateCache)

      // 2) Invoke the backend with the inputs and the binding context computed from step 1.
      //    This is the one of the most time-consuming step with 80 to 500ms turnaround, depending on
      //    the complexity of the input .kt file.
      ProgressManager.checkCanceled()
      val generationState = try {
        tracker.record("codegen") {
          backendCodeGen(project,
                         analysisResult,
                         inputFiles,
                         inputFiles.first().module!!,
                         inlineCandidates)
        }
      } catch (e : LiveEditUpdateException) {
        if (e.error != LiveEditUpdateException.Error.UNABLE_TO_INLINE) {
          throw e
        }

        // 2.1) Add any extra source file this compilation need in order to support the input file calling an inline function
        //      from another source file then perform a compilation again.
        inputFiles = performInlineSourceDependencyAnalysis(resolution, file, analysisResult.bindingContext)

        // We need to perform the analysis once more with the new set of input files.
        val newAnalysisResult = resolution.analyzeWithAllCompilerChecks(inputFiles)

        // We will need to start using the new analysis for code gen.
        tracker.record("codegen_inline") {
          backendCodeGen(project,
                         newAnalysisResult,
                         inputFiles,
                         inputFiles.first().module!!,
                         inlineCandidates)
        }
      } catch (p : ProcessCanceledException) {
        throw p
      } catch (t : Throwable) {
        throw internalErrorCodeGenException(file, t)
      }

      // Run this validation *after* compilation so that PSI validation doesn't run until the class is in a state that compiles. This
      // allows the user time to undo incompatible changes without triggering an error, similar to how differ validation works.
      validatePsiDiff(inputs, file)

      // 3) Diff the newly generated class files from step 2 with the previously generated class files in order to decide which classes
      //    we want to send to the device along with what extra meta-information the agent needs.
      outputBuilder.getGeneratedCode(file, generationState.factory.asList(), irClassCache, inlineCandidateCache, output)
      return@runWithCompileLock
    }
  }

  fun resetState() {
    inlineCandidateCache.clear()

    try {
      // Desugarer caches jar indexes and entries. It MUST be closed and recreated.
      desugarer.close()
    } finally {
      desugarer = LiveEditDesugar()
    }
  }
}