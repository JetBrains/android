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
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.psi.KtFile

// LiveEditCompiler has too much logic built into it/tightly coupled with it to use for pre-compilation. This class extracts just the core
// compilation logic from LiveEditCompiler until we can refactor.
class Precompiler(private val project: Project, private val inlineCandidateCache: SourceInlineCandidateCache) {
  @Trace
  @RequiresReadLock
  fun compile(file: KtFile, module: com.intellij.openapi.module.Module? = null): List<ByteArray> {
    val output = mutableListOf<ByteArray>()
    var inputFiles = listOf(file)
    runWithCompileLock {
      val resolution = fetchResolution(project, inputFiles)
      val analysisResult = analyze(inputFiles, resolution)
      val inlineCandidates = analyzeSingleDepthInlinedFunctions(file, analysisResult.bindingContext, inlineCandidateCache)
      val generationState: GenerationState = try {
        backendCodeGen(project, analysisResult, inputFiles, module ?: inputFiles.first().module!!, inlineCandidates)
      } catch (e: LiveEditUpdateException) {
        if (e.error != LiveEditUpdateException.Error.UNABLE_TO_INLINE) {
          throw e
        }

        inputFiles = performInlineSourceDependencyAnalysis(resolution, file, analysisResult.bindingContext)

        val newAnalysisResult = resolution.analyzeWithAllCompilerChecks(inputFiles)
        backendCodeGen(project, newAnalysisResult, inputFiles, inputFiles.first().module!!, inlineCandidates)
      }

      generationState.factory.asList()
        .filter { it.relativePath.endsWith(".class") }
        .map { it.asByteArray() }
        .forEach { output.add(it) }
    }

    return output
  }
}