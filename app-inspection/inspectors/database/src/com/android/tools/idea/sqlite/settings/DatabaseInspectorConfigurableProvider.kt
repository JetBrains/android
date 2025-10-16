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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.sqlite.localization.DatabaseInspectorBundle.message
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.xmlb.XmlSerializerUtil
import javax.swing.JComponent
import javax.swing.JPanel

class DatabaseInspectorConfigurableProvider : ConfigurableProvider() {
  override fun createConfigurable(): Configurable {
    return DatabaseInspectorConfigurable()
  }
}

private class DatabaseInspectorConfigurable : SearchableConfigurable {

  private val settings = DatabaseInspectorSettings.getInstance()

  private val propertyGraph = PropertyGraph()
  private var isOfflineModeEnabled = propertyGraph.property(settings.isOfflineModeEnabled)
  private var isForceOpen = propertyGraph.property(settings.isForceOpen)

  override fun createComponent(): JPanel {
    return panel {
      row {
        checkBox(message("enable.offline.mode"))
          .bindSelected(isOfflineModeEnabled)
          .named("enableOfflineMode")
      }
      if (StudioFlags.APP_INSPECTION_USE_EXPERIMENTAL_DATABASE_INSPECTOR.get()) {
        row {
          checkBox(message("force.open.database")).bindSelected(isForceOpen).named("forceOpen")
        }
      }
    }
  }

  override fun isModified() =
    isOfflineModeEnabled.get() != settings.isOfflineModeEnabled ||
      isForceOpen.get() != settings.isForceOpen

  override fun apply() {
    val isOfflineModeEnabled = isOfflineModeEnabled.get()
    settings.isOfflineModeEnabled = isOfflineModeEnabled
    settings.isForceOpen = isForceOpen.get()
  }

  override fun reset() {
    isOfflineModeEnabled.set(settings.isOfflineModeEnabled)
    isForceOpen.set(settings.isForceOpen)
  }

  override fun getDisplayName(): String {
    return message("database.inspector")
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

  var isForceOpen: Boolean = false

  override fun getState() = this

  override fun loadState(state: DatabaseInspectorSettings) = XmlSerializerUtil.copyBean(state, this)
}

private fun <T : JComponent> Cell<T>.named(name: String) = applyToComponent { this.name = name }
