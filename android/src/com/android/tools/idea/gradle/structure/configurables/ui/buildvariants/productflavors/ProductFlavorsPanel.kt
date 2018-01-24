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
package com.android.tools.idea.gradle.structure.configurables.ui.buildvariants.productflavors

import com.android.tools.idea.gradle.structure.configurables.android.buildvariants.productflavors.FlavorDimensionConfigurable
import com.android.tools.idea.gradle.structure.configurables.android.buildvariants.productflavors.ProductFlavorConfigurable
import com.android.tools.idea.gradle.structure.configurables.android.buildvariants.productflavors.ProductFlavorsTreeModel
import com.android.tools.idea.gradle.structure.configurables.ui.ConfigurablesMasterDetailsPanel
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.model.android.PsProductFlavor
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.util.IconUtil
import javax.swing.tree.TreePath

class ProductFlavorsPanel(val treeModel: ProductFlavorsTreeModel) : ConfigurablesMasterDetailsPanel<PsProductFlavor>(
   "Flavors",
   "android.psd.product_flavor",
    treeModel) {
  override fun getRemoveAction(): AnAction? {
    return object : DumbAwareAction(removeTextFor(null), removeDescriptionFor(null), IconUtil.getRemoveIcon()) {
      override fun update(e: AnActionEvent?) {
        e?.presentation?.apply {
          isEnabled = selectedConfigurable != null
          text = removeTextFor(selectedConfigurable)
          description = removeDescriptionFor(selectedConfigurable)
        }
      }

      override fun actionPerformed(e: AnActionEvent?) {
        when (selectedConfigurable) {
          is FlavorDimensionConfigurable -> {
            if (Messages.showYesNoDialog(
                e?.project,
                "Remove flavor dimension '${selectedConfigurable?.displayName}' from the module?",
                "Remove Flavor Dimension",
                Messages.getQuestionIcon()
              ) == Messages.YES) {
              val nodeToSelectAfter = selectedNode.nextSibling ?: selectedNode.previousSibling
              treeModel.removeFlavorDimension(selectedNode)
              selectNode(nodeToSelectAfter)
            }
          }
          is ProductFlavorConfigurable -> {
            if (Messages.showYesNoDialog(
                e?.project,
                "Remove product flavor '${selectedConfigurable?.displayName}' from the module?",
                "Remove Product Flavor",
                Messages.getQuestionIcon()
              ) == Messages.YES) {
              val nodeToSelectAfter = selectedNode.nextSibling ?: selectedNode.previousSibling ?: selectedNode.parent
              treeModel.removeProductFlavor(selectedNode)
              selectNode(nodeToSelectAfter)
            }
          }
        }
      }
    }
  }

  override fun getCreateActions(): List<AnAction> {
    return listOf(
        object : DumbAwareAction("Add Dimension", "", IconUtil.getAddIcon()) {
          override fun actionPerformed(e: AnActionEvent?) {
            val newName =
                Messages.showInputDialog(
                    e?.project,
                    "Enter a new flavor dimension name:",
                    "Create New Flavor Dimension",
                    null,
                    "", object : InputValidator {
                  override fun checkInput(inputString: String?): Boolean = !inputString.isNullOrBlank()
                  override fun canClose(inputString: String?): Boolean = !inputString.isNullOrBlank()
                })
            if (newName != null) {
              val (_, node) = treeModel.createFlavorDimension(newName)
              tree.selectionPath = TreePath(treeModel.getPathToRoot(node))
            }
          }
        },
        object : DumbAwareAction("Add Product Flavor", "", IconUtil.getAddIcon()) {
          override fun actionPerformed(e: AnActionEvent?) {
            val newName =
                Messages.showInputDialog(
                    e?.project,
                    "Enter a new product flavor name:",
                    "Create New Product Flavor",
                    null,
                    "", object : InputValidator {
                  override fun checkInput(inputString: String?): Boolean = !inputString.isNullOrBlank()
                  override fun canClose(inputString: String?): Boolean = !inputString.isNullOrBlank()
                })
            if (newName != null) {
              val selectedObject = selectedConfigurable
              val currentDimension = when (selectedObject) {
                is FlavorDimensionConfigurable -> selectedObject.flavorDimension
                is ProductFlavorConfigurable -> (selectedObject.model.dimension as? ParsedValue.Set.Parsed<String>)?.value
                else -> null
              }
              val (_, node) = treeModel.createProductFlavor(newName, currentDimension)
              tree.selectionPath = TreePath(treeModel.getPathToRoot(node))
            }
          }
        }
    )
  }

  override fun PsUISettings.getLastEditedItem(): String? = LAST_EDITED_FLAVOR_OR_DIMENSION

  override fun PsUISettings.setLastEditedItem(value: String?) {
    LAST_EDITED_FLAVOR_OR_DIMENSION = value
  }
}

private fun removeTextFor(configurable: NamedConfigurable<*>?) = when (configurable) {
  is FlavorDimensionConfigurable -> "Remove Flavor Dimension"
  is ProductFlavorConfigurable -> "Remove Product Flavor"
  else -> "Remove"
}

private fun removeDescriptionFor(configurable: NamedConfigurable<*>?) = when (configurable) {
  is FlavorDimensionConfigurable -> "Removes a flavor dimension"
  is ProductFlavorConfigurable -> "Removes a product flavor"
  else -> "Removes a product flavor or flavor dimension"
}

