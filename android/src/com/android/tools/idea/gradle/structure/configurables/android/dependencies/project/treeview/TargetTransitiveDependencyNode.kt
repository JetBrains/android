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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.project.treeview

import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode
import com.android.tools.idea.gradle.structure.model.android.ReverseDependency
import com.intellij.ui.treeStructure.SimpleNode

class TargetTransitiveDependencyNode(
  models: List<ReverseDependency.Transitive>,
  uiSettings: PsUISettings
) : AbstractPsModelNode<ReverseDependency.Transitive>(models, uiSettings) {

  init {
    autoExpandNode = false
  }

  override fun getChildren(): Array<SimpleNode> {
    val nodes = mutableListOf<SimpleNode>()
    val transitive = mutableListOf<ReverseDependency.Transitive>()
    val declared = mutableListOf<ReverseDependency.Declared>()
    models.forEach { dependency ->
      val declaredOrTransitiveLibraryDependencies = dependency.requestingResolvedDependency.getReverseDependencies()
      transitive.addAll(declaredOrTransitiveLibraryDependencies.filterIsInstance<ReverseDependency.Transitive>())
      declared.addAll(declaredOrTransitiveLibraryDependencies.filterIsInstance<ReverseDependency.Declared>())
    }
    transitive.groupBy { it.requestingResolvedDependency.spec }.forEach { transientGroup ->
      nodes.add(TargetTransitiveDependencyNode(transientGroup.value, uiSettings))
    }
    declared.groupBy { it.dependency }.forEach { declaredGroup ->
      nodes.add(TargetConfigurationNode(Configuration(declaredGroup.key.configurationName, declaredGroup.key.icon, false), uiSettings))
    }
    return nodes.toTypedArray()
  }
}