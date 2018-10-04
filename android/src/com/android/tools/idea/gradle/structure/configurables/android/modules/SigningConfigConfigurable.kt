/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.android.modules

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.android.ChildModelConfigurable
import com.android.tools.idea.gradle.structure.configurables.ui.PropertiesUiModel
import com.android.tools.idea.gradle.structure.configurables.ui.modules.SigningConfigConfigPanel
import com.android.tools.idea.gradle.structure.configurables.ui.simplePropertyEditor
import com.android.tools.idea.gradle.structure.configurables.ui.uiProperty
import com.android.tools.idea.gradle.structure.model.android.PsSigningConfig

class SigningConfigConfigurable(private val signingConfig: PsSigningConfig, val context: PsContext)
  : ChildModelConfigurable<PsSigningConfig, SigningConfigConfigPanel>(
  signingConfig) {
  override fun getBannerSlogan() = "Signing Config '${signingConfig.name}'"

  override fun createPanel(): SigningConfigConfigPanel = SigningConfigConfigPanel(signingConfig, context)
}

fun signingConfigPropertiesModel() =
  PropertiesUiModel(
    listOf(
      uiProperty(PsSigningConfig.SigningConfigDescriptors.storeFile, ::simplePropertyEditor),
      uiProperty(PsSigningConfig.SigningConfigDescriptors.storePassword, ::simplePropertyEditor),
// TODO(b/70501607): uiProperty(PsSigningConfig.SigningConfigDescriptors.storeType, ::simplePropertyEditor),
      uiProperty(PsSigningConfig.SigningConfigDescriptors.keyAlias, ::simplePropertyEditor),
      uiProperty(PsSigningConfig.SigningConfigDescriptors.keyPassword, ::simplePropertyEditor)
    ))

