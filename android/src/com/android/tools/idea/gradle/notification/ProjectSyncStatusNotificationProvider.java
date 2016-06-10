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
package com.android.tools.idea.gradle.notification;

import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.android.tools.idea.gradle.GradleSyncState.PROJECT_EXTERNAL_BUILD_FILES_CHANGED;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.Projects.*;
import static com.intellij.ide.actions.ShowFilePathAction.openFile;
import static com.intellij.openapi.externalSystem.service.notification.NotificationSource.PROJECT_SYNC;

/**
 * Notifies users that a Gradle project "sync" is either being in progress or failed.
 */
public class ProjectSyncStatusNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> KEY = Key.create("android.gradle.sync.status");

  @NotNull private final Project myProject;

  public ProjectSyncStatusNotificationProvider(@NotNull Project project) {
    myProject = project;
  }

  @Override
  @NotNull
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Override
  @Nullable
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    NotificationPanel oldPanel = (NotificationPanel)fileEditor.getUserData(getKey());
    NotificationPanel.Type newPanelType = notificationPanelType();
    return (oldPanel != null && oldPanel.type == newPanelType) ? oldPanel : newPanelType.create(myProject);
  }

  @NotNull
  private NotificationPanel.Type notificationPanelType() {
    if (!isBuildWithGradle(myProject)) {
      return NotificationPanel.Type.NONE;
    }
    GradleSyncState syncState = GradleSyncState.getInstance(myProject);
    if (!syncState.areSyncNotificationsEnabled()) {
      return NotificationPanel.Type.NONE;
    }
    if (syncState.isSyncInProgress()) {
      return NotificationPanel.Type.IN_PROGRESS;
    }
    if (lastGradleSyncFailed(myProject)) {
      return NotificationPanel.Type.FAILED;
    }
    if (hasErrors(myProject)) {
      return NotificationPanel.Type.ERRORS;
    }

    ThreeState gradleSyncNeeded = syncState.isSyncNeeded();
    if (gradleSyncNeeded == ThreeState.YES) {
      return NotificationPanel.Type.SYNC_NEEDED;
    }

    return NotificationPanel.Type.NONE;
  }

  private static class NotificationPanel extends EditorNotificationPanel {
    enum Type {
      NONE() {
        @Nullable
        @Override
        NotificationPanel create(@NotNull Project project) {
          return null;
        }
      },
      IN_PROGRESS() {
        @NotNull
        @Override
        NotificationPanel create(@NotNull Project project) {
          return new NotificationPanel(this, "Gradle project sync in progress...");
        }
      },
      FAILED() {
        @NotNull
        @Override
        NotificationPanel create(@NotNull Project project) {
          return new SyncProblemNotificationPanel(
            project, this, "Gradle project sync failed. Basic functionality (e.g. editing, debugging) will not work properly.");
        }
      },
      ERRORS() {
        @NotNull
        @Override
        NotificationPanel create(@NotNull Project project) {
          return new SyncProblemNotificationPanel(
            project, this, "Gradle project sync completed with some errors. Open the 'Messages' view to see the errors found.");
        }
      },
      SYNC_NEEDED() {
        @NotNull
        @Override
        NotificationPanel create(@NotNull Project project) {
          boolean areExternalBuildFilesChanged = PROJECT_EXTERNAL_BUILD_FILES_CHANGED.get(project, false);
          String buildFiles = areExternalBuildFilesChanged ? "External build files" : "Gradle files";

          return new StaleGradleModelNotificationPanel(
            project, this,
            buildFiles + " have changed since last project sync. A project sync may be necessary for the IDE to work properly.");
        }
      },
      ;

      @Nullable
      abstract NotificationPanel create(@NotNull Project project);
    }

    @NotNull private final Type type;

    NotificationPanel(@NotNull Type type, @NotNull String text) {
      this.type = type;
      setText(text);
    }
  }

  private static class StaleGradleModelNotificationPanel extends NotificationPanel {
    StaleGradleModelNotificationPanel(@NotNull Project project, @NotNull Type type, @NotNull String text) {
      super(type, text);

      createActionLabel("Sync Now", () -> {
        GradleProjectImporter.getInstance().requestProjectSync(project, null);
      });
    }
  }

  private static class SyncProblemNotificationPanel extends NotificationPanel {
    SyncProblemNotificationPanel(@NotNull Project project, @NotNull Type type, @NotNull String text) {
      super(type, text);

      createActionLabel("Try Again", () -> {
        GradleProjectImporter.getInstance().requestProjectSync(project, null);
      });

      createActionLabel("Open 'Messages' View", () -> {
        ExternalSystemNotificationManager.getInstance(project).openMessageView(GRADLE_SYSTEM_ID, PROJECT_SYNC);
      });

      createActionLabel("Show Log in " + ShowFilePathAction.getFileManagerName(), () -> {
        File logFile = new File(PathManager.getLogPath(), "idea.log");
        openFile(logFile);
      });
    }
  }
}
