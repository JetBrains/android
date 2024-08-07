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
package com.google.idea.blaze.base.run.exporter;

import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.common.actions.ActionPresentationHelper;
import com.intellij.execution.RunManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

/**
 * Export selected run configurations to file, so they can be checked in and shared between users.
 */
public class ExportRunConfigurationAction extends BlazeProjectAction implements DumbAware {

  @Override
  protected QuerySyncStatus querySyncSupport() {
    return QuerySyncStatus.SUPPORTED;
  }

  @Override
  protected void updateForBlazeProject(Project project, AnActionEvent e) {
    ActionPresentationHelper.of(e)
        .disableIf(RunManager.getInstance(project).getAllConfigurationsList().isEmpty())
        .commit();
  }

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    new ExportRunConfigurationDialog(project).show();
  }
}
