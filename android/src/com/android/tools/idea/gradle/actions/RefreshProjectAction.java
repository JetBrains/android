/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.actions;

import com.android.tools.idea.gradle.project.sync.ng.GradleProjectRefresh;
import com.android.tools.idea.gradle.project.sync.ng.NewGradleSync;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.RESOLVE_PROJECT;
import static org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID;

/**
 * The action button to refresh Gradle projects in Gradle View Toolbar.
 */
public class RefreshProjectAction extends AndroidStudioGradleAction {
  public RefreshProjectAction() {
    super("Refresh all Gradle projects");
  }

  @Override
  protected void doUpdate(@NotNull AnActionEvent e, @NotNull Project project) {
    // Disable refresh button if there're any running RESOLVE_PROJECT tasks.
    ExternalSystemProcessingManager processingManager = ServiceManager.getService(ExternalSystemProcessingManager.class);
    e.getPresentation().setEnabled(!processingManager.hasTaskOfTypeInProgress(RESOLVE_PROJECT, project));
  }

  @Override
  protected void doPerform(@NotNull AnActionEvent e, @NotNull Project project) {
    // We save all documents because there is a possible case that there is an external system config file changed inside the ide.
    FileDocumentManager.getInstance().saveAllDocuments();
    boolean useNewGradleSync = NewGradleSync.isEnabled();
    if (useNewGradleSync) {
      GradleProjectRefresh projectRefresh = new GradleProjectRefresh(project);
      projectRefresh.refresh();
    }
    else {
      ExternalSystemUtil.refreshProjects(project, SYSTEM_ID, true);
    }
  }
}
