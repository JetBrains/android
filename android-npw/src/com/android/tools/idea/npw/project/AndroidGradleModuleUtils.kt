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

@file:JvmName("AndroidGradleModuleUtils")

package com.android.tools.idea.npw.project

import com.android.SdkConstants
import com.android.annotations.concurrency.Slow
import com.android.builder.model.SourceProvider
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.GradleVersionCatalogDetector
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.wizard.template.Parameter
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import java.io.File
import java.io.IOException

/**
 * Convenience method to convert a [NamedModuleTemplate] into a [SourceProvider].
 * Note that this target source provider has many fields stubbed out and should be used carefully.
 *
 * TODO: Investigate getting rid of dependencies on [SourceProvider] in
 * [Parameter.validate] as this may allow us to delete this code
 */
fun NamedModuleTemplate.getSourceProvider() = SourceProviderAdapter(name, paths)

/**
 * Given a file and a project, return the Module corresponding to the containing Gradle project for the file.
 * If the file is not contained by any project then return null
 */
fun getContainingModule(file: File?, project: Project): Module? {
  if (project.isDisposed) return null
  val vFile = VfsUtil.findFileByIoFile(file!!, false)
  return if (vFile == null || vFile.isDirectory)
    null
  else
    ProjectFileIndex.getInstance(project).getModuleForFile(vFile, false)
}

/**
 * Set the executable bit on the 'gradlew' wrapper script on Mac/Linux.
 * On Windows, we use a separate gradlew.bat file which does not need an executable bit.
 */
@Throws(IOException::class)
fun setGradleWrapperExecutable(projectRoot: File) {
  if (!SystemInfo.isUnix) {
    return
  }
  val gradlewFile = File(projectRoot, SdkConstants.FN_GRADLE_WRAPPER_UNIX)
  if (!gradlewFile.isFile) {
    throw IOException("Could not find gradle wrapper. Command line builds may not work properly.")
  }
  FileUtil.setExecutable(gradlewFile)
}

/** Find the most appropriate Gradle Plugin version for the specified project. */
@Slow
fun determineAgpVersion(project: Project, isNewProject: Boolean): AgpVersion {
  val defaultAgpVersion = AgpVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())
  if (isNewProject) {
    return defaultAgpVersion
  }

  val versionInUse =
    GradleProjectSystemUtil.getAndroidGradleModelVersionInUse(project)
  if (versionInUse != null) {
    return versionInUse
  }
  // Use slow method
  val androidPluginInfo = AndroidPluginInfo.findFromBuildFiles(project)
  return androidPluginInfo?.pluginVersion ?: defaultAgpVersion
}

/** Find the most appropriate Kotlin plugin version for the specified project. */
@Slow
fun determineKotlinVersion(project: Project, isNewProject: Boolean): String {
  // See IdeaKotlinVersionProviderService.getKotlinVersionFromCompiler().
  val defaultKotlinVersion = KotlinPluginLayout.standaloneCompilerVersion.artifactVersion
  if (isNewProject) return defaultKotlinVersion

  val versionInUse = project.basePath?.let { GradleProjectSystemUtil.getKotlinVersionInUse(project, it) }
  return versionInUse ?: defaultKotlinVersion
}

/** Call detector to check whether Version Catalogs are in use.  Is (very occasionally) slow if cached value has been invalidated. */
@Slow
fun determineVersionCatalogUse(project: Project): Boolean {
  return GradleVersionCatalogDetector.getInstance(project).isVersionCatalogProject
}