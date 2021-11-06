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
import com.android.tools.idea.editors.literals.LiveEditService
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analyzer.AnalysisResult.Companion.compilationError
import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.kotlin.backend.common.phaser.PhaseConfig
import org.jetbrains.kotlin.backend.jvm.jvmPhases
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.codegen.ClassBuilderFactories
import org.jetbrains.kotlin.codegen.KotlinCodegenFacade
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.idea.project.platform
import org.jetbrains.kotlin.idea.refactoring.fqName.getKotlinFqName
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.name.FqName
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

  fun interface CodeGenCallback {
    operator fun invoke(className: String, methodSignature: String, classData: ByteArray, supportClasses: Map<String, ByteArray>)
  }

  /**
   * Compile a given set of MethodReferences to Java .class files and invoke a callback upon completion.
   */
  @Trace
  fun compile(project: Project, methods: List<LiveEditService.MethodReference>, callback: CodeGenCallback) {

    // If we (or the user) ended up setting the update time intervals to be long. It is very possible that
    // that multiple change events of the same file can be queue up. We keep track of what we have deploy
    // so we don't compile the same file twice.
    val compiled = HashSet<PsiFile>()
    for (method in methods) {
      val root = method.file

      if (root !is KtFile || compiled.contains(root)) {
        continue
      }
      val inputs = listOf(root)

      // A compile is always going to be a ReadAction because it reads an KtFile completely.
      ApplicationManager.getApplication().runReadAction {
        try {
          // Three steps process:

          // 1) Compute binding context based on any previous cached analysis results.
          //    On small edits of previous analyzed project, this operation should be below 30ms or so.
          var resolution = fetchResolution(project, inputs)
          var bindingContext = analyze(inputs, resolution)

          // 2) Invoke the backend with the inputs and the binding context computed from step 1.
          //    This is the one of the most time consuming step with 80 to 500ms turnaround depending the
          //    complexity of the input .kt file.
          var classes = backendCodeGen(project, resolution, bindingContext, inputs,
                                       AndroidLiveEditLanguageVersionSettings(root.languageVersionSettings))

          // 3) From the information we gather at the PSI changes and the output classes of Step 2, we
          //    decide which classes we want to send to the device along with what extra meta-information the
          //    agent need.
          if (!deployLiveEditToDevice(method.function, bindingContext, classes, callback)) return@runReadAction
        } catch (e : LiveEditUpdateException) {
          // TODO: We need to make deployLiveEditToDevice() atomic when there are multiple functions getting
          //       update even thought that's probably a very unlikely scenario.
          reportLiveEditError(e)
        } finally {
          compiled.add(root)
        }
      }
    }
  }

  /**
   * Fetch the resolution based on the cached service.
   */
  fun fetchResolution(project: Project, input: List<KtFile>): ResolutionFacade {
    val kotlinCacheService = KotlinCacheService.getInstance(project)
    return kotlinCacheService.getResolutionFacade(input, project.platform!!)
  }

  /**
   * Compute the BindingContext of the input file that can be used for code generation.
   *
   * This function needs to be done in a read action.
   */
  fun analyze(input: List<KtFile>, resolution: ResolutionFacade) : BindingContext {
    val analysisResult = com.android.tools.tracer.Trace.begin("analyzeWithAllCompilerChecks").use {
      resolution.analyzeWithAllCompilerChecks(input) {
        if (it.severity== Severity.ERROR) {
          throw LiveEditUpdateException.analysisError("Analyze Error. $it")
        }
      }
    }

    if (analysisResult.isError()) {
      throw LiveEditUpdateException.analysisError(analysisResult.error.message?:"No Error message")
    }

    for (diagnostic in analysisResult.bindingContext.diagnostics) {
      if (diagnostic.severity == Severity.ERROR) {
        throw LiveEditUpdateException.analysisError("Binding Context Error. $diagnostic")
      }
    }

    return analysisResult.bindingContext
  }

  /**
   * Invoke the Kotlin compiler that is part of the plugin. The compose plugin is also attached by the
   * the extension point to generate code for @composable functions.
   */
  fun backendCodeGen(project: Project, resolution: ResolutionFacade, bindingContext: BindingContext,
                     input: List<KtFile>, langVersion: LanguageVersionSettings): List<OutputFile> {
    val compilerConfiguration = CompilerConfiguration()
    compilerConfiguration.languageVersionSettings = langVersion

    // TODO: Resolve this using the project itself, somehow.
    compilerConfiguration.put(CommonConfigurationKeys.MODULE_NAME, "app_debug")

    val useComposeIR = StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT_USE_EMBEDDED_COMPILER.get();
    if (useComposeIR) {
      // Not 100% sure what causes the issue but not seeing this in the IR backend causes exceptions.
      compilerConfiguration.put(JVMConfigurationKeys.DO_NOT_CLEAR_BINDING_CONTEXT, true)
    }

    val generationStateBuilder = GenerationState.Builder(project,
                                                         ClassBuilderFactories.BINARIES,
                                                         resolution.moduleDescriptor,
                                                         bindingContext,
                                                         input,
                                                         compilerConfiguration);

    if (useComposeIR) {
      generationStateBuilder.codegenFactory(AndroidLiveEditJvmIrCodegenFactory(PhaseConfig(jvmPhases)))
    }

    val generationState = generationStateBuilder.build();

    try {
      KotlinCodegenFacade.compileCorrectFiles(generationState)
    } catch (e : Throwable) {
      handleCompilerErrors(e)
      return emptyList() // handleCompilerErrors() always throw anyways.
    }

    return generationState.factory.asList();
  }

  /**
   * Pick out what classes we need from the generated list of .class files and invoke the callback.
   */
  fun deployLiveEditToDevice(targetFunction: KtNamedFunction,
                             bindingContext: BindingContext,
                             compilerOutput: List<OutputFile>,
                             callback: CodeGenCallback): Boolean {
    val methodSignature = functionSignature(bindingContext, targetFunction)

    var elem: PsiElement = targetFunction
    while (elem.getKotlinFqName() == null || elem !is KtNamedFunction) {
      if (elem.parent == null) {
        throw LiveEditUpdateException.internalError("Could not find a non-null named method");
      }
      elem = elem.parent
    }

    val function: KtNamedFunction = elem

    // Class name can be either the class containing the function fragment or a KtFile
    var className = KtNamedDeclarationUtil.getParentFqName(function).toString()
    if (function.isTopLevel) {
      val grandParent: KtFile = function.parent as KtFile
      className = grandParent.javaFileFacadeFqName.toString()
    }

    if (className.isEmpty() || methodSignature.isEmpty()) {
      throw LiveEditUpdateException.internalError("Empty class name / method signature.")
    }

    if (compilerOutput.isEmpty()) {
      throw LiveEditUpdateException.internalError("No compiler output.")
    }

    // TODO: This needs a bit more work. Lambdas, inner classes..etc need to be mapped back.
    val internalClassName = className.replace(".", "/")
    var primaryClass = ByteArray(0)
    val supportClasses = mutableMapOf<String, ByteArray>()
    for (c in compilerOutput) {
      if (c.relativePath == "$internalClassName.class") {
        primaryClass = c.asByteArray()
      }
      else if (c.relativePath.endsWith(".class")) {
        val name = c.relativePath.substringBefore(".class")
        supportClasses[name] = c.asByteArray()
      }
    }
    callback(internalClassName, methodSignature, primaryClass, supportClasses)
    return true
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

        throw LiveEditUpdateException.knownIssue(201728545,
                                                 "unable to compile a file that reference a top level function in another source file.\n" +
                                                 "For now work around this by moving function $name inside the class.")
      }
    }
    throw LiveEditUpdateException.compilationError(e.message?:"No error message")
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