/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.editors;

import com.android.tools.idea.gradle.GradleImportNotificationListener;
import com.android.tools.idea.gradle.util.Projects;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Notifies users that a Gradle project "import" (or "sync") is either being in progress or failed.
 */
public class ProjectImportStatusNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> KEY = Key.create("android.gradle.sync.status");

  @NotNull private final Project myProject;

  public ProjectImportStatusNotificationProvider(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(VirtualFile file, FileEditor fileEditor) {
    if (GradleImportNotificationListener.isProjectImportInProgress()) {
      return new ProjectImportInProgressNotificationPanel();
    }
    if (Projects.lastGradleSyncFailed(myProject)) {
      return new ProjectImportFailedNotificationPanel();
    }
    return null;
  }

  private static class ProjectImportInProgressNotificationPanel extends EditorNotificationPanel {
    ProjectImportInProgressNotificationPanel() {
      setText("Gradle project import in progress.");
    }
  }

  private class ProjectImportFailedNotificationPanel extends EditorNotificationPanel {
    ProjectImportFailedNotificationPanel() {
      setText("Gradle project sync failed. Basic functionality (e.g. editing, debugging) will not work properly.");

      createActionLabel("Open Event Log", new Runnable() {
        @Override
        public void run() {
          ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow("Event Log");
          if (window != null) {
            window.activate(null, false);
          }
        }
      });

      createActionLabel("Show Log in " + ShowFilePathAction.getFileManagerName(), new Runnable() {
        @Override
        public void run() {
          File logFile = new File(PathManager.getLogPath(), "idea.log");
          ShowFilePathAction.openFile(logFile);
        }
      });
    }
  }
}
