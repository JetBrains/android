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
import com.android.tools.idea.kotlin.getQualifiedName
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.codegen.ClassBuilderFactories
import org.jetbrains.kotlin.codegen.KotlinCodegenFacade
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.idea.core.KotlinCompilerIde
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.nj2k.postProcessing.type
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclarationUtil
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isBoolean
import org.jetbrains.kotlin.types.typeUtil.isByte
import org.jetbrains.kotlin.types.typeUtil.isChar
import org.jetbrains.kotlin.types.typeUtil.isDouble
import org.jetbrains.kotlin.types.typeUtil.isFloat
import org.jetbrains.kotlin.types.typeUtil.isInt
import org.jetbrains.kotlin.types.typeUtil.isLong
import org.jetbrains.kotlin.types.typeUtil.isShort
import org.jetbrains.kotlin.types.typeUtil.isUnit

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

        ApplicationManager.getApplication().runReadAction {
          val kotlinCacheService = KotlinCacheService.getInstance(project)
          var resolution = kotlinCacheService.getResolutionFacade(filesToAnalyze,
                                                                  JvmPlatforms.unspecifiedJvmPlatform)

          val analysisResult = com.android.tools.tracer.Trace.begin("analyzeWithAllCompilerChecks").use {
            resolution.analyzeWithAllCompilerChecks(filesToAnalyze)
          }

          val compilerConfiguration = CompilerConfiguration();

          compilerConfiguration.languageVersionSettings = root.languageVersionSettings

          val generationState = GenerationState.Builder(project,
                                                        ClassBuilderFactories.BINARIES,
                                                        resolution.moduleDescriptor,
                                                        analysisResult.bindingContext,
                                                        filesToAnalyze,
                                                        compilerConfiguration).build()

          var methodSignature = functionSignature(method.function)
          var className = KtNamedDeclarationUtil.getParentFqName(method.function).toString()

          if (className.isEmpty() || methodSignature.isEmpty()) {
            return@runReadAction;
          }

          com.android.tools.tracer.Trace.begin("KotlinCodegenFacade").use {
            try {
              KotlinCodegenFacade.compileCorrectFiles(generationState)
            } catch (e : Throwable) {
              handleCompilerErrors(e)
              return@runReadAction;
            }
            compiled.add(root);
          }
          val classes = generationState.factory.asList();
          if (classes.isEmpty()) {
            // TODO: Error reporting.
            print(" We don't have successful classes");
            return@runReadAction;
          }

          // TODO: This needs a bit more work. Lambdas, inner classes..etc need to be mapped back.
          for (c in classes) {
            for (m in methods) {
              if (c.relativePath.contains(className.replace(".", "/") + ".class")) {
                var clazz = className;
                var method = methodSignature;
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

  fun handleCompilerErrors(e : Throwable) {
    // Given that the IDE already provide enough information about compilation errors, there is no
    // real need to surface any compilation exception. We will just print the true cause for the
    // exception for our own debugging purpose only.
    var cause = e;
    while (cause.cause != null) {
      cause = cause.cause!!
    }
    println("Live Edit: compilation error\n ${e.message}")
  }

  fun functionSignature(function : KtNamedFunction) : String {
    var functionName = function.name
    var params = ArrayList<String>()
    function.valueParameters.forEach {
      params.add(vmName(it.type()!!))
    }
    val paramSig = params.joinToString { it }
    var returnType = vmName(function.type()!!)
    return "$functionName($paramSig)$returnType"
  }

  fun vmName(type: KotlinType): String {
    if (type.isChar()) {
      return "C"
    }
    else if (type.isByte()) {
      return "B"
    }
    else if (type.isInt()) {
      return "I"
    }
    else if (type.isLong()) {
      return "L"
    }
    else if (type.isShort()) {
      return "S"
    }
    else if (type.isFloat()) {
      return "F"
    }
    else if (type.isDouble()) {
      return "D"
    }
    else if (type.isBoolean()) {
      return "Z"
    }
    else if (type.isUnit()) {
      return "V"
    }

    val fqName = type.getQualifiedName().toString()

    if (fqName == "kotlin.String") {
      return "Ljava/lang/String;";
    }

    return "L" + fqName.replace(".", "/") + ";"
  }
}