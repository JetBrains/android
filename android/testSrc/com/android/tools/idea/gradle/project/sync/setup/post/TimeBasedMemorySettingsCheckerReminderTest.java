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
package com.android.tools.idea.gradle.project.sync.setup.post;

import com.intellij.testFramework.PlatformTestCase;
import java.util.Calendar;
import java.util.GregorianCalendar;

public class TimeBasedMemorySettingsCheckerReminderTest extends PlatformTestCase {
  private Calendar myCalendar;
  private TimeBasedMemorySettingsCheckerReminder myReminder;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myCalendar = new GregorianCalendar();
    myReminder = new TimeBasedMemorySettingsCheckerReminder();
  }

  public void testShouldNotCheckWhenDoNotAskForAppIsSet() {
    myReminder.setDoNotAskForApplication();
    assertFalse(myReminder.shouldCheck(myProject));
  }

  public void testShouldNotCheckWhenDoNotAskForProjectIsSet() {
    myReminder.setDoNotAsk(myProject);
    assertFalse(myReminder.shouldCheck(myProject));
  }

  public void testShouldNotCheckWhenTimePassedIsLessThanOneDay() {
    myReminder.storeLastCheckTimestamp(myCalendar.getTimeInMillis());
    myCalendar.add(Calendar.HOUR, 10);
    assertFalse(myReminder.shouldCheck(myProject, myCalendar.getTimeInMillis()));
  }

  public void testShouldCheckAfterOneDay() {
    myReminder.storeLastCheckTimestamp(myCalendar.getTimeInMillis());
    myCalendar.add(Calendar.DATE, 1);
    assertTrue(myReminder.shouldCheck(myProject, myCalendar.getTimeInMillis()));
  }

  public void testStoreLastCheckTimeStamp() {
    long current = myCalendar.getTimeInMillis();
    myReminder.storeLastCheckTimestamp(current);
    String stored = myReminder.getStoredTimestamp();
    assertNotNull(stored);
    assertEquals(current, Long.parseLong(stored));
  }
}
