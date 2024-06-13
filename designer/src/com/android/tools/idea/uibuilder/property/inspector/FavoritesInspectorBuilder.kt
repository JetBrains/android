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
package com.android.tools.idea.uibuilder.property.inspector

import com.android.SdkConstants.APP_PREFIX
import com.android.SdkConstants.TOOLS_NS_NAME
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.idea.uibuilder.property.NlNewPropertyItem
import com.android.tools.idea.uibuilder.property.NlPropertiesModel
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.idea.uibuilder.property.support.NlTwoStateBooleanControlTypeProvider
import com.android.tools.idea.uibuilder.property.ui.EmptyTablePanel
import com.android.tools.property.panel.api.ControlType
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.EnumSupportProvider
import com.android.tools.property.panel.api.FilteredPTableModel
import com.android.tools.property.panel.api.InspectorBuilder
import com.android.tools.property.panel.api.InspectorLineModel
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertiesTable
import com.android.tools.property.panel.api.TableLineModel
import com.android.tools.property.panel.api.TableUIProvider
import com.android.tools.property.panel.impl.support.SimpleControlTypeProvider
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Splitter
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions

private const val ADD_ATTRIBUTE_ACTION_TITLE = "Add favorite"
private const val DELETE_ROW_ACTION_TITLE = "Remove selected favorite"
private const val FAVORITE_SEPARATOR_CHAR = ';'
private const val PACKAGE_SEPARATOR_CHAR = ':'

@VisibleForTesting const val FAVORITES_PROPERTY = "ANDROID.FAVORITE_PROPERTIES"

class FavoritesInspectorBuilder(
  private val model: NlPropertiesModel,
  enumSupportProvider: EnumSupportProvider<NlPropertyItem>,
) : InspectorBuilder<NlPropertyItem> {
  private val nameControlTypeProvider =
    SimpleControlTypeProvider<NlNewPropertyItem>(ControlType.TEXT_EDITOR)
  private val nameEditorProvider = EditorProvider.createForNames<NlNewPropertyItem>()
  private val controlTypeProvider = NlTwoStateBooleanControlTypeProvider(enumSupportProvider)
  private val editorProvider = EditorProvider.create(enumSupportProvider, controlTypeProvider)
  private val tableUIProvider =
    TableUIProvider(
      nameControlTypeProvider,
      nameEditorProvider,
      controlTypeProvider,
      editorProvider,
    )
  private val splitter = Splitter.on(FAVORITE_SEPARATOR_CHAR).trimResults().omitEmptyStrings()
  private var favoritesAsString = ""
  private var favorites = mutableSetOf<ResourceReference>()

  @VisibleForTesting
  fun loadFavoritePropertiesIfNeeded(): Set<ResourceReference> {
    val newFavoritesAsString = PropertiesComponent.getInstance().getValue(FAVORITES_PROPERTY, "")
    if (favoritesAsString != newFavoritesAsString) {
      try {
        val newFavorites = loadFavoriteProperties(newFavoritesAsString)
        favoritesAsString = newFavoritesAsString
        favorites.clear()
        favorites.addAll(newFavorites)
      } catch (ex: IllegalArgumentException) {
        // The property file must be corrupted. Reset the favorites:
        PropertiesComponent.getInstance().setValue(FAVORITES_PROPERTY, "")
        favoritesAsString = ""
        favorites.clear()
      }
    }
    return favorites
  }

  private fun loadFavoriteProperties(stringValue: String): Set<ResourceReference> {
    val favorites = mutableSetOf<ResourceReference>()
    for (attr in splitter.split(stringValue)) {
      val index = attr.lastIndexOf(PACKAGE_SEPARATOR_CHAR)
      val attrPackage = attr.substring(0, index)
      val attrName = attr.substring(index + 1)
      val namespace =
        when (attrPackage) {
          TOOLS_NS_NAME -> ResourceNamespace.TOOLS
          APP_PREFIX -> ResourceNamespace.RES_AUTO
          "" -> ResourceNamespace.ANDROID
          else -> ResourceNamespace.fromPackageName(attrPackage)
        }
      if (!attrPackage.contains(PACKAGE_SEPARATOR_CHAR)) {
        favorites.add(ResourceReference.attr(namespace, attrName))
      }
    }
    return favorites
  }

  private fun saveFavoriteProperties(newFavorites: Set<ResourceReference>) {
    var newFavoritesAsString = ""
    for (attr in newFavorites) {
      val attrPackage =
        when (attr.namespace) {
          ResourceNamespace.ANDROID -> ""
          ResourceNamespace.TOOLS -> TOOLS_NS_NAME
          ResourceNamespace.RES_AUTO -> APP_PREFIX
          else -> attr.namespace.packageName
        }
      if (attrPackage != null) {
        newFavoritesAsString += "$attrPackage:${attr.name}$FAVORITE_SEPARATOR_CHAR"
      }
    }
    PropertiesComponent.getInstance().setValue(FAVORITES_PROPERTY, newFavoritesAsString)
    favorites.clear()
    favorites.addAll(newFavorites)
  }

  override fun attachToInspector(
    inspector: InspectorPanel,
    properties: PropertiesTable<NlPropertyItem>,
  ) {
    if (properties.isEmpty || !InspectorSection.FAVORITES.visible) {
      return
    }
    val favorites = loadFavoritePropertiesIfNeeded()
    val favoritesTableModel =
      FilteredPTableModel(
        model,
        itemFilter = { favorites.contains(it.asReference) },
        insertOperation = ::insertNewItem,
        deleteOperation = { removeFromFavorites(it) },
        itemComparator = androidSortOrder,
      )
    val newPropertyInstance =
      NlNewPropertyItem(
        model,
        PropertiesTable.emptyTable(),
        { !favorites.contains(it.asReference) },
        { newDelegateWasAssigned(it, favoritesTableModel) },
      )
    val addNewRow = AddNewRowAction(newPropertyInstance)
    val deleteRowAction = DeleteRowAction()
    val actions = listOf(addNewRow, deleteRowAction)
    val titleModel = inspector.addExpandableTitle(InspectorSection.FAVORITES.title, false, actions)
    val tableLineModel =
      inspector.addTable(favoritesTableModel, false, tableUIProvider, actions, titleModel)
    inspector.addComponent(EmptyTablePanel(addNewRow, tableLineModel), titleModel)
    addNewRow.titleModel = titleModel
    addNewRow.lineModel = tableLineModel
    deleteRowAction.titleModel = titleModel
    deleteRowAction.lineModel = tableLineModel
    newPropertyInstance.properties = properties
    newPropertyInstance.name = ""
  }

  /**
   * The [newPropertyItem] was assigned a new delegate.
   *
   * At this point we know which attribute was assigned. Save that new attribute to the list of
   * favorites, and clear [newPropertyItem] such that a new favorite can be specified.
   */
  private fun newDelegateWasAssigned(
    newPropertyItem: NlNewPropertyItem,
    tableModel: FilteredPTableModel<NlPropertyItem>,
  ) {
    val delegate = addToFavorites(newPropertyItem) ?: return
    newPropertyItem.name = ""
    tableModel.addNewItem(delegate)
    newPropertyItem.model.firePropertyValueChangeIfNeeded()
  }

  private fun insertNewItem(
    name: String,
    @Suppress("UNUSED_PARAMETER") value: String,
  ): NlPropertyItem? {
    val newPropertyInstance = NlNewPropertyItem(model, model.properties, { true })
    newPropertyInstance.name = name
    return addToFavorites(newPropertyInstance)
  }

  private fun addToFavorites(newPropertyItem: NlNewPropertyItem): NlPropertyItem? {
    val delegate = newPropertyItem.delegate ?: return null
    val reference = delegate.asReference ?: return null
    val favorites = loadFavoritePropertiesIfNeeded()
    val newFavorites = favorites.plus(reference)
    saveFavoriteProperties(newFavorites)
    return delegate
  }

  private fun removeFromFavorites(item: NlPropertyItem): Boolean {
    val favorites = loadFavoritePropertiesIfNeeded()
    val reference = item.asReference ?: return false
    val newFavorites = favorites.minus(reference)
    if (newFavorites.size == favorites.size) {
      return false
    }
    saveFavoriteProperties(newFavorites)
    return true
  }

  private class AddNewRowAction(val newProperty: NlNewPropertyItem) :
    AnAction(ADD_ATTRIBUTE_ACTION_TITLE, ADD_ATTRIBUTE_ACTION_TITLE, AllIcons.General.Add) {

    var titleModel: InspectorLineModel? = null
    var lineModel: TableLineModel? = null

    override fun actionPerformed(event: AnActionEvent) {
      titleModel?.expanded = true
      val model = lineModel ?: return
      val nextItem = model.addItem(newProperty)
      model.requestFocus(nextItem)
    }
  }

  private inner class DeleteRowAction :
    AnAction(DELETE_ROW_ACTION_TITLE, DELETE_ROW_ACTION_TITLE, AllIcons.General.Remove) {
    var titleModel: InspectorLineModel? = null
    var lineModel: TableLineModel? = null

    init {
      val manager = ActionManager.getInstance()
      shortcutSet = manager.getAction(IdeActions.ACTION_DELETE).shortcutSet
    }

    // Running on edt because of the table data model access
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(event: AnActionEvent) {
      val enabled = lineModel?.tableModel?.items?.isNotEmpty() ?: false
      event.presentation.isEnabled = enabled
    }

    override fun actionPerformed(event: AnActionEvent) {
      titleModel?.expanded = true
      val model = lineModel ?: return
      val selected =
        (model.selectedItem ?: model.tableModel.items.firstOrNull()) as? NlPropertyItem ?: return
      if (selected is NlNewPropertyItem) {
        // This item is not in the favorites yet, just remove the item in the table:
        model.removeItem(selected)
        return
      }
      if (removeFromFavorites(selected)) {
        model.removeItem(selected)
      }
    }
  }
}
