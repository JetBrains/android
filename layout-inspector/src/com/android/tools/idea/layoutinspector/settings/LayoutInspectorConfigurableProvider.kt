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
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import java.awt.Component
import java.awt.Dimension
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JPanel

const val STUDIO_RELEASE_NOTES_EMBEDDED_LI_URL =
  "https://d.android.com/r/studio-ui/embedded-layout-inspector"

/** Class used to provide a [Configurable] to show in Android Studio settings panel. */
class LayoutInspectorConfigurableProvider(
  private val showRestartAndroidStudioDialog: () -> Boolean = { showRestartStudioDialog() }
) : ConfigurableProvider() {

  override fun canCreateConfigurable(): Boolean {
    // only show the setting if the auto connect flat is enabled.
    return StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_AUTO_CONNECT_TO_FOREGROUND_PROCESS_ENABLED.get()
  }

  override fun createConfigurable(): Configurable {
    return LayoutInspectorConfigurable(showRestartAndroidStudioDialog)
  }
}

class LayoutInspectorConfigurable(private val showRestartAndroidStudioDialog: () -> Boolean) :
  SearchableConfigurable {
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
        setValue = { settings.autoConnectEnabled = it },
      ),
    )
  private val embeddedLayoutInspectorSettingControl =
    ToggleSettingController(
      enableEmbeddedLayoutInspectorCheckBox,
      Setting(
        getValue = { settings.embeddedLayoutInspectorEnabled },
        setValue = { settings.embeddedLayoutInspectorEnabled = it },
      ),
    )

  init {
    component.layout = BoxLayout(component, BoxLayout.PAGE_AXIS)
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

  override fun apply() {
    val autoConnectHasChanged = autoConnectSettingControl.apply()
    val embeddedLayoutInspectorHasChanged = embeddedLayoutInspectorSettingControl.apply()

    if (autoConnectHasChanged || embeddedLayoutInspectorHasChanged) {
      val restarted = showRestartAndroidStudioDialog()
      if (!restarted) {
        // if Studio wasn't restarted, we are going to restore the settings to their previous state.
        // This prevents entering an inconsistent state where the user has changed the settings but
        // not restarted Studio.
        if (autoConnectHasChanged) {
          enableAutoConnectCheckBox.isSelected = !enableAutoConnectCheckBox.isSelected
          autoConnectSettingControl.apply()
        }
        if (embeddedLayoutInspectorHasChanged) {
          enableEmbeddedLayoutInspectorCheckBox.isSelected =
            !enableEmbeddedLayoutInspectorCheckBox.isSelected
          embeddedLayoutInspectorSettingControl.apply()
        }
      }
    }
  }

  override fun reset() {
    autoConnectSettingControl.reset()
    embeddedLayoutInspectorSettingControl.reset()
  }

  override fun getDisplayName(): String {
    return LayoutInspectorBundle.message("layout.inspector")
  }

  override fun getId() = "layout.inspector.configurable"
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

private fun showRestartStudioDialog(): Boolean {
  val action =
    if (ApplicationManager.getApplication().isRestartCapable) {
      IdeBundle.message("ide.restart.action")
    } else {
      IdeBundle.message("ide.shutdown.action")
    }
  val result =
    Messages.showYesNoDialog(
      LayoutInspectorBundle.message("dialog.message.must.be.restarted.for.changes.to.take.effect"),
      IdeBundle.message("dialog.title.restart.required"),
      action,
      IdeBundle.message("ide.notnow.action"),
      Messages.getQuestionIcon(),
    )

  return if (result == Messages.YES) {
    ApplicationManagerEx.getApplicationEx().restart(true)
    true
  } else {
    false
  }
}
