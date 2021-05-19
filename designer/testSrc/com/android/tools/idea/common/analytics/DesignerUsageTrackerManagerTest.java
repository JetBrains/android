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

import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.AnalyticsSettingsData;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.android.AndroidTestCase;

public class DesignerUsageTrackerManagerTest extends AndroidTestCase {
  private static class DisposableObject implements Disposable {
    @Override
    public void dispose() {
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    AnalyticsSettingsData settings = new AnalyticsSettingsData();
    AnalyticsSettings.setInstanceForTest(settings);
  }


  public void testGetInstance() {
    Object nopTracker = new Object();
    DesignerUsageTrackerManager<Object, DisposableObject> manager = new DesignerUsageTrackerManager<>((a, b, c) -> new Object(), nopTracker);
    // Because we are testing the actual getInstanceInner instantiation, we tell the method
    assertEquals(nopTracker, manager.getInstanceInner(null, true));

    DisposableObject key1 = new DisposableObject();
    DisposableObject key2 = new DisposableObject();
    Disposer.register(getTestRootDisposable(), key1);
    Disposer.register(getTestRootDisposable(), key2);
    Object realTracker = manager.getInstanceInner(key1, true);
    assertEquals(realTracker, manager.getInstanceInner(key1, true));
    assertNotEquals(realTracker, manager.getInstanceInner(key2, true));

    assertNotEquals(nopTracker, manager.getInstanceInner(key1, false));
    // This should automatically dispose the cached tracker
    Disposer.dispose(key1);
    assertEquals("Dispose should invalidate the cache", nopTracker, manager.getInstanceInner(key1, false));
    assertEquals(
      "Expected a NopTracker. Disposed keys should not allocate new trackers.",
      nopTracker, manager.getInstanceInner(key1, true));
  }
}
