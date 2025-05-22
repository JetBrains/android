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
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResultListener
import com.android.tools.idea.projectsystem.getSyncManager
import com.android.tools.idea.res.ResourceClassRegistry
import com.android.tools.idea.res.StudioResourceIdManager
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.utils.function.RunOnce
import com.google.common.annotations.VisibleForTesting
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.resourceManagers.ModuleResourceManagers

/**
 * Project component responsible for clearing the resource cache after the initial project build if any resources
 * were accessed before source generation. If the last build of the project in the previous session was successful
 * (i.e. the initial build of this session is skipped), then the resource cache is already valid and will not be cleared.
 */
@Service(Service.Level.PROJECT)
class ClearResourceCacheAfterFirstBuild(private val project: Project) {
  private data class CacheClearedCallbacks(val onCacheCleared: Runnable, val onSourceGenerationError: Runnable)
  private val lock = Any()
  @GuardedBy("lock")
  private var state = State.INITIAL
  @GuardedBy("lock")
  private val waitingCallbacks = mutableSetOf<CacheClearedCallbacks>()

  // We initialize this connection here to ensure it only happens once.
  private var messageBusConnection: MessageBusConnection? = project.messageBus.connect().apply {
    subscribe(PROJECT_SYSTEM_SYNC_TOPIC, SyncResultListener {
      if (it.isSuccessful) syncSucceeded() else syncFailed()
    })
  }

  /**
   * Runs at most once when sync succeeds the first time, or when it is clear sync is not required.
   *
   * * Disconnects and clears the [MessageBusConnection]
   * * Clears the resource cache, if necessary
   * * Sets the state to [State.CACHE_CLEARED]
   * * Pulls all waiting callbacks out of the set of waiting callbacks and executes them
   */
  private val onSyncSucceeded = RunOnce {
    checkNotNull(messageBusConnection).disconnect()
    messageBusConnection = null
    clearResourceCacheIfNecessary()
    val cacheClearedCallbacks = synchronized(lock) {
      // runWhenResourceCacheClean should now execute onCacheCleared immediately when called
      state = State.CACHE_CLEARED
      waitingCallbacks.mapTo(mutableSetOf(), CacheClearedCallbacks::onCacheCleared)
        .also { waitingCallbacks.clear() }
    }
    for (cacheClearedCallback in cacheClearedCallbacks) {
      cacheClearedCallback.run()
    }
  }

  class MyStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
      // Ensure this is instantiated which will subscribe it to updates until the first successful project sync.
      getInstance(project)
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
   * @param parentDisposable parent disposable for the CacheClearedCallback that will be created. Corresponding CacheClearedCallback will be
   * removed when parentDisposable is disposed.
   */
  fun runWhenResourceCacheClean(onCacheClean: Runnable, onSourceGenerationError: Runnable, parentDisposable: Disposable) {
    // There's no need to wait for the first successful project sync this session if the project's sync
    // state is already clean. In this case, we can go ahead and clear the cache and then treat things
    // as if sync succeeded.
    if (!isCacheClean() && isSyncStateClean()) onSyncSucceeded()

    val localState = synchronized(lock) {
      if (state != State.CACHE_CLEARED) {
        val callbacks = CacheClearedCallbacks(onCacheClean, onSourceGenerationError)
        if (waitingCallbacks.add(callbacks)) {
          Disposer.register(parentDisposable) {
            synchronized(lock) { waitingCallbacks.remove(callbacks) }
          }
        }
      }
      state
    }

    when (localState) {
      State.INITIAL -> return
      State.SYNC_ERROR -> onSourceGenerationError.run()
      State.CACHE_CLEARED -> onCacheClean.run()
    }
  }

  private fun isSyncStateClean() = with(project.getSyncManager()) {
    !isSyncInProgress() && !isSyncNeeded() && getLastSyncResult().isSuccessful
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
  private fun clearResourceCacheIfNecessary() {
    if (project.getUserData(INCOMPLETE_RUNTIME_DEPENDENCIES) !== java.lang.Boolean.TRUE) return
    project.putUserData(INCOMPLETE_RUNTIME_DEPENDENCIES, null)
    ResourceClassRegistry.get(project).clearCache()

    ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID).forEach { facet ->
      // Unfortunately this runs on the AWT thread and the initialization of the managers below might be expensive.
      // Luckily, we are here just to clear the caches so if the service does not exist, we do not need to clear anything. For that
      // purpose we use the getInstanceIfCreated method if available.
      StudioResourceRepositoryManager.getInstanceIfCreated(facet)?.resetAllCaches()
      StudioResourceIdManager.getInstanceIfCreated(facet.module)?.resetDynamicIds()
      ResourceClassRegistry.getInstanceIfCreated(project)?.clearCache()
      ModuleResourceManagers.getInstance(facet).localResourceManager.invalidateAttributeDefinitions()
    }
  }

  /**
   * Will be called when the sync succeeds. May happen more than once, although we do unsubscribe
   * when this is called.
   */
  @VisibleForTesting
  fun syncSucceeded() {
    onSyncSucceeded()
  }

  @VisibleForTesting
  /** Will be called every time a sync fails. */
  fun syncFailed() {
    val errorCallbacks =
      synchronized(lock) {
        // We will already run the failure callback immediately or not at all if we're in one of the
        // other states.
        if (state != State.INITIAL) return
        state = State.SYNC_ERROR
        // Return a copy
        waitingCallbacks.mapTo(mutableSetOf(), CacheClearedCallbacks::onSourceGenerationError)
      }
    for (errorCallback in errorCallbacks) {
      errorCallback.run()
    }
  }

  /** Indicates whether the cache has been cleared.*/
  @VisibleForTesting
  internal fun isCacheClean() = synchronized(lock) { state == State.CACHE_CLEARED }

  /**
   * The state diagram for this class:
   *
   * * Starts in INITIAL
   * * Can move from INITIAL to SYNC_ERROR or CACHE_CLEARED
   * * Can move from SYNC_ERROR to CACHE_CLEARED
   */
  private enum class State {
    INITIAL, SYNC_ERROR, CACHE_CLEARED
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ClearResourceCacheAfterFirstBuild =
        project.service()

    @JvmStatic
    private val INCOMPLETE_RUNTIME_DEPENDENCIES = Key.create<Boolean>("IncompleteRuntimeDependencies")
  }
}
