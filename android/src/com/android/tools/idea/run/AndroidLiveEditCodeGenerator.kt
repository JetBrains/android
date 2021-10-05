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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.backend.common.phaser.PhaseConfig
import org.jetbrains.kotlin.backend.jvm.jvmPhases
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.codegen.ClassBuilderFactories
import org.jetbrains.kotlin.codegen.KotlinCodegenFacade
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclarationUtil
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.components.hasDefaultValue
import org.jetbrains.kotlin.resolve.jvm.jvmSignature.KotlinToJvmSignatureMapper
import java.lang.Math.ceil
import java.util.ServiceLoader

const val SLOTS_PER_INT = 10
const val BITS_PER_INT = 31

class AndroidLiveEditCodeGenerator {

  private val SIGNATURE_MAPPER = ServiceLoader.load(
    KotlinToJvmSignatureMapper::class.java,
    KotlinToJvmSignatureMapper::class.java.classLoader
  ).iterator().next()

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

        if (analysisResult.isError()) {
          println("Live Edit: resolution analysis error\n ${analysisResult.error.message}")
          return@runReadAction
        }

        var bindingContext = analysisResult.bindingContext

        for (diagnostic in bindingContext.diagnostics) {
          if (diagnostic.severity == Severity.ERROR) {
            println("Live Edit: resolution analysis error\n $diagnostic")
            return@runReadAction
          }
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
                                                      bindingContext,
                                                      filesToAnalyze,
                                                      compilerConfiguration);

        if (useComposeIR) {
          generationStateBuilder.codegenFactory(AndroidLiveEditJvmIrCodegenFactory(PhaseConfig(jvmPhases)))
        }

        val generationState = generationStateBuilder.build();

        val methodSignature = functionSignature(bindingContext, method.function)

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
      var message = cause.message!!
      if (message.contains("Unhandled intrinsic in ExpressionCodegen")) {
        var nameStart = message.indexOf("name:") + "name:".length
        var nameEnd = message.indexOf(' ', nameStart)
        var name = message.substring(nameStart, nameEnd)

        println("Live Edit: Bug (b/201728545) unable to compile a file that reference a top level function in another source file. " +
                " For now work around this by moving function $name inside the class.")
        return
      }
    }
    println("Live Edit: compilation error\n ${e.message}")
  }

  fun functionSignature(context: BindingContext, function : KtNamedFunction) : String {
    val desc = context[BindingContext.FUNCTION, function]
    val signature = SIGNATURE_MAPPER.mapToJvmMethodSignature(desc!!)

    if (!desc.annotations.hasAnnotation(FqName("androidx.compose.runtime.Composable"))) {
      // This is a pure Kotlin function and not a Composable. The method signature will not
      // be changed by the compose compiler at all.
      return signature.toString()
    }

    // The number of synthetic int param added to a function is the total of:
    // 1. max (1, ceil(numParameters / 10))
    // 2. 0 default int parameters if none of the N parameters have default expressions
    // 3. ceil(N / 31) N parameters have default expressions if there are any defaults
    //
    // The formula follows the one found in ComposableFunctionBodyTransformer.kt

    var totalSyntheticParamCount = 0
    var realValueParamsCount = desc.valueParameters.size

    if (realValueParamsCount == 0) {
      totalSyntheticParamCount += 1;
    } else {
      val totalParams = realValueParamsCount
      totalSyntheticParamCount += ceil(totalParams.toDouble() / SLOTS_PER_INT.toDouble()).toInt()
    }

    var numDefaults = desc.valueParameters.count { it.hasDefaultValue() }

    if (desc.valueParameters.size != 0 && numDefaults != 0) {
      totalSyntheticParamCount += ceil(realValueParamsCount.toDouble() / BITS_PER_INT.toDouble()).toInt()
    }

    var target = signature.toString()

    // Add the Composer parameter as well as number of additional ints computed above.
    var additionalParams = "Landroidx/compose/runtime/Composer;"
    for (x in 1 .. totalSyntheticParamCount) additionalParams += "I"
    target = target.replace(")", additionalParams + ")")
    return target
  }
}