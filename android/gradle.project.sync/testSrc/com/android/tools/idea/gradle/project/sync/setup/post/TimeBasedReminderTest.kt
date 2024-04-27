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

import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationsConfiguration
import com.intellij.notification.impl.NotificationsConfigurationImpl
import com.intellij.testFramework.PlatformTestCase
import java.util.Calendar
import java.util.GregorianCalendar

class TimeBasedReminderTest : PlatformTestCase() {
  private lateinit var calendar: Calendar
  private lateinit var reminder: TimeBasedReminder
  private val NOTIFICATION_ID = "testTimeBasedReminderGroupId"

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

  fun testNotificationSettingsChange() {
    NotificationsConfiguration.getNotificationsConfiguration().changeSettings(NOTIFICATION_ID, NotificationDisplayType.BALLOON, true, true)
    val reminderNotification = TimeBasedReminder(project, "test.property", notificationGroupId = NOTIFICATION_ID, defaultShouldLog = true,
                                                 defaultShouldRead = true, defaultNotificationType = NotificationDisplayType.BALLOON)
    // Check notifications are disabled
    reminderNotification.doNotAskForApplication = true
    var notificationSettings = NotificationsConfigurationImpl.getSettings(NOTIFICATION_ID)
    assertEquals(NotificationDisplayType.NONE, notificationSettings.displayType)
    assertFalse(notificationSettings.isShouldLog)
    assertFalse(notificationSettings.isShouldReadAloud)
    assertTrue(reminderNotification.doNotAskForApplication)
    // Check they can be re-enabled by changing the reminder
    reminderNotification.doNotAskForApplication = false
    notificationSettings = NotificationsConfigurationImpl.getSettings(NOTIFICATION_ID)
    assertEquals(NotificationDisplayType.BALLOON, notificationSettings.displayType)
    assertTrue(notificationSettings.isShouldLog)
    assertTrue(notificationSettings.isShouldReadAloud)
    assertFalse(reminderNotification.doNotAskForApplication)
    // Check that enabling the notification also enables the reminder
    reminderNotification.doNotAskForApplication = true
    assertTrue(reminderNotification.doNotAskForApplication)
    NotificationsConfiguration.getNotificationsConfiguration().changeSettings(NOTIFICATION_ID, NotificationDisplayType.STICKY_BALLOON, true, true)
    assertFalse(reminderNotification.doNotAskForApplication)
  }
}
