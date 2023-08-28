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
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.internalError
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.psi.KtFile

// LiveEditCompiler has too much logic built into it/tightly coupled with it to use for pre-compilation. This class extracts just the core
// compilation logic from LiveEditCompiler until we can refactor.
class Precompiler(private val project: Project, private val inlineCandidateCache: SourceInlineCandidateCache) {

  @Trace
  fun compile(file: VirtualFile): List<ByteArray> {
    val output = mutableListOf<ByteArray>()
    ApplicationManager.getApplication().runReadAction {
      val ktFile = PsiManager.getInstance(project).findFile(file) as KtFile? ?: return@runReadAction

      // Don't precompile contents from source jars and other read-only files.
      // (Technically it is doable but Android Studio doesn't track the dependencies of those jars so they are usually full of errors)
      if (!ktFile.isWritable) {
        return@runReadAction
      }

      // Don't precompile .kts files.
      if (ktFile.isScript()) {
        return@runReadAction
      }

      compileKtFile(ktFile, output)
    }
    return output
  }

  private fun compileKtFile(file: KtFile, output: MutableList<ByteArray>) {
    val tracker = PerformanceTracker()
    var inputFiles = listOf(file)

    runWithCompileLock {
      val resolution = tracker.record("resolution_fetch") { fetchResolution(project, inputFiles) }
      val analysisResult = tracker.record("analysis") { analyze(inputFiles, resolution) }
      val inlineCandidates = analyzeSingleDepthInlinedFunctions(file, analysisResult.bindingContext, inlineCandidateCache)
      val generationState: GenerationState = try {
        tracker.record("precompile") {
          backendCodeGen(project,
                         analysisResult,
                         inputFiles,
                         inputFiles.first().module!!,
                         inlineCandidates)
        }
      } catch (e: LiveEditUpdateException) {
        if (e.error != LiveEditUpdateException.Error.UNABLE_TO_INLINE) {
          throw e
        }

        inputFiles = performInlineSourceDependencyAnalysis(resolution, file, analysisResult.bindingContext)

        val newAnalysisResult = resolution.analyzeWithAllCompilerChecks(inputFiles)
        tracker.record("precompile_inline") {
          backendCodeGen(project,
                         newAnalysisResult,
                         inputFiles,
                         inputFiles.first().module!!,
                         inlineCandidates)
        }
      } catch (t: Throwable) {
        throw internalError("Internal Error During Code Gen", t)
      }

      generationState.factory.asList()
        .filter { it.relativePath.endsWith(".class") }
        .map { it.asByteArray() }
        .forEach { output.add(it) }
    }
  }
}