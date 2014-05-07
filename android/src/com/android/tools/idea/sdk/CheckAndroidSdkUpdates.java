/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.sdk;

import com.android.sdklib.repository.local.UpdateResult;
import com.android.sdklib.repository.remote.RemoteSdk;
import com.android.tools.idea.gradle.project.AndroidGradleNotification;
import com.android.tools.idea.gradle.service.notification.CustomNotificationListener;
import com.android.tools.idea.gradle.service.notification.NotificationHyperlink;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.actions.RunAndroidSdkManagerAction;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Runs a background task to initialize the SDK data structures
 * and determine if there are available SDK updates.
 *
 * This may or may not require network checks on the SDK repository.
 */
public abstract class CheckAndroidSdkUpdates {

  private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.sdk.CheckAndroidSdkUpdates");
  private static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.balloonGroup("Android SDK Notification Group");

  public static void checkNow(@NotNull final Project project) {
    // TODO this really needs a way for users to disable the check.
    // We want a settings panel for that. Right now let's not show
    // the feature. Users who really want it can use this *temporary*
    // env var to enable it.
    if (!"1".equals(System.getenv("STUDIO_SDK_CHECK"))) {
      LOG.debug("SDK check disabled by default, export STUDIO_SDK_CHECK=1 to enable it.");
      return;
    }

    final AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
    if (sdkData == null) {
      LOG.info("Android SDK Data == null");
      return;
    }

    final SdkState state = SdkState.getInstance(sdkData);
    Runnable onSuccess = new Runnable() {
      @Override
      public void run() {
        UpdateResult updates = state.getUpdates();
        if (updates != null && !updates.getUpdatedPkgs().isEmpty()) {
          ApplicationEx app = ApplicationManagerEx.getApplicationEx();

          app.invokeLater(new Runnable() {
            @Override
            public void run() {
              displayHasUpdateNotification(project, sdkData);
            }
          });
        }

        int n = updates == null ? 0 : updates.getUpdatedPkgs().size();
        LOG.info("Android SDK: " + n + " updates found");

      }
    };

    state.loadAsync(RemoteSdk.DEFAULT_EXPIRATION_PERIOD_MS,
                    true,  // canBeCancelled
                    onSuccess,
                    null);  // onError
  }

  private static void displayHasUpdateNotification(@NotNull Project project, @NotNull final AndroidSdkData sdkData) {
    final AndroidGradleNotification notification = AndroidGradleNotification.getInstance(project);

    NotificationHyperlink sdkManagerHyperlink = new NotificationHyperlink("sdk.man.show", "Open SDK Manager") {
      @Override
      protected void execute(@NotNull Project project) {
        RunAndroidSdkManagerAction.runSpecificSdkManager(project, sdkData.getLocalSdk().getLocation());

        Notification n = notification.getNotification();
        if (n != null) {
          n.expire();
        }
      }
    };

    String msg =
      "Updates are available for the Android SDK.<br>\n" +
      sdkManagerHyperlink.toString();

    NotificationListener notificationListener = new CustomNotificationListener(project, sdkManagerHyperlink);
    notification.showBalloon("Android SDK", msg, NotificationType.INFORMATION, NOTIFICATION_GROUP, notificationListener);
  }
}
