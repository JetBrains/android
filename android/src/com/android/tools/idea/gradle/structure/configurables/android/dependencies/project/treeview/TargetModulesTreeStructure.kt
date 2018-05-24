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

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractDependencyNode
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractBaseTreeStructure
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.PsRootNode
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.SimpleNodeComparator
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsDeclaredLibraryAndroidDependency
import com.android.tools.idea.gradle.structure.model.android.PsLibraryAndroidDependency
import com.google.common.collect.HashMultimap
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.intellij.openapi.util.Pair
import com.intellij.ui.treeStructure.SimpleNode
import java.util.*

internal class TargetModulesTreeStructure(var uiSettings: PsUISettings) : AbstractBaseTreeStructure() {

  private val rootNode: PsRootNode = PsRootNode(uiSettings)

  override fun getRootElement(): Any = rootNode

  fun displayTargetModules(dependencyNodes: List<AbstractDependencyNode<out PsAndroidDependency>>) {
    // Key: module name, Value: pair of module and version of the dependency used in the module.
    val modules = mutableMapOf<String, Pair<PsAndroidModule, String>>()

    // Key: module name, Value: configuration names.
    val configurationNamesByModule = HashMultimap.create<String, Configuration>()

    // From the list of AbstractDependencyNode:
    //  1. Extract modules (to show them as top-level nodes)
    //  2. Extract variants/artifact (i.e. PsDependencyContainer) per module (to show them as children nodes of the module nodes)
    dependencyNodes.forEach { node ->
      val declaredDependencyNodes = getDeclaredDependencyNodeHierarchy(node)

      // Create the module and version used.
      val versionByModule = mutableMapOf<String, String?>()
      for (dependency in node.models) {
        if (dependency is PsLibraryAndroidDependency) {
          val spec = dependency.spec
          // For library dependencies we display the version of the library being used.
          val module = dependency.parent
          versionByModule[module.name] = spec.version
        }
      }

      val topParentNode = declaredDependencyNodes[declaredDependencyNodes.size - 1]
      for (dependency in topParentNode.models) {
        val module = dependency.parent
        val moduleName = module.name
        val existing = modules[moduleName]
        if (existing == null) {
          modules[moduleName] = Pair.create(module, versionByModule[moduleName]!!)
        }
      }

      declaredDependencyNodes.forEach { declaredDependencyNode ->
        val declaredDependencies = getDeclaredDependencies(declaredDependencyNode)

        declaredDependencies.forEach { declaredDependency ->
          val module = declaredDependency.parent
          val moduleName = module.name

          for (container in declaredDependency.containers) {
            val artifact = container.findArtifact(module, false)

            val configurationName = declaredDependency.configurationName
            if (artifact != null && artifact.containsConfigurationName(configurationName)) {
              val transitive = declaredDependencyNode !== node

              val configurations = configurationNamesByModule.get(moduleName)
              var found = false
              for (configuration in configurations) {
                if (configuration.name == configurationName) {
                  configuration.addType(transitive)
                  found = true
                  break
                }
              }

              if (!found) {
                val icon = artifact.icon
                configurationNamesByModule.put(moduleName, Configuration(configurationName, icon, transitive))
              }
            }
          }
        }
      }
    }

    // Now we create the tree nodes.
    val children = mutableListOf<TargetAndroidModuleNode>()

    for (moduleAndVersion in modules.values) {
      val module = moduleAndVersion.getFirst()
      val moduleNode = TargetAndroidModuleNode(rootNode, module, moduleAndVersion.getSecond())

      val configurations = configurationNamesByModule.get(module.name).toMutableList()
      configurations.sort()

      val nodes = mutableListOf<TargetConfigurationNode>()
      configurations.forEach { configuration -> nodes.add(TargetConfigurationNode(configuration, uiSettings)) }

      moduleNode.setChildren(nodes)
      children.add(moduleNode)
    }

    Collections.sort(children, SimpleNodeComparator())
    rootNode.setChildren(children)
  }

  private fun getDeclaredDependencyNodeHierarchy(node: AbstractDependencyNode<*>): List<AbstractDependencyNode<out PsAndroidDependency>> {
    val nodes = mutableListOf<AbstractDependencyNode<out PsAndroidDependency>>()
    if (node.isDeclared) {
      nodes.add(node)
    }
    var current: SimpleNode = node
    while (true) {
      val parent = current.parent
      if (parent is AbstractDependencyNode<*> && parent.isDeclared) {
        nodes.add(parent as AbstractDependencyNode<out PsAndroidDependency>)
      }
      else if (parent == null) {
        break
      }
      current = parent
    }
    return nodes
  }

  private fun getDeclaredDependencies(node: AbstractDependencyNode<out PsAndroidDependency>): List<PsDeclaredLibraryAndroidDependency> {
    return node.models
      .filter { it -> it is PsDeclaredLibraryAndroidDependency }
      .map { it -> it as PsDeclaredLibraryAndroidDependency }
      .filter { it.isDeclared }
  }
}
