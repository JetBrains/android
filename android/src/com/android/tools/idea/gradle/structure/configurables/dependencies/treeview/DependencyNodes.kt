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
package com.android.tools.idea.gradle.structure.configurables.dependencies.treeview

import com.android.tools.idea.gradle.structure.configurables.ui.dependencies.PsDependencyComparator
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsNode
import com.android.tools.idea.gradle.structure.model.*
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import com.intellij.util.containers.SortedList

fun createNodesForResolvedDependencies(
  parent: AbstractPsNode,
  collection: PsDependencyCollection<*, *, *>
): List<AbstractPsModelNode<*>> {
  val allTransitive = Sets.newHashSet<String>()
  val children = Lists.newArrayList<AbstractPsModelNode<*>>()

  val declared = SortedList(PsDependencyComparator(parent.uiSettings))
  val mayBeTransitive = Lists.newArrayList<PsLibraryDependency>()
  for (dependency in collection.modules) {
    if (dependency.isDeclared) {
      declared.add(dependency)
    }
    addTransitive(dependency, allTransitive)
  }
  for (dependency in collection.libraries) {
    if (dependency.isDeclared) {
      declared.add(dependency)
    }
    else {
      mayBeTransitive.add(dependency)
    }
    addTransitive(dependency, allTransitive)
  }

  // Any other dependencies that are not declared, but somehow were not found as transitive.
  val otherUnrecognised = mayBeTransitive
    .filter { it -> !allTransitive.contains(it.spec.compactNotation()) }
  declared.addAll(otherUnrecognised)

  for (dependency in declared) {
    val child = AbstractDependencyNode.createResolvedNode(parent, dependency)
    if (child != null) {
      children.add(child)
    }
  }

  return children
}

private fun addTransitive(dependency: PsLibraryDependency,
                          allTransitive: MutableSet<String>) {
  if (dependency is PsResolvedLibraryDependency) {

    for (transitive in dependency.getTransitiveDependencies()) {
      if (allTransitive.add(transitive.spec.compactNotation())) {
        addTransitive(transitive, allTransitive)
      }
    }
  }
}

private fun addTransitive(dependency: PsModuleDependency,
                          allTransitive: MutableSet<String>) {
  if (dependency is PsResolvedModuleDependency) {

    dependency.targetModuleResolvedDependencies?.libraries?.forEach { transitive ->
      if (allTransitive.add(transitive.spec.compactNotation())) {
        addTransitive(transitive, allTransitive);
      }
    }

    dependency.targetModuleResolvedDependencies?.modules?.forEach { transitive ->
      if (allTransitive.add(transitive.gradlePath)) {
        addTransitive(transitive, allTransitive);
      }
    }
  }
}
