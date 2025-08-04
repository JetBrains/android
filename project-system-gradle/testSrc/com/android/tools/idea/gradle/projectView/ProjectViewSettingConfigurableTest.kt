/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.projectView

import com.android.flags.junit.FlagRule
import com.android.tools.adtui.TreeWalker
import com.android.tools.idea.flags.StudioFlags
import com.intellij.testFramework.LightPlatformTestCase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.Component
import javax.swing.JCheckBox
import kotlin.test.fail

private val SHOW_DEFAULT_PROJECT_VIEW_SETTINGS_FLAG = StudioFlags.SHOW_DEFAULT_PROJECT_VIEW_SETTINGS

@RunWith(JUnit4::class)
class ProjectViewSettingConfigurableTest : LightPlatformTestCase() {
  private lateinit var projectViewSettings: AndroidProjectViewSettings

  @get:Rule
  val flagRule = FlagRule(SHOW_DEFAULT_PROJECT_VIEW_SETTINGS_FLAG, true)

  override fun setUp() {
    super.setUp()
    projectViewSettings = AndroidProjectViewSettings.getInstance()
  }

  @Test
  fun testCheckboxWitFlagDisabled_checkboxNotShown() {
    SHOW_DEFAULT_PROJECT_VIEW_SETTINGS_FLAG.override(false)
    val configurable = ProjectViewSettingConfigurable()

    val checkbox = configurable.createComponent()?.getNamedComponent<JCheckBox>("defaultProjectView")
    assertNull(checkbox)
  }

  @Test
  fun testCheckboxWithFlagEnabledAndDefaultTrue_checkBoxSelected() {
    SHOW_DEFAULT_PROJECT_VIEW_SETTINGS_FLAG.override(true)
    projectViewSettings.defaultToProjectView = true
    val configurable = ProjectViewSettingConfigurable()

    val checkbox = configurable.createComponent()?.getNamedComponent<JCheckBox>("defaultProjectView")
    assertNotNull(checkbox)
    assertTrue(checkbox!!.isEnabled)
    assertTrue(checkbox.isSelected)
  }

  @Test
  fun testCheckboxWithFlagEnabledAndDefaultTrue_checkBoxNotSelected() {
    SHOW_DEFAULT_PROJECT_VIEW_SETTINGS_FLAG.override(true)
    projectViewSettings.defaultToProjectView = false
    val configurable = ProjectViewSettingConfigurable()

    val checkbox = configurable.createComponent()?.getNamedComponent<JCheckBox>("defaultProjectView")
    assertNotNull(checkbox)
    assertTrue(checkbox!!.isEnabled)
    assertFalse(checkbox.isSelected)
  }

  @Test
  fun testCheckboxWithFlagEnabledAndCustomPropertySet_checkBoxDisabled() {
    SHOW_DEFAULT_PROJECT_VIEW_SETTINGS_FLAG.override(true)
    System.setProperty("studio.projectview", "true")
    projectViewSettings.defaultToProjectView = false
    val configurable = ProjectViewSettingConfigurable()

    val checkbox = configurable.createComponent()?.getNamedComponent<JCheckBox>("defaultProjectView")
    assertNotNull(checkbox)
    assertFalse(checkbox!!.isEnabled)
  }
}

private inline fun <reified T : Component> Component?.getNamedComponent(name: String): T? {
  if (this == null) {
    fail("Unexpected null component")
  }
  return TreeWalker(this).descendants().filterIsInstance<T>().find { it.name == name }
}
