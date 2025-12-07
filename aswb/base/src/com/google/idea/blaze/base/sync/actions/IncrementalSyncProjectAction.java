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
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;

/** Syncs the project with BUILD files. */
public class IncrementalSyncProjectAction extends BlazeProjectSyncAction {

  public static final String ID = "Blaze.IncrementalSyncProject";

  @Override
  protected void runSync(Project project, AnActionEvent e) {
    doIncrementalSync(getClass(), project, e);
  }

  public static void doIncrementalSync(Class<?> klass, Project project, @Nullable AnActionEvent e) {
    QuerySyncManager qsm = QuerySyncManager.getInstance(project);
    QuerySyncActionStatsScope scope = QuerySyncActionStatsScope.create(project, klass, e);
    qsm.deltaSync(scope, TaskOrigin.USER_ACTION);
  }

  @Override
  protected void updateForBlazeProject(Project project, AnActionEvent e) {
    updateIcon(e);
  }

  private static void updateIcon(AnActionEvent e) {
    Project project = e.getProject();
    Presentation presentation = e.getPresentation();
    BlazeSyncStatus statusHelper = BlazeSyncStatus.getInstance(project);
    presentation.setEnabled(!statusHelper.syncInProgress());
  }

  @Override
  protected QuerySyncStatus querySyncSupport() {
    return QuerySyncStatus.SUPPORTED;
  }

  @Override
  public ActionUpdateThread getActionUpdateThread() {
    // Not clear what `showPopupNotification` does and why.
    return ActionUpdateThread.EDT;
  }
}
