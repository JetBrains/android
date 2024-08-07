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
package com.google.idea.blaze.base.sync.actions;

import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.BlazeSyncManager;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.google.idea.common.actions.ActionPresentationHelper;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;

/** A partial (additive) sync which runs no blaze build actions, only updating directories */
public class UpdateDirectoriesSyncAction extends BlazeProjectSyncAction {

  @Override
  protected void runSync(Project project, AnActionEvent e) {
    BlazeSyncManager.getInstance(project)
        .directoryUpdate(/* inBackground= */ false, /* reason= */ "UpdateDirectoriesSyncAction");
  }

  @Override
  protected void updateForBlazeProject(Project project, AnActionEvent e) {
    String text = String.format("Sync Directories (no %s build)", Blaze.buildSystemName(project));
    ActionPresentationHelper.of(e)
        .disableIf(BlazeSyncStatus.getInstance(project).syncInProgress())
        .setText(text)
        .commit();
  }

  @Override
  protected QuerySyncStatus querySyncSupport() {
    return QuerySyncStatus.HIDDEN;
  }
}
