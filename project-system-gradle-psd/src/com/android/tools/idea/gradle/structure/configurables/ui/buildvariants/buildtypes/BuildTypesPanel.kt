// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.gradle.structure.configurables.ui.buildvariants.buildtypes

import com.android.tools.idea.gradle.structure.configurables.ConfigurablesTreeModel
import com.android.tools.idea.gradle.structure.configurables.findChildFor
import com.android.tools.idea.gradle.structure.configurables.getModel
import com.android.tools.idea.gradle.structure.configurables.ui.ConfigurablesMasterDetailsPanel
import com.android.tools.idea.gradle.structure.configurables.ui.NameValidator
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.renameWithDialog
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsBuildType
import com.android.tools.idea.structure.dialog.logUsagePsdAction
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.PSDEvent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.util.IconUtil
import javax.swing.tree.TreePath

const val BUILD_TYPES_DISPLAY_NAME: String = "Build Types"
const val BUILD_TYPES_PLACE_NAME: String = "android.psd.build_type"
class BuildTypesPanel(
  val module: PsAndroidModule,
  val treeModel: ConfigurablesTreeModel,
  uiSettings: PsUISettings
) :
  ConfigurablesMasterDetailsPanel<PsBuildType>(BUILD_TYPES_DISPLAY_NAME, BUILD_TYPES_PLACE_NAME, treeModel, uiSettings) {

  private val nameValidator = NameValidator { module.validateBuildTypeName(it.orEmpty()) }

  override fun getRemoveAction(): AnAction {
    return object : DumbAwareAction("Remove Build Type", "Removes a Build Type", IconUtil.removeIcon) {
      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = selectedConfigurable != null
      }

      override fun actionPerformed(e: AnActionEvent) {
        if (Messages.showYesNoDialog(
            e.project,
            "Remove build type '${selectedConfigurable?.displayName}' from the module?",
            "Remove Build Type",
            Messages.getQuestionIcon()
          ) == Messages.YES) {
          module.parent.ideProject.logUsagePsdAction(AndroidStudioEvent.EventKind.PROJECT_STRUCTURE_DIALOG_BUILTYPES_REMOVE)
          val nodeToSelectAfter = selectedNode.nextSibling ?: selectedNode.previousSibling
          module.removeBuildType(selectedNode.getModel() ?: return)
          selectNode(nodeToSelectAfter)
        }
      }
    }
  }

  override fun getRenameAction(): AnAction {
    return object : DumbAwareAction("Rename Build Type", "Renames a Build Type", IconUtil.editIcon) {
      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = selectedConfigurable != null
      }

      override fun actionPerformed(e: AnActionEvent) {
        renameWithDialog(
          "Enter a new name for build type '${selectedConfigurable?.displayName}':",
          "Rename Build Type",
          false,
          "Also rename related build types",
          selectedConfigurable?.displayName,
          nameValidator
        ) { newName, alsoRenameReferences ->
          module.parent.ideProject.logUsagePsdAction(AndroidStudioEvent.EventKind.PROJECT_STRUCTURE_DIALOG_BUILTYPES_RENAME)
          (selectedNode.getModel<PsBuildType>() ?: return@renameWithDialog).rename(newName)
        }
      }
    }
  }

  override fun getCreateActions(): List<AnAction> {
    return listOf<DumbAwareAction>(
        object : DumbAwareAction("Add Build Type", "", IconUtil.addIcon) {
          override fun actionPerformed(e: AnActionEvent) {
            val newName =
                Messages.showInputDialog(
                  e.project,
                  "Enter a new build type name:",
                  "Create New Build Type",
                  null,
                  "",
                  nameValidator)
            if (newName != null) {
              module.parent.ideProject.logUsagePsdAction(AndroidStudioEvent.EventKind.PROJECT_STRUCTURE_DIALOG_BUILTYPES_ADD)
              val buildType = module.addNewBuildType(newName)
              val node = treeModel.rootNode.findChildFor(buildType)
              tree.selectionPath = TreePath(treeModel.getPathToRoot(node))
            }
          }
        }
    )
  }

  override fun PsUISettings.getLastEditedItem(): String? = LAST_EDITED_BUILD_TYPE

  override fun PsUISettings.setLastEditedItem(value: String?) {
    LAST_EDITED_BUILD_TYPE = value
  }

  override val topConfigurable: PSDEvent.PSDTopTab = PSDEvent.PSDTopTab.PROJECT_STRUCTURE_DIALOG_TOP_TAB_BUILD_TYPES
}
