/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit.k2

import com.android.tools.idea.run.deployment.liveedit.IrClassCache
import com.android.tools.idea.run.deployment.liveedit.LiveEditCompilerInput
import com.android.tools.idea.run.deployment.liveedit.LiveEditCompilerOutput
import com.android.tools.idea.run.deployment.liveedit.LiveEditOutputBuilder
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.compilationError
import com.android.tools.idea.run.deployment.liveedit.ReadActionPrebuildChecks
import com.android.tools.idea.run.deployment.liveedit.SourceInlineCandidateCache
import com.android.tools.idea.run.deployment.liveedit.checkPsiErrorElement
import com.android.tools.idea.run.deployment.liveedit.runWithCompileLock
import com.android.tools.idea.run.deployment.liveedit.setOptions
import com.android.tools.idea.run.deployment.liveedit.validatePsiDiff
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaCompilationResult
import org.jetbrains.kotlin.analysis.api.components.KaCompilerTarget
import org.jetbrains.kotlin.analysis.api.diagnostics.getDefaultMessageWithFactoryName
import org.jetbrains.kotlin.codegen.ClassBuilderFactories
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.psi.KtFile

internal class LiveEditCompilerForK2(
  private val inlineCandidateCache: SourceInlineCandidateCache,
  private val irClassCache: IrClassCache,
  private val outputBuilder: LiveEditOutputBuilder,
  private val module: Module? = null,
) {
  @OptIn(KaExperimentalApi::class)
  fun compile(file: KtFile, inputs: Collection<LiveEditCompilerInput>, output: LiveEditCompilerOutput.Builder) {
    runWithCompileLock {
      ReadActionPrebuildChecks(file)
      val result = backendCodeGenForK2(file, module)
      val compilerOutput = result.output.map { OutputFileForKtCompiledFile(it) }

      // Run this validation *after* compilation so that PSI validation doesn't run until the class is in a state that compiles. This
      // allows the user time to undo incompatible changes without triggering an error, similar to how differ validation works.
      validatePsiDiff(inputs, file)

      outputBuilder.getGeneratedCode(file, compilerOutput, irClassCache, inlineCandidateCache, output)
      return@runWithCompileLock
    }
  }
}

@KaExperimentalApi
fun backendCodeGenForK2(file: KtFile, module: Module?): KaCompilationResult.Success {
  module?.let {
    if (file.module != it) {
      throw LiveEditUpdateException.internalErrorFileOutsideModule(file)
    }
  }

  // Since K2 compile AA reports syntax error, this may be unnecessary, but it throws an exception early when it has a syntax error.
  // In other words, there is no performance penalty from this early check. Let's keep it because there is no guarantee that
  // K2 compile AA covers all cases.
  listOf(file).checkPsiErrorElement()

  val configuration = CompilerConfiguration().apply {
    setModuleName(file)
    setOptions(file.languageVersionSettings)
  }

  // TODO(316965795): Check the performance and the responsiveness once we complete K2 LE implementation.
  //                  Add/remove ProgressManager.checkCanceled() based on the performance and the responsiveness.
  ProgressManager.checkCanceled()

  analyze(file) {
    val result = this@analyze.compile(file, configuration, KaCompilerTarget.Jvm(ClassBuilderFactories.BINARIES)) {
      // This is a lambda for `allowedErrorFilter` parameter. `compiler` API internally filters diagnostic errors with
      // `allowedErrorFilter`. If `allowedErrorFilter(diagnosticError)` is true, the error will not be reported.
      // Since we want to always report the diagnostic errors, we just return `false` here.
      false
    }
    when (result) {
      is KaCompilationResult.Success -> return result
      is KaCompilationResult.Failure -> throw compilationError(result.errors.joinToString { it.getDefaultMessageWithFactoryName() })
    }
  }
}

private fun CompilerConfiguration.setModuleName(file: KtFile) {
  val containingModule = file.module
  if (containingModule != null) {
    put(CommonConfigurationKeys.MODULE_NAME, containingModule.name)
  }
}