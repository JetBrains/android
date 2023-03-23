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

import com.android.annotations.concurrency.GuardedBy
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResultListener
import com.android.tools.idea.projectsystem.getSyncManager
import com.android.tools.idea.res.ResourceClassRegistry
import com.android.tools.idea.res.ResourceIdManager
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.openapi.util.Key
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.android.resourceManagers.ModuleResourceManagers
import org.jetbrains.android.util.AndroidUtils

/**
 * Project component responsible for clearing the resource cache after the initial project build if any resources
 * were accessed before source generation. If the last build of the project in the previous session was successful
 * (i.e. the initial build of this session is skipped), then the resource cache is already valid and will not be cleared.
 */
class ClearResourceCacheAfterFirstBuild(private val project: Project) {
  private class CacheClearedCallback(val onCacheCleared: Runnable, val onSourceGenerationError: Runnable)

  private val lock = Any()
  @GuardedBy("lock")
  private var cacheClean = false
  @GuardedBy("lock")
  private var errorOccurred = false
  private val callbacks = mutableListOf<CacheClearedCallback>()
  private var messageBusConnection: MessageBusConnection? = null

  class MyStartupActivity : ProjectPostStartupActivity {
    override suspend fun execute(project: Project) {
      // Listen for sync results until the first successful project sync.
      val serviceInstance = getInstance(project)
      serviceInstance.messageBusConnection = project.messageBus.connect().apply {
        subscribe(PROJECT_SYSTEM_SYNC_TOPIC, object : SyncResultListener {
          override fun syncEnded(result: SyncResult) {
            if (result.isSuccessful) {
              if (serviceInstance.messageBusConnection != null) {
                serviceInstance.messageBusConnection = null
                disconnect()
              }

              serviceInstance.syncSucceeded()
            }
            else {
              serviceInstance.syncFailed()
            }
          }
        })
      }
    }
  }

  /**
   * Delays the execution of [onCacheClean] until after the initial project build finishes and, if necessary, the resource
   * cache has been cleared.
   *
   * In the event that source generation fails, [onSourceGenerationError] will be executed. This gives callers the opportunity
   * to notify the user that whatever feature [onCacheClean] supports will be unavailable until after a successful project sync.
   * The execution of [onSourceGenerationError] does not stop the later execution of [onCacheClean] once source generation is
   * complete and the resource cache has been validated.
   *
   * @param onCacheClean callback to execute once the resource cache has been validated
   * @param onSourceGenerationError callback to execute if source generation failed
   */
  fun runWhenResourceCacheClean(onCacheClean: Runnable, onSourceGenerationError: Runnable) {
    // There's no need to wait for the first successful project sync this session if the project's sync state
    // is already clean. In this case, we can go ahead and clear the cache and notify callbacks of a success.
    messageBusConnection?.apply {
      if (syncStateClean()) {
        messageBusConnection = null
        disconnect()

        syncSucceeded()
        onCacheClean.run()
        return
      }
    }

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

  private fun syncStateClean(): Boolean {
    val syncManager = project.getSyncManager()
    return !syncManager.isSyncInProgress() && !syncManager.isSyncNeeded() && syncManager.getLastSyncResult().isSuccessful
  }

  /**
   * Sets the flag indicating that computed runtime dependencies are incomplete because
   * the underlying project model was not yet finalized when the computation happened.
   */
  fun setIncompleteRuntimeDependencies() {
    project.putUserData(INCOMPLETE_RUNTIME_DEPENDENCIES, true)
  }

  /**
   * Dump the cached resources if we have accessed the resources before the build was ready.
   * Clear the file based resources and attributes that may have been created based on those resources.
   */
  @VisibleForTesting
  fun clearResourceCacheIfNecessary() {
    if (project.getUserData(INCOMPLETE_RUNTIME_DEPENDENCIES) === java.lang.Boolean.TRUE) {
      project.putUserData(INCOMPLETE_RUNTIME_DEPENDENCIES, null)
      ResourceClassRegistry.get(project).clearCache()

      AndroidUtils.getApplicationFacets(project).forEach { facet ->
        StudioResourceRepositoryManager.getInstance(facet).resetAllCaches()
        ResourceIdManager.get(facet.module).resetDynamicIds()
        ResourceClassRegistry.get(project).clearCache()
        ModuleResourceManagers.getInstance(facet).localResourceManager.invalidateAttributeDefinitions()
      }
    }

    // runWhenResourceCacheClean should now execute onCacheCleared immediately when called
    synchronized(lock) {
      cacheClean = true
    }
  }

  @VisibleForTesting
  fun syncSucceeded() {
    clearResourceCacheIfNecessary()

    callbacks.forEach { it.onCacheCleared.run() }
    callbacks.clear()
  }

  @VisibleForTesting
  fun syncFailed() {
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
    fun getInstance(project: Project): ClearResourceCacheAfterFirstBuild =
        project.getService(ClearResourceCacheAfterFirstBuild::class.java)

    @JvmStatic
    private val INCOMPLETE_RUNTIME_DEPENDENCIES = Key.create<Boolean>("IncompleteRuntimeDependencies")
  }
}
