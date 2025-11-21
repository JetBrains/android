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

import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractBaseTreeStructure
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.PsRootNode
import com.android.tools.idea.gradle.structure.model.PsBaseDependency
import com.android.tools.idea.gradle.structure.model.android.PsAndroidArtifact
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsDeclaredLibraryAndroidDependency
import com.android.tools.idea.gradle.structure.model.android.PsLibraryAndroidDependency
import com.android.tools.idea.gradle.structure.model.android.PsModuleAndroidDependency
import com.android.tools.idea.gradle.structure.model.android.PsResolvedLibraryAndroidDependency
import com.android.tools.idea.gradle.structure.model.android.ReverseDependency
import com.android.tools.idea.gradle.structure.model.toLibraryKey
import java.util.function.Function

class TargetModulesTreeStructure(var uiSettings: PsUISettings) : AbstractBaseTreeStructure() {

  private val rootNode: PsRootNode = PsRootNode(uiSettings)

  override fun getRootElement(): Any = rootNode

  fun displayTargetModules(dependencyNodes: List<List<PsBaseDependency>>) {
    // Key: module name, Value: pair of module and version of the dependency used in the module.
    val models = dependencyNodes.flatMap { it}
    val libraryModels = models.filterIsInstance<PsLibraryAndroidDependency>()
    val moduleModels = models.filterIsInstance<PsModuleAndroidDependency>()
    if (libraryModels.isNotEmpty() && moduleModels.isNotEmpty()) return

    if (moduleModels.isNotEmpty()) return

    if (libraryModels.groupBy { it.spec.toLibraryKey() }.size > 1) {
      rootNode.setChildren(listOf())
      return
    }

    val resolvedLibraryModels = libraryModels.filterIsInstance<PsResolvedLibraryAndroidDependency>()
      .groupBy { it.artifact.parent.parent }

    val declaredLibraryModels = libraryModels.filterIsInstance<PsDeclaredLibraryAndroidDependency>()
      .groupBy { it.parent }

    fun createModuleNode(module: PsAndroidModule): TargetAndroidModuleNode {
      val moduleResolvedLibraryModels = resolvedLibraryModels[module]
        ?.groupBy { it.artifact }
        .orEmpty()
      val moduleDeclaredLibraryConfigurationNames = declaredLibraryModels[module]?.map { it.configurationName }?.toSet() ?: emptySet()

      val artifacts =
        (moduleResolvedLibraryModels.keys +
         (module.resolvedVariants.flatMap { it.artifacts }.filter { artifact ->
           moduleDeclaredLibraryConfigurationNames.any {
             artifact.containsConfigurationName(it)
           }
         })
        ).sortedWith(comparator)

      fun createArtifactNode(artifact: PsAndroidArtifact): TargetAndroidArtifactNode {
        val declaredDependencies = declaredLibraryModels[artifact.parent.parent]
          ?.filter { artifact.containsConfigurationName(it.configurationName) }
          .orEmpty()
        val transitiveDependencies = moduleResolvedLibraryModels[artifact]
          ?.flatMap { it.getReverseDependencies().filterIsInstance<ReverseDependency.Transitive>() }
          .orEmpty()
        return TargetAndroidArtifactNode(artifact, null, uiSettings).apply {
          setChildren(
            declaredDependencies.map { TargetConfigurationNode(Configuration(it.configurationName, artifact.icon), uiSettings) } +
            transitiveDependencies.map { TargetTransitiveDependencyNode(listOf(it), it.requestingResolvedDependency.spec, uiSettings) }
          )
        }
      }

      return TargetAndroidModuleNode(rootNode, module, null, artifacts.map { artifact -> createArtifactNode(artifact) })
    }

    val modules = models.map {it.parent}.distinct().sortedBy { it.name }
    rootNode.setChildren(modules.mapNotNull { module -> (module as? PsAndroidModule)?.let { createModuleNode(it) } })
  }
}

private val comparator: Comparator<PsAndroidArtifact> =
  Comparator
    .comparing(Function<PsAndroidArtifact, String> { it.name })
    .thenComparing(Function<PsAndroidArtifact, String> { it.parent.name })
