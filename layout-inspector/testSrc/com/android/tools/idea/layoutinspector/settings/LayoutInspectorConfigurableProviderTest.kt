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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.runningdevices.withAutoConnect
import com.android.tools.idea.layoutinspector.runningdevices.withEmbeddedLayoutInspector
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.testFramework.ApplicationRule
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import org.junit.Rule
import org.junit.Test
import javax.swing.JCheckBox
import javax.swing.JPanel

class LayoutInspectorConfigurableProviderTest {
  @get:Rule val applicationRule = ApplicationRule()

  @Test
  fun testConfigurableName() {
    val provider = LayoutInspectorConfigurableProvider()
    val configurable = provider.createConfigurable()

    assertThat("Layout Inspector").isEqualTo(configurable.displayName)
  }

  @Test
  fun testConfigurableId() {
    val provider = LayoutInspectorConfigurableProvider()
    val configurable = provider.createConfigurable() as SearchableConfigurable

    assertThat("layout.inspector.configurable").isEqualTo(configurable.id)
  }

  @Test
  fun testConfigurableControls() {
    val configurable1 =
      LayoutInspectorConfigurableProvider().createConfigurable() as SearchableConfigurable
    val component1 = configurable1.createComponent()!!

    assertThat(component1.components).hasLength(2)
    assertThat((component1.components[0] as JCheckBox).text)
      .isEqualTo("Enable auto connect (requires a restart of Android Studio)")

    val previous = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_IN_RUNNING_DEVICES_ENABLED.get()
    StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_IN_RUNNING_DEVICES_ENABLED.override(true)

    val configurable2 =
      LayoutInspectorConfigurableProvider().createConfigurable() as SearchableConfigurable
    val component2 = configurable2.createComponent()!!
    val enableEmbeddedLiPanel = component2.components[1] as JPanel

    assertThat(component2.components).hasLength(2)
    assertThat((component2.components[0] as JCheckBox).text)
      .isEqualTo("Enable auto connect (requires a restart of Android Studio)")
    assertThat((enableEmbeddedLiPanel.components[0] as JCheckBox).text)
      .isEqualTo("Enable embedded Layout Inspector (requires a restart of Android Studio)")

    StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_IN_RUNNING_DEVICES_ENABLED.override(previous)
  }

  @Test
  fun testConfigurableSettingAutoConnectInteraction() = withAutoConnect {
    var restartStudio = true
    var restartDialogShown = false
    val provider = LayoutInspectorConfigurableProvider {
      restartDialogShown = true
      restartStudio
    }
    val configurable = provider.createConfigurable()
    val enableAutoConnectCheckBox = configurable.createComponent()!!.getComponent(0) as JBCheckBox

    // load settings from configurable to swing
    configurable.reset()
    assertThat(enableAutoConnect).isTrue()
    assertThat(enableAutoConnectCheckBox.isSelected).isTrue()

    // uncheck the checkbox
    enableAutoConnectCheckBox.isSelected = false

    assertThat(configurable.isModified).isTrue()
    assertThat(enableAutoConnect).isTrue()

    // store setting from swing to configurable
    configurable.apply()
    assertThat(restartDialogShown).isTrue()
    assertThat(configurable.isModified).isFalse()
    assertThat(enableAutoConnectCheckBox.isSelected).isFalse()
    assertThat(enableAutoConnect).isFalse()

    // load settings from configurable to swing
    configurable.reset()
    assertThat(enableAutoConnect).isFalse()
    assertThat(enableAutoConnectCheckBox.isSelected).isFalse()

    // back to true
    enableAutoConnect = true
    // load settings from configurable to swing
    configurable.reset()
    assertThat(enableAutoConnect).isTrue()
    assertThat(enableAutoConnectCheckBox.isSelected).isTrue()

    restartStudio = false

    // uncheck the checkbox
    enableAutoConnectCheckBox.isSelected = false

    assertThat(configurable.isModified).isTrue()
    assertThat(enableAutoConnect).isTrue()

    // store setting from swing to configurable
    // the settings shouldn't be stored because studio is not restarted
    configurable.apply()
    assertThat(restartDialogShown).isTrue()
    assertThat(configurable.isModified).isFalse()
    assertThat(enableAutoConnectCheckBox.isSelected).isTrue()
    assertThat(enableAutoConnect).isTrue()
  }

  @Test
  fun testConfigurableSettingEmbeddedLayoutInspectorInteraction() = withEmbeddedLayoutInspector {
    val previous = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_IN_RUNNING_DEVICES_ENABLED.get()
    StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_IN_RUNNING_DEVICES_ENABLED.override(true)

    var restartStudio = true
    var restartDialogShown = false
    val provider = LayoutInspectorConfigurableProvider {
      restartDialogShown = true
      restartStudio
    }
    val configurable = provider.createConfigurable()
    val enableEmbeddedLiPanel = configurable.createComponent()!!.getComponent(1) as JPanel
    val enableEmbeddedLiCheckBox = enableEmbeddedLiPanel.components.first() as JCheckBox
    assertThat(enableEmbeddedLiPanel.components[2]).isInstanceOf(ActionLink::class.java)

    // make sure to start with property set to true
    enableEmbeddedLayoutInspector = true

    // load settings from configurable to swing
    configurable.reset()
    assertThat(enableEmbeddedLayoutInspector).isTrue()
    assertThat(enableEmbeddedLiCheckBox.isSelected).isTrue()

    // uncheck the checkbox
    enableEmbeddedLiCheckBox.isSelected = false

    assertThat(configurable.isModified).isTrue()
    assertThat(enableEmbeddedLayoutInspector).isTrue()

    // store setting from swing to configurable
    configurable.apply()
    assertThat(restartDialogShown).isTrue()
    assertThat(configurable.isModified).isFalse()
    assertThat(enableEmbeddedLiCheckBox.isSelected).isFalse()
    assertThat(enableEmbeddedLayoutInspector).isFalse()

    // load settings from configurable to swing
    configurable.reset()
    assertThat(enableEmbeddedLayoutInspector).isFalse()
    assertThat(enableEmbeddedLiCheckBox.isSelected).isFalse()

    // back to true
    enableEmbeddedLayoutInspector = true
    // load settings from configurable to swing
    configurable.reset()
    assertThat(enableEmbeddedLayoutInspector).isTrue()
    assertThat(enableEmbeddedLiCheckBox.isSelected).isTrue()

    restartStudio = false

    // uncheck the checkbox
    enableEmbeddedLiCheckBox.isSelected = false

    assertThat(configurable.isModified).isTrue()
    assertThat(enableEmbeddedLayoutInspector).isTrue()

    // store setting from swing to configurable
    // the settings shouldn't be stored because studio is not restarted
    configurable.apply()
    assertThat(restartDialogShown).isTrue()
    assertThat(configurable.isModified).isFalse()
    assertThat(enableEmbeddedLiCheckBox.isSelected).isTrue()
    assertThat(enableEmbeddedLayoutInspector).isTrue()

    StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_IN_RUNNING_DEVICES_ENABLED.override(previous)
  }
}
