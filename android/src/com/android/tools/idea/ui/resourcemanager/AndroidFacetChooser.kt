/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager

import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.projectsystem.isMainModule
import com.android.tools.idea.util.androidFacet
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet

internal const val MODULE_NAME_KEY = "ModuleName"

/**
 * Find the facet corresponding to the current opened editor if any, otherwise returns the
 * facet of the first Android module if any is found.
 */
internal fun findCompatibleFacetFromOpenedFiles(project: Project): AndroidFacet? =
  // Find facet for active files in editor
  FileEditorManager.getInstance(project).selectedFiles.mapNotNull { file ->
    ModuleUtilCore.findModuleForFile(file, project)?.getMainModule()?.androidFacet
  }.firstOrNull() ?:
  // Fallback to the first facet we can find
  findCompatibleFacets(project).firstOrNull()

/**
 * Find the Facet that was last selected in a ResourceExplorer for a given project.
 *
 */
internal fun findLastSelectedFacet(project: Project): AndroidFacet? =
  getFacetForModuleName(PropertiesComponent.getInstance(project).getValue("$RES_MANAGER_PREF_KEY.$MODULE_NAME_KEY"), project)

/**
 * Returns [AndroidFacet]s corresponding only to the main module.
 */
internal fun findCompatibleFacets(project: Project): List<AndroidFacet> =
  ModuleManager.getInstance(project).modules.filter { it.isMainModule() }.mapNotNull { it.androidFacet }

/**
 * True if the given [androidFacet] is supported in the ResourceExplorer.
 */
internal fun compatibleFacetExists(androidFacet: AndroidFacet): Boolean =
  when (val mainFacet = androidFacet.module.getMainModule().androidFacet) {
    null -> false
    else -> findCompatibleFacets(androidFacet.module.project).any { compatibleFacet -> mainFacet == compatibleFacet }
  }

internal fun getFacetForModuleName(moduleName: String?, project: Project): AndroidFacet? {
  return findCompatibleFacets(project).firstOrNull { it.module.name == moduleName }
}