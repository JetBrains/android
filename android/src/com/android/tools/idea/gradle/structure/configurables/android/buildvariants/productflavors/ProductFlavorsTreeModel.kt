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
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.intellij.openapi.ui.NamedConfigurable
import javax.swing.tree.DefaultMutableTreeNode

class ProductFlavorsTreeModel(
    module: PsAndroidModule,
    rootNode: DefaultMutableTreeNode
) : ConfigurablesTreeModel(module, rootNode) {

  fun createProductFlavor(newName: String, currentDimension: String?): Pair<ProductFlavorConfigurable, DefaultMutableTreeNode> {
    val productFlavor = module.addNewProductFlavor(newName)
    if (currentDimension != null) {
      productFlavor.dimension = ParsedValue.Set.Parsed(currentDimension)
    }
    val configurable = ProductFlavorConfigurable(productFlavor)
    // TODO: properly handle not found and pre 3.0
    val node = createNode(findDimensionNode(currentDimension.orEmpty()) ?: rootNode, configurable)
    return configurable to node
  }

  fun createFlavorDimension(newName: String): Pair<FlavorDimensionConfigurable, DefaultMutableTreeNode> {
    module.addNewFlavorDimension(newName)
    val configurable = FlavorDimensionConfigurable(module, newName)
    val node = createNode(rootNode, configurable)
    return configurable to node
  }

  private fun findDimensionNode(dimension: String) =
      rootNode.children().toList()
          .map { it as? DefaultMutableTreeNode }
          .find { (it?.userObject as? FlavorDimensionConfigurable)?.flavorDimension == dimension }
}

fun createProductFlavorsModel(module: PsAndroidModule): ProductFlavorsTreeModel =
    ProductFlavorsTreeModel(
        module,
        createConfigurablesTree(
            object : NamedContainerConfigurableBase<String>("Flavor Dimensions") {
              override fun getChildren(): List<NamedConfigurable<String>> =
                  module.parsedModel?.android()?.flavorDimensions()
                      ?.map { FlavorDimensionConfigurable(module, it.value()) }
                      ?: listOf()
            }))


