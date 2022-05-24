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
import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.JarDependencyNode
import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.LibraryDependencyNode
import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.ModuleDependencyNode
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
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

sealed class DependencyKey
data class LibraryKey(val library: PsLibraryKey) : DependencyKey()
data class ModuleKey(val modulePath: String) : DependencyKey()
data class JarLibraryKey(val path: String) : DependencyKey()

class DependenciesTreeRootNode(
  model: PsProject,
  uiSettings: PsUISettings
) : AbstractPsResettableNode<DependencyKey, AbstractDependencyNode<*, PsBaseDependency>, PsProject>(uiSettings) {

  override val models: List<PsProject> = listOf(model)

  init {
    updateNameAndIcon()
  }

  private lateinit var collector: DependencyCollector

  override fun getKeys(from: Unit): Set<DependencyKey> {
    collector = DependencyCollector()
    firstModel.forEachModule(Consumer { module -> collectDependencies(module, collector) })

    return (collector.libraryDependenciesBySpec.keys.map { LibraryKey(it) } +
            collector.moduleDependenciesByGradlePath.keys.map { ModuleKey(it) } +
            collector.jarDependenciesByPath.keys.map { JarLibraryKey(it) })
      .sortedWith(DependencyKeyComparator)
      .toSet()
  }


  override fun create(key: DependencyKey): AbstractDependencyNode<*, PsBaseDependency> =
    @Suppress("UNCHECKED_CAST") // Create/update types always match.
    (when (key) {
      is LibraryKey -> LibraryDependencyNode(this@DependenciesTreeRootNode)
      is ModuleKey -> ModuleDependencyNode(this@DependenciesTreeRootNode)
      is JarLibraryKey -> JarDependencyNode(this@DependenciesTreeRootNode)
    } as AbstractDependencyNode<*, PsBaseDependency>)

  override fun update(key: DependencyKey, node: AbstractDependencyNode<*, PsBaseDependency>) =
    node.init(
      when (key) {
        is LibraryKey -> collector.libraryDependenciesBySpec[key.library]!!
        is ModuleKey -> collector.moduleDependenciesByGradlePath[key.modulePath].orEmpty()
        is JarLibraryKey -> collector.jarDependenciesByPath[key.path].orEmpty()
      }
    )

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

private object DependencyKeyComparator : Comparator<DependencyKey> {

  private fun DependencyKey.getTypePriority() = when (this) {
    is ModuleKey -> 0
    is LibraryKey -> 1
    is JarLibraryKey -> 2
  }

  private fun DependencyKey.getSortText() = when (this) {
    is ModuleKey -> modulePath.split(':').lastOrNull().orEmpty()
    is LibraryKey -> library.group + ":" + library.name
    is JarLibraryKey -> path
  }

  override fun compare(d1: DependencyKey, d2: DependencyKey): Int =
    compareValuesBy(d1, d2, { it.getTypePriority() }, { it.getSortText() })
}
