/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.projectsystem

import com.android.annotations.concurrency.GuardedBy
import com.android.tools.idea.util.listenUntilNextSync
import com.android.tools.idea.util.runWhenSmartAndSyncedOnEdt
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.VisibleForTesting
import java.util.WeakHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val projectSubscriptionsLock = ReentrantLock()

@GuardedBy("projectSubscriptionsLock")
private val projectSubscriptions = WeakHashMap<Project, WeakHashMap<Disposable, BuildListener>>()

/**
 * Executes [method] against all the non-disposed subscriptions for the [project]. Removes disposed subscriptions.
 */
private fun forEachNonDisposedBuildListener(project: Project, method: (BuildListener) -> Unit) {
  projectSubscriptionsLock.withLock {
    projectSubscriptions[project]?.let { subscriptions ->
      // Clear disposed
      subscriptions.keys.removeIf { Disposer.isDisposed(it) }

      ArrayList<BuildListener>(subscriptions.values)
    } ?: emptyList()
  }.forEach(method)
}

interface BuildListener {
  /**
   * Called when a build has completed except for clean builds.
   */
  fun buildSucceeded() {}

  /**
   * Called when a build has failed.
   */
  fun buildFailed() {}

  /**
   * Called when a build is started, except for clean builds.
   */
  fun buildStarted() {}

  /**
   * Called when a clean build completes.
   */
  fun buildCleaned() {
    // By default, we assume that a cleaned build means destroying the state we had and we treat it as a failed build.
    buildFailed()
  }
}

/**
 * This sets up a listener that receives updates every time a build starts or finishes. On successful build, it calls
 * [BuildListener.buildSucceeded] method of the passed [BuildListener]. If the build fails, [BuildListener.buildFailed] will be called
 * instead.
 * This class ignores "clean" target builds and will not notify the listener when a clean happens since most listeners will not need to
 * listen for changes on "clean" target builds. If you need to listen for "clean" target builds, use [ProjectSystemBuildManager] directly.
 *
 * This is intended to be used by [com.intellij.openapi.fileEditor.FileEditor]'s. The editor should recreate/amend the model to reflect
 * build changes. This set up should be called in the constructor the last, so that all other members are initialized as it could call
 * [BuildListener.buildSucceeded] method straight away.
 */
fun setupBuildListener(project: Project,
                       buildable: BuildListener,
                       parentDisposable: Disposable,
                       allowMultipleSubscriptionsPerProject: Boolean = false) =
  setupBuildListener(project, buildable, parentDisposable, allowMultipleSubscriptionsPerProject,
                     ProjectSystemService.getInstance(project).projectSystem.getBuildManager())

@VisibleForTesting
fun setupBuildListener(
  project: Project,
  buildable: BuildListener,
  parentDisposable: Disposable,
  allowMultipleSubscriptionsPerProject: Boolean,
  buildManager: ProjectSystemBuildManager = ProjectSystemService.getInstance(project).projectSystem.getBuildManager(),
) {
  if (Disposer.isDisposed(parentDisposable)) {
    Logger.getInstance("com.android.tools.idea.common.util.ChangeManager")
      .warn("calling setupBuildListener for a disposed component $parentDisposable")
    return
  }
  // If we are not yet subscribed to this project, we should subscribe. If allowMultipleSubscriptionsPerProject is set to true, a build
  // listener is added anyway, so it's up to the caller to avoid setting up redundant build listeners.
  val projectNotSubscribed = projectSubscriptionsLock.withLock {
    val notSubscribed = projectSubscriptions[project]?.isEmpty() != false
    projectSubscriptions.computeIfAbsent(project) { WeakHashMap() }
    notSubscribed
  }
  // Double check that listener is properly disposed. Add test for it.

  if (projectNotSubscribed || allowMultipleSubscriptionsPerProject) {
    buildManager.addBuildListener(
      parentDisposable,
      object : ProjectSystemBuildManager.BuildListener {
        // We do not have to check isDisposed inside the callbacks since they won't get called if parentDisposable is disposed
        override fun buildStarted(mode: ProjectSystemBuildManager.BuildMode) {
          if (mode == ProjectSystemBuildManager.BuildMode.CLEAN) return

          forEachNonDisposedBuildListener(project,
                                          BuildListener::buildStarted)
        }

        override fun buildCompleted(result: ProjectSystemBuildManager.BuildResult) {
          // We do not call refresh if the build was not successful or if it was simply a clean build.
          val isCleanBuild = result.mode == ProjectSystemBuildManager.BuildMode.CLEAN
          if (result.status == ProjectSystemBuildManager.BuildStatus.SUCCESS && !isCleanBuild) {
            forEachNonDisposedBuildListener(
              project,
              BuildListener::buildSucceeded)
          }
          else {
            if (isCleanBuild) {
              forEachNonDisposedBuildListener(
                project,
                BuildListener::buildCleaned)
            }
            else {
              forEachNonDisposedBuildListener(
                project,
                BuildListener::buildFailed)
            }
          }
        }
      })
  }
  /**
   * Sets up the listener once all conditions are met. This method can only be called once the project has synced and is smart.
   */
  fun setupListenerWhenSmartAndSynced(buildManager: ProjectSystemBuildManager) {
    if (Disposer.isDisposed(parentDisposable)) return

    projectSubscriptionsLock.withLock {
      projectSubscriptions[project]!!.let {
        it[parentDisposable] = buildable
        Disposer.register(parentDisposable) {
          projectSubscriptionsLock.withLock disposable@{
            val subscriptions = projectSubscriptions[project] ?: return@disposable
            subscriptions.remove(parentDisposable)
          }
        }
      }
    }

    val lastResult = buildManager.getLastBuildResult()
    if (lastResult.status == ProjectSystemBuildManager.BuildStatus.SUCCESS) {
      // This is called from runWhenSmartAndSyncedOnEdt callback which should not be called if parentDisposable is disposed
      buildable.buildSucceeded()
    }
  }

  /**
   * Setup listener. This method does not make assumptions about the project sync and smart status.
   */
  fun setupListener(buildManager: ProjectSystemBuildManager) {
    if (Disposer.isDisposed(parentDisposable)) return
    // We are not registering before the constructor finishes, so we should be safe here
    project.runWhenSmartAndSyncedOnEdt(parentDisposable, { result ->
      if (result.isSuccessful) {
        setupListenerWhenSmartAndSynced(buildManager)
      }
      else {
        // The project failed to sync, run initialization when the project syncs correctly
        project.listenUntilNextSync(parentDisposable, object : ProjectSystemSyncManager.SyncResultListener {
          override fun syncEnded(result: ProjectSystemSyncManager.SyncResult) {
            // Sync has completed but we might not be in smart mode so re-run the initialization
            setupListener(buildManager)
          }
        })
      }
    })
  }

  setupListener(buildManager)
}