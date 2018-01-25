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
package com.android.tools.idea.gradle.structure.configurables.ui

import com.android.tools.idea.gradle.structure.configurables.ConfigurablesTreeModel
import com.google.common.collect.Lists
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.MasterDetailsComponent
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.openapi.util.ActionCallback
import com.intellij.ui.JBSplitter
import com.intellij.ui.navigation.Place
import com.intellij.ui.navigation.Place.goFurther
import com.intellij.ui.navigation.Place.queryFurther
import com.intellij.util.IconUtil
import com.intellij.util.ui.tree.TreeUtil
import java.util.*
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

/**
 * A master-details panel for configurables representing type [ModelT].
 */
abstract class ConfigurablesMasterDetailsPanel<ModelT>(
    override val title: String,
    private val placeName: String,
    private val treeModel: ConfigurablesTreeModel
) : MasterDetailsComponent(), ModelPanel<ModelT>, Place.Navigator, PanelWithUiState {

  private var inQuietSelection = false

  abstract fun getRemoveAction(): AnAction?
  abstract fun getCreateActions(): List<AnAction>
  abstract fun PsUISettings.getLastEditedItem(): String?
  abstract fun PsUISettings.setLastEditedItem(value: String?)

  init {
    splitter.orientation = true
    (splitter as JBSplitter).splitterProportionKey = "android.psd.proportion.configurables"
    tree.model = treeModel
    tree.isRootVisible = false
    TreeUtil.expandAll(tree)
  }

  override fun createActions(fromPopup: Boolean): ArrayList<AnAction>? {
    val result = mutableListOf<AnAction>()

    val createActions = getCreateActions()
    if (createActions.isNotEmpty()) {
      result.add(
          MyActionGroupWrapper(object : ActionGroup("Add", "Add", IconUtil.getAddIcon()) {
            override fun getChildren(e: AnActionEvent?): Array<AnAction> {
              return createActions.toTypedArray()
            }
          }))
    }

    val removeAction = getRemoveAction()
    if (removeAction != null) {
      result.add(removeAction)
    }

    return Lists.newArrayList(result)
  }

  override fun processRemovedItems() {
    // Changes are applied at the Project/<All modules> level.
  }

  override fun wasObjectStored(editableObject: Any?): Boolean {
    // Changes are applied at the Project/<All modules> level.
    return false
  }

  override fun getDisplayName(): String = title

  override fun navigateTo(place: Place?, requestFocus: Boolean): ActionCallback? {
    val configurableDisplayName = place?.getPath(placeName) as? String ?: return ActionCallback.DONE
    val matchingNode = findConfigurableNode(configurableDisplayName)
    if (matchingNode != null) {
      tree.selectionPath = TreePath(treeModel.getPathToRoot(matchingNode))
      val navigator = matchingNode.userObject as? Place.Navigator
      return goFurther(navigator, place, requestFocus)
    }
    return ActionCallback.REJECTED
  }

  private fun findConfigurableNode(configurableDisplayName: String): MyNode? =
    treeModel
      .rootNode
      .breadthFirstEnumeration()
      .asSequence()
      .mapNotNull { it as? MyNode }
      .firstOrNull {
        val namedConfigurable = it.userObject as? NamedConfigurable<*>
        namedConfigurable?.displayName == configurableDisplayName
      }

  override fun queryPlace(place: Place) {
    val selectedNode = tree.selectionPath?.lastPathComponent as? MasterDetailsComponent.MyNode
    val namedConfigurable = selectedNode?.userObject as? NamedConfigurable<*>
    if (namedConfigurable != null) {
      place.putPath(placeName, namedConfigurable.displayName)
      queryFurther(namedConfigurable, place)
    }
  }

  override fun updateSelection(configurable: NamedConfigurable<*>?) {
    super.updateSelection(configurable)
    saveUiState()
    myHistory.pushQueryPlace()
  }

  override fun restoreUiState() {
    val lastEditedItem = PsUISettings.getInstance().getLastEditedItem()
    if (lastEditedItem != null) {
      val configurableNode = findConfigurableNode(lastEditedItem)
      if (configurableNode != null) {
        inQuietSelection = true
        try {
          selectNode(configurableNode)
        } finally {
          inQuietSelection = false
        }
      }
    }
  }

  private fun saveUiState() {
    if (selectedConfigurable == null) return
    PsUISettings.getInstance().setLastEditedItem(selectedConfigurable?.displayName)
  }

  protected fun selectNode(node: TreeNode?) {
    if (node != null) {
      tree.selectionPath = TreePath(treeModel.getPathToRoot(node))
    }
  }
}