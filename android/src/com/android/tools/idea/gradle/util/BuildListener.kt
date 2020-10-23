package com.android.tools.idea.gradle.util

import com.android.annotations.concurrency.GuardedBy
import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildListener
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.util.listenUntilNextSync
import com.android.tools.idea.util.runWhenSmartAndSyncedOnEdt
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.android.uipreview.ModuleClassLoaderManager
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

      subscriptions.values.forEach(method)
    }
  }
}

fun BuildStatus?.isSuccess(): Boolean = this?.isBuildSuccessful ?: false

interface BuildListener {
  fun buildSucceeded()
  fun buildFailed() {}
  fun buildStarted() {}
}

/**
 * This sets up a listener that receives updates every time gradle build starts or finishes. On successful build, it calls
 * [BuildListener.buildSucceeded] method of the passed [BuildListener]. If the build fails, [BuildListener.buildFailed] will be called
 * instead.
 * This class ignores "clean" target builds and will not notify the listener when a clean happens since most listeners will not need to
 * listen for changes on "clean" target builds. If you need to listen for "clean" target builds, use [GradleBuildState] directly.
 *
 * This is intended to be used by [com.intellij.openapi.fileEditor.FileEditor]'s. The editor should recreate/amend the model to reflect
 * build changes. This set up should be called in the constructor the last, so that all other members are initialized as it could call
 * [BuildListener.buildSucceeded] method straight away.
 */
fun setupBuildListener(
  project: Project,
  buildable: BuildListener,
  parentDisposable: Disposable) {
  if (Disposer.isDisposed(parentDisposable)) {
    Logger.getInstance("com.android.tools.idea.common.util.ChangeManager")
      .warn("calling setupBuildListener for a disposed component $parentDisposable")
    return
  }
  // If we are not yet subscribed to this project, we should subscribe
  if (projectSubscriptionsLock.withLock {
      val notSubscribed = projectSubscriptions[project] == null
      projectSubscriptions.computeIfAbsent(project) {
        Disposer.register(parentDisposable, {
          projectSubscriptionsLock.withLock {
            projectSubscriptions.remove(project)
          }
        })
        WeakHashMap()
      }
      notSubscribed
    }) {
    GradleBuildState.subscribe(project, object : GradleBuildListener.Adapter() {
      // We do not have to check isDisposed inside the callbacks since they won't get called if parentDisposable is disposed
      override fun buildStarted(context: BuildContext) {
        if (context.buildMode == BuildMode.CLEAN) return

        forEachNonDisposedBuildListener(project, BuildListener::buildStarted)
      }

      override fun buildFinished(status: BuildStatus, context: BuildContext?) {
        // We do not call refresh if the build was not successful or if it was simply a clean build.
        if (status.isSuccess() && context?.buildMode != BuildMode.CLEAN) {
          // Before calling any of the build listeners we should invalidate current ClassLoaders for the rebuilt modules
          ModuleManager.getInstance(project).modules.forEach { ModuleClassLoaderManager.get().clearCache(it) }

          forEachNonDisposedBuildListener(project, BuildListener::buildSucceeded)
        }
        else {
          forEachNonDisposedBuildListener(project, BuildListener::buildFailed)
        }
      }
    })
  }
  /**
   * Sets up the listener once all conditions are met. This method can only be called once the project has synced and is smart.
   */
  fun setupListenerWhenSmartAndSynced() {
    if (Disposer.isDisposed(parentDisposable)) return

    projectSubscriptionsLock.withLock {
      projectSubscriptions[project]!!.let {
        it[parentDisposable] = buildable
        Disposer.register(parentDisposable, {
          projectSubscriptionsLock.withLock {
            projectSubscriptions[project]?.remove(parentDisposable)
          }
        })
      }
    }

    val status = GradleBuildState.getInstance(project).summary?.status
    if (status.isSuccess()) {
      // This is called from runWhenSmartAndSyncedOnEdt callback which should not be called if parentDisposable is disposed
      buildable.buildSucceeded()
    }
  }

  /**
   * Setup listener. This method does not make assumptions about the project sync and smart status.
   */
  fun setupListener() {
    if (Disposer.isDisposed(parentDisposable)) return
    // We are not registering before the constructor finishes, so we should be safe here
    project.runWhenSmartAndSyncedOnEdt(parentDisposable, { result ->
      if (result.isSuccessful) {
        setupListenerWhenSmartAndSynced()
      }
      else {
        // The project failed to sync, run initialization when the project syncs correctly
        project.listenUntilNextSync(parentDisposable, object : ProjectSystemSyncManager.SyncResultListener {
          override fun syncEnded(result: ProjectSystemSyncManager.SyncResult) {
            // Sync has completed but we might not be in smart mode so re-run the initialization
            setupListener()
          }
        })
      }
    })
  }

  setupListener()
}