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
package com.android.tools.idea.gradle.structure.configurables.dependencies.treeview

import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsNode
import com.android.tools.idea.gradle.structure.model.PsDeclaredModuleDependency
import com.android.tools.idea.gradle.structure.model.PsModuleDependency
import com.android.tools.idea.gradle.structure.model.PsResolvedModuleDependency
import com.android.tools.idea.gradle.structure.model.targetModuleResolvedDependencies
import com.intellij.ui.treeStructure.SimpleNode

class ModuleDependencyNode : AbstractDependencyNode<PsModuleDependency> {
  private val myChildren = mutableListOf<AbstractPsModelNode<*>>()

  constructor(parent: AbstractPsNode, dependency: PsResolvedModuleDependency) : super(parent, dependency) {
    myName = dependency.toText()
    setUp(dependency)
  }

  constructor(parent: AbstractPsNode, dependencies: Collection<PsDeclaredModuleDependency>) :
    super(parent, dependencies.toList()) {
    myName = firstModel.toText()
  }

  private fun setUp(moduleDependency: PsResolvedModuleDependency) {
    myChildren.addAll(createNodesForResolvedDependencies(this, moduleDependency.targetModuleResolvedDependencies ?: return))
  }

  override fun getChildren(): Array<SimpleNode> = myChildren.toTypedArray()
}
