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
import com.android.tools.idea.structure.configurables.ui.CrossModuleUiStateComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.MasterDetailsComponent
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.ui.JBSplitter
import com.intellij.ui.navigation.Place
import com.intellij.ui.navigation.Place.goFurther
import com.intellij.ui.navigation.Place.queryFurther
import com.intellij.util.IconUtil
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.kotlin.utils.addIfNotNull
import javax.swing.JComponent
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

const val PROPERTY_PLACE_NAME: String = "android.psd.property"
/**
 * A master-details panel for configurables representing type [ModelT].
 */
abstract class ConfigurablesMasterDetailsPanel<ModelT>(
    override val title: String,
    private val placeName: String,
    private val treeModel: ConfigurablesTreeModel,
    private val uiSettings: PsUISettings
) : MasterDetailsComponent(), ModelPanel<ModelT>, Place.Navigator, CrossModuleUiStateComponent, Disposable {

  private var inQuietSelection = false

  abstract fun getRemoveAction(): AnAction?
  abstract fun getRenameAction(): AnAction?
  abstract fun getCreateActions(): List<AnAction>
  abstract fun PsUISettings.getLastEditedItem(): String?
  abstract fun PsUISettings.setLastEditedItem(value: String?)

  init {
    // Calling initTree() first so that various changes made by the call can can be
    // overridden below. The method invocation, however, is still required. It configures
    // the cell renderer which does not display icons otherwise.
    @Suppress("LeakingThis")
    initTree()

    splitter.orientation = true
    (splitter as JBSplitter).splitterProportionKey = "android.psd.proportion.configurables"
    tree.model = treeModel
    myRoot = treeModel.rootNode as MyNode
    treeModel.addTreeModelListener(object: TreeModelListener{
      override fun treeNodesInserted(e: TreeModelEvent?) {
        val treePath = e?.treePath
        if (treePath?.parentPath == null) {
          tree.expandPath(treePath)
        }
      }

      override fun treeStructureChanged(e: TreeModelEvent?)= Unit

      override fun treeNodesChanged(e: TreeModelEvent?) = Unit

      override fun treeNodesRemoved(e: TreeModelEvent?) = Unit
    })
    tree.isRootVisible = false
    TreeUtil.expandAll(tree)
  }

  private var myComponent: JComponent? = null

  override fun getComponent(): JComponent = myComponent ?: super.createComponent().also {
    myComponent = it
    // Additionally register some shortcuts to be available while focus is within the component.
    registerShortcuts(it)
  }

  final override fun createComponent(): Nothing = throw UnsupportedOperationException("Use getComponent() instead.")

  override fun dispose() = disposeUIResources()

  private var actionsCreated: Boolean = false
  private var createActionInstance: AnAction? = null
  private var removeActionInstance: AnAction? = null
  private var renameActionInstance: AnAction? = null

  override fun createActions(fromPopup: Boolean): ArrayList<AnAction>? {
    if (!actionsCreated) {
      val createActions = getCreateActions()
      createActionInstance = when {
        createActions.size == 1 -> createActions[0]
        createActions.isNotEmpty() ->
          MyActionGroupWrapper(object : ActionGroup("Add", "Add", IconUtil.addIcon) {
            override fun getChildren(e: AnActionEvent?): Array<AnAction> {
              return createActions.toTypedArray()
            }
          })
        else -> null
      }

      removeActionInstance = getRemoveAction()
      renameActionInstance = getRenameAction()
      actionsCreated = true
      // Register shortcuts for tree (as other components are not yet available). It is enough to make tooltips include them.
      registerShortcuts(tree)
    }

    return ArrayList<AnAction>().apply {
      addIfNotNull(createActionInstance)
      addIfNotNull(removeActionInstance)
      addIfNotNull(renameActionInstance)
    }
  }

  private fun registerShortcuts(focusComponent: JComponent) {
    fun AnAction.withShortcutsIn(focusComponent: JComponent, action: String) {
      registerCustomShortcutSet(ActionManager.getInstance().getAction(action).shortcutSet, focusComponent)
    }

    createActionInstance?.withShortcutsIn(focusComponent, IdeActions.ACTION_NEW_ELEMENT)
    removeActionInstance?.withShortcutsIn(tree, IdeActions.ACTION_DELETE)
    renameActionInstance?.withShortcutsIn(focusComponent, IdeActions.ACTION_RENAME)
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

  private fun findFirstLeafConfigurableNode(): MyNode? =
    treeModel
      .rootNode
      .breadthFirstEnumeration()
      .asSequence()
      .mapNotNull { it as? MyNode }
      .firstOrNull { it != myRoot && it.childCount == 0 }

  override fun queryPlace(place: Place) {
    val selectedNode = tree.selectionPath?.lastPathComponent as? MasterDetailsComponent.MyNode
    val namedConfigurable = selectedNode?.userObject as? NamedConfigurable<*>
    if (namedConfigurable != null) {
      place.putPath(placeName, namedConfigurable.displayName)
      queryFurther(namedConfigurable, place)
    }
  }

  override fun updateSelection(configurable: NamedConfigurable<*>?) {
    // UpdateSelection might be expensive as it always rebuilds the element tree.
    if (configurable === myCurrentConfigurable) return
    super.updateSelection(configurable)
    if (!inQuietSelection) {
      saveUiState()
      myHistory.pushQueryPlace()
    }
  }

  override fun restoreUiState() {
    val configurableNode = uiSettings.getLastEditedItem()?.let { findConfigurableNode(it) } ?: findFirstLeafConfigurableNode()
    if (configurableNode != null) {
      inQuietSelection = true
      try {
        selectNode(configurableNode)
      }
      finally {
        inQuietSelection = false
      }
    }
  }

  private fun saveUiState() {
    if (selectedConfigurable == null) return
    uiSettings.setLastEditedItem(selectedConfigurable?.displayName)
  }

  protected fun selectNode(node: TreeNode?) {
    if (node != null) {
      tree.selectionPath = TreePath(treeModel.getPathToRoot(node))
    }
  }

  // This override prevents this class from inheriting setHistory implementations from both MasterDetailsComponent and Place.Navigator
  override fun setHistory(history: com.intellij.ui.navigation.History) {
    myHistory = history
  }
}

fun validateAndShow(title: String = "Error", validateAction: () -> String?): Boolean =
  validateAction()?.also { Messages.showErrorDialog(it, title) } == null

class NameValidator(val validator: (String?) -> String?) : InputValidator {
  override fun checkInput(inputString: String?): Boolean = !inputString.isNullOrBlank()
  override fun canClose(inputString: String?): Boolean =
    validateAndShow { validator(inputString) }
}

fun renameWithDialog(
  message: String,
  title: String,
  renameReferencesCheckbox: Boolean,
  renameReferencesMessage: String,
  currentName: String?,
  validator: NameValidator,
  block: (newName: String, renameReferences: Boolean) -> Unit
) {
  val (newName, alsoRenameRelated) = Messages.showInputDialogWithCheckBox(
    message,
    title,
    renameReferencesMessage,
    renameReferencesCheckbox,
    renameReferencesCheckbox,
    null,
    currentName.orEmpty(),
    validator
  )
  if (newName != null) {
    block(newName, alsoRenameRelated)
  }
}
