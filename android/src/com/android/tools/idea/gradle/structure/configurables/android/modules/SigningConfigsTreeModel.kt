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
package com.android.tools.idea.gradle.structure.configurables.android.modules

import com.android.tools.idea.gradle.structure.configurables.ConfigurablesTreeModel
import com.android.tools.idea.gradle.structure.configurables.NamedContainerConfigurableBase
import com.android.tools.idea.gradle.structure.configurables.createConfigurablesTree
import com.android.tools.idea.gradle.structure.configurables.listFromGenerator
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsSigningConfig
import com.intellij.openapi.ui.NamedConfigurable
import javax.swing.tree.DefaultMutableTreeNode

class SigningConfigsTreeModel(
    module: PsAndroidModule,
    rootNode: DefaultMutableTreeNode
) : ConfigurablesTreeModel(module, rootNode) {

  fun createSigningConfig(newName: String): Pair<SigningConfigConfigurable, DefaultMutableTreeNode> {
    val signingConfig = module.addNewSigningConfig(newName)
    val configurable = SigningConfigConfigurable(signingConfig)
    val node = createNode(rootNode, configurable)
    return configurable to node
  }

  fun removeSigningConfig(node: DefaultMutableTreeNode) {
    val signingConfigConfigurable = node.userObject as SigningConfigConfigurable
    val signingConfig = signingConfigConfigurable.model
    module.removeSigningConfig(signingConfig)
    removeNodeFromParent(node)
  }
}

fun createSigningConfigsModel(module: PsAndroidModule): SigningConfigsTreeModel =
    SigningConfigsTreeModel(
        module,
        createConfigurablesTree(
            object : NamedContainerConfigurableBase<PsSigningConfig>("Signing Configs") {
              override fun getChildren(): List<NamedConfigurable<PsSigningConfig>> =
                  listFromGenerator<PsSigningConfig> { consumer -> module.forEachSigningConfig { consumer(it) } }
                      .map(::SigningConfigConfigurable)
            }
        ))


