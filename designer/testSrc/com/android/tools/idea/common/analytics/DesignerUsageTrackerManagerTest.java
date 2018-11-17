/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;

import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.AnalyticsSettingsData;
import com.android.tools.idea.common.surface.DesignSurface;
import org.jetbrains.android.AndroidTestCase;

public class DesignerUsageTrackerManagerTest extends AndroidTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    AnalyticsSettingsData settings = new AnalyticsSettingsData();
    AnalyticsSettings.setInstanceForTest(settings);
  }


  public void testGetInstance() {
    Object nopTracker = new Object();
    DesignerUsageTrackerManager<Object> manager =  new DesignerUsageTrackerManager<>((a, b, c) -> new Object(), nopTracker);
    // Because we are testing the actual getInstanceInner instantiation, we tell the method
    assertEquals(nopTracker, manager.getInstanceInner(null, true));

    DesignSurface surface1 = mock(DesignSurface.class);
    DesignSurface surface2 = mock(DesignSurface.class);
    Object realTracker = manager.getInstanceInner(surface1, true);
    assertEquals(realTracker, manager.getInstanceInner(surface1, true));
    assertNotEquals(realTracker, manager.getInstanceInner(surface2, true));
  }
}
