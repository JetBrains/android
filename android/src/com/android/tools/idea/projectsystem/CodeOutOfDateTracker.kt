/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.tools.idea.res.ResourceNotificationManager
import com.android.tools.idea.util.androidFacet
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.KotlinLanguage
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Interface for tracking build status.
 */
interface CodeOutOfDateTracker: ModificationTracker {
  /**
   * Call this method when an external event has caused the saved build to not be "usable" anymore. This will force a call to the
   * `needsRefreshCallback` passed to [create] at some point in the future.
   */
  fun invalidateSavedBuildStatus()

  @TestOnly
  fun needsRefreshOnSuccessfulBuild(): Boolean

  @TestOnly
  fun buildWillTriggerRefresh(): Boolean

  companion object {
    fun create(module: Module?, parentDisposable: Disposable, needsRefreshCallback: () -> Unit): CodeOutOfDateTracker =
      module?.let { CodeOutOfDateTrackerImpl(it, parentDisposable, needsRefreshCallback) } ?: NopCodeOutOfDateTrackerImpl
  }
}

/**
 * A [CodeOutOfDateTracker] used when the [Module] is not available. It does not do tracking.
 */
object NopCodeOutOfDateTrackerImpl : CodeOutOfDateTracker {
  override fun invalidateSavedBuildStatus() {}
  override fun needsRefreshOnSuccessfulBuild(): Boolean = false
  override fun buildWillTriggerRefresh(): Boolean = false
  override fun getModificationCount(): Long = 0
}

private class CodeOutOfDateTrackerImpl constructor(module: Module,
                                                   parentDisposable: Disposable,
                                                   needsRefreshCallback: () -> Unit) : CodeOutOfDateTracker {
  private val log = Logger.getInstance(CodeOutOfDateTrackerImpl::class.java)

  /**
   * Lock used when processing events that affect the need of refreshing previews.
   * These events are the invocations of [invalidateSavedBuildStatus] and the
   * events captured by the ResourceChangeListener and the BuildListener.
   */
  private val previewFreshnessLock = ReentrantLock()

  @GuardedBy("previewFreshnessLock")
  private var needsRefreshOnSuccessfulBuild = true

  @GuardedBy("previewFreshnessLock")
  private var kotlinJavaModificationCount = -1L
  private val kotlinJavaModificationTracker = PsiModificationTracker.getInstance(module.project).forLanguages { lang ->
    lang.`is`(KotlinLanguage.INSTANCE) || lang.`is`(JavaLanguage.INSTANCE)
  }

  private val resourceChangeListener: ((Set<ResourceNotificationManager.Reason>) -> Unit) = { reasons ->
    // If this listener was triggered by any reason but a project build or a configuration change,
    // then we need to refresh the previews on the next successful build.
    if (reasons.any { it != ResourceNotificationManager.Reason.PROJECT_BUILD && it != ResourceNotificationManager.Reason.CONFIGURATION_CHANGED }) {
      invalidateSavedBuildStatus()
    }
  }

  init {
    // TODO: Remove this code. This is a workaround for setupBuildListener not supporting multiple listeners for the
    //       same parentDisposable
    val buildDisposable = Disposer.newDisposable()
    Disposer.register(parentDisposable, buildDisposable)
    setupBuildListener(module.project, object : BuildListener {
      @GuardedBy("previewFreshnessLock")
      private var pendingBuildsCount = 0

      @GuardedBy("previewFreshnessLock")
      private var someConcurrentBuildFailed = false

      override fun buildStarted() {
        previewFreshnessLock.withLock {
          pendingBuildsCount = pendingBuildsCount.inc()
          // The modification count is updated here and not in 'buildSucceeded' so that the changes
          // made during the build process are not considered to be included in such build
          val newKotlinJavaModificationCount = kotlinJavaModificationTracker.modificationCount
          if (newKotlinJavaModificationCount != kotlinJavaModificationCount) {
            needsRefreshOnSuccessfulBuild = true
            kotlinJavaModificationCount = newKotlinJavaModificationCount
          }
        }
      }

      override fun buildSucceeded() {
        var needsRefresh = false
        previewFreshnessLock.withLock {
          // This build listener could be set in the middle of a build process, what could lead to unintended behaviors.
          // Make sure to avoid problems related to this by keeping pendingBuildsCount non-negative.
          if (pendingBuildsCount > 0) pendingBuildsCount = pendingBuildsCount.dec()
          else log.warn("pendingBuildsCount was $pendingBuildsCount when buildSucceeded")

          if (needsRefreshOnSuccessfulBuild) needsRefresh = true

          if (pendingBuildsCount == 0) {
            // Only reset the need of refreshing the previews when every concurrent build succeeded
            // As it might happen that the need of refresh was set by a build that failed.
            if (!someConcurrentBuildFailed) {
              needsRefreshOnSuccessfulBuild = false
            }
            // As there are no more pending builds, reset the failures flag
            someConcurrentBuildFailed = false
          }
        }

        if (needsRefresh) {
          needsRefreshCallback()
        }
      }

      override fun buildFailed() {
        previewFreshnessLock.withLock {
          // This build listener could be set in the middle of a build process, what could lead to unintended behaviors.
          // Make sure to avoid problems related to this by keeping pendingBuildsCount non-negative.
          if (pendingBuildsCount > 0) pendingBuildsCount = pendingBuildsCount.dec()
          else log.warn("pendingBuildsCount was $pendingBuildsCount when buildFailed")
          // If there are some other concurrent builds happening, set the failures flag to true.
          // Otherwise, reset it.
          someConcurrentBuildFailed = pendingBuildsCount != 0
        }
      }

      override fun buildCleaned() {
        invalidateSavedBuildStatus()
        buildFailed()
      }
    }, parentDisposable = buildDisposable)

    module.androidFacet?.let { facet ->
      // Set a ResourceChangeListener to update the need of refreshing the previews when corresponds
      ResourceNotificationManager
        .getInstance(module.project)
        .addListener(resourceChangeListener, facet, null, null)
      Disposer.register(parentDisposable) {
        ResourceNotificationManager
          .getInstance(module.project)
          .removeListener(resourceChangeListener, facet, null, null)
      }
    } ?: log.error("Couldn't set the ResourceChangeListener, some previews might not be refreshed correctly after successful builds")
  }

  @TestOnly
  override fun needsRefreshOnSuccessfulBuild() = needsRefreshOnSuccessfulBuild

  @TestOnly
  override fun buildWillTriggerRefresh() = needsRefreshOnSuccessfulBuild || kotlinJavaModificationCount != kotlinJavaModificationTracker.modificationCount
  override fun getModificationCount() = kotlinJavaModificationTracker.modificationCount

  override fun invalidateSavedBuildStatus() {
    previewFreshnessLock.withLock {
      needsRefreshOnSuccessfulBuild = true
    }
  }
}