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

import com.android.flags.junit.FlagRule
import com.android.tools.adtui.TreeWalker
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.testFramework.LightPlatformTestCase
import java.awt.Component
import javax.swing.JCheckBox
import kotlin.test.fail
import org.junit.Rule

private val EXPERIMENTAL_FLAG = StudioFlags.APP_INSPECTION_USE_EXPERIMENTAL_DATABASE_INSPECTOR

class DatabaseInspectorConfigurableProviderTest : LightPlatformTestCase() {
  private lateinit var databaseInspectorSettings: DatabaseInspectorSettings

  @get:Rule val flagRule = FlagRule(EXPERIMENTAL_FLAG)

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

  fun testConfigurableSettingsInteraction_enableOfflineMode() {
    val provider = DatabaseInspectorConfigurableProvider()
    val configurable = provider.createConfigurable()
    val checkbox =
      configurable.createComponent().getNamedComponent<JCheckBox>("enableOfflineMode")
        ?: kotlin.test.fail("Can't find enableOfflineMode checkbox")

    databaseInspectorSettings.isOfflineModeEnabled = true
    configurable.reset()

    assertTrue(databaseInspectorSettings.isOfflineModeEnabled)
    assertTrue(checkbox.isSelected)

    checkbox.isSelected = false

    assertTrue(configurable.isModified)
    assertTrue(databaseInspectorSettings.isOfflineModeEnabled)

    configurable.apply()

    assertFalse(configurable.isModified)
    assertFalse(checkbox.isSelected)
    assertFalse(databaseInspectorSettings.isOfflineModeEnabled)

    configurable.reset()

    assertFalse(databaseInspectorSettings.isOfflineModeEnabled)
    assertFalse(checkbox.isSelected)
  }

  fun testConfigurableSettingsInteraction_forceOpen() {
    EXPERIMENTAL_FLAG.override(true)

    val provider = DatabaseInspectorConfigurableProvider()
    val configurable = provider.createConfigurable()
    val checkbox =
      configurable.createComponent().getNamedComponent<JCheckBox>("forceOpen")
        ?: kotlin.test.fail("Can't find forceOpen checkbox")

    databaseInspectorSettings.isForceOpen = true
    configurable.reset()

    assertTrue(databaseInspectorSettings.isForceOpen)
    assertTrue(checkbox.isSelected)

    checkbox.isSelected = false

    assertTrue(configurable.isModified)
    assertTrue(databaseInspectorSettings.isForceOpen)

    configurable.apply()

    assertFalse(configurable.isModified)
    assertFalse(checkbox.isSelected)
    assertFalse(databaseInspectorSettings.isForceOpen)

    configurable.reset()

    assertFalse(databaseInspectorSettings.isForceOpen)
    assertFalse(checkbox.isSelected)
  }

  fun testForceOpenCheckbox_flagDisabled_notShown() {
    val provider = DatabaseInspectorConfigurableProvider()
    val configurable = provider.createConfigurable()

    val checkbox = configurable.createComponent().getNamedComponent<JCheckBox>("forceOpen")

    assertNull(checkbox)
  }
}

private inline fun <reified T : Component> Component?.getNamedComponent(name: String): T? {
  if (this == null) {
    fail("Unexpected null component")
  }
  return TreeWalker(this).descendants().filterIsInstance<T>().find { it.name == name }
}
