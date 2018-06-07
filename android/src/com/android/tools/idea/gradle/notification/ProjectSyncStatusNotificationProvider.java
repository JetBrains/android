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

import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.sync.GradleFiles;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.ThreeState;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_USER_REQUEST;
import static com.intellij.ide.actions.ShowFilePathAction.openFile;
import static com.intellij.openapi.externalSystem.service.notification.NotificationSource.PROJECT_SYNC;
import static com.intellij.util.ThreeState.YES;

/**
 * Notifies users that a Gradle project "sync" is either being in progress or failed.
 */
public class ProjectSyncStatusNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel>
  implements DumbAware {

  @NotNull private static final Key<EditorNotificationPanel> KEY = Key.create("android.gradle.sync.status");

  @NotNull private final Project myProject;
  @NotNull private final GradleProjectInfo myProjectInfo;
  @NotNull private final GradleSyncState mySyncState;

  public ProjectSyncStatusNotificationProvider(@NotNull Project project,
                                               @NotNull GradleProjectInfo projectInfo,
                                               @NotNull GradleSyncState syncState) {
    myProject = project;
    myProjectInfo = projectInfo;
    mySyncState = syncState;
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

    if (oldPanel != null) {
      if (oldPanel.type == newPanelType) {
        return oldPanel;
      }
      if (oldPanel instanceof Disposable) {
        Disposer.dispose((Disposable)oldPanel);
      }
    }

    return newPanelType.create(myProject);
  }

  @VisibleForTesting
  @NotNull
  NotificationPanel.Type notificationPanelType() {
    if (!myProjectInfo.isBuildWithGradle()) {
      return NotificationPanel.Type.NONE;
    }
    if (!mySyncState.areSyncNotificationsEnabled()) {
      return NotificationPanel.Type.NONE;
    }
    if (mySyncState.isSyncInProgress()) {
      return NotificationPanel.Type.IN_PROGRESS;
    }
    if (mySyncState.lastSyncFailed()) {
      return NotificationPanel.Type.FAILED;
    }
    if (mySyncState.getSummary().hasSyncErrors()) {
      return NotificationPanel.Type.ERRORS;
    }

    ThreeState gradleSyncNeeded = mySyncState.isSyncNeeded();
    if (gradleSyncNeeded == YES) {
      return NotificationPanel.Type.SYNC_NEEDED;
    }

    return NotificationPanel.Type.NONE;
  }

  @VisibleForTesting
  static class NotificationPanel extends EditorNotificationPanel {
    enum Type {
      NONE() {
        @Override
        @Nullable
        NotificationPanel create(@NotNull Project project) {
          return null;
        }
      },
      IN_PROGRESS() {
        @Override
        @NotNull
        NotificationPanel create(@NotNull Project project) {
          return new NotificationPanel(this, "Gradle project sync in progress...");
        }
      },
      FAILED() {
        @Override
        @NotNull
        NotificationPanel create(@NotNull Project project) {
          String text = "Gradle project sync failed. Basic functionality (e.g. editing, debugging) will not work properly.";
          return new SyncProblemNotificationPanel(project, this, text);
        }
      },
      ERRORS() {
        @Override
        @NotNull
        NotificationPanel create(@NotNull Project project) {
          String text = "Gradle project sync completed with some errors. Open the 'Messages' view to see the errors found.";
          return new SyncProblemNotificationPanel(project, this, text);
        }
      },
      SYNC_NEEDED() {
        @Override
        @NotNull
        NotificationPanel create(@NotNull Project project) {
          boolean buildFilesModified = GradleFiles.getInstance(project).areExternalBuildFilesModified();
          String text = (buildFilesModified ? "External build files" : "Gradle files") +
                        " have changed since last project sync. A project sync may be necessary for the IDE to work properly.";
          return new StaleGradleModelNotificationPanel(project, this, text);
        }
      },;

      @Nullable
      abstract NotificationPanel create(@NotNull Project project);
    }

    @NotNull private final Type type;

    NotificationPanel(@NotNull Type type, @NotNull String text) {
      this.type = type;
      setText(text);
    }
  }

  // Notification panel which may contain actions which we don't want to be executed during indexing (e.g.,
  // retrying sync itself)
  @VisibleForTesting
  static class IndexingSensitiveNotificationPanel extends NotificationPanel implements Disposable {
    private final DumbService myDumbService;

    IndexingSensitiveNotificationPanel(@NotNull Project project, @NotNull Type type, @NotNull String text) {
      this(project, type, text, DumbService.getInstance(project));
    }

    @VisibleForTesting
    IndexingSensitiveNotificationPanel(@NotNull Project project,
                                       @NotNull Type type,
                                       @NotNull String text,
                                       @NotNull DumbService dumbService) {
      super(type, text);

      myDumbService = dumbService;

      Disposer.register(project, this);
      MessageBusConnection connection = project.getMessageBus().connect(this);
      connection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
        @Override
        public void enteredDumbMode() {
          setVisible(false);
        }

        @Override
        public void exitDumbMode() {
          setVisible(true);
        }
      });

      // First subscribe, then update visibility
      setVisible(!myDumbService.isDumb());
    }

    @Override
    public void dispose() {
      // Empty - we have nothing to dispose explicitly but this class has to be Disposable in order for the child
      // message bus connection to get disposed once the panel is no longer needed
    }
  }

  private static class StaleGradleModelNotificationPanel extends IndexingSensitiveNotificationPanel {
    StaleGradleModelNotificationPanel(@NotNull Project project, @NotNull Type type, @NotNull String text) {
      super(project, type, text);

      createActionLabel("Sync Now",
                        () -> GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(project, TRIGGER_USER_REQUEST));
    }
  }

  private static class SyncProblemNotificationPanel extends IndexingSensitiveNotificationPanel {
    SyncProblemNotificationPanel(@NotNull Project project, @NotNull Type type, @NotNull String text) {
      super(project, type, text);

      createActionLabel("Try Again",
                        () -> GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(project, TRIGGER_USER_REQUEST));

      createActionLabel("Open 'Messages' View",
                        () -> ExternalSystemNotificationManager.getInstance(project).openMessageView(GRADLE_SYSTEM_ID, PROJECT_SYNC));

      createActionLabel("Show Log in " + ShowFilePathAction.getFileManagerName(), () -> {
        File logFile = new File(PathManager.getLogPath(), "idea.log");
        openFile(logFile);
      });
    }
  }
}
