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
package com.android.tools.idea.gradle.project.compatibility;

import com.android.tools.idea.gradle.project.compatibility.VersionMetadataUpdater.CheckInterval;
import org.junit.Test;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.*;

/**
 * Tests for {@link CheckInterval}.
 */
public class CheckIntervalTest {
  @Test
  public void testNeedsUpdate() {
    long now = System.currentTimeMillis();

    assertFalse(CheckInterval.DAILY.needsUpdate(now));
    long twoDaysAgo = MILLISECONDS.convert(2, DAYS);
    long lastUpdate = now - twoDaysAgo;
    assertTrue(CheckInterval.DAILY.needsUpdate(lastUpdate));

    assertFalse(CheckInterval.WEEKLY.needsUpdate(now));
    assertFalse(CheckInterval.WEEKLY.needsUpdate(lastUpdate));
    long twoWeeksAgo = MILLISECONDS.convert(14, DAYS);
    lastUpdate = now - twoWeeksAgo;
    assertTrue(CheckInterval.WEEKLY.needsUpdate(lastUpdate));
  }

  @Test
  public void testFind() {
    // Verify that search is case insensitive
    assertEquals(CheckInterval.NONE, CheckInterval.find("none"));
    assertEquals(CheckInterval.NONE, CheckInterval.find("None"));
    assertEquals(CheckInterval.NONE, CheckInterval.find("NONE"));
    assertEquals(CheckInterval.DAILY, CheckInterval.find("daily"));
    assertEquals(CheckInterval.DAILY, CheckInterval.find("Daily"));
    assertEquals(CheckInterval.DAILY, CheckInterval.find("DAILY"));
    assertEquals(CheckInterval.WEEKLY, CheckInterval.find("weekly"));
    assertEquals(CheckInterval.WEEKLY, CheckInterval.find("Weekly"));
    assertEquals(CheckInterval.WEEKLY, CheckInterval.find("WEEKLY"));

    // Verify that the default value is "weekly"
    assertEquals(CheckInterval.WEEKLY, CheckInterval.find(null));
    assertEquals(CheckInterval.WEEKLY, CheckInterval.find(""));
    assertEquals(CheckInterval.WEEKLY, CheckInterval.find("Hello World!"));
  }
}