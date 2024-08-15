/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android;

import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.AnalyticsSettingsData;
import org.junit.rules.ExternalResource;

/** Runs before Android Studio integration tests. */
public class AndroidIntegrationTestSetupRule extends ExternalResource {
  @Override
  protected void before() throws Throwable {
    System.setProperty("android.studio.sdk.manager.disabled", "true");

    // AS 3.3 and higher requires analytics to be initialized, or it'll try to create a file in
    // the home directory (~/.android/...)
    AnalyticsSettingsData analyticsSettings = new AnalyticsSettingsData();
    analyticsSettings.setOptedIn(false);
    AnalyticsSettings.setInstanceForTest(analyticsSettings);
  }
}
