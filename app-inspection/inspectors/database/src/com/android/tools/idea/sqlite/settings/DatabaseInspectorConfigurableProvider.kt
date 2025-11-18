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
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.util.xmlb.XmlSerializerUtil
import javax.swing.JComponent

class DatabaseInspectorConfigurableProvider(private val project: Project) : ConfigurableProvider() {
  override fun createConfigurable(): Configurable {
    return DatabaseInspectorConfigurable(project)
  }
}

private const val DRIVER_INTERFACE = "androidx.sqlite.SQLiteDriver"
private const val CONNECTION_INTERFACE = "androidx.sqlite.SQLiteConnection"

private class DatabaseInspectorConfigurable(private val project: Project) : SearchableConfigurable {

  private val settings = DatabaseInspectorSettings.getInstance()
  private val projectSettings = DatabaseInspectorProjectSettings.getInstance(project)

  private val propertyGraph = PropertyGraph()
  private var isOfflineModeEnabled = propertyGraph.property(settings.isOfflineModeEnabled)
  private var isForceOpen = propertyGraph.property(settings.isForceOpen)
  private var additionDriverClass = propertyGraph.property(projectSettings.additionalDriverClass)
  private var additionConnectionClass =
    propertyGraph.property(projectSettings.additionalConnectionClass)
  private var isIgnoreFrameworkApi = propertyGraph.property(projectSettings.isIgnoreFrameworkApi)

  private val panel = panel {
    row {
      checkBox(message("enable.offline.mode"))
        .bindSelected(isOfflineModeEnabled)
        .named("enableOfflineMode")
    }
    if (StudioFlags.APP_INSPECTION_USE_EXPERIMENTAL_DATABASE_INSPECTOR.get()) {
      row { checkBox(message("force.open.database")).bindSelected(isForceOpen).named("forceOpen") }
    }
    if (StudioFlags.APP_INSPECTION_ENABLE_ADDITIONAL_SQL_DRIVER.get()) {
      row(message("additional.driver.class")) {
        classPicker(DRIVER_INTERFACE).bindText(additionDriverClass).named("driverClass")
      }
      row(message("additional.connection.class")) {
        classPicker(CONNECTION_INTERFACE, message("additional.connection.class.tooltip"))
          .bindText(additionConnectionClass)
          .named("connectionClass")
          .enabledIf(
            object : ComponentPredicate() {
              override fun addListener(listener: (Boolean) -> Unit) {
                additionDriverClass.afterChange { listener(invoke()) }
              }

              override fun invoke() = additionDriverClass.get().isNotEmpty()
            }
          )
      }

      row {
        checkBox(message("ignore.framework.api"))
          .bindSelected(isIgnoreFrameworkApi)
          .named("ignoreFrameworkApi")
          .applyToComponent { toolTipText = message("ignore.framework.api.tooltip") }
      }
    }
  }

  override fun createComponent() = panel

  override fun isModified() =
    isOfflineModeEnabled.get() != settings.isOfflineModeEnabled ||
      isForceOpen.get() != settings.isForceOpen ||
      additionDriverClass.get() != projectSettings.additionalDriverClass ||
      additionConnectionClass.get() != projectSettings.additionalConnectionClass ||
      isIgnoreFrameworkApi.get() != projectSettings.isIgnoreFrameworkApi

  override fun apply() {
    val isOfflineModeEnabled = isOfflineModeEnabled.get()
    settings.isOfflineModeEnabled = isOfflineModeEnabled
    settings.isForceOpen = isForceOpen.get()
    projectSettings.additionalDriverClass = additionDriverClass.get()
    projectSettings.additionalConnectionClass = additionConnectionClass.get()
    projectSettings.isIgnoreFrameworkApi = isIgnoreFrameworkApi.get()
  }

  override fun reset() {
    isOfflineModeEnabled.set(settings.isOfflineModeEnabled)
    isForceOpen.set(settings.isForceOpen)
    additionDriverClass.set(projectSettings.additionalDriverClass)
    additionConnectionClass.set(projectSettings.additionalConnectionClass)
    isIgnoreFrameworkApi.set(projectSettings.isIgnoreFrameworkApi)
  }

  override fun getDisplayName(): String {
    return message("database.inspector")
  }

  override fun getId() = "database.inspector"

  private fun Row.classPicker(base: String, toolTip: String? = null): Cell<ClassPicker> {
    return cell(ClassPicker(project, base, toolTip)).align(AlignX.FILL)
  }
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

@Service(PROJECT)
@State(
  name = "DatabaseInspectorProjectSettings",
  storages = [Storage("databaseInspectorProjectSettings.xml")],
)
class DatabaseInspectorProjectSettings :
  PersistentStateComponent<DatabaseInspectorProjectSettings> {

  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<DatabaseInspectorProjectSettings>()
  }

  var additionalDriverClass: String = ""

  var additionalConnectionClass: String = ""

  var isIgnoreFrameworkApi: Boolean = false

  override fun getState() = this

  override fun loadState(state: DatabaseInspectorProjectSettings) =
    XmlSerializerUtil.copyBean(state, this)
}

private fun <T : JComponent> Cell<T>.named(name: String) = applyToComponent { this.name = name }

private fun Cell<ClassPicker>.bindText(
  property: ObservableMutableProperty<String>
): Cell<ClassPicker> {
  return applyToComponent {
    text = property.get()
    property.afterChange { text = it }
    addDocumentListener(
      object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          property.set(event.document.text)
        }
      }
    )
  }
}
