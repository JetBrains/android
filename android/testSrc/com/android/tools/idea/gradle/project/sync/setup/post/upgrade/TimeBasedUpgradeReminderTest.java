/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.post.upgrade;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;

import java.util.Calendar;
import java.util.GregorianCalendar;

import static java.util.Calendar.*;

/**
 * Tests for {@link TimeBasedUpgradeReminder}.
 */
public class TimeBasedUpgradeReminderTest extends IdeaTestCase {
  private Calendar myCalendar;
  private TimeBasedUpgradeReminder myUpgradeReminder;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myCalendar = new GregorianCalendar();
    myCalendar.set(HOUR, 10);
    myCalendar.set(MINUTE, 26);
    myCalendar.set(SECOND, 18);

    myUpgradeReminder = new TimeBasedUpgradeReminder();
  }

  public void testShouldRecommendUpgradeWhenTimePassedIsLessThanOneDay() {
    Project project = getProject();
    myUpgradeReminder.storeLastUpgradeRecommendation(project, getCurrentTimeInMs());
    myCalendar.add(HOUR, 5);

    assertFalse(myUpgradeReminder.shouldRecommendUpgrade(project, getCurrentTimeInMs()));
  }

  public void testShouldRecommendUpgradeWhenTimePassedIsOneDay() {
    Project project = getProject();
    myUpgradeReminder.storeLastUpgradeRecommendation(project, getCurrentTimeInMs());
    myCalendar.add(DATE, 1);

    assertTrue(myUpgradeReminder.shouldRecommendUpgrade(project, getCurrentTimeInMs()));
  }

  public void testStoreLastUpgradeRecommendation() {
    Project project = getProject();
    myUpgradeReminder.storeLastUpgradeRecommendation(project, getCurrentTimeInMs());

    String stored = myUpgradeReminder.getStoredTimestamp(project);
    assertNotNull(stored);
    long timestamp = Long.parseLong(stored);
    assertEquals(getCurrentTimeInMs(), timestamp);
  }

  private long getCurrentTimeInMs() {
    return myCalendar.getTimeInMillis();
  }
}