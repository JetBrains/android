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
package com.google.idea.blaze.base.sync.actions;

import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.qsync.QuerySyncManager.TaskOrigin;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.sync.BlazeSyncManager;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.Project;
import icons.BlazeIcons;
import javax.annotation.Nullable;
import javax.swing.Icon;

/** Syncs the project with BUILD files. */
public class IncrementalSyncProjectAction extends BlazeProjectSyncAction {

  public static final String ID = "Blaze.IncrementalSyncProject";

  @Override
  protected void runSync(Project project, AnActionEvent e) {
    doIncrementalSync(getClass(), project, e);
  }

  public static void doIncrementalSync(Class<?> klass, Project project, @Nullable AnActionEvent e) {
    if (Blaze.getProjectType(project) == ProjectType.QUERY_SYNC) {
      QuerySyncManager qsm = QuerySyncManager.getInstance(project);
      QuerySyncActionStatsScope scope = QuerySyncActionStatsScope.create(klass, e);
      if (!qsm.isProjectLoaded()) {
        qsm.onStartup(scope);
      } else {
        qsm.deltaSync(scope, TaskOrigin.USER_ACTION);
      }
    } else {
      BlazeSyncManager.getInstance(project)
          .incrementalProjectSync(/* reason= */ "IncrementalSyncProjectAction");
    }
  }

  @Override
  protected void updateForBlazeProject(Project project, AnActionEvent e) {
    updateIcon(e);
  }

  private static void updateIcon(AnActionEvent e) {
    Project project = e.getProject();
    Presentation presentation = e.getPresentation();
    BlazeSyncStatus statusHelper = BlazeSyncStatus.getInstance(project);
    presentation.setEnabled(!statusHelper.syncInProgress());
    if (Blaze.getProjectType(project) == ProjectType.QUERY_SYNC) {
      return;
    }
    BlazeSyncStatus.SyncStatus status = statusHelper.getStatus();
    presentation.setIcon(getIcon(status));

    if (status == BlazeSyncStatus.SyncStatus.DIRTY
        && !BlazeUserSettings.getInstance().getSyncStatusPopupShown()) {
      BlazeUserSettings.getInstance().setSyncStatusPopupShown(true);
      showPopupNotification(project);
    }
  }

  private static Icon getIcon(BlazeSyncStatus.SyncStatus status) {
    switch (status) {
      case FAILED:
        return BlazeIcons.Failed;
      default:
        return BlazeIcons.Logo;
    }
  }

  private static final NotificationGroup NOTIFICATION_GROUP =
      new NotificationGroup("Changes since last blaze sync", NotificationDisplayType.BALLOON, true);

  private static void showPopupNotification(Project project) {
    String message =
        String.format(
            "Some relevant files (e.g. BUILD files, .blazeproject file) "
                + "have changed since the last sync. "
                + "Please press the 'Sync' button in the toolbar to re-sync your %s project.",
            ApplicationNamesInfo.getInstance().getFullProductName());
    Notification notification =
        new Notification(
            NOTIFICATION_GROUP.getDisplayId(),
            String.format("Changes since last %s sync", Blaze.buildSystemName(project)),
            message,
            NotificationType.INFORMATION);
    notification.setImportant(true);
    Notifications.Bus.notify(notification, project);
  }

  @Override
  protected QuerySyncStatus querySyncSupport() {
    return QuerySyncStatus.SUPPORTED;
  }

  @Override
  public ActionUpdateThread getActionUpdateThread() {
    // Not clear what `showPopupNotification` does and why.
    return ActionUpdateThread.EDT;
  }
}
