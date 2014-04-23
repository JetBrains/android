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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.internal.repository.sources.SdkSources;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.sdklib.repository.local.Update;
import com.android.sdklib.repository.local.UpdateResult;
import com.android.sdklib.repository.remote.RemotePkgInfo;
import com.android.sdklib.repository.remote.RemoteSdk;
import com.android.tools.idea.gradle.project.AndroidGradleNotification;
import com.android.tools.idea.gradle.service.notification.CustomNotificationListener;
import com.android.tools.idea.gradle.service.notification.NotificationHyperlink;
import com.android.utils.ILogger;
import com.google.common.collect.Multimap;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
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

  private static long ourCheckTimestampMs;
  private static BackgroundableProcessIndicator ourIndicator;

  public static void checkNow(@NotNull Project project) {
    long now = System.currentTimeMillis();
    if (now - ourCheckTimestampMs <= RemoteSdk.DEFAULT_EXPIRATION_PERIOD_MS) {
      LOG.info("Skip: too early");
      return;
    }

    AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
    if (sdkData == null) {
      LOG.info("Android SDK Data == null");
      return;
    }

    // TODO this really needs a way for users to disable the check.
    // We want a settings panel for that. Right now let's not show
    // the feature. Users who really want it can use this *temporary*
    // env var to enable it.
    if (!"1".equals(System.getenv("STUDIO_SDK_CHECK"))) {
      LOG.debug("SDK check disabled by default, export STUDIO_SDK_CHECK=1 to enable it.");
      return;
    }


    if (ourIndicator == null) {
      ourCheckTimestampMs = now;
      SdkUpdateCheckTask task = new SdkUpdateCheckTask(project, sdkData);
      ourIndicator = new BackgroundableProcessIndicator(task);
      ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, ourIndicator);
    }
  }

  private static class IndicatorLogger implements ILogger {
    @NotNull private final ProgressIndicator myIndicator;

    public IndicatorLogger(@NotNull ProgressIndicator indicator) {
      myIndicator = indicator;
    }

    @Override
    public void error(@Nullable Throwable t, @Nullable String msgFormat, Object... args) {
      if (msgFormat == null && t != null) {
        myIndicator.setText2(t.toString());
      } else if (msgFormat != null) {
        myIndicator.setText2(String.format(msgFormat, args));
      }
    }

    @Override
    public void warning(@NonNull String msgFormat, Object... args) {
      myIndicator.setText2(String.format(msgFormat, args));
    }

    @Override
    public void info(@NonNull String msgFormat, Object... args) {
      myIndicator.setText2(String.format(msgFormat, args));
    }

    @Override
    public void verbose(@NonNull String msgFormat, Object... args) {
      // skip here, don't log verbose strings
    }
  }

  private static class SdkUpdateCheckTask extends Task.Backgroundable {

    @NotNull
    private final AndroidSdkData mySdkData;

    public SdkUpdateCheckTask(@NotNull Project project, @NotNull AndroidSdkData sdkData) {
      super(project,
            "Checking for Android SDK Updates",
            true /*canBeCancelled*/,
            PerformInBackgroundOption.ALWAYS_BACKGROUND);
      mySdkData = sdkData;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      try {
        IndicatorLogger logger = new IndicatorLogger(indicator);

        ApplicationEx app = ApplicationManagerEx.getApplicationEx();
        SdkLifecycleListener notifier = app.getMessageBus().syncPublisher(SdkLifecycleListener.TOPIC);

        // fetch local sdk
        indicator.setText("Loading local SDK...");
        indicator.setText2("");
        LocalPkgInfo[] localPkgInfos = mySdkData.getLocalSdk().getPkgsInfos(PkgType.PKG_ALL);
        notifier.localSdkLoaded(mySdkData.getLocalSdk());
        indicator.setFraction(0.25);

        if (indicator.isCanceled()) {
          return;
        }

        // fetch sdk repository sources.
        indicator.setText("Find SDK Repository...");
        indicator.setText2("");
        SdkSources sources = mySdkData.getRemoteSdk().fetchSources(RemoteSdk.DEFAULT_EXPIRATION_PERIOD_MS, logger);
        indicator.setFraction(0.50);

        if (indicator.isCanceled()) {
          return;
        }

        // fetch remote sdk
        indicator.setText("Check SDK Repository...");
        indicator.setText2("");
        Multimap<PkgType, RemotePkgInfo> remotePkgs = mySdkData.getRemoteSdk().fetch(sources, logger);
        notifier.remoteSdkLoaded(mySdkData.getRemoteSdk());
        indicator.setFraction(0.75);

        if (indicator.isCanceled()) {
          return;
        }

        // compute updates
        indicator.setText("Compute SDK updates...");
        indicator.setText2("");
        UpdateResult updates = Update.computeUpdates(localPkgInfos, remotePkgs);
        indicator.setFraction(1.0);

        if (!updates.getUpdatedPkgs().isEmpty()) {
          app.invokeLater(new Runnable() {
            @Override
            public void run() {
              displayHasUpdateNotification();
            }
          });
        }

        int n = updates == null ? 0 : updates.getUpdatedPkgs().size();
        LOG.info("Android SDK: " + n + " updates found");

      } finally {
        ourIndicator = null;
      }
    }

    private void displayHasUpdateNotification() {
      assert getProject() != null;

      final AndroidGradleNotification notification = AndroidGradleNotification.getInstance(getProject());

      NotificationHyperlink sdkManagerHyperlink = new NotificationHyperlink("sdk.man.show", "Open SDK Manager") {
        @Override
        protected void execute(@NotNull Project project) {
          RunAndroidSdkManagerAction.runSpecificSdkManager(getProject(), mySdkData.getLocation());

          Notification n = notification.getNotification();
          if (n != null) {
            n.expire();
          }
        }
      };

      String msg =
        "Updates are available for the Android SDK.<br>\n" +
        sdkManagerHyperlink.toString();

      NotificationListener notificationListener = new CustomNotificationListener(getProject(), sdkManagerHyperlink);
      notification.showBalloon("Android SDK", msg, NotificationType.INFORMATION, NOTIFICATION_GROUP, notificationListener);
    }
  }
}
