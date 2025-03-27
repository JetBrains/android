/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync.action;

import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.qsync.QuerySyncManager.TaskOrigin;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.NotNull;

/** An internal action to reload the querysync project. */
public class ReloadProject extends AnAction {

  @Override
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation p = e.getPresentation();
    if (Blaze.getProjectType(e.getProject()) != ProjectType.QUERY_SYNC) {
      p.setVisible(false);
      return;
    }
    p.setEnabled(!BlazeSyncStatus.getInstance(e.getProject()).syncInProgress());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
    QuerySyncManager.getInstance(anActionEvent.getProject())
        .reloadProject(
            QuerySyncActionStatsScope.create(getClass(), anActionEvent), TaskOrigin.USER_ACTION);
  }
}
