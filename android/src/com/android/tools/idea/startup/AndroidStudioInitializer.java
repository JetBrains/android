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

import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.analytics.IdeBrandProviderKt;
import com.android.tools.idea.diagnostics.AndroidStudioSystemHealthMonitor;
import com.android.tools.idea.stats.AndroidStudioUsageTracker;
import com.android.tools.idea.stats.ConsentDialog;
import com.intellij.analytics.AndroidStudioAnalytics;
import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
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
    setupAnalytics();

    // Initialize System Health Monitor after Analytics.
    // AndroidStudioSystemHealthMonitor requires ActionManager to be ready, but this code is a part
    // of its initialization. By pushing initialization to background thread, the thread will
    // block until ActionManager is ready and use its instance, instead of making another one.
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      AndroidStudioSystemHealthMonitor.getInstance().start();
    });
  }

  /** Sets up collection of Android Studio specific analytics. */
  private static void setupAnalytics() {
    AndroidStudioAnalytics.getInstance().initializeAndroidStudioUsageTrackerAndPublisher();

    ConsentDialog.showConsentDialogIfNeeded();

    ApplicationInfo application = ApplicationInfo.getInstance();
    UsageTracker.setVersion(application.getStrictVersion());
    UsageTracker.setIdeBrand(IdeBrandProviderKt.currentIdeBrand());
    if (ApplicationManager.getApplication().isInternal()) {
      UsageTracker.setIdeaIsInternal(true);
    }
    AndroidStudioUsageTracker.setup(JobScheduler.getScheduler());
  }
}
