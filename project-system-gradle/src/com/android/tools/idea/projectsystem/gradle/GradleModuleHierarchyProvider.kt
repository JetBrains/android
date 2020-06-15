/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID
import com.android.tools.idea.projectsystem.ModuleHierarchyProvider
import com.intellij.ProjectTopics
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.isExternalSystemAwareModule
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ModuleRootManager

class GradleModuleHierarchyProvider(private val project: Project) {
  private var moduleSubmodules: Map<ComponentManager, List<Module>>? = null // Keys: Modules and the project.

  init {
    // Project systems are not currently disposable and live until their project is disposed. Thus we subscribe to events for the
    // project lifetime.
    project.messageBus.connect().subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
      // Typically should not take time, but may be slower if another thread is building the map (unlikely to occur unless project roots are
      // changed frequently which itself is a bigger problem).
      override fun rootsChanged(event: ModuleRootEvent) = reset()
    })
  }

  val forProject = object : ModuleHierarchyProvider {
    override val submodules: Collection<Module>
      get() = getSubmodules(project)
  }

  fun createForModule(module: Module): ModuleHierarchyProvider {
    return object : ModuleHierarchyProvider {
      override val submodules: Collection<Module>
        get() = getSubmodules(module)
    }
  }

  @Synchronized
  fun reset() {
    moduleSubmodules = null
  }

  @Synchronized
  private fun getSubmodules(projectOrModule: ComponentManager): List<Module> {
    val map = moduleSubmodules ?: buildMap().also { moduleSubmodules = it }
    return map[projectOrModule].orEmpty()
  }

  private fun buildMap(): Map<ComponentManager, List<Module>> {
    val moduleManager = ModuleManager.getInstance(project)
    val grouper = moduleManager.getModuleGrouper(null)

    fun moduleHierarchyId(module: Module?): List<String>? {
      if (!isExternalSystemAwareModule(GRADLE_SYSTEM_ID, module ?: return null)) return null
      return grouper.getModuleAsGroupPath(module) ?: grouper.getGroupPath(module)
    }

    val modules = moduleManager.modules
    val projectRootHierarchyId = emptyList<String>()
    val hierarchyIdToSubmodulesMap = mutableMapOf<List<String>, MutableList<Module>>()
    val hierarchyIdToModuleMap = mutableMapOf<List<String>, Module>()

    for (module in modules) {
      val hierarchyId = moduleHierarchyId(module) ?: continue
      hierarchyIdToSubmodulesMap[hierarchyId] = mutableListOf()
      hierarchyIdToModuleMap[hierarchyId] = module
    }
    hierarchyIdToSubmodulesMap[projectRootHierarchyId] = mutableListOf()

    for (module in modules) {
      val hierarchyId = moduleHierarchyId(module) ?: continue

      var parent = hierarchyId
      while (parent.isNotEmpty()) {
        parent = parent.dropLast(1)
        // Eventually we either find an existing parent or it becomes "", which is always present.
        // NOTE: This even works when there are gaps in the parents chain which can happen if a qualified module names consists of
        //       components whose names may contain dots ".".  For example, the following Gradle project hierarchy
        //       :
        //           :libs
        //               :libs:just.a.library
        // will produce "project_name.libs.just.a.library" as a qualified name which will result in the following hierarchyId
        //        ==> [ "project_name", "libs", "just", "a", "library"].
        val submodules = hierarchyIdToSubmodulesMap[parent]
        if (submodules != null) {
          // We found the closest existing parent module. Add the module to its collection of submodules.
          submodules.add(module)
          break
        }
      }
    }

    val emptyOnlyRootModule =
      hierarchyIdToSubmodulesMap[projectRootHierarchyId]
        ?.singleOrNull()
        ?.takeIf {
          // If there is only one top level module and it is empty, flatten it.
          ModuleRootManager.getInstance(it).sourceRootUrls.isEmpty()
        }

    if (emptyOnlyRootModule != null) {
      val onlyRootModuleHierarchyId = moduleHierarchyId(emptyOnlyRootModule)
      hierarchyIdToSubmodulesMap[projectRootHierarchyId] = hierarchyIdToSubmodulesMap.remove(onlyRootModuleHierarchyId) ?: mutableListOf()
    }
    // Workaround for the way module group for the top level module is set up in sync (if the name of the project includes spaces etc).
    hierarchyIdToSubmodulesMap[projectRootHierarchyId]
      ?.takeIf { it.size > 1 }
      ?.removeIf {
        ModuleRootManager.getInstance(it).sourceRootUrls.isEmpty() && hierarchyIdToSubmodulesMap[moduleHierarchyId(it)]?.isEmpty() == true
      }
    return hierarchyIdToSubmodulesMap.mapKeys<List<String>, List<Module>, ComponentManager> { hierarchyIdToModuleMap[it.key] ?: project }
  }
}