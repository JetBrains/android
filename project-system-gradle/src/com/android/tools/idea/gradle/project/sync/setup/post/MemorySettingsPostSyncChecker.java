/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.post;

import com.android.tools.idea.memorysettings.MemorySettingsRecommendation;
import com.android.tools.idea.memorysettings.MemorySettingsUtil;
import com.google.wireless.android.sdk.stats.MemorySettingsEvent.EventKind;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.NotificationsManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.util.system.CpuArch;
import java.util.Locale;
import org.jetbrains.android.util.AndroidBundle;

/**
 * A class to check memory settings after project sync. It may recommend new
 * memory settings based on available RAM of the machine and the project size.
 */
public class MemorySettingsPostSyncChecker {
  private static final Logger LOG = Logger.getInstance(MemorySettingsPostSyncChecker.class);
  private static final NotificationGroup NOTIFICATION_GROUP = new NotificationGroup(
    "Memory Settings Notification",
    NotificationDisplayType.STICKY_BALLOON,
    true,
    null,
    null,
    null,
    PluginId.getId("org.jetbrains.android"));

  /**
   * Checks memory settings and shows a notification if new memory settings
   * are recommended: users may save the recommended settings and restart,
   * or go to memory settings configuration dialog to configure themselves,
   * or set a reminder in a day, or completely ignore the notification.
   */
  public static void checkSettings(
    Project project,
    TimeBasedReminder reminder) {
    LOG.info(String.format(Locale.US, "64bits? : %b, current: %d, available RAM: %d",
                           !CpuArch.is32Bit(),
                           MemorySettingsUtil.getCurrentXmx(),
                           MemorySettingsUtil.getMachineMem()));
    if (!MemorySettingsUtil.memorySettingsEnabled()
        || !reminder.shouldAsk()
        || hasNotification(project)) {
      LOG.info("Skipped checking memory settings");
      return;
    }

    int currentXmx = MemorySettingsUtil.getCurrentXmx();
    int recommended = MemorySettingsRecommendation.getRecommended(project, currentXmx);
    if (recommended > 0) {
      showNotification(project, currentXmx, recommended, reminder);
    }
  }

  private static boolean hasNotification(Project project) {
    return NotificationsManager
             .getNotificationsManager()
             .getNotificationsOfType(MemorySettingsNotification.class, project)
             .length > 0;
  }

  private static void showNotification(
    Project project,
    int currentXmx,
    int recommended,
    TimeBasedReminder reminder) {
    log(EventKind.SHOW_RECOMMENDATION, currentXmx, recommended);
    reminder.updateLastTimestamp();

    Notification notification = new MemorySettingsNotification(
      AndroidBundle.message("memory.settings.postsync.message",
                            String.valueOf(currentXmx),
                            String.valueOf(recommended)));
    notification.setTitle(AndroidBundle.message("memory.settings.postsync.title", ApplicationNamesInfo.getInstance().getFullProductName()));
    notification.addAction(NotificationAction.createSimpleExpiring(AndroidBundle.message("memory.settings.postsync.save"), () -> {
      log(EventKind.SAVE_AND_RESTART, currentXmx, recommended);
      MemorySettingsUtil.saveXmx(recommended);
      ApplicationManagerEx.getApplicationEx().restart(true);
    }));
    notification.addAction(NotificationAction.createSimpleExpiring(AndroidBundle.message("memory.settings.postsync.configure"), () -> {
      log(EventKind.CONFIGURE, currentXmx, recommended);
      ShowSettingsUtilImpl.showSettingsDialog(project, "memory.settings", "");
      reminder.setDoNotAskForProject(true);
    }));
    notification.addAction(NotificationAction.createSimpleExpiring(AndroidBundle.message("memory.settings.postsync.do.not.ask.for.project"), () -> {
      log(EventKind.DO_NOT_ASK, currentXmx, recommended);
      reminder.setDoNotAskForProject(true);
    }));
    notification.addAction(NotificationAction.createSimpleExpiring(AndroidBundle.message("memory.settings.postsync.do.not.show.again"), () -> {
      log(EventKind.DO_NOT_ASK, currentXmx, recommended);
      reminder.setDoNotAskForApplication(true);
    }));

    notification.notify(project);
  }

  private static void log(EventKind kind, int currentXmx, int recommended) {
    MemorySettingsUtil.log(kind, currentXmx, -1, -1,
                           recommended, -1, -1,
                           -1, -1, -1);
  }

  static class MemorySettingsNotification extends Notification {
    public MemorySettingsNotification(String content) {
      super(NOTIFICATION_GROUP.getDisplayId(), "Memory Settings", content, NotificationType.INFORMATION);
    }
  }
}
