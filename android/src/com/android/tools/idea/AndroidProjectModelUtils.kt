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

/**
 * Helper functions for functionality that should be easy to do with the universal project model, but for now needs to be implemented using
 * whatever we have.
 *
 * TODO: remove all of this once we have the project model.
 */
@file:JvmName("AndroidProjectModelUtils")

package com.android.tools.idea

import com.android.builder.model.AaptOptions
import com.android.projectmodel.AndroidSubmodule
import com.android.projectmodel.ExternalLibrary
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet

/**
 * Returns information about all [ExternalLibrary] dependencies that contribute resources in the project, indexed by
 * [ExternalLibrary.address] which is unique within a project.
 *
 * TODO: ExternalLibrary.address is unique within an [AndroidSubmodule], not necessarily within a [Project]
 */
fun findAllLibrariesWithResources(project: Project): Map<String, ExternalLibrary> {
  return ModuleManager.getInstance(project)
    .modules
    .asSequence()
    // Don't iterate *all* project modules *recursively*, as this is O(n*n) complexity, where n is the modules count.
    .map{findDependenciesWithResources(it, recursively = false)}
    .fold(HashMap<String, ExternalLibrary>()) { inProject, inModule ->
      inProject.putAll(inModule)
      inProject
    }
}

/**
 * Returns information about all [ExternalLibrary] dependencies that contribute resources in a given module, indexed by
 * [ExternalLibrary.address] which is unique within a project.
 *
 * @param recursively indicates if the module dependencies should be searched recursively. `false` = search only own module dependencies
 */
fun findDependenciesWithResources(module: Module, recursively: Boolean = true): Map<String, ExternalLibrary> {
  return module.getModuleSystem()
    .getResolvedLibraryDependencies(recursively)
    .filterIsInstance<ExternalLibrary>()
    .filter { it.hasResources }
    .associateBy { library -> library.address }
}

/**
 * Checks namespacing of the module with the given [AndroidFacet].
 */
val AndroidFacet.namespacing: AaptOptions.Namespacing get() = AndroidModel.get(this)?.namespacing ?: AaptOptions.Namespacing.DISABLED
