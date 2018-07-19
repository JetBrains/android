/*
 * Copyright (C) 2017 The Android Open Source Project
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
@file:JvmName("DependencyManagementUtil")

package com.android.tools.idea.util

import com.android.SdkConstants
import com.android.annotations.VisibleForTesting
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.support.AndroidxName
import com.android.support.AndroidxNameUtils
import com.android.tools.idea.projectsystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil

/**
 * Returns true iff the dependency with [artifactId] is transitively available to this [module].
 * This function returns false if the project's dependency model is unavailable and therefore dependencies
 * could not be checked (e.g. Project is syncing with build system or any dependency management error occurs).
 * To handle dependency management errors, use methods defined in [AndroidProjectSystem] and catch
 * [DependencyManagementException].
 * @param artifactId the dependency's maven artifact id.
 */
fun Module.dependsOn(artifactId: GoogleMavenArtifactId): Boolean {
  try {
    // TODO this artifact to coordinate translation is temporary and will be removed when GradleCoordinates are swapped in for GoogleMavenArtifactId.
    val coordinate = GradleCoordinate(artifactId.mavenGroupId, artifactId.mavenArtifactId, "+")
    return getModuleSystem().getResolvedDependency(coordinate) != null
  }
  catch (e: DependencyManagementException) {
    Logger.getInstance(this.javaClass.name).warn(e.message)
  }
  return false
}

/**
 * Returns whether this module depends on the new support library artifacts (androidx).
 */
fun Module.dependsOnAndroidx(): Boolean =
  GoogleMavenArtifactId.values()
    .filter { it.mavenGroupId.startsWith(SdkConstants.ANDROIDX_PKG) }
    .any { dependsOn(it) }

/**
 * Returns whether this module depends on the old support library artifacts (com.android.support).
 */
fun Module.dependsOnOldSupportLib(): Boolean =
  GoogleMavenArtifactId.values()
    .filter { it.mavenGroupId.startsWith(SdkConstants.SUPPORT_LIB_GROUP_ID) }
    .any { dependsOn(it) }

fun Module?.mapAndroidxName(name: AndroidxName): String {
  val dependsOnAndroidx = this?.dependsOnAndroidx() ?: return name.defaultName()
  return if (dependsOnAndroidx) name.newName() else name.oldName()
}

fun Module.mapGradleCoordinateToAndroidx(coordinate: String): String {
  if (this.isDisposed || coordinate.isEmpty()) {
    return coordinate
  }

  return if (this.dependsOnAndroidx()) {
    AndroidxNameUtils.getCoordinateMapping(coordinate)
  }
  else coordinate
}

/**
 * Add libraries with given [GradleCoordinate] as dependencies; this method will show a dialog prompting the user for confirmation if
 * [promptUserBeforeAdding] is set to true and return with no-op if user chooses to not add the dependencies. If any of the dependencies
 * are added successfully and [requestSync] is set to true, this method will request a sync to make sure the artifacts are resolved.
 * In this case, the sync will happen asynchronously and this method will not wait for it to finish before returning.
 *
 * Callers may configure preview version preferences with [preferPreview]:

 * If [preferPreview] is false this method will first look for stable versions of the dependency for addition. In the event that a stable
 * version does not exist, this method will fallback and look for a preview version of the dependency instead.
 *
 * If [preferPreview] is true this method will look for the latest version regardless if it's a preview version.
 *
 * This method shows no confirmation dialog and performs a no-op if the list of artifacts is the empty list.
 * This method does not trigger a sync if none of the artifacts were added successfully or if [requestSync] is false.
 * @return list of artifacts that were not successfully added. i.e. If the returned list is empty, then all were added successfully.
 */
@JvmOverloads
fun Module.addDependencies(coordinates: List<GradleCoordinate>, promptUserBeforeAdding: Boolean, requestSync: Boolean = true,
                           preferPreview: Boolean = false)
  : List<GradleCoordinate> {

  if (coordinates.isEmpty()) {
    return listOf()
  }

  val moduleSystem = getModuleSystem()
  val distinctCoordinates = coordinates.distinctBy { Pair(it.groupId, it.artifactId) }
  val unavailableDependencies: MutableList<GradleCoordinate> = mutableListOf()
  val versionedDependencies: MutableList<GradleCoordinate> = mutableListOf()

  // Separate the list of deps into a list of versioned coordinates and a list of unavailable coordinates.
  distinctCoordinates.forEach {
    val versionedCoordinate =
      if (preferPreview) {
        project.getProjectSystem().getAvailableDependency(it, true)
      }
      else {
        project.getProjectSystem().getAvailableDependency(it, false) ?: project.getProjectSystem().getAvailableDependency(it, true)
      }

    if (versionedCoordinate == null) {
      unavailableDependencies.add(it)
    } else {
      versionedDependencies.add(versionedCoordinate)
    }
  }

  if (unavailableDependencies.isNotEmpty()) {
    return unavailableDependencies
  }

  if (promptUserBeforeAdding && !userWantsToAdd(project, distinctCoordinates)) {
    return distinctCoordinates
  }

  versionedDependencies.forEach { moduleSystem.registerDependency(transformVersionIfNeeded(it, moduleSystem)) }

  if (requestSync) {
    project.getSyncManager().syncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED, true)
  }

  return emptyList()
}

private fun transformVersionIfNeeded(coordinate: GradleCoordinate, moduleSystem: AndroidModuleSystem): GradleCoordinate {
  // Platform support libraries must all use the same version.
  if (!isPlatformSupportLibrary(coordinate)) {
    return coordinate
  }

  val version = getExistingPlatformSupportLibraryVersion(moduleSystem) ?: return coordinate

  val coordinateString = "${coordinate.groupId}:${coordinate.artifactId}:$version"
  return GradleCoordinate.parseCoordinateString(coordinateString) ?: throw RuntimeException(
    "Could not parse gradle coordinate string $coordinateString")
}

private fun isPlatformSupportLibrary(coordinate: GradleCoordinate) =
  GoogleMavenArtifactId.forCoordinate(coordinate)?.isPlatformSupportLibrary ?: false

private fun getExistingPlatformSupportLibraryVersion(moduleSystem: AndroidModuleSystem): GradleVersion? =
  GoogleMavenArtifactId.values()
    .asSequence()
    .filter { it.isPlatformSupportLibrary }
    .mapNotNull { moduleSystem.getRegisteredDependency(it.getCoordinate("+")) }
    .mapNotNull { it.version }
    .firstOrNull()

private fun userWantsToAdd(project: Project, coordinates: List<GradleCoordinate>): Boolean {
  if (ApplicationManager.getApplication().isUnitTestMode) {
    return true
  }
  return Messages.OK == Messages.showOkCancelDialog(project, createAddDependencyMessage(coordinates), "Add Project Dependency", Messages.getErrorIcon())
}

@VisibleForTesting
fun createAddDependencyMessage(coordinates: List<GradleCoordinate>): String {
  val libraryNames = coordinates.joinToString(", ") { it.toString() }
  return "This operation requires the ${StringUtil.pluralize("library", coordinates.size)} $libraryNames. \n\n" +
      "Would you like to add ${StringUtil.pluralize("this", coordinates.size)} now?"
}
