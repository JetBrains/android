/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.editors.liveedit.LiveEditAdvancedConfiguration
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.backend.common.phaser.PhaseConfig
import org.jetbrains.kotlin.backend.jvm.FacadeClassSourceShimForFragmentCompilation
import org.jetbrains.kotlin.backend.jvm.JvmGeneratorExtensionsImpl
import org.jetbrains.kotlin.backend.jvm.JvmIrCodegenFactory
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.codegen.ClassBuilderFactories
import org.jetbrains.kotlin.codegen.KotlinCodegenFacade
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.InvalidModuleException
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.core.util.analyzeInlinedFunctions
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.load.kotlin.toSourceElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.source.PsiSourceFile
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedContainerSource
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private fun handleCompilerErrors(e: Throwable) {
  // These should be rethrown as per the javadoc for ProcessCanceledException. This allows the
  // internal IDE code for handling read/write actions to function as expected.
  if (e is ProcessCanceledException) {
    throw e
  }

  if (e is InvalidModuleException) {
    throw ProcessCanceledException(e)
  }

  // Given that the IDE already provide enough information about compilation errors, there is no
  // real need to surface any compilation exception. We will just print the true cause for the
  // exception for our own debugging purpose only.
  var cause = e;
  while (cause.cause != null) {
    cause = cause.cause!!

    if (cause is InvalidModuleException) {
      throw ProcessCanceledException(e)
    }

    // The Kotlin compiler probably shouldn't be swallowing these, but since we can't change that,
    // detect and re-throw them here as the proper exception type.
    if (cause is ProcessCanceledException) {
      throw cause
    }

    cause.message?.let { message ->
      if (message.contains("Back-end (JVM) Internal error: Couldn't inline method call")) {
        // We currently don't support inline function calls to another source code file.

        val nameStart = message.indexOf("Couldn't inline method call: CALL '") + "Couldn't inline method call: CALL '".length
        val nameEnd = message.indexOf("'", nameStart)
        val name = message.substring(nameStart, nameEnd)

        throw LiveEditUpdateException.inlineFailure("Unable to update function that references" +
                                                    " an inline function from another source file: $name")
      }
    }
  }
  throw LiveEditUpdateException.compilationError(e.message ?: "No error message", null, e)
}

/**
 * Scope containing the different phases of the compilation that can be executed under
 * with [runWithCompileLock].
 */
interface CompileScope {
  /**
   * Fetch the resolution based on the cached service.
   */
  fun fetchResolution(project: Project, input: List<KtFile>): ResolutionFacade

  /**
   * Given a source file A.kt and an initial analysis result, compute a list of source files (A.kt included) need in order to correctly
   * compile the A.kt and any inline functions it needs from another source file.
   */
  fun performInlineSourceDependencyAnalysis(resolution: ResolutionFacade, file: KtFile, bindingContext: BindingContext) : List<KtFile>

    /**
   * Compute the BindingContext of the input file that can be used for code generation.
   *
   * This function needs to be done in a read action.
   */
  fun analyze(input: List<KtFile>, resolution: ResolutionFacade): BindingContext

  /**
   * Invoke the Kotlin compiler that is part of the plugin. The compose plugin is also attached by the
   * the extension point to generate code for @composable functions.
   */
  fun backendCodeGen(project: Project, resolution: ResolutionFacade,
                     bindingContext: BindingContext,
                     input: List<KtFile>,
                     module: Module,
                     inlineClassRequest : Set<SourceInlineCandidate>?,
                     langVersion: LanguageVersionSettings): GenerationState
}

private object CompileScopeImpl : CompileScope {
  /**
   * Lock that ensures that [runWithCompileLock] only allows one execution at a time.
   */
  val compileLock = ReentrantLock()

  override fun fetchResolution(project: Project, input: List<KtFile>): ResolutionFacade {
    val kotlinCacheService = KotlinCacheService.getInstance(project)
    return kotlinCacheService.getResolutionFacade(input)
  }

  override fun performInlineSourceDependencyAnalysis(resolution: ResolutionFacade, file: KtFile, bindingContext: BindingContext) : List<KtFile> {
    val (_, filesToCompile) = analyzeInlinedFunctions(resolution, file, false, bindingContext)
    return filesToCompile
  }

  override fun analyze(input: List<KtFile>, resolution: ResolutionFacade): BindingContext {
    val trace = com.android.tools.tracer.Trace.begin("analyzeWithAllCompilerChecks")
    try {
      var exception: LiveEditUpdateException? = null
      val analysisResult = resolution.analyzeWithAllCompilerChecks(input) {
        if (it.severity == Severity.ERROR) {
          exception = LiveEditUpdateException.analysisError("Analyze Error. $it", it.psiFile)
        }
      }
      if (exception != null) {
        throw exception!!
      }

      if (analysisResult.isError()) {
        throw LiveEditUpdateException.analysisError(analysisResult.error.message ?: "No Error message")
      }

      for (diagnostic in analysisResult.bindingContext.diagnostics) {
        if (diagnostic.severity == Severity.ERROR) {
          throw LiveEditUpdateException.analysisError("Binding Context Error. $diagnostic", diagnostic.psiFile)
        }
      }

      return analysisResult.bindingContext
    }
    finally {
      trace.close()
    }
  }

  override fun backendCodeGen(project: Project, resolution: ResolutionFacade, bindingContext: BindingContext, input: List<KtFile>,
                              module: Module,
                              inlineClassRequest : Set<SourceInlineCandidate>?, langVersion: LanguageVersionSettings): GenerationState {

    val compilerConfiguration = CompilerConfiguration()
    compilerConfiguration.languageVersionSettings = langVersion

    compilerConfiguration.put(CommonConfigurationKeys.MODULE_NAME, module.name)
    KotlinFacet.get(module)?.let { kotlinFacet ->
      (kotlinFacet.configuration.settings.compilerArguments as K2JVMCompilerArguments).moduleName?.let {
        compilerConfiguration.put(CommonConfigurationKeys.MODULE_NAME, it)
      }
    }

    val useComposeIR = LiveEditAdvancedConfiguration.getInstance().useEmbeddedCompiler
    if (useComposeIR) {
      // Not 100% sure what causes the issue but not seeing this in the IR backend causes exceptions.
      compilerConfiguration.put(JVMConfigurationKeys.DO_NOT_CLEAR_BINDING_CONTEXT, true)

      // We don't support INVOKE_DYNAMIC in the interpeter at the moment.
      compilerConfiguration.put(JVMConfigurationKeys.SAM_CONVERSIONS, JvmClosureGenerationScheme.CLASS)
      compilerConfiguration.put(JVMConfigurationKeys.LAMBDAS, JvmClosureGenerationScheme.CLASS)
    }

    val generationStateBuilder = GenerationState.Builder(project,
                                                         ClassBuilderFactories.BINARIES,
                                                         resolution.moduleDescriptor,
                                                         bindingContext,
                                                         input,
                                                         compilerConfiguration);

    if (useComposeIR) {
      generationStateBuilder.codegenFactory(JvmIrCodegenFactory(
        compilerConfiguration,
        PhaseConfig(org.jetbrains.kotlin.backend.jvm.jvmPhases),
        jvmGeneratorExtensions = object : JvmGeneratorExtensionsImpl(compilerConfiguration) {
          override fun getContainerSource(descriptor: DeclarationDescriptor): DeserializedContainerSource? {
            val psiSourceFile =
              descriptor.toSourceElement.containingFile as? PsiSourceFile ?: return super.getContainerSource(descriptor)
            return FacadeClassSourceShimForFragmentCompilation(psiSourceFile)
          }
        },
        shouldStubAndNotLinkUnboundSymbols = true,
      ))
    }

    val generationState = generationStateBuilder.build();
    inlineClassRequest?.forEach {
      it.fetchByteCodeFromBuildIfNeeded()
      it.fillInlineCache(generationState.inlineCache)
    }

    try {
      KotlinCodegenFacade.compileCorrectFiles(generationState)
    } catch (e: Throwable) {
      handleCompilerErrors(e) // handleCompilerErrors() always throws.
    }

    return generationState
  }
}

/**
 * Executes the given [callable] in the context of a [CompileScope] that allows running the different compilation
 * phases.
 * Only one caller of this method will have access to the [CompileScope] at at time.
 */
fun <T> runWithCompileLock(callable: CompileScope.() -> T) = CompileScopeImpl.compileLock.withLock {
  CompileScopeImpl.callable()
}