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
package com.android.tools.idea.gradle.project.sync.setup.post

import com.intellij.testFramework.PlatformTestCase
import java.util.Calendar
import java.util.GregorianCalendar

class TimeBasedReminderTest : PlatformTestCase() {
  private lateinit var calendar: Calendar
  private lateinit var reminder: TimeBasedReminder

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    calendar = GregorianCalendar()
    reminder = TimeBasedReminder(project, "test.property")
  }

  override fun tearDown() {
    reminder.doNotAskForApplication = false
    reminder.doNotAskForProject = false
    super.tearDown()
  }

  fun testShouldNotCheckWhenDoNotAskForAppIsSet() {
    reminder.doNotAskForApplication = true
    assertFalse(reminder.shouldAsk())
  }

  fun testShouldNotCheckWhenDoNotAskForProjectIsSet() {
    reminder.doNotAskForProject = true
    assertFalse(reminder.shouldAsk())
  }

  fun testShouldNotCheckWhenTimePassedIsLessThanOneDay() {
    reminder.lastTimeStamp = calendar.timeInMillis
    calendar.add(Calendar.HOUR, 10)
    assertFalse(reminder.shouldAsk(calendar.timeInMillis))
  }

  fun testShouldCheckAfterOneDay() {
    reminder.lastTimeStamp = calendar.timeInMillis
    calendar.add(Calendar.HOUR, 25)
    assertTrue(reminder.shouldAsk(calendar.timeInMillis))
  }

  fun testStoreLastCheckTimeStamp() {
    val current = calendar.timeInMillis
    reminder.lastTimeStamp = current
    val stored = reminder.lastTimeStamp
    assertNotNull(stored)
    assertEquals(current, stored)
  }
  
  fun testPropertiesReadWrite() {
    reminder.lastTimeStamp = 777
    reminder.doNotAskForApplication = true
    reminder.doNotAskForProject = true
    
    assertEquals(777, reminder.lastTimeStamp)
    assertTrue(reminder.doNotAskForApplication)
    assertTrue(reminder.doNotAskForProject)
  }
}
