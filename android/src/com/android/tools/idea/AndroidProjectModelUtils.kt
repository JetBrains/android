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

import com.android.projectmodel.ExternalAndroidLibrary
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.Namespacing
import com.android.tools.idea.projectsystem.DependencyScopeType
import com.android.tools.idea.projectsystem.FindDependenciesWithResourcesToken
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.getTokenOrNull
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet

/**
 * Returns information about all [ExternalAndroidLibrary] dependencies that contribute resources in the project, indexed by
 * [ExternalAndroidLibrary.address] which is unique within a project.
 */
fun findAllLibrariesWithResources(project: Project): Map<String, ExternalAndroidLibrary> {
  return ModuleManager.getInstance(project)
    .modules
    .asSequence()
    .map(::findDependenciesWithResources)
    .fold(HashMap()) { inProject, inModule ->
      inProject.putAll(inModule)
      inProject
    }
}

/**
 * Returns information about all [ExternalAndroidLibrary] dependencies that contribute resources in a given module, indexed by
 * [ExternalAndroidLibrary.address] which is unique within a project.
 */
fun findDependenciesWithResources(module: Module): Map<String, ExternalAndroidLibrary> =
  (module.project.getProjectSystem().getTokenOrNull(FindDependenciesWithResourcesToken.EP_NAME)
     ?.findDependencies(module.getModuleSystem(), module)
    ?: module.getModuleSystem().getAndroidLibraryDependencies(DependencyScopeType.MAIN))
    .filter { it.hasResources }
    .associateBy { library -> library.address }

/**
 * Checks namespacing of the module with the given [AndroidFacet].
 */
val AndroidFacet.namespacing: Namespacing get() = AndroidModel.get(this)?.namespacing ?: Namespacing.DISABLED
