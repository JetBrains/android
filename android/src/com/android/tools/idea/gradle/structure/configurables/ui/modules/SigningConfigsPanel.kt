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
import com.android.tools.idea.gradle.structure.model.android.PsSigningConfig
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.util.IconUtil
import javax.swing.tree.TreePath

class SigningConfigsPanel(val treeModel: SigningConfigsTreeModel) :
    ConfigurablesMasterDetailsPanel<PsSigningConfig>(
        "Signing Configs",
        "android.psd.signing_config",
        treeModel
    ) {
  override fun getRemoveAction(): AnAction? {
    return object : DumbAwareAction("Remove Signing Config", "Remove Signing Config", IconUtil.getRemoveIcon()) {
      override fun actionPerformed(e: AnActionEvent?) {
        TODO("Implement remove signing config")
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
              tree.selectionPath = TreePath(treeModel.getPathToRoot(node))
            }
          }
        }
    )
  }
}