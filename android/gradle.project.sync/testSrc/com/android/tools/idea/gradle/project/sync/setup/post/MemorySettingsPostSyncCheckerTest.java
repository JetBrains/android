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

import com.android.tools.analytics.HostData;
import com.android.tools.analytics.stubs.StubOperatingSystemMXBean;
import com.android.tools.idea.gradle.project.sync.setup.post.MemorySettingsPostSyncChecker.MemorySettingsNotification;
import com.android.tools.idea.memorysettings.MemorySettingsRecommendation;
import com.intellij.diagnostic.VMOptions;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationsManager;
import com.intellij.testFramework.HeavyPlatformTestCase;
import java.io.IOException;
import org.mockito.Mock;

import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class MemorySettingsPostSyncCheckerTest extends HeavyPlatformTestCase {
  @Mock
  private TimeBasedReminder myReminder;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    cleanNotification();
  }

  @Override
  public void tearDown() throws Exception {
    cleanNotification();
    super.tearDown();
  }

  public void testNoNotificationIfSmallRam() {
    stubHostData(4);
    MemorySettingsPostSyncChecker.checkSettings(myProject, myReminder);
    Notification[] notifications = getNotifications();
    assertSize(0, notifications);
  }

  public void testNoNotificationIfShouldNotCheck() {
    when(myReminder.shouldAsk()).thenReturn(false);
    MemorySettingsPostSyncChecker.checkSettings(myProject, myReminder);
    Notification[] notifications = getNotifications();
    assertSize(0, notifications);
  }

  public void testNotificationIfRecommended() throws IOException {
    when(myReminder.shouldAsk()).thenReturn(true);
    // set the heap size to be small so there is a recommendation made
    stubHostData(16);
    MemorySettingsPostSyncChecker.checkSettings(myProject, myReminder);
    MemorySettingsPostSyncChecker.checkSettings(myProject, myReminder);
    Notification[] notifications = getNotifications();
    // Check twice but there should be only one notification
    assertSize(1, notifications);
  }

  private void cleanNotification() {
    Notification[] notifications = getNotifications();
    for (Notification notification : notifications) {
      notification.expire();
    }
  }

  private Notification[] getNotifications() {
    return NotificationsManager.getNotificationsManager().getNotificationsOfType(
      MemorySettingsNotification.class, myProject);
  }

  private void stubHostData(int machineMemInGB) {
    HostData.setOsBean(new StubOperatingSystemMXBean() {
      @Override
      public long getTotalPhysicalMemorySize() {
        return machineMemInGB * 1024 * 1024L * 1024L;
      }
    });
  }
}