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

import com.android.tools.idea.gradle.structure.configurables.android.ChildModelConfigurable
import com.android.tools.idea.gradle.structure.configurables.ui.modules.SigningConfigConfigPanel
import com.android.tools.idea.gradle.structure.configurables.ui.properties.SimplePropertyEditor
import com.android.tools.idea.gradle.structure.model.android.PsSigningConfig
import com.android.tools.idea.gradle.structure.model.meta.PropertiesUiModel
import com.android.tools.idea.gradle.structure.model.meta.uiProperty
import javax.swing.JComponent

class SigningConfigConfigurable(private val signingConfig: PsSigningConfig) : ChildModelConfigurable<PsSigningConfig>(signingConfig) {
  override fun getBannerSlogan() = "Signing Config '${signingConfig.name}'"

  override fun createOptionsPanel(): JComponent = SigningConfigConfigPanel(signingConfig).component
}

fun signingConfigPropertiesModel() =
    PropertiesUiModel(
        listOf(
            uiProperty(PsSigningConfig.SigningConfigDescriptors.storeFile, ::SimplePropertyEditor),
            uiProperty(PsSigningConfig.SigningConfigDescriptors.storePassword, ::SimplePropertyEditor),
// TODO(b/70501607): uiProperty(PsSigningConfig.SigningConfigDescriptors.storeType, ::SimplePropertyEditor),
            uiProperty(PsSigningConfig.SigningConfigDescriptors.keyAlias, ::SimplePropertyEditor)
// TODO(b/70501607): uiProperty(PsSigningConfig.SigningConfigDescriptors.keyPassword, ::SimplePropertyEditor)
        ))

