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
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.internalErrorCompileCommandException
import com.android.tools.idea.run.deployment.liveedit.desugaring.LiveEditDesugar
import com.android.tools.idea.run.deployment.liveedit.desugaring.LiveEditDesugarRequest
import com.android.tools.idea.run.deployment.liveedit.desugaring.LiveEditDesugarResponse
import com.android.tools.idea.run.deployment.liveedit.desugaring.MinApiLevel
import com.android.tools.idea.run.deployment.liveedit.k2.LiveEditCompilerForK2
import com.android.tools.idea.run.deployment.liveedit.tokens.ApplicationLiveEditServices
import com.google.common.collect.HashMultimap
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.konan.file.isBitcode
import org.jetbrains.kotlin.psi.KtFile
import java.util.Optional

class LiveEditCompiler(val project: Project, private val irClassCache: IrClassCache) {

  internal interface LiveEditCompilerForKotlinVersion {
    fun compileKtFile(applicationLiveEditServices: ApplicationLiveEditServices,
                      file: KtFile,
                      inputs: Collection<LiveEditCompilerInput>): List<OutputFile>
  }

  private var applicationLiveEditServices: ApplicationLiveEditServices? = null
  private val LOGGER = LogWrapper(Logger.getInstance(LiveEditCompiler::class.java))

  // Cache of fully-qualified class name to inlineable bytecode on disk or in memory
  var inlineCandidateCache = SourceInlineCandidateCache()

  // Each Deployment would invoke resetState() to ensure we have a non-null desugarer.
  private var desugarer : LiveEditDesugar? = null
  private val outputBuilderWithAnalysis = LiveEditOutputBuilder()
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
          val compilerOutput = if (KotlinPluginModeProvider.isK2Mode()) {
            LiveEditCompilerForK2(project, file.module!!)
          } else {
            LiveEditCompilerForK1(project, inlineCandidateCache)
          }.compileKtFile(applicationLiveEditServices(), file, input)

          // Run this validation *after* compilation so that PSI validation doesn't run until the class is in a state that compiles. This
          // allows the user time to undo incompatible changes without triggering an error, similar to how differ validation works.
          validatePsiDiff(input, file)

          outputBuilderWithAnalysis.getGeneratedCode(applicationLiveEditServices!!, file, compilerOutput, irClassCache,
                                                     inlineCandidateCache, outputBuilder)

          val outputs = outputBuilder.build()
          logger.dumpCompilerOutputs(outputs.classes)

          // Desugaring pass
          val request = LiveEditDesugarRequest(outputs, apiVersions)
          desugaredOutputs = desugarer!!.desugar(request)
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

    var needVibeImplementations = changedFiles.values().any { it.vibe != null }
    if (needVibeImplementations) {
      val transformer = VibeTransformerProvider.EP_NAME.extensions.firstOrNull()?.createVibeTransformer()
      if (transformer == null) {
        // Unlikely given a valid Studio install.
        throw LiveEditUpdateException.internalErrorVibeEdit("No extension for: " + VibeTransformerProvider.EP_NAME.name)
      }

      // TODO: Need better implementation for multiple prompts?
      //  What happens when Studio bot has multiple mutation for a given file?
      //  This could happen if Studio bot choose to send a mutation for us and before Live Edit completes, the read action got cancelled
      //  and then Studio bot send us another one. Possible solution is have some sort of time stamp and we combine the prompts somehow.
      //  For now, we can just concat the two prompts if needed.

      for (file in changedFiles.keySet()) {

        // For now just join all the prompts together if we have multiple vibe prompt attached to a file.
        val changes = changedFiles[file]
        val prompt = changes.mapNotNull{it.vibe}.joinToString(separator = "\n\n")

        // TODO: Consider making Live Edit a suspendable call as well.
        val result = runBlocking { transformer.transformVibe (file, prompt) }

        if (result.error.isNotEmpty()) {
          throw LiveEditUpdateException.internalErrorVibeEdit(result.error)
        }

        WriteCommandAction.runWriteCommandAction(project) {
          file.viewProvider.document.setText(result.result)
        }

      }

      // TODO: FIX THIS!

      // After we replaced the VIBE body from the result of AIDA,
      // we are racing against the K2 FIR analysis (I think).

      // I think the analysis is also a Read action so Live Edit might finish
      // before the analysis is fully done?
      Thread.sleep(1000)
    }

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

  private fun applicationLiveEditServices() = applicationLiveEditServices ?: error("not yet initialized")

  fun setApplicationLiveEditServicesForTests(applicationLiveEditServices: ApplicationLiveEditServices) {
    if (this.applicationLiveEditServices != null) {
      error("applicationLiveEditServices must not be already set")
    }
    this.applicationLiveEditServices = applicationLiveEditServices
  }

  fun resetState(applicationLiveEditServices: ApplicationLiveEditServices) {
    inlineCandidateCache.clear()
    this.applicationLiveEditServices = applicationLiveEditServices
    try {
      // Desugarer caches jar indexes and entries. It MUST be closed and recreated.
      desugarer?.close()
    } finally {
      desugarer = LiveEditDesugar(applicationLiveEditServices)
    }
  }
}
