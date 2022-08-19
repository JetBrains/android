/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.analytics.IdeBrandProviderKt;
import com.android.tools.idea.diagnostics.AndroidStudioSystemHealthMonitor;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.instrumentation.threading.ThreadingChecker;
import com.android.tools.idea.serverflags.ServerFlagDownloader;
import com.android.tools.idea.stats.AndroidStudioUsageTracker;
import com.android.tools.idea.stats.ConsentDialog;
import com.android.tools.idea.stats.GcPauseWatcher;
import com.google.common.base.Predicates;
import com.intellij.analytics.AndroidStudioAnalytics;
import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.XmlHighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.AppUIUtil;
import java.util.concurrent.ScheduledExecutorService;
import org.jetbrains.annotations.NotNull;

/**
 * Performs Android Studio specific initialization tasks that are build-system-independent.
 * <p>
 * <strong>Note:</strong> Do not add any additional tasks unless it is proven that the tasks are common to all IDEs. Use
 * {@link GradleSpecificInitializer} instead.
 * </p>
 */
public class AndroidStudioInitializer implements ActionConfigurationCustomizer {

  @Override
  public void customize(@NotNull ActionManager actionManager) {
    ScheduledExecutorService scheduler = JobScheduler.getScheduler();
    scheduler.execute(ServerFlagDownloader::downloadServerFlagList);

    setupAnalytics();
    setupThreadingAgentEventListener();
    if (StudioFlags.TWEAK_COLOR_SCHEME.get()) {
      tweakDefaultColorScheme();
    }

    // Initialize System Health Monitor after Analytics and ServerFlag.
    // AndroidStudioSystemHealthMonitor requires ActionManager to be ready, but this code is a part
    // of its initialization. By pushing initialization to background thread, the thread will
    // block until ActionManager is ready and use its instance, instead of making another one.
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      if (AndroidStudioSystemHealthMonitor.getInstance() == null) {
        new AndroidStudioSystemHealthMonitor().start();
      }
    });
  }

  private static void tweakDefaultColorScheme() {
    // Modify built-in "Default" color scheme to remove background from XML tags.
    // "Darcula" and user schemes will not be touched.
    EditorColorsScheme colorsScheme = EditorColorsManager.getInstance().getScheme(EditorColorsScheme.DEFAULT_SCHEME_NAME);
    TextAttributes textAttributes = colorsScheme.getAttributes(HighlighterColors.TEXT);
    TextAttributes xmlTagAttributes = colorsScheme.getAttributes(XmlHighlighterColors.XML_TAG);
    xmlTagAttributes.setBackgroundColor(textAttributes.getBackgroundColor());
  }

  /*
   * sets up collection of Android Studio specific analytics.
   */
  private static void setupAnalytics() {
    AndroidStudioAnalytics.getInstance().initializeAndroidStudioUsageTrackerAndPublisher();

    if (StudioFlags.NEW_CONSENT_DIALOG.get()) {
      ConsentDialog.showConsentDialogIfNeeded();
    }
    else {
      // If the user hasn't opted in, we will ask IJ to check if the user has
      // provided a decision on the statistics consent. If the user hasn't made a
      // choice, a modal dialog will be shown asking for a decision
      // before the regular IDE ui components are shown.
      if (!AnalyticsSettings.getOptedIn()) {
        Application application = ApplicationManager.getApplication();
        // If we're running in a test or headless mode, do not show the dialog
        // as it would block the test & IDE from proceeding.
        // NOTE: in this case the metrics logic will be left in the opted-out state
        // and no metrics are ever sent.
        if (!application.isUnitTestMode() && !application.isHeadlessEnvironment()) {
          ApplicationManager.getApplication().invokeLater(() -> AppUIUtil.showConsentsAgreementIfNeeded(getLog(), Predicates.alwaysTrue()));
        }
      }
    }

    ApplicationInfo application = ApplicationInfo.getInstance();
    UsageTracker.setVersion(application.getStrictVersion());
    UsageTracker.setIdeBrand(IdeBrandProviderKt.currentIdeBrand());
    if (ApplicationManager.getApplication().isInternal()) {
      UsageTracker.setIdeaIsInternal(true);
    }
    AndroidStudioUsageTracker.setup(JobScheduler.getScheduler());
    new GcPauseWatcher();
  }

  private static void setupThreadingAgentEventListener() {
    ThreadingChecker.initialize();
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(AndroidStudioInitializer.class);
  }
}
