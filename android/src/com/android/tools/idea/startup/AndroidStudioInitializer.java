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

import com.android.tools.adtui.webp.WebpMetadata;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.analytics.IdeBrandProviderKt;
import com.android.tools.idea.diagnostics.AndroidStudioSystemHealthMonitor;
import com.android.tools.idea.stats.AndroidStudioUsageTracker;
import com.android.tools.idea.stats.ConsentDialog;
import com.intellij.analytics.AndroidStudioAnalytics;
import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;

/**
 * Performs Android Studio specific initialization tasks that are build-system-independent.
 * <p>
 * <strong>Note:</strong> Do not add any additional tasks unless it is proven that the tasks are common to all IDEs. Use
 * {@link GradleSpecificInitializer} instead.
 * </p>
 */
public class AndroidStudioInitializer implements ApplicationInitializedListener {

  @Override
  public void componentsInitialized() {
    setupAnalytics();

    // Initialize System Health Monitor after Analytics.
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      AndroidStudioSystemHealthMonitor.getInstance().start();
    });

    // TODO: Remove this once the issue has been properly fixed in the IntelliJ platform
    //  see https://youtrack.jetbrains.com/issue/IDEA-316037
    // Automatic registration of WebP support through the WebP plugin can fail
    // because of a race condition in the creation of IIORegistry.
    // Trying again here ensures that the WebP support is correctly registered.
    WebpMetadata.ensureWebpRegistered();
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
