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
@file:JvmName("ProjectSystemSyncUtil")

package com.android.tools.idea.projectsystem

import com.android.annotations.concurrency.AnyThread
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResultListener
import com.google.common.util.concurrent.ListenableFuture
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger
import com.intellij.util.messages.Topic

/**
 * Provides a build-system-agnostic interface for triggering, responding to, and gathering information about project syncs.
 */
interface ProjectSystemSyncManager {
  /**
   * Requests synchronizing the IDE model with the build system model of the project. Source generation
   * may be triggered when sync completes. If source generation is triggered, the future result of the sync request will
   * not be set until after source generation completes.
   *
   * @param reason the caller's reason for requesting a sync
   *
   * @return the future result of the sync request. The future will complete with a [SyncResult] value describing the
   * outcome. Note that cancellations, whether initiated manually or by user preferences, are considered normal outcomes
   * and will not result in an exception. However, the [ListenableFuture] may fail with an exception in case
   * of unexpected problems.
   */
  fun requestSyncProject(reason: ProjectSystemSyncManager.SyncReason): ListenableFuture<SyncResult>

  /**
   * Replaced by requestSyncProject
   *
   * Kept for runtime compatibility with the Gradle profiler
   */
  @Deprecated("Replaced by requestSyncProject", replaceWith = ReplaceWith("requestSyncProject(reason)"), level = DeprecationLevel.HIDDEN)
  fun syncProject(reason: ProjectSystemSyncManager.SyncReason): ListenableFuture<SyncResult> = requestSyncProject(reason)

  /**
   * Returns whether or not a sync is in progress. The return value of this method can change at any time as syncs are performed.
   * To listen for changes in the return value due to a sync ending, subscribe to [PROJECT_SYSTEM_SYNC_TOPIC].
   *
   * Currently, we do not provide a way for callers to be notified when the return value changes due to a sync starting.
   *
   * @return true if the project is syncing
   */
  fun isSyncInProgress(): Boolean

  /**
   * Indicates whether or not a project sync is needed. Generally, a sync is needed when build system files have been modified
   * since the last sync began.
   *
   * The return value of this method can change at any time as syncs are performed and build files are updated. Currently, we do not
   * provide a way for callers to be notified when the return value becomes stale.
   *
   * @return true if the project needs to be synced
   */
  fun isSyncNeeded(): Boolean

  /**
   * Returns the result of the last completed project sync. If for some reason the [ProjectSystemSyncManager] does not know about the result
   * of the most recently completed sync (e.g. the project has never been synced), this method will return [SyncResult.UNKNOWN].
   *
   * The return value of this method can change at any time as project syncs are completed. Callers who wish to be notified when the
   * return value of this method has become stale should listen for sync results by subscribing to [PROJECT_SYSTEM_SYNC_TOPIC] instead
   * of calling this method.
   *
   * @return the result of the last completed sync, or [SyncResult.UNKNOWN] if this information is unavailable
   */
  fun getLastSyncResult(): SyncResult

  /** The result of a sync request */
  enum class SyncResult(val isSuccessful: Boolean) {
    /** The result of the latest sync could not be determined */
    UNKNOWN(false),
    /** The user cancelled the sync */
    CANCELLED(false),
    /** Sync was not requested because of Auto-Sync being disabled. */
    SKIPPED_DUE_TO_AUTO_SYNC_DISABLED(false),
    /** Sync failed */
    FAILURE(false),
    /** The user has compilation errors or errors in build system files */
    PARTIAL_SUCCESS(true),
    /**
     * The project state was loaded from the cached result of the last successful sync, but may not reflect the current state of the project
     * (e.g. the initial project sync was skipped without checking to see if the cached state was valid).
     */
    SKIPPED_OUT_OF_DATE(true),
    /** The project state was loaded from a cache instead of performing an actual sync */
    SKIPPED(true),
    /** Sync succeeded */
    SUCCESS(true);
  }

  /** The requestor's reason for syncing the project */
  data class SyncReason(val forStats: Trigger) {
    companion object {
      /** The project is being loaded */
      @JvmField
      val PROJECT_LOADED = SyncReason(Trigger.TRIGGER_PROJECT_REOPEN)
      /** The project has been modified */
      @JvmField
      val PROJECT_MODIFIED = SyncReason(Trigger.TRIGGER_PROJECT_MODIFIED)
      /** The project has been modified (dependency updated) */
      @JvmField
      val PROJECT_DEPENDENCY_UPDATED = SyncReason(Trigger.TRIGGER_GRADLEDEPENDENCY_UPDATED)
      /** The user requested the sync directly (by pushing the button) */
      @JvmField
      val USER_REQUEST = SyncReason(Trigger.TRIGGER_USER_REQUEST)
    }
  }

  /** Listener which provides a callback for when syncs complete */
  fun interface SyncResultListener {
    @AnyThread
    fun syncEnded(result: SyncResult)
  }
}

/** Endpoint for broadcasting changes in global sync status */
@JvmField val PROJECT_SYSTEM_SYNC_TOPIC = Topic<SyncResultListener>("Project sync", SyncResultListener::class.java)

fun Trigger.toReason(): ProjectSystemSyncManager.SyncReason = ProjectSystemSyncManager.SyncReason(this)