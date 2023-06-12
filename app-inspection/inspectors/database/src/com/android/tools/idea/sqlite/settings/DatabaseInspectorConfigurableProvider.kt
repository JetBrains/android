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

import com.android.tools.idea.sqlite.localization.DatabaseInspectorBundle
import com.android.tools.idea.sqlite.localization.DatabaseInspectorBundle.message
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.xmlb.XmlSerializerUtil
import java.awt.BorderLayout
import javax.swing.JPanel

class DatabaseInspectorConfigurableProvider : ConfigurableProvider() {
  override fun createConfigurable(): Configurable {
    return DatabaseInspectorConfigurable()
  }
}

private class DatabaseInspectorConfigurable : SearchableConfigurable {
  private val component: JPanel = JPanel(BorderLayout())
  private val enableOfflineModeCheckBox = JBCheckBox(message("enable.offline.mode"))

  private val settings = DatabaseInspectorSettings.getInstance()

  init {
    component.add(enableOfflineModeCheckBox, BorderLayout.PAGE_START)
  }

  override fun createComponent() = component

  override fun isModified() = enableOfflineModeCheckBox.isSelected != settings.isOfflineModeEnabled

  override fun apply() {
    val isOfflineModeEnabled = enableOfflineModeCheckBox.isSelected
    settings.isOfflineModeEnabled = isOfflineModeEnabled
  }

  override fun reset() {
    enableOfflineModeCheckBox.isSelected = settings.isOfflineModeEnabled
  }

  override fun getDisplayName(): String {
    return DatabaseInspectorBundle.message("database.inspector")
  }

  override fun getId() = "database.inspector"
}

@State(name = "DatabaseInspectorSettings", storages = [Storage("databaseInspectorSettings.xml")])
class DatabaseInspectorSettings : PersistentStateComponent<DatabaseInspectorSettings> {

  companion object {
    @JvmStatic
    fun getInstance(): DatabaseInspectorSettings {
      return ApplicationManager.getApplication().getService(DatabaseInspectorSettings::class.java)
    }
  }

  var isOfflineModeEnabled = true

  override fun getState() = this

  override fun loadState(state: DatabaseInspectorSettings) = XmlSerializerUtil.copyBean(state, this)
}
