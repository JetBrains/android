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
package com.android.tools.compose.debug

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.configurationStore.deserialize
import com.intellij.configurationStore.serialize
import com.intellij.testFramework.UsefulTestCase.assertContainsElements
import com.intellij.testFramework.UsefulTestCase.assertDoesntContain
import com.intellij.ui.classFilter.DebuggerClassFilterProvider
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl
import com.intellij.xdebugger.settings.DebuggerSettingsCategory
import org.junit.Rule
import org.junit.Test
import javax.swing.JCheckBox

class ComposeDebuggerTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private fun getClassFilterPatterns(): List<String> =
    DebuggerClassFilterProvider.EP_NAME.extensionList.flatMap { it.filters }.map { it.pattern }

  @Test
  fun testFilterSettings() {
    val classPattern = "androidx.compose.runtime*"

    ComposeDebuggerSettings.getInstance().filterComposeRuntimeClasses = true
    assertContainsElements(getClassFilterPatterns(), classPattern)

    ComposeDebuggerSettings.getInstance().filterComposeRuntimeClasses = false
    assertDoesntContain(getClassFilterPatterns(), classPattern)
  }

  @Test
  fun testSerialization() {
    val settingsManager = XDebuggerSettingManagerImpl.getInstanceImpl()

    val settings = ComposeDebuggerSettings.getInstance()
    assert(settings.filterComposeRuntimeClasses)
    settings.filterComposeRuntimeClasses = false

    val element = serialize(settingsManager.state!!)
    settings.filterComposeRuntimeClasses = true
    settingsManager.loadState(
      element!!.deserialize(XDebuggerSettingManagerImpl.SettingsState::class.java)
    )
    assert(!settings.filterComposeRuntimeClasses)
  }

  @Test
  fun testDebuggerSettingsUi() {
    val settings = ComposeDebuggerSettings.getInstance()
    val configurable = settings.createConfigurables(DebuggerSettingsCategory.STEPPING).single()

    settings.filterComposeRuntimeClasses = false

    val component = configurable.createComponent()!!
    val checkbox = component.getComponent(0) as JCheckBox
    configurable.reset()
    assert(!checkbox.isSelected)

    checkbox.isSelected = true
    assert(configurable.isModified)
    configurable.apply()
    assert(settings.filterComposeRuntimeClasses)
    assert(!configurable.isModified)

    configurable.disposeUIResources()
  }
}
