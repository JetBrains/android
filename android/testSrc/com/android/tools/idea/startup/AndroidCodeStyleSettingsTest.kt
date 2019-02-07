/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.startup

import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.psi.codeStyle.CodeStyleSchemes
import org.jetbrains.android.AndroidTestCase

private const val DEFAULT_RIGHT_MARGIN = 100
private const val CUSTOM_RIGHT_MARGIN = 90

class AndroidCodeStyleSettingsTest : AndroidTestCase() {
  fun testInitializeDefaults() {
    val propertiesComponent = PropertiesComponent.getInstance()
    val schemes = CodeStyleSchemes.getInstance()
    val defaultScheme = schemes.defaultScheme
    val defaultSettings = defaultScheme.codeStyleSettings

    propertiesComponent.setValue(AndroidCodeStyleSettings.CONFIG_V1, false)
    AndroidCodeStyleSettings.initializeDefaults(propertiesComponent)
    assertThat(defaultSettings.defaultRightMargin).isEqualTo(DEFAULT_RIGHT_MARGIN)

    defaultSettings.defaultRightMargin = CUSTOM_RIGHT_MARGIN
    assertThat(defaultSettings.defaultRightMargin).isEqualTo(CUSTOM_RIGHT_MARGIN)

    AndroidCodeStyleSettings.initializeDefaults(propertiesComponent)
    assertThat(defaultScheme.codeStyleSettings.defaultRightMargin).isEqualTo(CUSTOM_RIGHT_MARGIN)

    propertiesComponent.setValue(AndroidCodeStyleSettings.CONFIG_V1, false)
    AndroidCodeStyleSettings.initializeDefaults(propertiesComponent)
    assertThat(defaultSettings.defaultRightMargin).isEqualTo(DEFAULT_RIGHT_MARGIN)
  }
}
