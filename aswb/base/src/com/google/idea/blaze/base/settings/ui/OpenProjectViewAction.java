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
package com.google.idea.blaze.base.settings.ui;

import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.Optional;

/** Opens the user's local project view file. */
public class OpenProjectViewAction extends BlazeProjectAction implements DumbAware {

  private static final Logger logger = Logger.getInstance(OpenProjectViewAction.class);

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    openLocalProjectViewFile(project);
  }

  /** Opens the user's local project view file. */
  public static void openLocalProjectViewFile(Project project) {
    Optional<VirtualFile> projectViewFile = getLocalProjectViewFile(project);
    if (projectViewFile.isPresent()) {
      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, projectViewFile.get());
      FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    }
  }

  private static Optional<VirtualFile> getLocalProjectViewFile(Project project) {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    File projectViewFile = new File(importSettings.getProjectViewFile());
    VirtualFile virtualFile = VfsUtil.findFileByIoFile(projectViewFile, true);
    if (virtualFile == null) {
      logger.warn("Can't find virtual file for " + importSettings.getProjectViewFile());
    }
    return Optional.ofNullable(virtualFile);
  }

  @Override
  protected QuerySyncStatus querySyncSupport() {
    return QuerySyncStatus.SUPPORTED;
  }
}
