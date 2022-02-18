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
package com.android.tools.idea.compose.preview.fast

import com.android.tools.idea.editors.liveedit.LiveEditConfig
import com.android.tools.idea.run.deployment.liveedit.AndroidLiveEditJvmIrCodegenFactory
import com.android.tools.idea.run.deployment.liveedit.AndroidLiveEditLanguageVersionSettings
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.io.createFile
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
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.idea.project.platform
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Implementation of the [CompilerDaemonClient] that uses the embedded compiler in Android Studio. This allows
 * to compile fragments of code in-process similar to how Live Edit does for the emulator.
 */
class EmbeddedCompilerClientImpl(private val project: Project, private val log: Logger) : CompilerDaemonClient {
  private val _isRunning = AtomicBoolean(false)
  override val isRunning: Boolean
    get() = _isRunning.get()

  /**
   * Compute the BindingContext of the input file that can be used for code generation.
   *
   * This function needs to be done in a read action.
   */
  private fun analyze(input: List<KtFile>, resolution: ResolutionFacade): BindingContext {
    log.debug("analyze")
    var exception: LiveEditUpdateException? = null
    val analysisResult = resolution.analyzeWithAllCompilerChecks(input) {
      if (it.severity == Severity.ERROR) {
        exception = LiveEditUpdateException.analysisError("Analyze Error. $it")
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
        throw LiveEditUpdateException.analysisError("Binding Context Error. $diagnostic")
      }
    }

    log.debug("analyze result $analysisResult")
    return analysisResult.bindingContext
  }

  private fun compileKtFiles(inputs: List<KtFile>, outputDirectory: Path) {
    log.debug("compileKtFile($inputs, $outputDirectory)")
    val resolution = runReadAction {
      log.debug("fetchResolution")
      KotlinCacheService.getInstance(project).getResolutionFacade(inputs, project.platform!!)
    }
    val bindingContext = runReadAction { analyze(inputs, resolution) }

    val languageVersionSettings = inputs.first().languageVersionSettings
    val generationState = runReadAction {
      backendCodeGen(resolution, bindingContext, inputs, AndroidLiveEditLanguageVersionSettings(languageVersionSettings))
    }
    generationState.factory.asList().forEach {
      val path = outputDirectory.resolve(it.relativePath)
      path.createFile()
      Files.write(path, it.asByteArray())
    }
  }

  /**
   * Invoke the Kotlin compiler that is part of the plugin. The compose plugin is also attached by the
   * the extension point to generate code for @composable functions.
   */
  private fun backendCodeGen(resolution: ResolutionFacade, bindingContext: BindingContext,
                             input: List<KtFile>, langVersion: LanguageVersionSettings): GenerationState {
    val compilerConfiguration = CompilerConfiguration()
    compilerConfiguration.languageVersionSettings = langVersion

    // TODO: Resolve this using the project itself, somehow.
    compilerConfiguration.put(CommonConfigurationKeys.MODULE_NAME, "app_debug")

    val useComposeIR = LiveEditConfig.getInstance().useEmbeddedCompiler
    if (useComposeIR) {
      // Not 100% sure what causes the issue but not setting this in the IR backend causes exceptions.
      compilerConfiguration.put(JVMConfigurationKeys.DO_NOT_CLEAR_BINDING_CONTEXT, true)
    }

    val generationStateBuilder = GenerationState.Builder(project,
                                                         ClassBuilderFactories.BINARIES,
                                                         resolution.moduleDescriptor,
                                                         bindingContext,
                                                         input,
                                                         compilerConfiguration);

    if (useComposeIR) {
      generationStateBuilder.codegenFactory(AndroidLiveEditJvmIrCodegenFactory(compilerConfiguration, PhaseConfig(jvmPhases)))
    }

    val generationState = generationStateBuilder.build();

    try {
      KotlinCodegenFacade.compileCorrectFiles(generationState)
    }
    catch (e: Throwable) {
      handleCompilerErrors(e) // handleCompilerErrors() always throws.
    }

    return generationState
  }

  private fun handleCompilerErrors(e: Throwable) {
    // These should be rethrown as per the javadoc for ProcessCanceledException. This allows the
    // internal IDE code for handling read/write actions to function as expected.
    if (e is ProcessCanceledException) {
      throw e
    }

    // Given that the IDE already provide enough information about compilation errors, there is no
    // real need to surface any compilation exception. We will just print the true cause for the
    // exception for our own debugging purpose only.
    var cause = e;
    while (cause.cause != null) {
      cause = cause.cause!!

      // The Kotlin compiler probably shouldn't be swallowing these, but since we can't change that,
      // detect and re-throw them here as the proper exception type.
      if (cause is ProcessCanceledException) {
        throw cause
      }

      val message = cause.message!!
      if (message.contains("Unhandled intrinsic in ExpressionCodegen")) {
        val nameStart = message.indexOf("name:") + "name:".length
        val nameEnd = message.indexOf(' ', nameStart)
        val name = message.substring(nameStart, nameEnd)

        throw LiveEditUpdateException.knownIssue(201728545,
                                                 "unable to compile a file that reference a top level function in another source file.\n" +
                                                 "For now work around this by moving function $name inside the class.")
      }
    }
    throw LiveEditUpdateException.compilationError(e.message ?: "No error message", e)
  }

  override suspend fun compileRequest(
    files: Collection<PsiFile>,
    module: Module,
    outputDirectory: Path,
    indicator: ProgressIndicator): CompilationResult {
    _isRunning.set(true)
    return try {
      val inputs = files.filterIsInstance<KtFile>().toList()
      try {
        compileKtFiles(inputs, outputDirectory = outputDirectory)
        CompilationResult.Success
      }
      catch (t: Throwable) {
        return CompilationResult.RequestException(t)
      }
    }
    finally {
      _isRunning.set(false)
    }
  }

  override fun dispose() {}
}