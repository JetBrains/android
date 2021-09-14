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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.kotlin.getQualifiedName
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.backend.common.phaser.PhaseConfig
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.codegen.ClassBuilderFactories
import org.jetbrains.kotlin.codegen.KotlinCodegenFacade
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.backend.jvm.JvmIrCodegenFactory
import org.jetbrains.kotlin.backend.jvm.jvmPhases
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.nj2k.postProcessing.type
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtDeclarationModifierList
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
      val root = method.file
      if (root !is KtFile || compiled.contains(root)) {
        continue
      }

      val filesToAnalyze = listOf(root)

      ApplicationManager.getApplication().runReadAction {
        val kotlinCacheService = KotlinCacheService.getInstance(project)
        val resolution = kotlinCacheService.getResolutionFacade(filesToAnalyze,
                                                                JvmPlatforms.unspecifiedJvmPlatform)

        val analysisResult = com.android.tools.tracer.Trace.begin("analyzeWithAllCompilerChecks").use {
          resolution.analyzeWithAllCompilerChecks(filesToAnalyze)
        }

        val compilerConfiguration = CompilerConfiguration()

        compilerConfiguration.languageVersionSettings = root.languageVersionSettings

        val useComposeIR = StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT_USE_EMBEDDED_COMPILER.get();
        if (useComposeIR) {
          // Not 100% sure what causes the issue but not seeing this in the IR backend causes exceptions.
          compilerConfiguration.put(JVMConfigurationKeys.DO_NOT_CLEAR_BINDING_CONTEXT, true)
        }

        val generationStateBuilder = GenerationState.Builder(project,
                                                      ClassBuilderFactories.BINARIES,
                                                      resolution.moduleDescriptor,
                                                      analysisResult.bindingContext,
                                                      filesToAnalyze,
                                                      compilerConfiguration);

        if (useComposeIR) {
          generationStateBuilder.codegenFactory(JvmIrCodegenFactory(PhaseConfig(jvmPhases)))
        }

        val generationState = generationStateBuilder.build();

        val methodSignature = functionSignature(method.function)

        // Class name can be either the class containing the function fragment or a KtFile
        var className = KtNamedDeclarationUtil.getParentFqName(method.function).toString()
        if (method.function.isTopLevel) {
          val grandParent : KtFile = method.function.parent as KtFile
          className = grandParent.javaFileFacadeFqName.toString()
        }

        if (className.isEmpty() || methodSignature.isEmpty()) {
          return@runReadAction
        }

        com.android.tools.tracer.Trace.begin("KotlinCodegenFacade").use {
          try {
            KotlinCodegenFacade.compileCorrectFiles(generationState)
          } catch (e : Throwable) {
            handleCompilerErrors(e)
            return@runReadAction
          }
          compiled.add(root)
        }
        val classes = generationState.factory.asList();
        if (classes.isEmpty()) {
          // TODO: Error reporting.
          print(" We don't have successful classes");
          return@runReadAction
        }

        // TODO: This needs a bit more work. Lambdas, inner classes..etc need to be mapped back.
        val internalClassName = className.replace(".", "/") + ".class"
        for (c in classes) {
          if (!c.relativePath.contains(internalClassName)) {
            continue
          }
          for (m in methods) {
            callback(className, methodSignature, c.asByteArray())
            // TODO: Deal with multiple requests
            break
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
    val functionName = function.name
    val params = ArrayList<String>()
    function.valueParameters.forEach {
      params.add(vmName(it.type()!!))
    }

    // If the target function is an @Composable function, we know that the compose compiler
    // will append two more parameters when generating code. Therefore, we need to take
    // that into account and assume a composable function has two more parameter than it
    // showed in the PSI tree.
    for (annotation in (function.firstChild as KtDeclarationModifierList).annotationEntries) {
      if (annotation.getQualifiedName().toString() == "androidx.compose.runtime.Composable") {
        params.add("Landroidx/compose/runtime/Composer;")
        params.add("I")
      }
    }

    val paramSig = params.joinToString(separator = "") { it }
    val returnType = vmName(function.type()!!)
    return "$functionName($paramSig)$returnType"
  }

  fun vmName(type: KotlinType): String {
    when {
      type.isChar() -> {
        return "C"
      }
      type.isByte() -> {
        return "B"
      }
      type.isInt() -> {
        return "I"
      }
      type.isLong() -> {
        return "L"
      }
      type.isShort() -> {
        return "S"
      }
      type.isFloat() -> {
        return "F"
      }
      type.isDouble() -> {
        return "D"
      }
      type.isBoolean() -> {
        return "Z"
      }
      type.isUnit() -> {
        return "V"
      }
      else -> {
        val fqName = type.getQualifiedName().toString()

        if (fqName == "kotlin.String") {
          return "Ljava/lang/String;"
        }

        return "L" + fqName.replace(".", "/") + ";"
      }
    }
  }
}
