/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.compose.aa

import androidx.compose.compiler.plugins.kotlin.ComposeConfiguration
import androidx.compose.compiler.plugins.kotlin.ComposePluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.IrVerificationMode
import org.jetbrains.kotlin.idea.fir.extensions.KotlinFirCompilerPluginConfigurationForIdeProvider

class K2ComposeCompilerConfigurationProvider : KotlinFirCompilerPluginConfigurationForIdeProvider {
  override fun provideCompilerConfigurationWithCustomOptions(
    original: CompilerConfiguration
  ): CompilerConfiguration {
    return original.copy().apply {
      /* We have to pass
         - generateFunctionKeyMetaClasses = true,
         - useK2 = KotlinPluginModeProvider.isK2Mode(),
         - featureFlags = FeatureFlags().apply { setFeature(FeatureFlag.IntrinsicRemember, false) },
         - skipIfRuntimeNotFound = true,
         - and messageCollector
        to ComposeIrGenerationExtension()
      */
      put(ComposeConfiguration.LIVE_LITERALS_ENABLED_KEY, false)
      put(ComposeConfiguration.LIVE_LITERALS_V2_ENABLED_KEY, false)
      put(ComposeConfiguration.GENERATE_FUNCTION_KEY_META_ANNOTATION_KEY, true)
      put(ComposeConfiguration.SOURCE_INFORMATION_ENABLED_KEY, false)
      put(ComposeConfiguration.INTRINSIC_REMEMBER_OPTIMIZATION_ENABLED_KEY, false)
      put(ComposeConfiguration.DECOYS_ENABLED_KEY, false)
      put(CommonConfigurationKeys.VERIFY_IR, IrVerificationMode.NONE)

      put(ComposeConfiguration.NON_SKIPPING_GROUP_OPTIMIZATION_ENABLED_KEY, false)
      put(ComposeConfiguration.STRONG_SKIPPING_ENABLED_KEY, false)

      put(ComposeConfiguration.TRACE_MARKERS_ENABLED_KEY, true)

      // Loading the compose version class from the compose runtime will cause an exception for
      // a non-compose module because of the missing dependency on compose runtime. This option
      // prevents the compose compiler plugin from throwing an exception when the compose
      // version class is not found.
      put(ComposeConfiguration.SKIP_IR_LOWERING_IF_RUNTIME_NOT_FOUND_KEY, true)
    }
  }

  @OptIn(ExperimentalCompilerApi::class)
  override fun isConfigurationProviderForCompilerPlugin(
    registrar: CompilerPluginRegistrar
  ): Boolean {
    return registrar.javaClass == ComposePluginRegistrar::class.java
  }
}