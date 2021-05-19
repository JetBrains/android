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
package com.android.tools.idea.common.model

import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.concurrency.addCallback
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getSyncManager
import com.android.tools.idea.util.addDependencies
import com.android.tools.idea.util.dependsOn
import com.android.tools.idea.util.userWantsToAdd
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import org.jetbrains.android.facet.AndroidFacet

/**
 * Handles dependencies for the Layout Editor.
 *
 * This class acts as an abstraction layer between Layout Editor component and the build system to manage
 * dependencies required by the provided [NlComponent]
 */
class NlDependencyManager private constructor() {

  companion object {
    @JvmStatic
    fun getInstance(): NlDependencyManager = ApplicationManager.getApplication().getService(NlDependencyManager::class.java)
  }

  data class AddDependenciesResult(val hadMissingDependencies: Boolean, val dependenciesPresent: Boolean)

  /**
   * Makes sure the dependencies of the components being added are present and resolved in the module.
   * If they are not: ask the user if they can be added now.
   *
   * Returns an instance of [AddDependenciesResult] with hadMissingDependencies set to true if some dependencies were not yet present, and
   * dependenciesPresent set to true if all dependencies were already present or if they were added (i.e. the user chose to install them).
   */
  @JvmOverloads
  fun addDependencies(
    components: Iterable<NlComponent>,
    facet: AndroidFacet,
    syncDoneCallback: (() -> Unit)? = null
  ): AddDependenciesResult {
    val moduleSystem = facet.module.getModuleSystem()
    val missingDependencies = collectDependencies(components).filter { moduleSystem.getRegisteredDependency(it) == null }
    if (missingDependencies.isEmpty()) {
      // We don't have any missing dependencies, therefore they're all present.
      return AddDependenciesResult(hadMissingDependencies = false, dependenciesPresent = true)
    }

    if (facet.module.addDependencies(missingDependencies, false, requestSync = false).isNotEmpty()) {
      // User clicked "No" when asked to install the missing dependencies, so they won't be present.
      return AddDependenciesResult(hadMissingDependencies = true, dependenciesPresent = false)
    }

    // When the user clicks "Yes" to install the missing dependencies, sync the project so they'll effectively be present.
    val syncResult: ListenableFuture<ProjectSystemSyncManager.SyncResult> =
      facet.module.project.getSyncManager().syncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED)

    if (syncDoneCallback != null) {
      syncResult.addCallback(directExecutor(), success = { syncDoneCallback() }, failure = { syncDoneCallback() })
    }

    return AddDependenciesResult(hadMissingDependencies = true, dependenciesPresent = true)
  }

  /**
   * Adds the dependencies in a separate thread backed by a [ProgressIndicator] since this method might end up making network operations to
   * add missing dependencies. Receives an optional callback to be called if there were no missing dependencies and to be passed to
   * [addDependencies], which will set is as the callback of SyncManager#syncProject.
   *
   * @see addDependencies
   */
  fun addDependenciesAsync(
    components: Iterable<NlComponent>,
    facet: AndroidFacet,
    progressIndicatorTitle: String,
    callback: Runnable
  ) {
    ProgressManager.getInstance().run(object : Task.Backgroundable(facet.module.project, progressIndicatorTitle) {

      private var hadMissingDependencies: Boolean = false

      override fun run(indicator: ProgressIndicator) {
        hadMissingDependencies = addDependencies(components, facet) { callback.run() }.hadMissingDependencies
      }

      override fun onSuccess() {
        if (!hadMissingDependencies) {
          // Only invoke the callback if there were no missing dependencies. If missing dependencies were installed later, the callback will
          // be invoked by SyncManager#syncProject callback (see addDependencies).
          callback.run()
        }
      }
    })
  }

  /**
   * Returns true if the current module depends on the specified library.
   */
  fun isModuleDependency(artifact: GoogleMavenArtifactId, facet: AndroidFacet): Boolean = facet.module.dependsOn(artifact)

  /**
   * Returns the [GradleVersion] of the library with specified [artifactId] that the current module depends on.
   * @return the revision or null if the module does not depend on the specified library or the maven version is unknown.
   */
  fun getModuleDependencyVersion(artifactId: GoogleMavenArtifactId, facet: AndroidFacet): GradleVersion? =
      facet.module.getModuleSystem().getResolvedDependency(artifactId.getCoordinate("+"))?.version

  /**
   * Checks if there is any missing dependencies and ask the user only if they are some.
   *
   * User cannot be asked to accept dependencies in a write action.
   * Calls to this method should be made outside a write action, or all dependencies should already be added.
   */
  fun checkIfUserWantsToAddDependencies(toAdd: List<NlComponent>, facet: AndroidFacet): Boolean {
    val dependencies = collectDependencies(toAdd)
    val moduleSystem = facet.module.getModuleSystem()
    val missing = dependencies.filter { moduleSystem.getRegisteredDependency(it) == null }
    if (missing.none()) {
      return true
    }

    val application = ApplicationManagerEx.getApplicationEx()
    if (application.isWriteActionInProgress) {
      kotlin.assert(false) {
        "User cannot be asked to accept dependencies in a write action." +
            "Calls to this method should be made outside a write action"
      }
      return true
    }

    return userWantsToAdd(facet.module.project, missing)
  }

  /**
   * Finds all dependencies for the given components and maps them to a [GradleCoordinate]
   * @see GradleCoordinate.parseCoordinateString()
   */
  private fun collectDependencies(components: Iterable<NlComponent>): Iterable<GradleCoordinate> {
    return components
        .flatMap { it.dependencies}
        .mapNotNull { artifact -> GradleCoordinate.parseCoordinateString("$artifact:+") }
        .toList()
  }
}
