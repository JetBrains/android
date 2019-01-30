/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.properties

import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.InspectorView
import com.android.tools.property.panel.api.PropertiesModel
import com.android.tools.property.panel.api.PropertiesModelListener
import com.android.tools.property.panel.api.PropertiesTable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.containers.ContainerUtil
import kotlin.properties.Delegates

/**
 * Layout Inspector Properties Model
 *
 * Holds the [properties] shown in the properties and is responsible for:
 * requesting a new table of properties when needed and notifying the UI
 * for misc updates.
 */
class InspectorPropertiesModel : PropertiesModel<InspectorPropertyItem> {
  private val modelListeners: MutableList<PropertiesModelListener<InspectorPropertyItem>> = ContainerUtil.createConcurrentList()
  private val provider = PropertiesProvider(this)

  override var properties: PropertiesTable<InspectorPropertyItem> = PropertiesTable.emptyTable()
    private set

  var inspectorModel by Delegates.observable<InspectorModel?>(null) { _, old, new -> modelChanged(old, new)}

  private fun modelChanged(oldModel: InspectorModel?, newModel: InspectorModel?) {
    oldModel?.selectionListeners?.remove(::handleNewSelection)
    newModel?.selectionListeners?.add(::handleNewSelection)
  }

  override fun deactivate() {
    properties = PropertiesTable.emptyTable()
  }

  override fun addListener(listener: PropertiesModelListener<InspectorPropertyItem>) {
    modelListeners.add(listener)
  }

  override fun removeListener(listener: PropertiesModelListener<InspectorPropertyItem>) {
    modelListeners.remove(listener)
  }

  @Suppress("UNUSED_PARAMETER")
  private fun handleNewSelection(oldView: InspectorView?, newView: InspectorView?) {
    val newProperties = provider.getProperties(newView)
    ApplicationManager.getApplication().invokeLater {
      properties = newProperties
      firePropertiesGenerated()
    }
  }

  private fun firePropertiesGenerated() {
    modelListeners.forEach { it.propertiesGenerated(this) }
  }
}
