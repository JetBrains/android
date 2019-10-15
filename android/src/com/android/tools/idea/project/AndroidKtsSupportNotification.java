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
package com.android.tools.idea.project;

import static com.intellij.notification.NotificationDisplayType.NONE;
import static com.intellij.notification.NotificationType.WARNING;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.sync.hyperlink.FileBugHyperlink;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationsConfiguration;
import com.intellij.notification.impl.NotificationSettings;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class AndroidKtsSupportNotification {
  public static final String KTS_DISABLED_WARNING_MSG = "This project uses Gradle KTS build files which are not fully supported. Some functions may be affected.";
  public static final String KTS_ENABLED_WARNING_MSG = "Support for <tt>gradle.kts</tt> build files is experimental: please file bugs to report any problems you encounter.";
  public static final String KTS_WARNING_TITLE = "Gradle Kotlinscript Build Files";
  public static final NotificationGroup KTS_NOTIFICATION_GROUP = NotificationGroup.balloonGroup("Gradle KTS build files");

  @NotNull private final Project myProject;
  private boolean alreadyShown;

  @NotNull
  public static AndroidKtsSupportNotification getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, AndroidKtsSupportNotification.class);
  }

  public AndroidKtsSupportNotification(@NotNull Project project) {
    myProject = project;
    alreadyShown = false;
  }

  public void showWarningIfNotShown() {
    if (!alreadyShown) {
      if (StudioFlags.KOTLIN_DSL_PARSING.get()) {
        AndroidNotification.getInstance(myProject)
          .showBalloon(KTS_WARNING_TITLE, KTS_ENABLED_WARNING_MSG, WARNING, KTS_NOTIFICATION_GROUP,
                       new DisableAndroidKtsNotificationHyperlink(),
                       new FileBugHyperlink());
      }
      else {
        AndroidNotification.getInstance(myProject)
          .showBalloon(KTS_WARNING_TITLE, KTS_DISABLED_WARNING_MSG, WARNING, KTS_NOTIFICATION_GROUP,
                       new DisableAndroidKtsNotificationHyperlink());
      }
      // Make sure that it was displayed, otherwise notification will not show until project is reopened.
      NotificationSettings settings = NotificationsConfigurationImpl.getSettings(KTS_NOTIFICATION_GROUP.getDisplayId());
      alreadyShown = settings.getDisplayType() != NONE || settings.isShouldLog();
    }
  }

  public static class DisableAndroidKtsNotificationHyperlink extends NotificationHyperlink {

    protected DisableAndroidKtsNotificationHyperlink() {
      super("disableKtsNotification", "Disable this warning");
    }

    @Override
    protected void execute(@NotNull Project project) {
      NotificationsConfiguration.getNotificationsConfiguration().changeSettings(KTS_NOTIFICATION_GROUP.getDisplayId(), NONE, false, false);
    }
  }
}
