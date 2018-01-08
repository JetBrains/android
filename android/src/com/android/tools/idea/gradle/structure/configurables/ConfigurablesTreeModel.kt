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
package com.android.tools.idea.gradle.structure.configurables

import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.intellij.openapi.ui.MasterDetailsComponent
import com.intellij.openapi.ui.NamedConfigurable
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * An abstract tree model that represents a hierarchy of [NamedConfigurable]s.
 */
abstract class ConfigurablesTreeModel protected constructor(
    val module: PsAndroidModule,
    val rootNode: DefaultMutableTreeNode

) : DefaultTreeModel(rootNode) {

  /**
   * Creates a [MasterDetailsComponent.MyNode] for a given [configurable] and adds it to the tree under a node [under].
   */
  protected fun createNode(under: DefaultMutableTreeNode, configurable: NamedConfigurable<*>): DefaultMutableTreeNode {
    val node = MasterDetailsComponent.MyNode(configurable)
    under.add(node)
    reload(under)
    return node
  }
}

/**
 * Generates a list using a given [generator].
 */
fun <T> listFromGenerator(generator: ((T) -> Unit) -> Unit): List<T> {
  val result = mutableListOf<T>()
  generator {
    result.add(it)
  }
  return result
}