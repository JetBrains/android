/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.startup;

import com.android.tools.analytics.AnalyticsPublisher;
import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.AnalyticsSettingsData;
import com.android.tools.analytics.HighlightingStats;
import com.android.tools.analytics.StudioUpdateAnalyticsUtil;
import com.android.tools.analytics.UsageTracker;
import com.android.utils.ILogger;
import com.intellij.analytics.AndroidStudioAnalytics;
import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.ConsentOptionsProvider;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidStudioAnalyticsImpl extends AndroidStudioAnalytics {
  private ILogger androidLogger;

  @Override
  public boolean isAllowed() {
    // As we cannot control when IJ calls into this code, we need to load the AnalyticsSettings if
    // we're not initialized yet, to ensure we properly return opt-in status.
    if (!AnalyticsSettings.getInitialized()) {
      Application application = ApplicationManager.getApplication();
      if (application != null && application.isUnitTestMode()) {
        AnalyticsSettingsData analyticsSettings = new AnalyticsSettingsData();
        analyticsSettings.setOptedIn(false);
        AnalyticsSettings.setInstanceForTest(analyticsSettings);
      } else {
        AnalyticsSettings.initialize(getAndroidLogger());
      }
    }
    return AnalyticsSettings.getOptedIn();

  }


  @Override
  public void recordHighlightingLatency(Document document, long latencyMs) {
    HighlightingStats.getInstance().recordHighlightingLatency(document, latencyMs);
  }

  @Override
  public void logUpdateDialogOpenManually(@NotNull String newBuild) {
    StudioUpdateAnalyticsUtil.logUpdateDialogOpenManually(newBuild);
  }

  @Override
  public void logNotificationShown(@NotNull String newBuild) {
    StudioUpdateAnalyticsUtil.logNotificationShown(newBuild);
  }

  @Override
  public void logClickNotification(@NotNull String newBuild) {
    StudioUpdateAnalyticsUtil.logClickNotification(newBuild);
  }

  @Override
  public void logUpdateDialogOpenFromNotification(@NotNull String newBuild) {
    StudioUpdateAnalyticsUtil.logUpdateDialogOpenFromNotification(newBuild);
  }

  @Override
  public void logClickIgnore(String newBuild) {
    StudioUpdateAnalyticsUtil.logClickIgnore(newBuild);
  }

  @Override
  public void logClickLater(String newBuild) {
    StudioUpdateAnalyticsUtil.logClickLater(newBuild);
  }

  @Override
  public void logDownloadSuccess(String newBuild) {
    StudioUpdateAnalyticsUtil.logDownloadSuccess(newBuild);
  }

  @Override
  public void logDownloadFailure(String newBuild) {
    StudioUpdateAnalyticsUtil.logDownloadFailure(newBuild);
  }

  @Override
  public void updateAndroidStudioMetrics() {
    updateAndroidStudioMetrics(getConsentOptionsProvider().isSendingUsageStatsAllowed());
  }

  private @Nullable ConsentOptionsProvider getConsentOptionsProvider() {
    return UsageStatisticsPersistenceComponent.getConsentOptionsProvider();
  }

  private void updateAndroidStudioMetrics(boolean allowed) {

    // Update the settings & tracker based on allowed state, will initialize on first call.
    boolean updated = false;
    try {
      if (allowed == AnalyticsSettings.getOptedIn()) {
        updated = false;
      } else {
        AnalyticsSettings.setOptedIn(allowed);
        AnalyticsSettings.saveSettings();
        updated = true;
      }
    } catch (IOException e) {
      getAndroidLogger().error(e, "Unable to update analytics settings");
    }
    if (updated) {
      initializeAndroidStudioUsageTrackerAndPublisher();
    }
  }

  @Override
  public void initializeAndroidStudioUsageTrackerAndPublisher() {
    ILogger logger = getAndroidLogger();

    ScheduledExecutorService scheduler = JobScheduler.getScheduler();
    AnalyticsSettings.initialize(logger, scheduler);

    try {
      // If AnalyticsSettings and IJ opt-in status disagree, then we assume IJ is correct.
      // This catches cornercases such as manual modifications as well as deal with the
      // incorrect rename of "hasOptedIn" to "optedIn" in some early 3.3 canary builds.
      boolean ijOptedIn = getConsentOptionsProvider().isSendingUsageStatsAllowed();
      if (AnalyticsSettings.getOptedIn() != ijOptedIn) {
        AnalyticsSettings.setOptedIn(ijOptedIn);
        AnalyticsSettings.saveSettings();
      }
      UsageTracker.initialize(scheduler);
    } catch (Exception e) {
      logger.warning("Unable to initialize analytics tracker: " + e.getMessage());
      return;
    }
    // Update usage tracker maximums for long-lived process.
    UsageTracker.setMaxJournalTime(10, TimeUnit.MINUTES);
    UsageTracker.setMaxJournalSize(1000);

    ApplicationInfo application = ApplicationInfo.getInstance();
    AnalyticsPublisher.updatePublisher(logger, scheduler, application.getStrictVersion());
  }

  private ILogger getAndroidLogger() {
    if (androidLogger == null) {
      Logger intelliJLogger = Logger.getInstance("#com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent");
      // Create logger & scheduler based on IntelliJ/ADT helpers.
      androidLogger = new ILogger() {
        @Override
        public void error(@com.android.annotations.Nullable Throwable t,
                          @com.android.annotations.Nullable String msgFormat,
                          Object... args) {
          intelliJLogger.error(String.format(msgFormat, args), t);
        }

        @Override
        public void warning(String msgFormat, Object... args) {
          intelliJLogger.warn(String.format(msgFormat, args));
        }

        @Override
        public void info(String msgFormat, Object... args) {
          intelliJLogger.info(String.format(msgFormat, args));
        }

        @Override
        public void verbose(String msgFormat, Object... args) {
          info(msgFormat, args);
        }
      };
    }
    return androidLogger;
  }
}
