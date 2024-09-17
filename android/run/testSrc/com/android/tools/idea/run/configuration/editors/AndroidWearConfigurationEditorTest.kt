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
package com.android.tools.idea.run.configuration.editors

import com.android.testutils.delayUntilCondition
import com.android.tools.adtui.TreeWalker
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.run.configuration.AndroidWatchFaceConfiguration
import com.android.tools.idea.run.configuration.AndroidWatchFaceConfigurationType
import com.android.tools.idea.run.configuration.addWatchFace
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleListCellRenderer
import javax.swing.JList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AndroidWearConfigurationEditorTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private lateinit var runConfiguration: AndroidWatchFaceConfiguration
  private lateinit var settingsEditor: AndroidWearConfigurationEditor<AndroidWatchFaceConfiguration>

  @Before
  fun setUp() {
    val runConfigurationFactory = AndroidWatchFaceConfigurationType().configurationFactories[0]
    runConfiguration = AndroidWatchFaceConfiguration(projectRule.project, runConfigurationFactory)
    settingsEditor =
      runConfiguration.configurationEditor
        as AndroidWearConfigurationEditor<AndroidWatchFaceConfiguration>
    Disposer.register(projectRule.testRootDisposable, settingsEditor)
  }

  @Test
  fun testComponentComboBoxDisabled() = runBlocking {
    val editor = settingsEditor.component as DialogPanel
    val modulesComboBox = TreeWalker(editor).descendants().filterIsInstance<ComboBox<*>>().first()
    val componentComboBox =
      TreeWalker(editor).descendants().filterIsInstance<ComboBox<*>>()[1] as ComboBox<String>
    var comboBoxRenderer =
      componentComboBox.renderer.getListCellRendererComponent(
        JList(),
        componentComboBox.item,
        -1,
        false,
        false,
      ) as SimpleListCellRenderer<String>

    assertThat(modulesComboBox.item).isNull()
    assertThat(comboBoxRenderer.isEnabled).isFalse()
    assertThat(comboBoxRenderer.text).isEqualTo("Module is not chosen")

    runConfiguration.setModule(projectRule.module) // To set myModule
    settingsEditor.resetFrom(runConfiguration)
    delayUntilCondition(200) {
      comboBoxRenderer =
        componentComboBox.renderer.getListCellRendererComponent(
          JList(),
          componentComboBox.item,
          -1,
          false,
          false,
        ) as SimpleListCellRenderer<String>
      comboBoxRenderer.text == "Watch Face not found"
    }
    comboBoxRenderer =
      componentComboBox.renderer.getListCellRendererComponent(
        JList(),
        componentComboBox.item,
        -1,
        false,
        false,
      ) as SimpleListCellRenderer<String>
    assertThat(comboBoxRenderer.text).isEqualTo("Watch Face not found")
    assertThat(comboBoxRenderer.isEnabled).isFalse()
  }

  @Test
  fun testComponentComboBoxEnabled() = runBlocking {
    val watchFaceClass = withContext(uiThread) { projectRule.fixture.addWatchFace().qualifiedName }

    runConfiguration.setModule(projectRule.module)
    runConfiguration.componentLaunchOptions.componentName = watchFaceClass
    settingsEditor.resetFrom(runConfiguration)
    val componentComboBox =
      TreeWalker(settingsEditor.component).descendants().filterIsInstance<ComboBox<*>>()[1]
        as ComboBox<String>

    delayUntilCondition(200) { componentComboBox.item != null }

    assertThat(componentComboBox.isEnabled).isTrue()
    assertThat(componentComboBox.item).isEqualTo(watchFaceClass)
  }
}
