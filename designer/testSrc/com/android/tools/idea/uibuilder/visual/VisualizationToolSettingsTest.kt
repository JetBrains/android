/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual

import org.jetbrains.android.AndroidTestCase

class VisualizationToolSettingsTest: AndroidTestCase() {

  private var defaultVisible: Boolean = false
  private var defaultShowDecoration: Boolean = false
  private lateinit var defaultConfigurationSet: ConfigurationSet
  private lateinit var customConfigurationSets: Map<String, CustomConfigurationSet>

  override fun setUp() {
    super.setUp()
    val settings = VisualizationToolSettings.getInstance()
    defaultVisible = settings.globalState.isVisible
    defaultShowDecoration = settings.globalState.showDecoration
    defaultConfigurationSet = settings.globalState.lastSelectedConfigurationSet
    customConfigurationSets = settings.globalState.customConfigurationSets
  }

  override fun tearDown() {
    val settings = VisualizationToolSettings.getInstance()
    try {
      settings.globalState.isVisible = defaultVisible
      settings.globalState.showDecoration = defaultShowDecoration
      settings.globalState.lastSelectedConfigurationSet = defaultConfigurationSet
      settings.globalState.customConfigurationSets = customConfigurationSets
    } catch (t: Throwable) {
      addSuppressedException(t)
    } finally {
      super.tearDown()
    }
  }

  fun testShareGlobalState() {
    val settings1 = VisualizationToolSettings.getInstance()
    val settings2 = VisualizationToolSettings.getInstance()

    assertEquals(settings1.globalState, settings2.globalState)
  }

  fun testSaveLoadSettings() {
    val settings = VisualizationToolSettings.getInstance()

    val visible = false
    val showDecoration = false
    val configurationSet = ConfigurationSet.LargeFont
    val customConfigurations = mutableMapOf<String, CustomConfigurationSet>()

    settings.globalState.isVisible = visible
    settings.globalState.showDecoration = showDecoration
    settings.globalState.lastSelectedConfigurationSet = configurationSet
    settings.globalState.customConfigurationSets = customConfigurations

    // Check the values are same after getting another instance.
    val anotherSettings = VisualizationToolSettings.getInstance()
    assertEquals(visible, anotherSettings.globalState.isVisible)
    assertEquals(showDecoration, anotherSettings.globalState.showDecoration)
    assertEquals(configurationSet, anotherSettings.globalState.lastSelectedConfigurationSet)
    assertEquals(customConfigurations, anotherSettings.globalState.customConfigurationSets)
  }
}
