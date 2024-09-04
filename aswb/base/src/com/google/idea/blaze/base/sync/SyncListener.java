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
package com.google.idea.blaze.base.sync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.SyncScope.SyncCanceledException;
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

/** Extension interface for listening to syncs. */
public interface SyncListener {
  ExtensionPointName<SyncListener> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.SyncListener");

  /** Called after open documents have been saved, prior to starting the blaze sync. */
  default void onSyncStart(Project project, BlazeContext context, SyncMode syncMode)
      throws SyncFailedException, SyncCanceledException {}

  /**
   * Called just prior to starting a blaze build during sync.
   *
   * @param fullProjectSync true if all project targets are being synced.
   * @param buildId a unique ID associated with each sync build. {@link #afterQuerySync} is
   *     guaranteed to be called with this build ID at some point.
   */
  default void buildStarted(
      Project project,
      BlazeContext context,
      boolean fullProjectSync,
      int buildId,
      ImmutableList<TargetExpression> targets) {}

  /** Called on successful (or partially successful) completion of a sync */
  default void onSyncComplete(
      Project project,
      BlazeContext context,
      BlazeImportSettings importSettings,
      ProjectViewSet projectViewSet,
      ImmutableSet<Integer> buildIds,
      BlazeProjectData blazeProjectData,
      SyncMode syncMode,
      SyncResult syncResult) {}

  /** Guaranteed to be called once per sync, regardless of whether it successfully completed */
  default void afterSync(
      Project project,
      BlazeContext context,
      SyncMode syncMode,
      SyncResult syncResult,
      ImmutableSet<Integer> buildIds) {}

  /** Called after sync. Only used in new query-sync * */
  default void afterQuerySync(Project project, BlazeContext context) {}

  /** Called before sync. Only used in new query-sync * */
  default void onQuerySyncStart(Project project, BlazeContext context) {}
}
