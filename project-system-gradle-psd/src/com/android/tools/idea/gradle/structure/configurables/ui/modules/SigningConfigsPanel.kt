/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.ui.modules

import com.android.tools.idea.gradle.structure.configurables.ConfigurablesTreeModel
import com.android.tools.idea.gradle.structure.configurables.findChildFor
import com.android.tools.idea.gradle.structure.configurables.getModel
import com.android.tools.idea.gradle.structure.configurables.ui.ConfigurablesMasterDetailsPanel
import com.android.tools.idea.gradle.structure.configurables.ui.NameValidator
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.renameWithDialog
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsSigningConfig
import com.android.tools.idea.structure.dialog.logUsagePsdAction
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.PSDEvent
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.YES

const val SIGNING_CONFIGS_DISPLAY_NAME = "Signing Configs"
class SigningConfigsPanel(
  val module: PsAndroidModule,
  val treeModel: ConfigurablesTreeModel,
  psUiSettings: PsUISettings
) :
    ConfigurablesMasterDetailsPanel<PsSigningConfig>(
      SIGNING_CONFIGS_DISPLAY_NAME,
      "android.psd.signing_config",
      treeModel,
      psUiSettings
    ) {
  private val nameValidator = NameValidator { module.validateSigningConfigName(it.orEmpty()) }

  override fun getRemoveAction(): AnAction {
    return object : DumbAwareAction("Remove Signing Config", "Removes a Signing Config", AllIcons.General.Remove) {
      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = selectedConfigurable != null
      }

      override fun actionPerformed(e: AnActionEvent) {
        if (Messages.showYesNoDialog(
            e.project,
            "Remove signing config '${selectedConfigurable?.displayName}' from the module?",
            "Remove Signing Config",
            Messages.getQuestionIcon()
          ) == YES) {
          module.parent.ideProject.logUsagePsdAction(AndroidStudioEvent.EventKind.PROJECT_STRUCTURE_DIALOG_MODULES_SIGNINGCONFIGS_REMOVE)
          val nodeToSelectAfter = selectedNode.nextSibling ?: selectedNode.previousSibling
          module.removeSigningConfig(selectedNode.getModel() ?: return)
          selectNode(nodeToSelectAfter)
        }
      }
    }
  }

  override fun getRenameAction(): AnAction {
    return object : DumbAwareAction("Rename Signing Config", "Renames a Signing Config", AllIcons.Actions.Edit) {
      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = selectedConfigurable != null
      }

      override fun actionPerformed(e: AnActionEvent) {
        renameWithDialog(
          "Enter a new name for signing config '${selectedConfigurable?.displayName}':",
          "Rename Signing Type",
          true,
          "Also update references",
          selectedConfigurable?.displayName,
          nameValidator
        ) { newName, alsoRenameReferences ->
          module.parent.ideProject.logUsagePsdAction(AndroidStudioEvent.EventKind.PROJECT_STRUCTURE_DIALOG_MODULES_SIGNINGCONFIGS_RENAME)
          (selectedNode.getModel<PsSigningConfig>() ?: return@renameWithDialog).rename(newName, alsoRenameReferences)
        }
      }
    }
  }

  override fun getCreateActions(): List<AnAction> {
    return listOf<DumbAwareAction>(
        object : DumbAwareAction("Add Signing Config", "", AllIcons.General.Add) {
          override fun actionPerformed(e: AnActionEvent) {
            val newName =
                Messages.showInputDialog(
                  e.project,
                  "Enter a new signing config name:",
                  "Create New Signing Config",
                  null,
                  "",
                  nameValidator)
            if (newName != null) {
              module.parent.ideProject.logUsagePsdAction(AndroidStudioEvent.EventKind.PROJECT_STRUCTURE_DIALOG_MODULES_SIGNINGCONFIGS_ADD)
              val signingConfig = module.addNewSigningConfig(newName)
              val node = treeModel.rootNode.findChildFor(signingConfig)
              selectNode(node)
            }
          }
        }
    )
  }

  override fun PsUISettings.getLastEditedItem(): String? = LAST_EDITED_SIGNING_CONFIG

  override fun PsUISettings.setLastEditedItem(value: String?) {
    LAST_EDITED_SIGNING_CONFIG = value
  }

  override val topConfigurable: PSDEvent.PSDTopTab = PSDEvent.PSDTopTab.PROJECT_STRUCTURE_DIALOG_TOP_TAB_SIGNING_CONFIGS
}
