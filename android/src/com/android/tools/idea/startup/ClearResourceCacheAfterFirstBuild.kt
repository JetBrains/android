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
package com.android.tools.idea.startup

import com.android.annotations.VisibleForTesting
import com.android.annotations.concurrency.GuardedBy
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SourceGenerationCallback
import com.android.tools.idea.projectsystem.getSyncManager
import com.android.tools.idea.res.AppResourceRepository
import com.android.tools.idea.res.ResourceClassRegistry
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import org.jetbrains.android.resourceManagers.ModuleResourceManagers
import org.jetbrains.android.util.AndroidUtils

/**
 * Project component responsible for clearing the resource cache after the initial project build if any resources
 * were accessed before source generation. If the last build of the project in the previous session was successful
 * (i.e. the initial build of this session is skipped), then the resource cache is already valid and will not be cleared.
 */
class ClearResourceCacheAfterFirstBuild(project: Project) : AbstractProjectComponent(project), SourceGenerationCallback {
  private class CacheClearedCallback(val onCacheCleared: Runnable, val onSourceGenerationError: Runnable)

  private val lock = Any()
  @GuardedBy("lock")
  private var cacheClean = false
  @GuardedBy("lock")
  private var errorOccurred = false
  private val callbacks = mutableListOf<CacheClearedCallback>()

  override fun projectOpened() {
    // Using the project system APIs for a project for the first time will permanently associate the project with an
    // implementation of AndroidProjectSystem. When testing, we often want to force a test project to be associated with a
    // particular project system by setting up certain conditions before accessing the project system APIs (e.g. registering
    // the TestProjectSystem extension in a set up method). Accessing the project's build manager here during a unit test
    // would cause the test project to be prematurely associated with a project system before any set up code gets executed.
    // See http://b/68809026
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      myProject.getSyncManager().addSourceGenerationCallback(this)
    }
  }

  /**
   * Delays the execution of [onCacheClean] until after the initial project build finishes and, if necessary, the resource
   * cache has been cleared.
   *
   * In the event that source generation fails, [onSourceGenerationError] will be executed. This gives callers the opportunity
   * to notify the user that whatever feature [onCacheClean] supports will be unavailable until after a successful build.
   * The execution of [onSourceGenerationError] does not stop the later execution of [onCacheClean] once source generation is
   * complete and the resource cache has been validated.
   *
   * @param onCacheClean callback to execute once the resource cache has been validated
   * @param onSourceGenerationError callback to execute if source generation failed
   */
  fun runWhenResourceCacheClean(onCacheClean: Runnable, onSourceGenerationError: Runnable) {
    val (cacheClean, errorOccurred) = synchronized(lock) {
      if (!cacheClean) {
        callbacks.add(CacheClearedCallback(onCacheClean, onSourceGenerationError))
      }

      Pair(cacheClean, errorOccurred)
    }

    if (cacheClean) {
      onCacheClean.run()
    }
    else if (errorOccurred) {
      onSourceGenerationError.run()
    }
  }

  /**
   * Dump the cached resources if we have accessed the resources before the build was ready.
   * Clear the file based resources and attributes that may have been created based on those resources.
   */
  @VisibleForTesting
  fun clearResourceCacheIfNecessary() {
    if (AppResourceRepository.testAndClearTempResourceCached(myProject)) {
      ResourceClassRegistry.get(myProject).clearCache()

      AndroidUtils.getApplicationFacets(myProject).forEach { facet ->
        facet.refreshResources()
        ModuleResourceManagers.getInstance(facet).localResourceManager.invalidateAttributeDefinitions()
      }
    }

    // runWhenResourceCacheClean should now execute onCacheCleared immediately when called
    synchronized(lock) {
      cacheClean = true
    }
  }

  override fun sourcesGenerated() {
    clearResourceCacheIfNecessary()

    callbacks.forEach { it.onCacheCleared.run() }
    callbacks.clear()
  }

  override fun sourceGenerationError() {
    val toExecute = mutableListOf<CacheClearedCallback>()

    synchronized(lock) {
      // runWhenResourceCacheClean should now execute onSourceGeneration immediately when called
      if (!errorOccurred) {
        errorOccurred = true
        toExecute.addAll(callbacks)
      }
    }

    toExecute.forEach { it.onSourceGenerationError.run() }
  }

  /**
   * Indicates whether or not clearResourceCacheIfNecessary has been called.
   */
  @VisibleForTesting
  fun isCacheClean() = synchronized(lock) { cacheClean }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ClearResourceCacheAfterFirstBuild = project
        .getComponent(ClearResourceCacheAfterFirstBuild::class.java)
  }
}
