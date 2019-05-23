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
package com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.graph

import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.AbstractDependencyNode
import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.DependencyNodeComparator
import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.JarDependencyNode
import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.ModuleDependencyNode
import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.createLibraryDependencyNode
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.dependencies.PsDependencyComparator
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsResettableNode
import com.android.tools.idea.gradle.structure.model.PsBaseDependency
import com.android.tools.idea.gradle.structure.model.PsDeclaredJarDependency
import com.android.tools.idea.gradle.structure.model.PsDeclaredLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsDeclaredModuleDependency
import com.android.tools.idea.gradle.structure.model.PsJarDependency
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsLibraryKey
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.PsResolvedJarDependency
import com.android.tools.idea.gradle.structure.model.PsResolvedLibraryDependency
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule
import com.android.tools.idea.gradle.structure.model.toLibraryKey
import java.util.function.Consumer

class DependenciesTreeRootNode(model: PsProject, uiSettings: PsUISettings) : AbstractPsResettableNode<PsProject>(model, uiSettings) {
  private val dependencyNodeComparator: DependencyNodeComparator = DependencyNodeComparator(
    PsDependencyComparator(PsUISettings().apply { DECLARED_DEPENDENCIES_SHOW_GROUP_ID = true }))

  override fun createChildren(): List<AbstractDependencyNode<out PsBaseDependency>> {
    val collector = DependencyCollector()
    firstModel.forEachModule(Consumer { module -> collectDependencies(module, collector) })

    val libraryNodes =
      collector.libraryDependenciesBySpec
        .map { (key, dependencies) ->
          when {
            dependencies.distinctBy { it.spec }.size == 1 ->
              createLibraryDependencyNode(this, dependencies, forceGroupId = true)
            else ->
              LibraryGroupDependencyNode(this, key, dependencies).apply {
                children = dependencies.groupBy { it.spec }
                  .entries
                  .sortedBy { it.key.version }
                  .map { (_, list) -> createLibraryDependencyNode(this, list, false) }
              }
          }
        }
    val moduleNodes = collector.moduleDependenciesByGradlePath.values
      .map { ModuleDependencyNode(this, it.toList()) }
    val jarNodes = collector.jarDependenciesByPath.values.map { JarDependencyNode(this, it.toList()) }
    return (moduleNodes + libraryNodes + jarNodes).sortedWith(dependencyNodeComparator)
  }

  private fun collectDependencies(module: PsModule, collector: DependencyCollector) {
    module.dependencies.forEachLibraryDependency { collector.add(it) }
    module.dependencies.forEachJarDependency { collector.add(it) }
    module.dependencies.forEachModuleDependency { collector.add(it) }
    when (module) {
      is PsAndroidModule -> module.resolvedVariants.forEach { variant ->
        variant.forEachArtifact { artifact ->
          artifact.dependencies.forEachLibraryDependency { collector.add(it) }
        }
      }
      is PsJavaModule -> module.resolvedDependencies.forEachLibraryDependency { collector.add(it) }
    }
  }

  class DependencyCollector {
    internal val libraryDependenciesBySpec = mutableMapOf<PsLibraryKey, MutableList<PsLibraryDependency>>()
    internal val jarDependenciesByPath = mutableMapOf<String, MutableList<PsJarDependency>>()
    internal val moduleDependenciesByGradlePath = mutableMapOf<String, MutableList<PsDeclaredModuleDependency>>()

    internal fun add(dependency: PsDeclaredModuleDependency) {
      addModule(dependency)
    }

    internal fun add(dependency: PsDeclaredLibraryDependency) {
      addLibrary(dependency)
    }

    internal fun add(dependency: PsResolvedLibraryDependency) {
      addLibrary(dependency)
    }

    internal fun add(dependency: PsDeclaredJarDependency) {
      addJar(dependency)
    }

    internal fun add(dependency: PsResolvedJarDependency) {
      addJar(dependency)
    }

    private fun addLibrary(dependency: PsLibraryDependency) {
      libraryDependenciesBySpec.getOrPut(dependency.spec.toLibraryKey()) { mutableListOf() }.add(dependency)
    }

    private fun addJar(dependency: PsJarDependency) {
      jarDependenciesByPath.getOrPut(dependency.filePath) { mutableListOf() }.add(dependency)
    }

    private fun addModule(dependency: PsDeclaredModuleDependency) {
      moduleDependenciesByGradlePath.getOrPut(dependency.gradlePath) { mutableListOf() }.add(dependency)
    }
  }
}
