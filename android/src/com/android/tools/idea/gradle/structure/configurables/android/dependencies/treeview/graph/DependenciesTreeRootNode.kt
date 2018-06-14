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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.graph

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractDependencyNode
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.DependencyNodeComparator
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.LibraryDependencyNode
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.ModuleDependencyNode
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.dependencies.PsDependencyComparator
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsResettableNode
import com.android.tools.idea.gradle.structure.model.PsLibraryKey
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsLibraryAndroidDependency
import com.android.tools.idea.gradle.structure.model.android.PsModuleAndroidDependency
import com.android.tools.idea.gradle.structure.model.toLibraryKey
import java.util.function.Consumer

class DependenciesTreeRootNode(model: PsProject, uiSettings: PsUISettings) : AbstractPsResettableNode<PsProject>(model, uiSettings) {
  private val dependencyNodeComparator: DependencyNodeComparator = DependencyNodeComparator(
    PsDependencyComparator(PsUISettings().apply { DECLARED_DEPENDENCIES_SHOW_GROUP_ID = true }))

  override fun createChildren(): List<AbstractDependencyNode<out PsAndroidDependency>> {
    val collector = DependencyCollector()
    firstModel.forEachModule(Consumer { module -> collectDependencies(module, collector) })

    val libraryNodes =
      collector.libraryDependenciesBySpec
        .map { (key, dependencies) ->
          when {
            dependencies.distinctBy { it.spec }.size == 1 ->
              LibraryDependencyNode(this, null, dependencies, forceGroupId = true)
            else ->
              LibraryGroupDependencyNode(this, key, dependencies).apply {
                children = dependencies.groupBy { it.spec }.map { (_, list) -> LibraryDependencyNode(this, null, list, false) }
              }
          }
        }
    val moduleNodes = collector.moduleDependenciesByGradlePath.values
      .map { ModuleDependencyNode(this, it) }
    return (libraryNodes + moduleNodes).sortedWith(dependencyNodeComparator)
  }

  private fun collectDependencies(module: PsModule, collector: DependencyCollector) {
    (module as? PsAndroidModule)?.run {
      dependencies.forEach(collector::add)
      variants.forEach { variant ->
        variant.forEachArtifact { artifact ->
          artifact.dependencies.forEachLibraryDependency(collector::add)
        }
      }
    }
  }

  class DependencyCollector {
    internal val libraryDependenciesBySpec = mutableMapOf<PsLibraryKey, MutableList<PsLibraryAndroidDependency>>()
    internal val moduleDependenciesByGradlePath = mutableMapOf<String, MutableList<PsModuleAndroidDependency>>()

    internal fun add(dependency: PsAndroidDependency) {
      when (dependency) {
        is PsLibraryAndroidDependency -> add(dependency)
        is PsModuleAndroidDependency -> add(dependency)
      }
    }

    private fun add(dependency: PsLibraryAndroidDependency) {
      libraryDependenciesBySpec.getOrPut(dependency.spec.toLibraryKey(), { mutableListOf() }).add(dependency)
    }

    private fun add(dependency: PsModuleAndroidDependency) {
      moduleDependenciesByGradlePath.getOrPut(dependency.gradlePath, { mutableListOf() }).add(dependency)
    }
  }
}
