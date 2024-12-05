/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.idea.run.deployment.liveedit.tokens.ApplicationLiveEditServices
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.kotlin.psi.KtFile

internal class LiveEditCompilerForK1(
  private val project: Project,
  private val inlineCandidateCache: SourceInlineCandidateCache
) : LiveEditCompiler.LiveEditCompilerForKotlinVersion {

  override fun compileKtFile(applicationLiveEditServices: ApplicationLiveEditServices,
                             file: KtFile,
                             inputs: Collection<LiveEditCompilerInput>): List<OutputFile> {
    val tracker = PerformanceTracker()
    var inputFiles = listOf(file)

    return runWithCompileLock {
      readActionPrebuildChecks(project, file)

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
          backendCodeGen(
            applicationLiveEditServices,
            project,
            analysisResult,
            inputFiles,
            ModuleUtilCore.findModuleForFile(inputFiles.first())!!,
            inlineCandidates
          )
        }
      } catch (e: LiveEditUpdateException) {
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
          backendCodeGen(
            applicationLiveEditServices,
            project,
            newAnalysisResult,
            inputFiles,
            ModuleUtilCore.findModuleForFile(inputFiles.first())!!,
            inlineCandidates
          )
        }
      } catch (p: ProcessCanceledException) {
        throw p
      } catch (t: Throwable) {
        throw LiveEditUpdateException.internalErrorCodeGenException(file, t)
      }
      generationState.factory.asList()
    }
  }
}