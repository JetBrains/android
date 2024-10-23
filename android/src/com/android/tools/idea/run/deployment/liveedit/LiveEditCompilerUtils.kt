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
package com.android.tools.idea.run.deployment.liveedit

import com.android.tools.idea.flags.StudioFlags
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.descendantsOfType
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.psi.KtFile

internal fun validatePsiDiff(inputs: Collection<LiveEditCompilerInput>, file: KtFile) {
  val oldState = inputs.first().oldState // All these should be the same; they all refer to the same file. This will get refactored.
  val newState = getPsiValidationState(file)
  val errors = validatePsiChanges(oldState, newState)
  if (errors.isNotEmpty()) {
    throw errors[0]
  }
}

internal fun List<KtFile>.checkPsiErrorElement() {
  forEach { file ->
    val errorElement = file.descendantsOfType<PsiErrorElement>().firstOrNull()
    errorElement?.let { throw LiveEditUpdateException.compilationError(it.errorDescription, it.containingFile, null) }
  }
}

internal fun CompilerConfiguration.setOptions(languageVersionSettings: LanguageVersionSettings) {
  put(CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS, languageVersionSettings)

  // Needed so we can diff changes to method parameters and parameter annotations.
  put(JVMConfigurationKeys.PARAMETERS_METADATA, true)

  // Not 100% sure what causes the issue but not seeing this in the IR backend causes exceptions.
  if (KotlinPluginModeProvider.isK1Mode()) {
    put(JVMConfigurationKeys.DO_NOT_CLEAR_BINDING_CONTEXT, true)
  }

  when(StudioFlags.CLOSURE_SCHEME.get()!!) {
    StudioFlags.ClosureScheme.CLASS -> {
      put(JVMConfigurationKeys.SAM_CONVERSIONS, JvmClosureGenerationScheme.CLASS)
      put(JVMConfigurationKeys.LAMBDAS, JvmClosureGenerationScheme.CLASS)
    }
    StudioFlags.ClosureScheme.INDY -> {
      put(JVMConfigurationKeys.SAM_CONVERSIONS, JvmClosureGenerationScheme.INDY)
      put(JVMConfigurationKeys.LAMBDAS, JvmClosureGenerationScheme.INDY)
    }
  }

  // Link via signatures and not descriptors.
  //
  // This ensures that even if the project has descriptors for basic types from multiple stdlib
  // versions, they all end up mapping to the basic types from the stdlib used for the current
  // compilation.
  //
  // See b/256957527 for details.
  put(JVMConfigurationKeys.LINK_VIA_SIGNATURES, true)
}
