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

import com.android.tools.idea.flags.ExperimentalConfigurable
import com.android.tools.idea.flags.ExperimentalConfigurable.ApplyState
import com.android.tools.idea.flags.ExperimentalSettingsContributor
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import java.awt.Component
import java.awt.Dimension
import java.awt.GridLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JPanel

const val STUDIO_RELEASE_NOTES_EMBEDDED_LI_URL =
  "https://d.android.com/r/studio-ui/embedded-layout-inspector"

/**
 * Class used to provide a [Configurable] to contribute to Android Studio Experimental Settings
 * panel.
 */
class LayoutInspectorConfigurableContributor : ExperimentalSettingsContributor {

  override fun getName(): String {
    return LayoutInspectorBundle.message("layout.inspector")
  }

  override fun shouldCreateConfigurable(project: Project): Boolean {
    // only show the setting if the auto connect flag is enabled.
    return StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_AUTO_CONNECT_TO_FOREGROUND_PROCESS_ENABLED.get()
  }

  override fun createConfigurable(project: Project): ExperimentalConfigurable {
    return LayoutInspectorConfigurable()
  }
}

class LayoutInspectorConfigurable() : ExperimentalConfigurable {
  private val component: JPanel = JPanel()
  private val enableAutoConnectCheckBox =
    JBCheckBox(LayoutInspectorBundle.message("enable.auto.connect"))
  private val embeddedLayoutInspectorSettingPanel = JPanel()
  private val enableEmbeddedLayoutInspectorCheckBox =
    JBCheckBox(LayoutInspectorBundle.message("enable.embedded.layout.inspector"))

  private val settings = LayoutInspectorSettings.getInstance()
  private val autoConnectSettingControl =
    ToggleSettingController(
      enableAutoConnectCheckBox,
      Setting(
        getValue = { settings.autoConnectEnabled },
        setValue = { settings.autoConnectEnabled = it }
      )
    )
  private val embeddedLayoutInspectorSettingControl =
    ToggleSettingController(
      enableEmbeddedLayoutInspectorCheckBox,
      Setting(
        getValue = { settings.embeddedLayoutInspectorEnabled },
        setValue = { settings.embeddedLayoutInspectorEnabled = it }
      )
    )

  init {
    component.layout = GridLayout(0, 1)
    component.add(enableAutoConnectCheckBox)
    enableAutoConnectCheckBox.alignmentX = Component.LEFT_ALIGNMENT

    embeddedLayoutInspectorSettingPanel.layout =
      BoxLayout(embeddedLayoutInspectorSettingPanel, BoxLayout.LINE_AXIS)
    embeddedLayoutInspectorSettingPanel.add(enableEmbeddedLayoutInspectorCheckBox)
    embeddedLayoutInspectorSettingPanel.add(Box.createRigidArea(Dimension(20, 0)))
    embeddedLayoutInspectorSettingPanel.add(
      ActionLink(LayoutInspectorBundle.message("learn.more")) {
        BrowserUtil.browse(STUDIO_RELEASE_NOTES_EMBEDDED_LI_URL)
      }
    )

    if (StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_IN_RUNNING_DEVICES_ENABLED.get()) {
      component.add(embeddedLayoutInspectorSettingPanel)
      embeddedLayoutInspectorSettingPanel.alignmentX = Component.LEFT_ALIGNMENT
    }
  }

  override fun createComponent() = component

  override fun isModified() =
    autoConnectSettingControl.isModified || embeddedLayoutInspectorSettingControl.isModified

  override fun preApplyCallback(): ApplyState =
    when {
      isModified() -> ApplyState.RESTART // each setting requires a restart
      else -> ApplyState.OK
    }

  override fun apply() {
    autoConnectSettingControl.apply()
    embeddedLayoutInspectorSettingControl.apply()
  }

  override fun reset() {
    autoConnectSettingControl.reset()
    embeddedLayoutInspectorSettingControl.reset()
  }
}

private class Setting<T>(val getValue: () -> T, val setValue: (T) -> Unit)

private class ToggleSettingController(val checkBox: JCheckBox, val setting: Setting<Boolean>) {
  val isModified: Boolean
    get() = checkBox.isSelected != setting.getValue()

  /**
   * Update [setting] with the current value of [check].
   *
   * @return true if the value in [setting] has changed. False otherwise.
   */
  fun apply(): Boolean {
    val previousValue = setting.getValue()
    val newValue = checkBox.isSelected
    setting.setValue(newValue)
    return previousValue != newValue
  }

  fun reset() {
    checkBox.isSelected = setting.getValue()
  }
}
