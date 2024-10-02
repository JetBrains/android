/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync;

import com.google.idea.blaze.base.qsync.settings.QuerySyncConfigurableProvider;
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.sync.actions.IncrementalSyncProjectAction;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.ui.EditorNotifications;
import java.nio.file.Path;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.swing.JComponent;

/**
 * A class which provides an Editor notification for a newly added file that needs to be synced
 * (Query sync specific).
 */
public class UnsyncedFileEditorNotificationProvider implements EditorNotificationProvider {

  private static final BoolExperiment ENABLED =
      new BoolExperiment("qsync.new.file.editor.notification", true);

  /**
   * If true, shows an editor notification for any file in a package whose BUILD file has been
   * modified since the last sync.
   */
  public static final BoolExperiment NOTIFY_ON_BUILD_FILE_CHANGES =
      new BoolExperiment("qsync.package.change.editor.notification", true);

  @Override
  @Nullable
  public Function<? super FileEditor, ? extends JComponent> collectNotificationData(
      Project project, VirtualFile virtualFile) {
    if (!Blaze.getProjectType(project).equals(ProjectType.QUERY_SYNC)) {
      return null;
    }
    if (!ENABLED.getValue()) {
      return null;
    }

    if (QuerySyncManager.getInstance(project).operationInProgress()) {
      return null;
    }

    if (NOTIFY_ON_BUILD_FILE_CHANGES.getValue()
        && QuerySyncManager.getInstance(project).getFileListener().hasModifiedBuildFiles()) {
      return editor ->
          getEditorNotificationPanel(
              project,
              "BUILD files for this project have changed. The project may be out of sync.");
    }

    Path path;
    try {
      path = virtualFile.toNioPath();
    } catch (UnsupportedOperationException e) {
      // Thrown for decompiled library code.
      return null;
    }

    if (!QuerySyncManager.getInstance(project).isProjectFileAddedSinceSync(path).orElse(false)) {
      return null;
    }

    return editor -> getEditorNotificationPanel(project, "This file is not synced");
  }

  private static EditorNotificationPanel getEditorNotificationPanel(Project project, String title) {
    EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText(title);
    panel
        .createActionLabel("Sync now", IncrementalSyncProjectAction.ID)
        .addHyperlinkListener(
            event -> EditorNotifications.getInstance(project).updateAllNotifications());
    panel.createActionLabel(
        "Enable automatic syncing",
        () -> {
          QuerySyncSettings.getInstance().enableSyncOnFileChanges(true);
          showAutoSyncNotification(project);
          IncrementalSyncProjectAction.doIncrementalSync(
              UnsyncedFileEditorNotificationProvider.class, project, null);
          EditorNotifications.getInstance(project).updateAllNotifications();
        });
    return panel;
  }

  private static void showAutoSyncNotification(Project project) {
    Notification notification =
        new Notification(
                QuerySyncManager.NOTIFICATION_GROUP,
                "Automatic syncing enabled",
                "To turn it off again, open query sync settings.",
                NotificationType.INFORMATION)
            .addAction(new ShowSettingsAction("Open settings..."));
    Notifications.Bus.notify(notification, project);
  }

  private static class ShowSettingsAction extends AnAction {

    public ShowSettingsAction(String text) {
      super(text, null, null);
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
      ShowSettingsUtil.getInstance()
          .showSettingsDialog(
              anActionEvent.getProject(), QuerySyncConfigurableProvider.getConfigurableClass());
    }

    @Override
    public ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
  }
}
