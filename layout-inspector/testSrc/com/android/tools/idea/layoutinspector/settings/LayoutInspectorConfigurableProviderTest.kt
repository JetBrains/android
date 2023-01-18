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
package com.android.tools.idea.layoutinspector.settings

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.ui.components.JBCheckBox
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LayoutInspectorConfigurableProviderTest {
  private lateinit var layoutInspectorSettings: LayoutInspectorSettings

  @get:Rule
  val applicationRule = ApplicationRule()

  @Before
  fun testSetUp() {
    layoutInspectorSettings = LayoutInspectorSettings.getInstance()
  }

  @Test
  fun testConfigurableName() {
    val provider = LayoutInspectorConfigurableProvider()
    val configurable = provider.createConfigurable()

    LightPlatformTestCase.assertEquals("Layout Inspector", configurable.displayName)
  }

  @Test
  fun testConfigurableId() {
    val provider = LayoutInspectorConfigurableProvider()
    val configurable = provider.createConfigurable() as SearchableConfigurable

    LightPlatformTestCase.assertEquals("layout.inspector.configurable", configurable.id)
  }

  @Test
  fun testConfigurableSettingInteraction() {
    val provider = LayoutInspectorConfigurableProvider()
    val configurable = provider.createConfigurable()
    val enableAutoConnectCheckBox = configurable.createComponent()!!.getComponent(0) as JBCheckBox

    // make sure to start with property set to true
    layoutInspectorSettings.setAutoConnectEnabledInSettings(true)

    // load settings from configurable to swing
    configurable.reset()
    assertThat(layoutInspectorSettings.autoConnectEnabled).isTrue()
    assertThat(enableAutoConnectCheckBox.isSelected).isTrue()

    // uncheck the checkbox
    enableAutoConnectCheckBox.isSelected = false

    assertThat(configurable.isModified).isTrue()
    assertThat(layoutInspectorSettings.autoConnectEnabled).isTrue()

    // store setting from swing to configurable
    configurable.apply()
    assertThat(configurable.isModified).isFalse()
    assertThat(enableAutoConnectCheckBox.isSelected).isFalse()
    assertThat(layoutInspectorSettings.autoConnectEnabled).isFalse()

    // load settings from configurable to swing
    configurable.reset()
    assertThat(layoutInspectorSettings.autoConnectEnabled).isFalse()
    assertThat(enableAutoConnectCheckBox.isSelected).isFalse()

    // back to true
    layoutInspectorSettings.setAutoConnectEnabledInSettings(true)
    // load settings from configurable to swing
    configurable.reset()
    assertThat(layoutInspectorSettings.autoConnectEnabled).isTrue()
    assertThat(enableAutoConnectCheckBox.isSelected).isTrue()
  }
}