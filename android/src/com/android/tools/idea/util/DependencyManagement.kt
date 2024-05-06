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
import com.android.annotations.concurrency.UiThread
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.support.AndroidxName
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.DependencyManagementException
import com.android.tools.idea.projectsystem.DependencyType
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getSyncManager
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.concurrency.ThreadingAssertions

typealias DependencyAnalysis = Triple<List<GradleCoordinate>, List<GradleCoordinate>, String>

/**
 * Returns true iff the dependency with [artifactId] is transitively available to this module.
 * This function returns false if the project's dependency model is unavailable and therefore dependencies
 * could not be checked (e.g. Project is syncing with build system or any dependency management error occurs).
 * To handle dependency management errors, use methods defined in [AndroidProjectSystem] and catch
 * [DependencyManagementException].
 *
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
  catch (e: Throwable) {
    if (!isDisposed) {
      throw e
    }
  }
  return false
}

/**
 * Returns whether this module depends on the new support library artifacts (androidx).
 */
fun Module.dependsOnAndroidx(): Boolean = getModuleSystem().useAndroidX

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

fun Module.dependsOnAppCompat(): Boolean =
  this.dependsOn(GoogleMavenArtifactId.APP_COMPAT_V7) || this.dependsOn(GoogleMavenArtifactId.ANDROIDX_APP_COMPAT_V7)

/**
 * Add maven projects as dependencies for this module. The maven group and artifact IDs are taken from given [GradleCoordinate]s and the
 * coordinates' version information are disregarded. This method will show a dialog prompting the user for confirmation if
 * [promptUserBeforeAdding] is set to true and return with no-op if user chooses to not add the dependencies. If any of the dependencies
 * are added successfully and [requestSync] is set to true, this method will request a sync to make sure the artifacts are resolved.
 * In this case, the sync will happen asynchronously and this method will not wait for it to finish before returning.
 *
 * An example usage for setting [promptUserBeforeAdding] to false: Suppose the user clicks on a URL that says "Click here to add <some
 * dependency>". In this case it's obvious that user wants to add the dependency so we shouldn't ask them again.
 *
 * Note: Setting [promptUserBeforeAdding] to false does not guarantee this method won't show any message dialogs. [promptUserBeforeAdding]
 * simply controls whether or not the user will be prompted before this method tries to add the dependencies. Should any errors occur, this
 * method will show the appropriate messages with details on the errors.
 *
 * If compatibility issues are detected such that no dependencies can be added, this method will show a warning message dialog with an
 * explanation of the issue returned from [AndroidModuleSystem.analyzeDependencyCompatibility].
 *
 * If there were errors adding any of the dependencies, this method will show an error message dialog with the dependencies that couldn't
 * be added as well as the reasons why they couldn't be added. A sync will still be performed as long as there were some dependencies added
 * successfully and [requestSync] is true.
 *
 * This method shows no confirmation dialog and performs a no-op if the list of artifacts is the empty list.
 * This method does not trigger a sync if none of the artifacts were added successfully or if [requestSync] is false.
 * @return list of artifacts that were not successfully added. i.e. If the returned list is empty, then all were added successfully.
 */
@JvmOverloads
@UiThread
fun Module.addDependenciesWithUiConfirmation(coordinates: List<GradleCoordinate>,
                                             promptUserBeforeAdding: Boolean,
                                             requestSync: Boolean = true,
                                             dependencyType: DependencyType = DependencyType.IMPLEMENTATION)
  : List<GradleCoordinate> {
  ThreadingAssertions.assertEventDispatchThread()
  if (coordinates.isEmpty()) {
    return listOf()
  }

  val moduleSystem = getModuleSystem()
  val distinctCoordinates = coordinates.distinctBy { Pair(it.groupId, it.artifactId) }
  val (compatibleDependencies, incompatibleDependencies, warning) = ProgressManager.getInstance().runProcessWithProgressSynchronously<DependencyAnalysis, Exception>(
    { moduleSystem.analyzeDependencyCompatibility(distinctCoordinates) },
    "Analyzing Dependency Compatibility",
    false,
    moduleSystem.module.project)

  // If [promptUserBeforeAdding] is false then we need to inform the user of any compatibility errors in a separate message window.
  if (promptUserBeforeAdding) {
    if (!userWantsToAdd(project, distinctCoordinates, warning)) {
      return distinctCoordinates
    }
  }
  else if (incompatibleDependencies.isNotEmpty()) {
    Messages.showErrorDialog(warning, "Compatibility Issues Detected")
    if (compatibleDependencies.isEmpty()) {
      return incompatibleDependencies
    }
  }

  val coordinatesToExceptions = ProgressManager.getInstance().runProcessWithProgressSynchronously<List<Pair<GradleCoordinate, DependencyManagementException>>, Exception>(
    {
      val coordinatesToExceptions: ArrayList<Pair<GradleCoordinate, DependencyManagementException>> = ArrayList()
      for (coordinate in compatibleDependencies) {
        try {
          moduleSystem.registerDependency(coordinate, dependencyType)
        }
        catch (e: DependencyManagementException) {
          coordinatesToExceptions.add(Pair(coordinate, e))
        }
      }
      coordinatesToExceptions
    },
    "Adding Dependencies",
    false,
    moduleSystem.module.project)

  val shouldSync = coordinatesToExceptions.size < compatibleDependencies.size && requestSync
  if (coordinatesToExceptions.isNotEmpty()) {
    var errorMessage = "The following dependencies could not be added:\n"
    for (coordinateToException in coordinatesToExceptions) {
      errorMessage += "${coordinateToException.first} Reason: ${coordinateToException.second.message}\n"
    }
    if (shouldSync) {
      errorMessage += "\nA sync will be still be performed to resolve the dependencies that were added successfully."
    }
    Messages.showErrorDialog(errorMessage, "Could Not Add Dependency")
  }
  if (shouldSync) {
    project.getSyncManager().syncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED)
  }

  return incompatibleDependencies + coordinatesToExceptions.map { it.first }
}

fun userWantsToAdd(project: Project, coordinates: List<GradleCoordinate>, warning: String = ""): Boolean {
  return Messages.OK == Messages.showOkCancelDialog(
    project, createAddDependencyMessage(coordinates, warning), "Add Project Dependency", Messages.getErrorIcon())
}

@VisibleForTesting
fun createAddDependencyMessage(coordinates: List<GradleCoordinate>, warning: String = ""): String {
  val libraryNames = coordinates.joinToString(", ") { it.toString() }
  val these = StringUtil.pluralize("this", coordinates.size)
  val libraries = StringUtil.pluralize("library", coordinates.size)
  val requires = "This operation requires the $libraries $libraryNames."
  if (warning.isEmpty()) {
    return "$requires\n\nWould you like to add $these now?"
  }
  val them = if (coordinates.size > 1) "them" else "it"
  return """$requires

Problem: $warning

The project may not compile after adding $these $libraries.
Would you like to add $them anyway?"""
}
