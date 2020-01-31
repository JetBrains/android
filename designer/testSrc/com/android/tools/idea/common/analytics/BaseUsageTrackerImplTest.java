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
package com.android.tools.idea.common.analytics;

import static com.android.resources.ScreenOrientation.PORTRAIT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.State;
import com.android.testutils.VirtualTimeScheduler;
import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.AnalyticsSettingsData;
import com.android.tools.analytics.LoggedUsage;
import com.android.tools.analytics.TestUsageTracker;
import com.android.tools.idea.configurations.Configuration;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import java.util.List;
import java.util.concurrent.Executor;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

public abstract class BaseUsageTrackerImplTest extends AndroidTestCase {
  protected static final Executor SYNC_EXECUTOR = Runnable::run;

  protected TestUsageTracker usageTracker;
  private final VirtualTimeScheduler myVirtualTimeScheduler = new VirtualTimeScheduler();

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    AnalyticsSettingsData settings = new AnalyticsSettingsData();
    AnalyticsSettings.setInstanceForTest(settings);
    usageTracker = new TestUsageTracker(myVirtualTimeScheduler);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      usageTracker.close();
    } finally {
      super.tearDown();
    }
  }

  @NotNull
  protected AndroidStudioEvent getLastLogUsage() {
    List<LoggedUsage> usages = usageTracker.getUsages();
    assertNotEmpty(usages);
    return usages.get(usages.size() - 1).getStudioEvent();
  }

  protected static Configuration getConfigurationMock() {
    IAndroidTarget target = mock(IAndroidTarget.class);
    when(target.getVersion()).thenReturn(new AndroidVersion(0, "mock"));

    State state = mock(State.class);
    when(state.getOrientation()).thenReturn(PORTRAIT);

    Configuration configuration = mock(Configuration.class);
    when(configuration.getTarget()).thenReturn(target);
    when(configuration.getDeviceState()).thenReturn(state);

    return configuration;
  }
}
