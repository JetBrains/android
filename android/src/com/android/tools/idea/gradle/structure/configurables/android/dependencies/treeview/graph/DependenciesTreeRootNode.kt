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

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.DependencyNodeComparator
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.LibraryDependencyNode
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.ModuleDependencyNode
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.dependencies.PsDependencyComparator
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsResettableNode
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsLibraryAndroidDependency
import com.android.tools.idea.gradle.structure.model.android.PsModuleAndroidDependency

class DependenciesTreeRootNode(model: PsProject, uiSettings: PsUISettings) : AbstractPsResettableNode<PsProject>(model, uiSettings) {
  private val dependencyNodeComparator: DependencyNodeComparator = DependencyNodeComparator(PsDependencyComparator(uiSettings))

  override fun createChildren(): List<AbstractPsModelNode<*>> {
    val collector = DependencyCollector()
    firstModel.forEachModule { module -> collectDeclaredDependencies(module, collector) }

    return (collector.libraryDependenciesBySpec.values.map { LibraryDependencyNode(this, null, it, forceGroupId = true) } +
           collector.moduleDependenciesByGradlePath.values.map { ModuleDependencyNode(this, it) })
             .sortedWith(dependencyNodeComparator)
  }

  private fun collectDeclaredDependencies(module: PsModule, collector: DependencyCollector) {
    (module as? PsAndroidModule)?.dependencies?.forEach(collector::add)
  }

  class DependencyCollector {
    internal val libraryDependenciesBySpec = mutableMapOf<PsArtifactDependencySpec, MutableList<PsLibraryAndroidDependency>>()
    internal val moduleDependenciesByGradlePath = mutableMapOf<String, MutableList<PsModuleAndroidDependency>>()

    internal fun add(dependency: PsAndroidDependency) {
      when (dependency) {
        is PsLibraryAndroidDependency -> add(dependency)
        is PsModuleAndroidDependency -> add(dependency)
      }
    }

    private fun add(dependency: PsLibraryAndroidDependency) {
      libraryDependenciesBySpec.getOrPut(dependency.spec, { mutableListOf() }).add(dependency)
    }

    private fun add(dependency: PsModuleAndroidDependency) {
      moduleDependenciesByGradlePath.getOrPut(dependency.gradlePath, { mutableListOf() }).add(dependency)
    }
  }
}
