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
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBCheckBox
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * Class used to provide a [Configurable] to show in Android Studio settings panel.
 */
class LayoutInspectorConfigurableProvider : ConfigurableProvider() {

  override fun canCreateConfigurable(): Boolean {
    // only show the setting if the auto connect flat is enabled.
    return StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_AUTO_CONNECT_TO_FOREGROUND_PROCESS_ENABLED.get()
  }

  override fun createConfigurable(): Configurable {
    return LayoutInspectorConfigurable()
  }
}

private class LayoutInspectorConfigurable : SearchableConfigurable {
  private val component: JPanel = JPanel(BorderLayout())
  private val enableAutoConnect = JBCheckBox(LayoutInspectorBundle.message("enable.auto.connect"))

  private val settings = LayoutInspectorSettings.getInstance()

  init {
    component.add(enableAutoConnect, BorderLayout.PAGE_START)
  }

  override fun createComponent() = component

  override fun isModified() = enableAutoConnect.isSelected != settings.autoConnectEnabled

  override fun apply() {
    val isAutoConnectEnabled = enableAutoConnect.isSelected
    settings.setAutoConnectEnabledInSettings(isAutoConnectEnabled)
  }

  override fun reset() {
    enableAutoConnect.isSelected = settings.autoConnectEnabled
  }

  override fun getDisplayName(): String {
    return LayoutInspectorBundle.message("layout.inspector")
  }

  override fun getId() = "layout.inspector.configurable"
}