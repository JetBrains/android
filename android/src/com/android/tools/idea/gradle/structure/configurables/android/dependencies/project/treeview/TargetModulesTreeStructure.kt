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
import com.android.tools.idea.gradle.structure.model.android.*
import com.android.tools.idea.gradle.structure.model.toLibraryKey
import java.util.*
import java.util.function.Function

class TargetModulesTreeStructure(var uiSettings: PsUISettings) : AbstractBaseTreeStructure() {

  private val rootNode: PsRootNode = PsRootNode(uiSettings)

  override fun getRootElement(): Any = rootNode

  fun displayTargetModules(dependencyNodes: List<List<PsAndroidDependency>>) {
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
      val moduleDeclaredLibraryModels = declaredLibraryModels[module]
        ?.flatMap { dependency -> dependency.containers.mapNotNull { it.findArtifact(dependency.parent)?.let { it to dependency } } }
        ?.groupBy({ it.first }, { it.second })
        .orEmpty()

      val artifacts = (moduleResolvedLibraryModels.keys + moduleDeclaredLibraryModels.keys).sortedWith(comparator)

      fun createArtifactNode(artifact: PsAndroidArtifact) =
        TargetAndroidArtifactNode(artifact, null, uiSettings)
          .also {
            it.setChildren(
              moduleDeclaredLibraryModels[artifact]?.map {
                TargetConfigurationNode(Configuration(it.configurationName, artifact.icon), uiSettings)
              }.orEmpty() +
              moduleResolvedLibraryModels[artifact]?.flatMap {
                val transitiveDependencies = it.getReverseDependencies().filterIsInstance<ReverseDependency.Transitive>()
                transitiveDependencies.map {
                  TargetTransitiveDependencyNode(listOf(it), it.requestingResolvedDependency.spec, uiSettings)
                }
              }.orEmpty()
            )
          }

      return TargetAndroidModuleNode(rootNode, module, null, artifacts.map { artifact -> createArtifactNode(artifact) })
    }

    val modules = models.map {it.parent}.distinct().sortedBy { it.name }
    rootNode.setChildren(modules.map { module -> createModuleNode(module) })
  }
}

private val comparator: Comparator<PsAndroidArtifact> =
  Comparator
    .comparing(Function<PsAndroidArtifact, String> { it.name })
    .thenComparing(Function<PsAndroidArtifact, String> { it.parent.name })
