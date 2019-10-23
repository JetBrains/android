/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.project.treeview

import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.AbstractDependencyNode
import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.AbstractPsNodeTreeBuilder
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.model.PsBaseDependency
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel

class TargetModulesTreeBuilder(
  tree: JTree,
  treeModel: DefaultTreeModel,
  uiSettings: PsUISettings
) : AbstractPsNodeTreeBuilder(tree, treeModel, TargetModulesTreeStructure(uiSettings)) {

  override fun isSmartExpand(): Boolean = false

  fun displayTargetModules(dependencyNodes: List<AbstractDependencyNode<out PsBaseDependency>>) {
    val treeStructure = treeStructure
    if (treeStructure is TargetModulesTreeStructure) {
      treeStructure.displayTargetModules(dependencyNodes.map { it.models })
      queueUpdate()
    }
  }
}
