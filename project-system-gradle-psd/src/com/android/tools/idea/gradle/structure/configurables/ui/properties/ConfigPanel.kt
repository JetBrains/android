/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.ui.properties

import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.ui.ComponentProvider
import com.android.tools.idea.gradle.structure.configurables.ui.PROPERTY_PLACE_NAME
import com.android.tools.idea.gradle.structure.configurables.ui.PropertiesUiModel
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Disposer
import com.intellij.ui.navigation.Place
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.JComponent

/**
 * A panel for editing configuration entities such as [PsProductFlavor] and [PsBuildType].
 *
 * [ModelT] is the model type of an entity being edited
 * [propertiesModel] the UI model of the properties being edited
 */
open class ConfigPanel<in ModelT>(
  val context: PsContext,
  val project: PsProject,
  private val module: PsModule?,
  private val model: ModelT,
  private val propertiesModel: PropertiesUiModel<ModelT>
) : ConfigPanelUi(), ComponentProvider, Place.Navigator, Disposable {
  private var editors = mutableListOf<ModelPropertyEditor<Any>>()
  private var editorsInitialized = false
  private var initialized = false

  override fun getComponent(): JComponent {
    if (!initialized) {
      initializeEditors()
      initialized = true
    }
    return uiComponent
  }

  private fun initializeEditors() {
    setNumberOfProperties(propertiesModel.properties.size)
    for (property in propertiesModel.properties) {
      val editor: ModelPropertyEditor<Any> = property.createEditor(context, project, module, model)
      val labelComponent = editor.labelComponent
      addPropertyComponents(labelComponent, editor.component, editor.statusComponent)
      editor.addFocusListener(object: FocusListener{
        override fun focusLost(e: FocusEvent?) = Unit
        override fun focusGained(e: FocusEvent?) = editor.component.scrollRectToVisible(Rectangle(Point(0, 0), editor.component.size))
      })
      editors.add(editor)
    }

    fun refresh() {
      // Editors refresh themselves on activation.
      if (uiComponent.isDisplayable) {
        editors.forEach { it.reloadIfNotChanged() }
      }
    }

    context.add(object : GradleSyncListener {
      override fun syncStarted(project: Project) = refresh()
      override fun syncSucceeded(project: Project) = refresh()
      override fun syncFailed(project: Project, errorMessage: String) = refresh()
      override fun syncSkipped(project: Project) = refresh()
    }, this)
  }

  override fun navigateTo(place: Place?, requestFocus: Boolean): ActionCallback {
    val propertyDescription = place?.getPath(PROPERTY_PLACE_NAME) ?: ActionCallback.REJECTED
    if (requestFocus) {
      val editor = editors.firstOrNull { it.property.description == propertyDescription }
      when (editor) {
        is CollectionPropertyEditor<*, *> -> {
          editor.component.scrollRectToVisible(editor.component.bounds)
          editor.component.requestFocus()
          ApplicationManager.getApplication().invokeLater {
            editor.addItem()
          }
        }
        else -> editor?.component?.requestFocus()
      }
    }
    return ActionCallback.DONE
  }

  override fun dispose() {
    editors.forEach { Disposer.dispose(it) }
  }
}

