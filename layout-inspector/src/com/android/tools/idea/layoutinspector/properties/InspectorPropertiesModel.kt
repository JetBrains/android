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
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.property.panel.api.PropertiesModel
import com.android.tools.property.panel.api.PropertiesModelListener
import com.android.tools.property.panel.api.PropertiesTable
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.VisibleForTesting
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

/**
 * Layout Inspector Properties Model
 *
 * Holds the [properties] shown in the properties and is responsible for: requesting a new table of
 * properties when needed and notifying the UI for misc updates.
 */
class InspectorPropertiesModel(parentDisposable: Disposable) :
  PropertiesModel<InspectorPropertyItem>, Disposable {
  private val modelListeners: MutableList<PropertiesModelListener<InspectorPropertyItem>> =
    ContainerUtil.createConcurrentList()
  private var provider: PropertiesProvider? = null
  private val selectionListener: (ViewNode?, ViewNode?, SelectionOrigin) -> Unit =
    { oldView, newView, selectionOrigin ->
      handleNewSelection(oldView, newView, selectionOrigin)
    }
  private val modificationListener =
    InspectorModel.ModificationListener { oldWindow, newWindow, isStructuralChange ->
      handleModelChange(oldWindow, newWindow, isStructuralChange)
    }
  private val connectionListener: (InspectorClient?) -> Unit = { handleConnectionChange(it) }
  private val propertiesListener = ResultListener { propertiesProvider, viewNode, propertiesTable ->
    updateProperties(propertiesProvider, viewNode, propertiesTable)
  }

  var structuralUpdates = 0
    private set

  override var properties: PropertiesTable<InspectorPropertyItem> = PropertiesTable.emptyTable()
    @VisibleForTesting set

  /**
   * The [properties] are for the [ViewNode] with a [ViewNode.drawId] equal to
   * [propertiesForDrawId]. Use this value to determine if we have an update to the current
   * properties or this is a different but similar node type.
   */
  private var propertiesForDrawId = 0L

  // TODO: There probably can only be 1 layout inspector per project. Do we need to handle changes?
  var layoutInspector: LayoutInspector? by Delegates.observable(null, ::inspectorChanged)

  init {
    Disposer.register(parentDisposable, this)
  }

  @Suppress("UNUSED_PARAMETER")
  private fun inspectorChanged(
    property: KProperty<*>,
    oldInspector: LayoutInspector?,
    newInspector: LayoutInspector?,
  ) {
    cleanUp(oldInspector)
    newInspector?.inspectorModel?.addSelectionListener(selectionListener)
    newInspector?.inspectorModel?.addModificationListener(modificationListener)
    newInspector?.inspectorModel?.addConnectionListener(connectionListener)
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

  override fun dispose() {
    cleanUp(layoutInspector)
    provider?.removeResultListener(propertiesListener)
  }

  private fun cleanUp(layoutInspector: LayoutInspector?) {
    layoutInspector?.inspectorModel?.removeSelectionListener(selectionListener)
    layoutInspector?.inspectorModel?.removeModificationListener(modificationListener)
    layoutInspector?.inspectorModel?.removeConnectionListener(connectionListener)
  }

  @Suppress("UNUSED_PARAMETER")
  private fun handleNewSelection(oldView: ViewNode?, newView: ViewNode?, origin: SelectionOrigin) {
    val currentProvider = provider
    if (newView != null && currentProvider != null && currentProvider != EmptyPropertiesProvider) {
      currentProvider.requestProperties(newView)
    } else {
      properties = PropertiesTable.emptyTable()
      propertiesForDrawId = 0L
      firePropertiesGenerated()
    }
  }

  @Suppress("UNUSED_PARAMETER")
  private fun handleModelChange(
    old: AndroidWindow?,
    new: AndroidWindow?,
    structuralChange: Boolean,
  ) {
    if (structuralChange) {
      structuralUpdates++
    }
    handleNewSelection(null, layoutInspector?.inspectorModel?.selection, SelectionOrigin.INTERNAL)
  }

  private fun handleConnectionChange(client: InspectorClient?) {
    provider?.removeResultListener(propertiesListener)
    provider = client?.provider
    provider?.addResultListener(propertiesListener)
  }

  private fun updateProperties(
    from: PropertiesProvider,
    view: ViewNode,
    table: PropertiesTable<InspectorPropertyItem>,
  ) {
    val selectedView = layoutInspector?.inspectorModel?.selection
    if (from != provider || selectedView == null || selectedView.drawId != view.drawId) {
      return
    }
    if (properties.sameKeys(table) && view.drawId == propertiesForDrawId) {
      for (property in table.values) {
        properties[property.namespace, property.name].value = property.snapshotValue
      }
      firePropertyValuesChanged()
    } else {
      properties = table
      propertiesForDrawId = view.drawId
      firePropertiesGenerated()
    }
  }

  private fun firePropertiesGenerated() {
    modelListeners.forEach {
      ApplicationManager.getApplication().invokeLater { it.propertiesGenerated(this) }
    }
  }

  private fun firePropertyValuesChanged() {
    modelListeners.forEach {
      ApplicationManager.getApplication().invokeLater { it.propertyValuesChanged(this) }
    }
  }
}
