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
package com.google.idea.blaze.base.scope.scopes;

import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.VetoableProjectManagerListener;
import com.intellij.openapi.ui.Messages;

/** Prevents the user from closing the project while the scope is open. */
public class ProjectCloseScope implements VetoableProjectManagerListener, BlazeScope {

  private final Project project;

  private boolean isApplicationExitingOrProjectClosing;

  public ProjectCloseScope(Project project) {
    this.project = project;
  }

  @Override
  public void onScopeBegin(BlazeContext context) {
    ProjectManager projectManager = ProjectManager.getInstance();
    projectManager.addProjectManagerListener(project, this);
  }

  @Override
  public void onScopeEnd(BlazeContext context) {
    ProjectManager projectManager = ProjectManager.getInstance();
    projectManager.removeProjectManagerListener(project, this);
  }

  @Override
  public void projectOpened(Project project) {}

  @Override
  public boolean canClose(Project project) {
    if (!project.equals(this.project)) {
      return true;
    }
    if (shouldPromptUser()) {
      askUserToWait();
      return false;
    }
    return false;
  }

  @Override
  public void projectClosed(Project project) {}

  @Override
  public void projectClosing(Project project) {
    if (project.equals(this.project)) {
      isApplicationExitingOrProjectClosing = true;
    }
  }

  private boolean shouldPromptUser() {
    return !isApplicationExitingOrProjectClosing;
  }

  private void askUserToWait() {
    String buildSystem = Blaze.buildSystemName(project);
    Messages.showMessageDialog(
        project,
        String.format("Please wait until %s command execution finishes", buildSystem),
        buildSystem + " Running",
        null);
  }
}
