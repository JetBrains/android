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

import static com.android.utils.BuildScriptUtil.isDefaultGradleBuildFile;
import static com.android.utils.BuildScriptUtil.isGradleSettingsFile;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_USER_STALE_CHANGES;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_USER_TRY_AGAIN;
import static com.intellij.openapi.module.ModuleUtilCore.findModuleForFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.ThreeState.YES;

import com.android.annotations.concurrency.AnyThread;
import com.android.tools.idea.actions.HideAndroidBannerAction;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncNeededReason;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.GradleSyncStateHolder;
import com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolverKeys;
import com.android.tools.idea.projectsystem.AndroidProjectSettingsService;
import com.android.tools.idea.projectsystem.AndroidProjectSystem;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.build.BuildContentManager;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.util.ui.UIUtil;
import java.awt.Color;
import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Notifies users that a Gradle project "sync" is required (because of changes to build files, or because the last attempt failed) or
 * in progress; if no sync is required or active, displays hints and/or diagnostics about editing the Project Structure.
 */
public class ProjectSyncStatusNotificationProvider implements DumbAware, EditorNotificationProvider {
  @NotNull private final AndroidProjectSystem myProjectSystem;
  @NotNull private final GradleSyncState mySyncState;
  private static final long HIDE_ACTION_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(30);

  @SuppressWarnings("unused") // Invoked by IDEA
  public ProjectSyncStatusNotificationProvider(@NotNull Project project) {
    this(ProjectSystemUtil.getProjectSystem(project), GradleSyncState.getInstance(project));
  }

  @NonInjectable
  public ProjectSyncStatusNotificationProvider(@NotNull AndroidProjectSystem projectSystem, @NotNull GradleSyncState syncState) {
    myProjectSystem = projectSystem;
    mySyncState = syncState;
  }

  @AnyThread
  @Override
  @Nullable
  public Function<? super FileEditor, ? extends JComponent> collectNotificationData(@NotNull Project project, @NotNull VirtualFile file) {
    NotificationPanel.Type newPanelType = notificationPanelType();
    return newPanelType.getProvider(project, file, mySyncState.getSyncNeededReason());
  }

  @VisibleForTesting
  @NotNull
  NotificationPanel.Type notificationPanelType() {
    if (!(myProjectSystem instanceof GradleProjectSystem) || shouldHideBanner()) {
      return NotificationPanel.Type.NONE;
    }
    if (mySyncState.isSyncInProgress()) {
      return NotificationPanel.Type.IN_PROGRESS;
    }
    if (mySyncState.lastSyncFailed()) {
      return NotificationPanel.Type.FAILED;
    }
    if (mySyncState.isSyncNeeded() == YES) {
      return NotificationPanel.Type.SYNC_NEEDED;
    }

    return NotificationPanel.Type.PROJECT_STRUCTURE;
  }

  /**
   * This method checks if the action to hide notification was triggered recently in {@link HideAndroidBannerAction}.
   * For multiple banners open in split windows, this method helps check the hidden timestamp so that all
   * banners are dismissed when `updateAllNotifications` is called.
   */
  static boolean shouldHideBanner() {
    long now = System.currentTimeMillis();
    String lastHiddenValue = PropertiesComponent.getInstance().getValue("PROJECT_STRUCTURE_NOTIFICATION_HIDE_ACTION_TIMESTAMP", "0");
    long lastHidden = Long.parseLong(lastHiddenValue);
    return (now - lastHidden) < HIDE_ACTION_TIMEOUT_MS;
  }

  @VisibleForTesting
  static class NotificationPanel extends EditorNotificationPanel {
    enum Type {
      NONE() {
        @Override
        @Nullable
        Function<? super FileEditor, NotificationPanel> getProvider(@NotNull Project project,
                                                                    @NotNull VirtualFile file,
                                                                    @Nullable GradleSyncNeededReason gradleSyncReason) {
          return null;
        }
      }, PROJECT_STRUCTURE() {
        @Override
        @Nullable
        Function<? super FileEditor, NotificationPanel> getProvider(@NotNull Project project,
                                                                    @NotNull VirtualFile file,
                                                                    @Nullable GradleSyncNeededReason gradleSyncReason) {
          if (ProjectStructureNotificationPanel.userAllowsShow()) {
            File ioFile = virtualToIoFile(file);
            if (!isDefaultGradleBuildFile(ioFile) && !isGradleSettingsFile(ioFile)) {
              return null;
            }

            Module module = findModuleForFile(file, project);
            if (module == null) {
              if (ApplicationManager.getApplication().isUnitTestMode()) {
                module = ModuleManager.getInstance(project).getModules()[0]; // arbitrary module
              }
              else {
                return null;
              }
            }
            Module finalModule = module;
            return (editor) -> new ProjectStructureNotificationPanel(project, finalModule);
          }
          return null;
        }
      }, IN_PROGRESS() {
        @Override
        @NotNull
        Function<? super FileEditor, NotificationPanel> getProvider(@NotNull Project project,
                                                                    @NotNull VirtualFile file,
                                                                    @Nullable GradleSyncNeededReason gradleSyncReason) {
          return (editor) -> new NotificationPanel("Gradle project sync in progress...");
        }
      }, FAILED() {
        @Override
        @NotNull
        Function<? super FileEditor, NotificationPanel> getProvider(@NotNull Project project,
                                                                    @NotNull VirtualFile file,
                                                                    @Nullable GradleSyncNeededReason gradleSyncReason) {
          String text = "Gradle project sync failed. Basic functionality (e.g. editing, debugging) will not work properly.";
          return (editor) -> new SyncProblemNotificationPanel(project, text);
        }
      }, SYNC_NEEDED() {
        @Override
        @Nullable
        Function<? super FileEditor, NotificationPanel> getProvider(@NotNull Project project,
                                                                    @NotNull VirtualFile file,
                                                                    @Nullable GradleSyncNeededReason gradleSyncReason) {
          if (gradleSyncReason == null) return null;

          String text = null;
          switch (gradleSyncReason) {
            case GRADLE_JVM_CONFIG_CHANGED ->
              text = "Gradle JDK configuration has changed. A project sync may be necessary for the IDE to apply those changes.";
            case GRADLE_BUILD_FILES_CHANGED ->
              text = "Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.";
            case EXTERNAL_BUILD_FILES_CHANGED -> {
              // Set this to true so that the request sent to gradle daemon contains arg -Pandroid.injected.refresh.external.native.model=true,
              // which would refresh the C++ project. See com.android.tools.idea.gradle.project.sync.common.CommandLineArgs for related logic.
              project.putUserData(AndroidGradleProjectResolverKeys.REFRESH_EXTERNAL_NATIVE_MODELS_KEY, true);

              text = "External build files have changed since last project sync. A project sync may be necessary for the IDE to work properly.";
            }
          }
          String finalText = text;
          return (editor) -> new StaleGradleModelNotificationPanel(project, finalText);
        }
      };

      @Nullable
      abstract Function<? super FileEditor, NotificationPanel> getProvider(@NotNull Project project,
                                                                           @NotNull VirtualFile file,
                                                                           @Nullable GradleSyncNeededReason gradleSyncReason);
    }

    NotificationPanel(@NotNull String text) {
      super((Color)null, Status.Info);
      setText(text);
    }
  }

  @VisibleForTesting
  static class StaleGradleModelNotificationPanel extends NotificationPanel {
    StaleGradleModelNotificationPanel(@NotNull Project project, @NotNull String text) {
      super(text);
      createActionLabel("Sync Now", () -> GradleSyncInvoker.getInstance()
        .requestProjectSync(project, new GradleSyncInvoker.Request(TRIGGER_USER_STALE_CHANGES), null));
      createActionLabel("Ignore these changes", () -> {
        GradleSyncStateHolder.getInstance(project).ignoreChangedFiles();
        this.setVisible(false);
      });
    }
  }

  @VisibleForTesting
  static class SyncProblemNotificationPanel extends NotificationPanel {
    SyncProblemNotificationPanel(@NotNull Project project, @NotNull String text) {
      super(text);

      createActionLabel("Try Again", () -> GradleSyncInvoker.getInstance()
        .requestProjectSync(project, new GradleSyncInvoker.Request(TRIGGER_USER_TRY_AGAIN), null));

      createActionLabel("Open 'Build' View", () -> {
        ToolWindow tw = BuildContentManager.getInstance(project).getOrCreateToolWindow();
        if (tw != null && !tw.isActive()) {
          tw.activate(null, false);
        }
      });

      createActionLabel("Show Log in " + RevealFileAction.getFileManagerName(), () -> {
        File logFile = new File(PathManager.getLogPath(), "idea.log");
        RevealFileAction.openFile(logFile);
      });
    }
  }

  @VisibleForTesting
  static class ProjectStructureNotificationPanel extends NotificationPanel {
    private static final String TEXT = "You can use the Project Structure dialog to view and edit your project configuration";
    private static final long RESHOW_TIMEOUT_MS = TimeUnit.DAYS.toMillis(30);

    ProjectStructureNotificationPanel(@NotNull Project project, @NotNull Module module) {
      super(TEXT);

      String shortcutText = KeymapUtil.getFirstKeyboardShortcutText("ShowProjectStructureSettings");
      String label = "Open";
      if (!"".equals(shortcutText)) {
        label += " (" + shortcutText + ")";
      }
      createActionLabel(label, () -> {
        ProjectSettingsService projectSettingsService = ProjectSettingsService.getInstance(project);
        if (projectSettingsService instanceof AndroidProjectSettingsService) {
          projectSettingsService.openModuleSettings(module);
        }
      });
      createActionLabel("Hide notification", () -> {
        PropertiesComponent.getInstance()
          .setValue("PROJECT_STRUCTURE_NOTIFICATION_LAST_HIDDEN_TIMESTAMP", Long.toString(System.currentTimeMillis()));
        setVisible(false);
      });
    }

    @NotNull
    @Override
    public Color getFallbackBackgroundColor() {
      return UIUtil.getPanelBackground();
    }

    static boolean userAllowsShow() {
      long now = System.currentTimeMillis();
      String lastHiddenValue = PropertiesComponent.getInstance().getValue("PROJECT_STRUCTURE_NOTIFICATION_LAST_HIDDEN_TIMESTAMP", "0");
      long lastHidden = Long.parseLong(lastHiddenValue);
      return (now - lastHidden) > RESHOW_TIMEOUT_MS;
    }
  }
}
