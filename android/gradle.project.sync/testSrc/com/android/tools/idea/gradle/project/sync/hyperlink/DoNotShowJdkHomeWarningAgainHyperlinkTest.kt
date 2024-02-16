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
package com.android.tools.idea.gradle.project.sync.hyperlink

import com.android.tools.idea.gradle.project.sync.GradleSyncState.Companion.JDK_LOCATION_WARNING_NOTIFICATION_GROUP
import com.intellij.notification.NotificationsConfiguration
import com.intellij.notification.impl.NotificationSettings
import com.intellij.notification.impl.NotificationsConfigurationImpl
import com.intellij.testFramework.HeavyPlatformTestCase

/**
 * Tests for [DoNotShowJdkHomeWarningAgainHyperlink].
 */
class DoNotShowJdkHomeWarningAgainHyperlinkTest : HeavyPlatformTestCase() {
  private lateinit var myOriginalSettings: NotificationSettings

  override fun setUp() {
    super.setUp()
    // Save settings to restore after testing
    myOriginalSettings = NotificationsConfigurationImpl.getSettings(JDK_LOCATION_WARNING_NOTIFICATION_GROUP.displayId)
  }

  override fun tearDown() {
    // Restore settings
    NotificationsConfiguration.getNotificationsConfiguration().changeSettings(JDK_LOCATION_WARNING_NOTIFICATION_GROUP.displayId,
                                                                              JDK_LOCATION_WARNING_NOTIFICATION_GROUP.displayType,
                                                                              myOriginalSettings.isShouldLog,
                                                                              myOriginalSettings.isShouldReadAloud)
    super.tearDown()
  }

  /**
   * Verify that executing the hyperlink disables logging of warning.
   */
  fun testExecute() {
    // Make sure logging is enabled
    NotificationsConfiguration.getNotificationsConfiguration().changeSettings(JDK_LOCATION_WARNING_NOTIFICATION_GROUP.displayId,
                                                                              JDK_LOCATION_WARNING_NOTIFICATION_GROUP.displayType,
                                                                              true /* enable logging */, myOriginalSettings.isShouldReadAloud)
    val initialSettings = NotificationsConfigurationImpl.getSettings(JDK_LOCATION_WARNING_NOTIFICATION_GROUP.displayId)
    assertTrue(initialSettings.isShouldLog)

    // Execute and confirm logging is disabled
    val hyperlink = DoNotShowJdkHomeWarningAgainHyperlink()
    assertNotNull(hyperlink)
    hyperlink.execute(project)
    val changedSettings = NotificationsConfigurationImpl.getSettings(JDK_LOCATION_WARNING_NOTIFICATION_GROUP.displayId)
    assertFalse(changedSettings.isShouldLog)
  }
}

