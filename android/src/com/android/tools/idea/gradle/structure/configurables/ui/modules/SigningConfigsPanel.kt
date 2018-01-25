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

import com.android.tools.idea.gradle.structure.configurables.android.modules.SigningConfigsTreeModel
import com.android.tools.idea.gradle.structure.configurables.ui.ConfigurablesMasterDetailsPanel
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.model.android.PsSigningConfig
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.YES
import com.intellij.util.IconUtil

class SigningConfigsPanel(
  val treeModel: SigningConfigsTreeModel,
  psUiSettings: PsUISettings
) :
    ConfigurablesMasterDetailsPanel<PsSigningConfig>(
        "Signing Configs",
        "android.psd.signing_config",
        treeModel,
        psUiSettings
    ) {
  override fun getRemoveAction(): AnAction? {
    return object : DumbAwareAction("Remove Signing Config", "Removes a Signing Config", IconUtil.getRemoveIcon()) {
      override fun update(e: AnActionEvent?) {
        e?.presentation?.isEnabled = selectedConfigurable != null
      }

      override fun actionPerformed(e: AnActionEvent?) {
        if (Messages.showYesNoDialog(
            e?.project,
            "Remove signing config '${selectedConfigurable?.displayName}' from the module?",
            "Remove Signing Config",
            Messages.getQuestionIcon()
          ) == YES) {
          val nodeToSelectAfter = selectedNode.nextSibling ?: selectedNode.previousSibling
          treeModel.removeSigningConfig(selectedNode)
          selectNode(nodeToSelectAfter)
        }
      }
    }
  }

  override fun getCreateActions(): List<AnAction> {
    return listOf<DumbAwareAction>(
        object : DumbAwareAction("Add Signing Config", "", IconUtil.getAddIcon()) {
          override fun actionPerformed(e: AnActionEvent?) {
            val newName =
                Messages.showInputDialog(
                    e?.project,
                    "Enter a new signing config name:",
                    "Create New Signing Config",
                    null,
                    "", object : InputValidator {
                  override fun checkInput(inputString: String?): Boolean = !inputString.isNullOrBlank()
                  override fun canClose(inputString: String?): Boolean = !inputString.isNullOrBlank()
                })
            if (newName != null) {
              val (_, node) = treeModel.createSigningConfig(newName)
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
}