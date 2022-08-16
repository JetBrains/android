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
package com.android.tools.idea.gradle.project.importing

import com.android.tools.idea.gradle.project.GradleProjectInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.io.File

/**
 * A [DirectoryIndexExcludePolicy] that temporarily excludes all directories under a Gradle root from the project while the initial sync
 * runs.
 *
 * This is needed to prevent directories that are normally excluded from the project after sync from being indexed or included in the VFS
 * before the initial sync completes. Otherwise, indexing itself may take much time and any file included in the VFS stays there and is
 * updated when the VFS area containing this file is refreshed. This in turn may result it massive VFS updates after building a project if
 * its build directories end up in the VFS.
 *
 * [InitialImportExcludeDirectoryPolicy] reports any directories directly under the project's Gradle roots as excluded while there is no
 * source roots yet in the project (i.e. sync has not yet succeeded).
 */
class InitialImportExcludeDirectoryPolicy(private val project: Project) : DirectoryIndexExcludePolicy {
  override fun getExcludeUrlsForProject(): Array<out String> {
    if (project.getUserData(EXCLUDE_DIRS_KEY) == false) return emptyArray()
    // Stop returning any exclude directories when sync succeeds. A successful sync sets up some source roots unless all modules are empty.
    // Note: We cannot rely on listeners here to clear the flag as roots are enumerated just after committing project model changes and
    //       listeners run too late.
    if (!project.isGradleProject() || project.projectHasSourceRoots()) {
      project.putUserData(EXCLUDE_DIRS_KEY, false)
      return emptyArray()
    }

    return project
      .getGradleRoots()
      // TODO(b/242440055): Multiple Gradle roots are not yet supported here.
      .flatMap { getDirectoriesToExcludeUnderGradleRoot(it) }
      .map { VfsUtilCore.pathToUrl(it.path) }
      .toTypedArray()
  }
}

private fun Project.isGradleProject(): Boolean {
  return GradleProjectInfo.getInstance(this).isBuildWithGradle
}

private fun Project.projectHasSourceRoots(): Boolean {
  return ProjectRootManager.getInstance(this).orderEntries().withoutLibraries().withoutSdk().sources().urls.isNotEmpty()
}

private fun Project.getGradleRoots(): List<File> {
  return GradleSettings.getInstance(this)
    .linkedProjectsSettings
    .mapNotNull { File(it.externalProjectPath) }
}

private fun getDirectoriesToExcludeUnderGradleRoot(gradleRoot: File): List<File> {
  val existingDirectoryNames =
    gradleRoot
      .listFiles()
      .orEmpty()
      .filter { it.isDirectory }
      .map { it.name }
      .toSet()

  val wellKnownDirectoryNames = setOf("build", ".gradle")

  return (existingDirectoryNames + wellKnownDirectoryNames)
    .map { gradleRoot.resolve(it) }
}

private val EXCLUDE_DIRS_KEY: Key<Boolean> = Key.create("temporary_excluded_dirs")