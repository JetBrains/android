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
package com.google.idea.blaze.base.sync.actions;

import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.qsync.QuerySyncManager.TaskOrigin;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.sync.BlazeSyncManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;

/**
 * Performs a full sync. Unlike an incremental sync, this completely wipes all previous project
 * data, starting from scratch.
 *
 * <p>It should only be needed to manually work around bugs in incremental sync.
 */
public class FullSyncProjectAction extends BlazeProjectSyncAction {

  @Override
  protected void runSync(Project project, AnActionEvent e) {
    if (Blaze.getProjectType(project) == ProjectType.QUERY_SYNC) {
      QuerySyncManager.getInstance(project)
          .fullSync(QuerySyncActionStatsScope.create(getClass(), e), TaskOrigin.USER_ACTION);
    } else {
      BlazeSyncManager.getInstance(project).fullProjectSync(/* reason= */ "FullSyncProjectAction");
    }
  }

  @Override
  protected QuerySyncStatus querySyncSupport() {
    return QuerySyncStatus.SUPPORTED;
  }
}
