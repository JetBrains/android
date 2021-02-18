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

import static com.android.tools.idea.gradle.actions.RefreshLinkedCppProjectsAction.REFRESH_EXTERNAL_NATIVE_MODELS_KEY;
import static com.android.utils.BuildScriptUtil.isDefaultGradleBuildFile;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_USER_STALE_CHANGES;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_USER_TRY_AGAIN;
import static com.intellij.openapi.module.ModuleUtilCore.findModuleForFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.ThreeState.YES;

import com.android.annotations.concurrency.AnyThread;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.sync.GradleFiles;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.projectsystem.AndroidProjectSettingsService;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.build.BuildContentManager;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.ThreeState;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import java.awt.Color;
import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Notifies users that a Gradle project "sync" is either being in progress or failed.
 */
public class ProjectSyncStatusNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  private static final long PROJECT_STRUCTURE_NOTIFICATION_RESHOW_TIMEOUT_MS = TimeUnit.DAYS.toMillis(30);

  @NotNull private static final Key<EditorNotificationPanel> KEY = Key.create("android.gradle.sync.status");

  /** The values are disposable notification panels created last for the editors. */
  @NotNull private static final ConcurrentMap<FileEditor, Disposable> ourDisposablePanels = new ConcurrentHashMap<>();
  @NotNull private static final Object ourDisposablePanelRegistrationLock = new Object();

  @NotNull private final GradleProjectInfo myProjectInfo;
  @NotNull private final GradleSyncState mySyncState;

  @SuppressWarnings("unused") // Invoked by IDEA
  public ProjectSyncStatusNotificationProvider(@NotNull Project project) {
    this(GradleProjectInfo.getInstance(project), GradleSyncState.getInstance(project));
  }

  @NonInjectable
  @TestOnly
  public ProjectSyncStatusNotificationProvider(@NotNull GradleProjectInfo projectInfo, @NotNull GradleSyncState syncState) {
    myProjectInfo = projectInfo;
    mySyncState = syncState;
  }

  @Override
  @NotNull
  public final Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @AnyThread
  @Override
  @Nullable
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor editor, @NotNull Project project) {
    NotificationPanel.Type newPanelType = notificationPanelType(project);
    NotificationPanel panel = newPanelType.create(project, file, myProjectInfo);
    // Keep track of the last disposable panel created for the editor to dispose it when a new panel for the same editor is created.
    // We cannot rely on editor.getUserData(KEY) because the panels created by this method are not necessarily stored there.
    registerDisposablePanel(editor, panel);

    return panel;
  }

  private static void registerDisposablePanel(@NotNull FileEditor editor, @Nullable NotificationPanel panel) {
    Disposable newDisposablePanel = panel instanceof Disposable ? (Disposable)panel : null;

    // The synchronized block below is intended to prevent disposal of the panel
    // before it is registered with the Disposer.
    synchronized (ourDisposablePanelRegistrationLock) {
      Disposable oldDisposablePanel =
          newDisposablePanel == null ? ourDisposablePanels.remove(editor) : ourDisposablePanels.put(editor, newDisposablePanel);
      if (oldDisposablePanel != null) {
        Disposer.dispose(oldDisposablePanel);
      }
      if (newDisposablePanel != null) {
        try {
          Disposer.register(newDisposablePanel, () -> ourDisposablePanels.remove(editor, newDisposablePanel));
          Disposer.register(editor, newDisposablePanel);
        }
        catch (Throwable t) {
          Disposer.dispose(newDisposablePanel);
          throw t;
        }
      }
    }
  }

  @NotNull
  private NotificationPanel.Type notificationPanelType(@NotNull Project project) {
    if (IdeInfo.getInstance().isAndroidStudio() || ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) {
      return notificationPanelType();
    } else {
      return NotificationPanel.Type.NONE;
    }
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
        NotificationPanel create(@NotNull Project project, @NotNull VirtualFile file, @NotNull GradleProjectInfo projectInfo) {
          if (!IdeInfo.getInstance().isAndroidStudio()) return null;
          if ((System.currentTimeMillis() -
               Long.parseLong(
                 PropertiesComponent.getInstance().getValue("PROJECT_STRUCTURE_NOTIFICATION_LAST_HIDDEN_TIMESTAMP", "0")) >
               PROJECT_STRUCTURE_NOTIFICATION_RESHOW_TIMEOUT_MS)) {
            if (!projectInfo.isBuildWithGradle()) {
              return null;
            }

            if (!isDefaultGradleBuildFile(virtualToIoFile(file))) {
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
            return new ProjectStructureNotificationPanel(project, this,
                                                         "You can use the Project Structure dialog to view and edit your project configuration",
                                                         module);
          }
          return null;
        }
      },
      IN_PROGRESS() {
        @Override
        @NotNull
        NotificationPanel create(@NotNull Project project, @NotNull VirtualFile file, @NotNull GradleProjectInfo projectInfo) {
          return new NotificationPanel(this, "Gradle project sync in progress...");
        }
      },
      FAILED() {
        @Override
        @NotNull
        NotificationPanel create(@NotNull Project project, @NotNull VirtualFile file, @NotNull GradleProjectInfo projectInfo) {
          String text = "Gradle project sync failed. Basic functionality (e.g. editing, debugging) will not work properly.";
          return new SyncProblemNotificationPanel(project, this, text);
        }
      },
      SYNC_NEEDED() {
        @Override
        @NotNull
        NotificationPanel create(@NotNull Project project, @NotNull VirtualFile file, @NotNull GradleProjectInfo projectInfo) {
          boolean buildFilesModified = GradleFiles.getInstance(project).areExternalBuildFilesModified();
          String text = (buildFilesModified ? "External build files" : "Gradle files") +
                        " have changed since last project sync. A project sync may be necessary for the IDE to work properly.";
          return new StaleGradleModelNotificationPanel(project, this, text);
        }
      };

      @Nullable
      abstract NotificationPanel create(@NotNull Project project, @NotNull VirtualFile file, @NotNull GradleProjectInfo projectInfo);
    }

    @NotNull private final Type type;

    NotificationPanel(@NotNull Type type, @NotNull String text) {
      this.type = type;
      setText(text);
    }
  }

  /** Notification panel which may contain actions which we don't want to be executed during indexing (e.g., retrying sync itself). */
  @VisibleForTesting
  static class IndexingSensitiveNotificationPanel extends NotificationPanel implements Disposable {

    IndexingSensitiveNotificationPanel(@NotNull Project project, @NotNull Type type, @NotNull String text) {
      this(project, type, text, DumbService.getInstance(project));
    }

    @VisibleForTesting
    IndexingSensitiveNotificationPanel(@NotNull Project project,
                                       @NotNull Type type,
                                       @NotNull String text,
                                       @NotNull DumbService dumbService) {
      super(type, text);

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

      // First subscribe, then update visibility.
      setVisible(!dumbService.isDumb());
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
      if (GradleFiles.getInstance(project).areExternalBuildFilesModified()) {
        // Set this to true so that the request sent to gradle daemon contains arg -Pandroid.injected.refresh.external.native.model=true,
        // which would refresh the C++ project. See com.android.tools.idea.gradle.project.sync.common.CommandLineArgs for related logic.
        project.putUserData(REFRESH_EXTERNAL_NATIVE_MODELS_KEY, true);
      }
      createActionLabel("Sync Now",
                        () -> GradleSyncInvoker.getInstance().requestProjectSync(project, TRIGGER_USER_STALE_CHANGES));
      createActionLabel("Ignore these changes", () -> {
        GradleFiles.getInstance(project).removeChangedFiles();
        this.setVisible(false);
      });
    }
  }

  private static class SyncProblemNotificationPanel extends IndexingSensitiveNotificationPanel {
    SyncProblemNotificationPanel(@NotNull Project project, @NotNull Type type, @NotNull String text) {
      super(project, type, text);

      createActionLabel("Try Again",
                        () -> GradleSyncInvoker.getInstance().requestProjectSync(project, TRIGGER_USER_TRY_AGAIN));

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
    ProjectStructureNotificationPanel(@NotNull Project project, @NotNull Type type, @NotNull String text, @NotNull Module module) {
      super(type, text);

      String shortcutText = KeymapUtil.getFirstKeyboardShortcutText("ShowProjectStructureSettings");
      String label = "Open";
      if (shortcutText != "") {
        label += " (" + shortcutText + ")";
      }
      createActionLabel(label, () -> {
        ProjectSettingsService projectSettingsService = ProjectSettingsService.getInstance(project);
        if (projectSettingsService instanceof AndroidProjectSettingsService) {
          projectSettingsService.openModuleSettings(module);
        }
      });
      createActionLabel("Hide notification", () -> {
        PropertiesComponent.getInstance().setValue("PROJECT_STRUCTURE_NOTIFICATION_LAST_HIDDEN_TIMESTAMP",
                                                   Long.toString(System.currentTimeMillis()));
        setVisible(false);
      });
    }

    @Override
    public Color getBackground() {
      Color color = EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.READONLY_BACKGROUND_COLOR);
      return color == null ? UIUtil.getPanelBackground() : color;
    }
  }
}
