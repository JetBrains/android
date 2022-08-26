/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * A handful of utility methods useful for suggesting package names when creating new files inside an Android project.
 */
@file:JvmName("AndroidPackageUtils")

package com.android.tools.idea.npw.project

import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.util.toIoFile
import com.intellij.openapi.roots.PackageIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet

/**
 * Return the package associated with the target directory.
 */
fun AndroidFacet.getPackageForPath(moduleTemplates: List<NamedModuleTemplate>, targetDirectory: VirtualFile): String? {
  val moduleSystem = this.getModuleSystem()
  if (moduleTemplates.isEmpty()) {
    return moduleSystem.getPackageName()
  }

  val srcDirectory = moduleTemplates[0].paths.getSrcDirectory(null) ?: return moduleSystem.getPackageName()

  // We generate a package name relative to the source root, but if the target path is not under the source root, we should just
  // fall back to the default application package.
  val srcPath = srcDirectory.toPath().toAbsolutePath()
  val targetPath = targetDirectory.toIoFile().toPath().toAbsolutePath()
  if (targetPath.startsWith(srcPath)) {
    val suggestedPackage = PackageIndex.getInstance(module.project).getPackageNameByDirectory(targetDirectory)

    if (!suggestedPackage.isNullOrEmpty()) {
      return suggestedPackage
    }
  }
  return moduleSystem.getPackageName()
}

/**
 * Convenience method to get [NamedModuleTemplate]s from the current project.
 */
fun AndroidFacet.getModuleTemplates(targetDirectory: VirtualFile?): List<NamedModuleTemplate> =
  module.getModuleSystem().getModuleTemplates(targetDirectory)
