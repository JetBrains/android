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

import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.property.panel.api.PropertiesModel
import com.android.tools.property.panel.api.PropertiesModelListener
import com.android.tools.property.panel.api.PropertiesTable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.VisibleForTesting
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

/**
 * Layout Inspector Properties Model
 *
 * Holds the [properties] shown in the properties and is responsible for:
 * requesting a new table of properties when needed and notifying the UI
 * for misc updates.
 */
class InspectorPropertiesModel : PropertiesModel<InspectorPropertyItem> {
  private val modelListeners: MutableList<PropertiesModelListener<InspectorPropertyItem>> = ContainerUtil.createConcurrentList()
  private var provider: PropertiesProvider? = null
  private val selectionListener = ::handleNewSelection
  private val modificationListener = ::handleModelChange
  private val connectionListener = ::handleConnectionChange
  private val propertiesListener = ::updateProperties

  var structuralUpdates = 0
    private set

  override var properties: PropertiesTable<InspectorPropertyItem> = PropertiesTable.emptyTable()
    @VisibleForTesting set

  // TODO: There probably can only be 1 layout inspector per project. Do we need to handle changes?
  var layoutInspector: LayoutInspector? by Delegates.observable(null, ::inspectorChanged)

  @Suppress("UNUSED_PARAMETER")
  private fun inspectorChanged(property: KProperty<*>, oldInspector: LayoutInspector?, newInspector: LayoutInspector?) {
    oldInspector?.inspectorModel?.selectionListeners?.remove(selectionListener)
    newInspector?.inspectorModel?.selectionListeners?.add(selectionListener)
    oldInspector?.inspectorModel?.modificationListeners?.remove(modificationListener)
    newInspector?.inspectorModel?.modificationListeners?.add(modificationListener)
    oldInspector?.inspectorModel?.connectionListeners?.remove(connectionListener)
    newInspector?.inspectorModel?.connectionListeners?.add(connectionListener)
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
  private fun handleNewSelection(oldView: ViewNode?, newView: ViewNode?, origin: SelectionOrigin) {
    val currentProvider = provider
    if (newView != null && currentProvider != null) {
      currentProvider.requestProperties(newView)
    }
    else {
      properties = PropertiesTable.emptyTable()
      firePropertiesGenerated()
    }
  }

  @Suppress("UNUSED_PARAMETER")
  private fun handleModelChange(old: AndroidWindow?, new: AndroidWindow?, structuralChange: Boolean) {
    if (structuralChange) {
      structuralUpdates++
    }
    handleNewSelection(null, layoutInspector?.inspectorModel?.selection, SelectionOrigin.INTERNAL)
  }

  private fun handleConnectionChange(client: InspectorClient?) {
    provider?.resultListeners?.remove(propertiesListener)
    provider = client?.provider
    provider?.resultListeners?.add(propertiesListener)
  }

  private fun updateProperties(from: PropertiesProvider, view: ViewNode, table: PropertiesTable<InspectorPropertyItem>) {
    val selectedView = layoutInspector?.inspectorModel?.selection
    if (from != provider || selectedView == null || selectedView.drawId != view.drawId) {
      return
    }
    properties = table
    firePropertiesGenerated()
  }

  private fun firePropertiesGenerated() {
    modelListeners.forEach { ApplicationManager.getApplication().invokeLater { it.propertiesGenerated(this) } }
  }
}
