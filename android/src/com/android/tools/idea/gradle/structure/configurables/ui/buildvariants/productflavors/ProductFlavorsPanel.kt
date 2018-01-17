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
import com.android.tools.idea.gradle.structure.model.android.PsProductFlavor
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.util.IconUtil
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class ProductFlavorsPanel(val treeModel: ProductFlavorsTreeModel) : ConfigurablesMasterDetailsPanel<PsProductFlavor>(
   "Flavors",
   "android.psd.product_flavor",
    treeModel) {
  override fun getRemoveAction(): AnAction? {
    return object : DumbAwareAction("Remove Product Flavor", "Removes a Product Flavor", IconUtil.getRemoveIcon()) {
      override fun actionPerformed(e: AnActionEvent?) {
        TODO("Implement remove product flavor")
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
              val selectedObject = (tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode)?.userObject
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
}