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

import com.google.common.util.concurrent.ListenableFuture
import com.intellij.util.messages.Topic

/**
 * Provides a build-system-agnostic interface for triggering, responding to, and gathering information about project syncs.
 */
interface ProjectSystemSyncManager {
  /**
   * Triggers synchronizing the IDE model with the build system model of the project. Source generation
   * may be triggered regardless of the value of {@code requireSourceGeneration}, though implementing classes may
   * use this flag for optimization.
   *
   * @param reason the caller's reason for requesting a sync
   * @param requireSourceGeneration a hint to the underlying project system to optionally generate sources after a successful sync
   *
   * @return the future result of the sync request
   */
  fun syncProject(reason: ProjectSystemSyncManager.SyncReason, requireSourceGeneration: Boolean = true): ListenableFuture<SyncResult>

  /**
   * Returns whether or not a sync is in progress. The return value of this method can change at any time as syncs are performed.
   * To listen for changes in the return value due to a sync ending, subscribe to [PROJECT_SYSTEM_SYNC_TOPIC].
   *
   * Currently, we do not provide a way for callers to be notified when the return value changes due to a sync starting.
   *
   * @return true if the project is syncing
   */
  fun isSyncInProgress(): Boolean

  /** The result of a sync request */
  enum class SyncResult(val isSuccessful: Boolean) {
    /** The user cancelled the sync */
    CANCELLED(false),
    /** Sync failed */
    FAILURE(false),
    /** The user has compilation errors or errors in build system files */
    PARTIAL_SUCCESS(true),
    /** The project state was loaded from a cache instead of performing an actual sync */
    SKIPPED(true),
    /** Sync succeeded */
    SUCCESS(true);
  }

  /** The requestor's reason for syncing the project */
  enum class SyncReason {
    /** The project is being loaded */
    PROJECT_LOADED,
    /** The project has been modified */
    PROJECT_MODIFIED,
    /** The user requested the sync directly (by pushing the button) */
    USER_REQUEST;
  }

  /** Listener which provides callbacks for the beginning and the end of syncs for an associated project */
  interface SyncResultListener {
    fun syncEnded(result: SyncResult)
  }

}

/** Endpoint for broadcasting changes in global sync status */
@JvmField val PROJECT_SYSTEM_SYNC_TOPIC = Topic<ProjectSystemSyncManager.SyncResultListener>("Project sync",
    ProjectSystemSyncManager.SyncResultListener::class.java)
