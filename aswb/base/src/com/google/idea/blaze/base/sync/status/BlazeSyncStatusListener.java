/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.status;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.intellij.openapi.project.Project;

/**
 * Application-wide listener for blaze syncs. Notifies per-project status listener when they start
 * and finish.
 */
public class BlazeSyncStatusListener implements SyncListener {

  @Override
  public void onSyncStart(Project project, BlazeContext context, SyncMode syncMode) {
    BlazeSyncStatus.getInstance(project).syncStarted();
  }

  @Override
  public void afterSync(
      Project project,
      BlazeContext context,
      SyncMode syncMode,
      SyncResult syncResult,
      ImmutableSet<Integer> buildIds) {
    BlazeSyncStatus.getInstance(project).syncEnded(syncMode, syncResult);
  }
}
