/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.projectsystem;

import static com.android.tools.idea.projectsystem.ProjectSystemSyncUtil.PROJECT_SYSTEM_SYNC_TOPIC;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Contract;

/** Blaze implementation of {@link ProjectSystemSyncManager} */
public class BlazeProjectSystemSyncManager implements ProjectSystemSyncManager {
  private final Project project;

  BlazeProjectSystemSyncManager(Project project) {
    this.project = project;
  }

  @Override
  public boolean isSyncInProgress() {
    return BlazeSyncStatus.getInstance(project).syncInProgress();
  }

  @Override
  public boolean isSyncNeeded() {
    return BlazeSyncStatus.getInstance(project).isDirty();
  }

  @Override
  public SyncResult getLastSyncResult() {
    return LastSyncResultCache.getInstance(project).lastSyncResult;
  }

  @Override
  public ListenableFuture<SyncResult> requestSyncProject(ProjectSystemSyncManager.SyncReason reason) {
    return Futures.immediateFuture(SyncResult.UNKNOWN);
  }

  @Contract(pure = true)
  private static SyncResult convertToProjectSystemSyncResult(
      com.google.idea.blaze.base.sync.SyncResult syncResult) {
    switch (syncResult) {
      case SUCCESS:
        return SyncResult.SUCCESS;
      case PARTIAL_SUCCESS:
        return SyncResult.PARTIAL_SUCCESS;
      case CANCELLED:
        return SyncResult.CANCELLED;
      case FAILURE:
        return SyncResult.FAILURE;
    }
    throw new RuntimeException(
        "No ProjectSystemSyncManager.SyncResult equivalent for " + syncResult);
  }

  /**
   * Listens for sync status changes and broadcasts them on the message bus of the project that was
   * synced.
   */
  @VisibleForTesting
  static class SyncStatusPublisher implements SyncListener {

    /** Called after sync. Only used in new query-sync * */
    @Override
    public void afterQuerySync(Project project, BlazeContext context) {
      LastSyncResultCache lastSyncResultCache = LastSyncResultCache.getInstance(project);

      if (context.isCancelled()) {
        lastSyncResultCache.lastSyncResult = SyncResult.CANCELLED;
      } else if (context.hasErrors()) {
        lastSyncResultCache.lastSyncResult = SyncResult.FAILURE;
      } else {
        lastSyncResultCache.lastSyncResult = SyncResult.SUCCESS;
      }

      project
          .getMessageBus()
          .syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC)
          .syncEnded(lastSyncResultCache.lastSyncResult);
    }
  }

  @VisibleForTesting
  static class LastSyncResultCache {
    SyncResult lastSyncResult = SyncResult.UNKNOWN;

    public static LastSyncResultCache getInstance(Project project) {
      return project.getService(LastSyncResultCache.class);
    }

    /**
     * For testing only. Makes it possible to clear records of previous sync results between tests
     * so that sync results from one test don't affect other tests.
     */
    public void reset() {
      lastSyncResult = SyncResult.UNKNOWN;
    }
  }
}
