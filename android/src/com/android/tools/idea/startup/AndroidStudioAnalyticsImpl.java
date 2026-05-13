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
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.IdeInfo;
import com.android.utils.ILogger;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.OptInToMetrics;
import com.google.wireless.android.sdk.stats.OptOutOfMetrics;
import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.ConsentOptionsProvider;
import com.intellij.ide.gdpr.DataSharingSettingsChangeListener;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service
public final class AndroidStudioAnalyticsImpl {
  private ILogger androidLogger;

  AndroidStudioAnalyticsImpl() {
    if (PlatformUtils.isJetBrainsProduct() && !ApplicationManager.getApplication().isUnitTestMode()) {
      throw new IllegalStateException("This service should never be used in IntelliJ");
    }
  }

  public static @NotNull AndroidStudioAnalyticsImpl getInstance() {
    if (PlatformUtils.isJetBrainsProduct() && !ApplicationManager.getApplication().isUnitTestMode()) {
      throw new IllegalStateException("This service should never be used in IntelliJ");
    }

    return ApplicationManager.getApplication().getService(AndroidStudioAnalyticsImpl.class);
  }

  private @NotNull ConsentOptionsProvider getConsentOptionsProvider() {
    // Adapted from UsageStatisticsPersistenceComponent#getConsentOptionsProvider.
    return Objects.requireNonNull(ApplicationManager.getApplication().getService(ConsentOptionsProvider.class));
  }

  // Tested by AnalyticsSettingsUiTest.
  @SuppressWarnings("UnstableApiUsage")
  public static class MyDataSharingSettingsChangeListener implements DataSharingSettingsChangeListener {

    @Override
    public void consentWritten() {
      // Redundant with consentsUpdated() below.
    }

    @Override
    public void consentsUpdated() {
      AndroidStudioAnalyticsImpl service = AndroidStudioAnalyticsImpl.getInstance();
      service.updateAndroidStudioMetrics(service.getConsentOptionsProvider().isSendingUsageStatsAllowed());
    }
  }

  private void updateAndroidStudioMetrics(boolean allowed) {

    // Update the settings & tracker based on allowed state, will initialize on first call.
    boolean updated = false;

    ScheduledExecutorService scheduler = JobScheduler.getScheduler();
    AnalyticsSettings.initialize(getAndroidLogger(), scheduler);

    try {
      if (allowed == AnalyticsSettings.getOptedIn()) {
        updated = false;
      }
      else {
        if (!allowed) {
          UsageTracker.log(AndroidStudioEvent.newBuilder()
                             .setKind(AndroidStudioEvent.EventKind.OPTOUT_METRICS)
                             .setOptOutOfMetrics(OptOutOfMetrics.newBuilder())
          );
        }
        AnalyticsSettings.setOptedIn(allowed);
        AnalyticsSettings.saveSettings();
        updated = true;
      }
    }
    catch (IOException e) {
      getAndroidLogger().error(e, "Unable to update analytics settings");
    }
    if (updated) {
      initializeAndroidStudioUsageTrackerAndPublisher();
      if (allowed) {
        UsageTracker.log(AndroidStudioEvent.newBuilder()
                           .setKind(AndroidStudioEvent.EventKind.OPTIN_METRICS)
                           .setOptInToMetrics(OptInToMetrics.newBuilder())
        );
      }
    }
  }

  public void initializeAndroidStudioUsageTrackerAndPublisher() {
    ILogger logger = getAndroidLogger();

    ScheduledExecutorService scheduler = JobScheduler.getScheduler();
    AnalyticsSettings.initialize(logger, ApplicationManager.getApplication().isUnitTestMode() ? null : scheduler);

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
        public void error(@Nullable Throwable t,
                          @Nullable String msgFormat,
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
