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

import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

/** Base class for sync actions. */
public abstract class BlazeProjectSyncAction extends BlazeProjectAction implements DumbAware {

  protected abstract void runSync(Project project, AnActionEvent e);

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    if (!BlazeSyncStatus.getInstance(project).syncInProgress()) {
      runSync(project, e);
    }
    updateStatus(project, e);
  }

  @Override
  protected void updateForBlazeProject(Project project, AnActionEvent e) {
    updateStatus(project, e);
  }

  private static void updateStatus(Project project, AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabled(!BlazeSyncStatus.getInstance(project).syncInProgress());
  }
}
