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

import com.android.tools.idea.gradle.structure.configurables.android.buildvariants.buildtypes.BuildTypesTreeModel
import com.android.tools.idea.gradle.structure.configurables.ui.ConfigurablesMasterDetailsPanel
import com.android.tools.idea.gradle.structure.model.android.PsBuildType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.util.IconUtil
import javax.swing.tree.TreePath

class BuildTypesPanel(val treeModel: BuildTypesTreeModel) :
    ConfigurablesMasterDetailsPanel<PsBuildType>("Build Types", "android.psd.build_type", treeModel) {
  override fun getRemoveAction(): AnAction? {
    return object : DumbAwareAction("Remove Build Type", "Removes a Build Type", IconUtil.getRemoveIcon()) {
      override fun actionPerformed(e: AnActionEvent?) {
        TODO("Implement remove build type")
      }
    }
  }

  override fun getCreateActions(): List<AnAction> {
    return listOf<DumbAwareAction>(
        object : DumbAwareAction("Add Variant", "", IconUtil.getAddIcon()) {
          override fun actionPerformed(e: AnActionEvent?) {
            val newName =
                Messages.showInputDialog(
                    e?.project,
                    "Enter a new build type name:",
                    "Create New Build Type",
                    null,
                    "", object : InputValidator {
                  override fun checkInput(inputString: String?): Boolean = !inputString.isNullOrBlank()
                  override fun canClose(inputString: String?): Boolean = !inputString.isNullOrBlank()
                })
            if (newName != null) {
              val (_, node) = treeModel.createBuildType(newName)
              tree.selectionPath = TreePath(treeModel.getPathToRoot(node))
            }
          }
        }
    )
  }
}