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
package com.android.tools.idea.diagnostics;

import com.android.tools.idea.flags.StudioFlags;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.SystemInfo;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

public class Win32DeprecationNotifier {
  private static final String WIN32_DEPRECATION_NOTIFICATION_LAST_SHOWN_TIME_KEY = "win32.deprecation.notification.last.shown.time";
  private static final String key = "windows32.deprecation.message";
  private static final Duration DEPRECATION_MIN_INTERVAL_BETWEEN_NOTIFICATIONS = Duration.ofDays(1);
  @NotNull private String myGroupId;

  Win32DeprecationNotifier(@NotNull String groupId) {
    myGroupId = groupId;
  }

  public void run() {
    if (SystemInfo.isWindows && SystemInfo.is32Bit && StudioFlags.WIN32_DEPRECATION_NOTIFICATION_ENABLED.get()) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> showNotification());
    }
  }

  private void showNotification() {
    if (shouldNotShow()) {
      return;
    }

    if (shownRecently()) {
      return;
    }

    setLastShownTime();
    Notification notification = new Notification(myGroupId,
                                                 "",
                                                 AndroidBundle.message(key),
                                                 NotificationType.INFORMATION);

    notification.addAction(new NotificationAction("Don't show again") {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        notification.expire();
        PropertiesComponent applicationProperties = PropertiesComponent.getInstance();
        if (applicationProperties != null) {
          applicationProperties.setValue("ignore." + key, "true");
        }
      }
    });

    ApplicationManager.getApplication().invokeLater(() -> Notifications.Bus.notify(notification));
  }

  private static boolean shouldNotShow() {
    PropertiesComponent applicationProperties = PropertiesComponent.getInstance();
    if(applicationProperties == null) {
      return false;
    }

    return applicationProperties.isValueSet("ignore." + key);
  }

  private static boolean shownRecently() {
    String lastShownTime = PropertiesComponent.getInstance().getValue(WIN32_DEPRECATION_NOTIFICATION_LAST_SHOWN_TIME_KEY);
    if (lastShownTime == null) {
      return false;
    }

    try {
      Instant lastShown = Instant.parse(lastShownTime);
      return lastShown.plus(DEPRECATION_MIN_INTERVAL_BETWEEN_NOTIFICATIONS).isAfter(Instant.now());
    }
    catch (DateTimeException e) {
      // corrupted date format. Return false here and overwrite with a good value.
      setLastShownTime();
      return false;
    }
  }

  private static void setLastShownTime() {
    PropertiesComponent.getInstance().setValue(WIN32_DEPRECATION_NOTIFICATION_LAST_SHOWN_TIME_KEY, Instant.now().toString());
  }
}