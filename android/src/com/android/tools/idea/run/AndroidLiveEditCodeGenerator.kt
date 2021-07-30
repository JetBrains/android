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
package com.android.tools.idea.run

import com.android.annotations.Trace
import com.android.tools.idea.editors.literals.LiveEditService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.codegen.ClassBuilderFactories
import org.jetbrains.kotlin.codegen.KotlinCodegenFacade
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile

class AndroidLiveEditCodeGenerator {

  /**
   * Compile a given set of MethodReferences to Java .class files and invoke a callback upon completion.
   */
  @Trace
  fun compile(project: Project, methods: List<LiveEditService.MethodReference>,
              callback: (className: String, methodSignature: String, classData: ByteArray) -> Unit) {
    val compiled = HashSet<PsiFile>()
    for (method in methods) {
      var root = method.file
      if (root != null && root is KtFile && !compiled.contains(root)) {
        val filesToAnalyze = listOf(root)
        val kotlinCacheService = KotlinCacheService.getInstance(project)
        var resolution = kotlinCacheService.getResolutionFacade(filesToAnalyze,
                                                                JvmPlatforms.unspecifiedJvmPlatform)

        val analysisResult = com.android.tools.tracer.Trace.begin("analyzeWithAllCompilerChecks").use {
          resolution.analyzeWithAllCompilerChecks(filesToAnalyze)
        }

        val compilerConfiguration = CompilerConfiguration();

        // TODO: How do we find out?
        // compilerConfiguration.languageVersionSettings = codeFragment.languageVersionSettings <-- this need to be updated.

        val generationState = GenerationState.Builder(project,
                                                      ClassBuilderFactories.BINARIES,
                                                      resolution.moduleDescriptor,
                                                      analysisResult.bindingContext,
                                                      filesToAnalyze,
                                                      compilerConfiguration).build()

    ApplicationManager.getApplication().runReadAction {

          com.android.tools.tracer.Trace.begin("KotlinCodegenFacade").use {
            KotlinCodegenFacade.compileCorrectFiles(generationState)
            compiled.add(root);
          }
          val classes = generationState.factory.asList();
          if (classes.isEmpty()) {
            // TODO: Error reporting.
            print(" We don't have successful classes");
          }

          // TODO: This needs a bit more work. Lambdas, inner classes..etc need to be mapped back.
          for (c in classes) {
            for (m in methods) {
              if (c.relativePath.contains(m.className.replace(".", "/") + ".class")) {
                var clazz = m.className;
                var method = m.methodSignature;
                var data = c.asByteArray();
                callback(clazz, method, data)

                // TODO: Deal with multiple requests
                break
              }
            }
          }
        }
      }
    }
  }
}