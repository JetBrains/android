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
package com.android.tools.idea.gradle.structure.configurables.android.buildvariants.productflavors

import com.android.tools.idea.gradle.structure.configurables.ConfigurablesTreeModel
import com.android.tools.idea.gradle.structure.configurables.NamedContainerConfigurableBase
import com.android.tools.idea.gradle.structure.configurables.createConfigurablesTree
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsFlavorDimension
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.intellij.openapi.ui.NamedConfigurable
import javax.swing.tree.DefaultMutableTreeNode

class ProductFlavorsTreeModel(
    module: PsAndroidModule,
    rootNode: DefaultMutableTreeNode
) : ConfigurablesTreeModel(module, rootNode) {

  fun createProductFlavor(newName: String, currentDimension: String?): DefaultMutableTreeNode? {
    val productFlavor = module.addNewProductFlavor(currentDimension.orEmpty(), newName)
    if (currentDimension != null) {
      productFlavor.dimension = ParsedValue.Set.Parsed(currentDimension, DslText.Literal)
    }
    val configurable = ProductFlavorConfigurable(productFlavor)
    // TODO: properly handle not found and pre 3.0
    return createNode(findDimensionNode(currentDimension.orEmpty()) ?: rootNode, configurable)
  }

  fun removeProductFlavor(node: DefaultMutableTreeNode) {
    val productFlavorConfigurable = node.userObject as ProductFlavorConfigurable
    val productFlavor = productFlavorConfigurable.model
    module.removeProductFlavor(productFlavor)
    removeNodeFromParent(node)
  }

  fun createFlavorDimension(newName: String): DefaultMutableTreeNode? {
    val flavorDimension = module.addNewFlavorDimension(newName)
    val configurable = FlavorDimensionConfigurable(module, flavorDimension)
    return createNode(rootNode, configurable)
  }

  fun removeFlavorDimension(node: DefaultMutableTreeNode) {
    val flavorDimensionConfigurable = node.userObject as FlavorDimensionConfigurable
    val flavorDimension = flavorDimensionConfigurable.flavorDimension
    module.removeFlavorDimension(flavorDimension.name)
    removeNodeFromParent(node)
  }

  private fun findDimensionNode(dimension: String) =
      rootNode.children().toList()
          .map { it as? DefaultMutableTreeNode }
        .find { (it?.userObject as? FlavorDimensionConfigurable)?.flavorDimension?.name == dimension }
}

fun createProductFlavorsModel(module: PsAndroidModule): ProductFlavorsTreeModel =
    ProductFlavorsTreeModel(
        module,
        createConfigurablesTree(
          object : NamedContainerConfigurableBase<PsFlavorDimension>("Flavor Dimensions") {
            override fun getChildren(): List<NamedConfigurable<PsFlavorDimension>> =
              module.flavorDimensions.map { FlavorDimensionConfigurable(module, it) }
            }))


