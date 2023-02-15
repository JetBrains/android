/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.sqlite.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.ui.components.JBCheckBox

class DatabaseInspectorConfigurableProviderTest : LightPlatformTestCase() {
  private lateinit var databaseInspectorSettings: DatabaseInspectorSettings

  override fun setUp() {
    super.setUp()
    databaseInspectorSettings = DatabaseInspectorSettings.getInstance()
  }

  fun testConfigurableName() {
    val provider = DatabaseInspectorConfigurableProvider()
    val configurable = provider.createConfigurable()

    assertEquals("Database Inspector", configurable.displayName)
  }

  fun testConfigurableId() {
    val provider = DatabaseInspectorConfigurableProvider()
    val configurable = provider.createConfigurable() as SearchableConfigurable

    assertEquals("database.inspector", configurable.id)
  }

  fun testConfigurableSettingsInteraction() {
    val provider = DatabaseInspectorConfigurableProvider()
    val configurable = provider.createConfigurable()
    val enableOfflineModeCheckbox = configurable.createComponent()!!.getComponent(0) as JBCheckBox

    databaseInspectorSettings.isOfflineModeEnabled = true
    configurable.reset()

    assertTrue(databaseInspectorSettings.isOfflineModeEnabled)
    assertTrue(enableOfflineModeCheckbox.isSelected)

    enableOfflineModeCheckbox.isSelected = false

    assertTrue(configurable.isModified)
    assertTrue(databaseInspectorSettings.isOfflineModeEnabled)

    configurable.apply()

    assertFalse(configurable.isModified)
    assertFalse(enableOfflineModeCheckbox.isSelected)
    assertFalse(databaseInspectorSettings.isOfflineModeEnabled)

    configurable.reset()

    assertFalse(databaseInspectorSettings.isOfflineModeEnabled)
    assertFalse(enableOfflineModeCheckbox.isSelected)
  }
}
