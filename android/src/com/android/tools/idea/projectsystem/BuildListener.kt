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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.util.WeakHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


/**
 * A per-project subscription to the [ProjectSystemBuildManager] (see [createBuildListener]) managing all the client subscriptions made via
 * [setupBuildListener]. This way we only have at most one subscription to each [Project].
 */
private class ProjectSubscription {
  val listenersMap: WeakHashMap<Disposable, BuildListener> = WeakHashMap()
  val projectSystemListenerDisposable: Disposable = Disposer.newDisposable()
}

private val projectSubscriptionsLock = ReentrantLock()

@GuardedBy("projectSubscriptionsLock")
private val projectSubscriptions = WeakHashMap<Project, ProjectSubscription>()

/**
 * Executes [method] against all the non-disposed subscriptions for the [project]. Removes disposed subscriptions.
 */
private fun forEachNonDisposedBuildListener(project: Project, method: (BuildListener) -> Unit) {
  projectSubscriptionsLock.withLock {
    projectSubscriptions[project]?.let { subscription ->
      // Clear disposed
      subscription.listenersMap.keys.removeIf { Disposer.isDisposed(it) }
      if (subscription.listenersMap.isEmpty()) {
        Disposer.dispose(subscription.projectSystemListenerDisposable)
        projectSubscriptions.remove(project)
      }

      ArrayList<BuildListener>(subscription.listenersMap.values)
    } ?: emptyList()
  }.forEach(method)
}

interface BuildListener {
  /**
   * Called when the [BuildListener] is fully setup. Other methods in the [BuildListener] might be
   * called before this method if the build is already completed when calling [setupBuildListener].
   *
   * This method must not be block.
   */
  fun startedListening() {}

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

private fun Project.createBuildListener() = object : ProjectSystemBuildManager.BuildListener {
  // We do not have to check isDisposed inside the callbacks since they won't get called if parentDisposable is disposed
  override fun buildStarted(mode: ProjectSystemBuildManager.BuildMode) {
    if (mode == ProjectSystemBuildManager.BuildMode.CLEAN) return

    forEachNonDisposedBuildListener(this@createBuildListener,
                                    BuildListener::buildStarted)
  }

  override fun buildCompleted(result: ProjectSystemBuildManager.BuildResult) {
    // We do not call refresh if the build was not successful or if it was simply a clean build.
    val isCleanBuild = result.mode == ProjectSystemBuildManager.BuildMode.CLEAN
    if (result.status == ProjectSystemBuildManager.BuildStatus.SUCCESS && !isCleanBuild) {
      forEachNonDisposedBuildListener(
        this@createBuildListener,
        BuildListener::buildSucceeded)
    }
    else {
      if (isCleanBuild) {
        forEachNonDisposedBuildListener(
          this@createBuildListener,
          BuildListener::buildCleaned)
      }
      else {
        forEachNonDisposedBuildListener(
          this@createBuildListener,
          BuildListener::buildFailed)
      }
    }
  }
}

/**
 * This sets up a listener that receives updates every time a build starts or finishes. On successful build, it calls
 * [BuildListener.buildSucceeded] method of the passed [BuildListener]. If the build fails, [BuildListener.buildFailed] will be called
 * instead. If the successful build is "clean" build [BuildListener.buildCleaned] will be called.
 *
 * This is intended to be used by [com.intellij.openapi.fileEditor.FileEditor]'s. The editor should recreate/amend the model to reflect
 * build changes. This set up should be called in the constructor the last, so that all other members are initialized as it could call
 * [BuildListener.buildSucceeded] method straight away.
 */
fun setupBuildListener(
  project: Project,
  buildable: BuildListener,
  parentDisposable: Disposable,
  buildManager: ProjectSystemBuildManager = ProjectSystemService.getInstance(project).projectSystem.getBuildManager(),
) {
  if (Disposer.isDisposed(parentDisposable)) {
    Logger.getInstance("com.android.tools.idea.common.util.ChangeManager")
      .warn("calling setupBuildListener for a disposed component $parentDisposable")
    return
  }

  /**
   * Sets up the listener once all conditions are met. This method can only be called once the project has synced and is smart on a
   * dispatcher thread.
   */
  fun setupListenerWhenSmartAndSynced(buildManager: ProjectSystemBuildManager) {
    ApplicationManager.getApplication().assertIsDispatchThread() // To verify parentDisposable is not disposed during the method execution
    if (Disposer.isDisposed(parentDisposable)) return

    val lastResult = buildManager.getLastBuildResult()
    if (lastResult.status == ProjectSystemBuildManager.BuildStatus.SUCCESS) {
      // This is called from runWhenSmartAndSyncedOnEdt callback which should not be called if parentDisposable is disposed
      buildable.buildStarted()
      buildable.buildSucceeded()
    }

    projectSubscriptionsLock.withLock {
      val subscription = projectSubscriptions.computeIfAbsent(project) {
        val projectSubscription = ProjectSubscription()
        // If we are not yet subscribed to this project, we should subscribe.
        buildManager.addBuildListener(
          projectSubscription.projectSystemListenerDisposable,
          project.createBuildListener()
        )
        buildable.startedListening()
        projectSubscription
      }
      subscription.listenersMap[parentDisposable] = buildable
      Disposer.register(parentDisposable) {
        projectSubscriptionsLock.withLock disposable@{
          val disposingSubscription = projectSubscriptions[project] ?: return@disposable
          disposingSubscription.listenersMap.remove(parentDisposable)
          if (disposingSubscription.listenersMap.isEmpty()) {
            Disposer.dispose(disposingSubscription.projectSystemListenerDisposable)
            projectSubscriptions.remove(project)
          }
        }
      }
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