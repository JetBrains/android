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
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationsManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class MemorySettingsPostSyncCheckerTest extends IdeaTestCase {
  @Mock
  private TimeBasedMemorySettingsCheckerReminder myReminder;

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
    stubHostData(4096);
    MemorySettingsPostSyncChecker.checkSettings(myProject, myReminder);
    Notification[] notifications = getNotifications();
    assertSize(0, notifications);
  }

  public void testNoNotificationIfShouldNotCheck() {
    when(myReminder.shouldCheck(myProject)).thenReturn(false);
    MemorySettingsPostSyncChecker.checkSettings(myProject, myReminder);
    Notification[] notifications = getNotifications();
    assertSize(0, notifications);
  }

  public void testNotificationIfRecommended() {
    when(myReminder.shouldCheck(myProject)).thenReturn(true);
    stubHostData(16 * 1024);
    MemorySettingsPostSyncChecker.checkSettings(myProject, myReminder);
    MemorySettingsPostSyncChecker.checkSettings(myProject, myReminder);
    Notification[] notifications = getNotifications();
    // Check twice but there should be only one notification
    assertSize(1, notifications);
  }

  public void testRecommend() {
    assertEquals(getRecommended(1280, 5120, 20), -1);
    assertEquals(getRecommended(1280, 5120, 100), 1536);
    assertEquals(getRecommended(1280, 5120, 200), 1536);
    assertEquals(getRecommended(1280, 8192, 20), 2048);
    assertEquals(getRecommended(1280, 8192, 120), 2048);
    assertEquals(getRecommended(1280, 8192, 200), 2048);
    assertEquals(getRecommended(1280, 16 * 1024, 20), 2048);
    assertEquals(getRecommended(1280, 16 * 1024, 50), 2048);
    assertEquals(getRecommended(1280, 16 * 1024, 100), 3072);
    assertEquals(getRecommended(1280, 16 * 1024, 200), 4096);
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

  private int getRecommended(int currentXmxInMB, int machineMemInMB, int moduleCount) {
    Project project = mock(Project.class);
    ModuleManager moduleManager = mock(ModuleManager.class);

    when(project.getComponent(ModuleManager.class)).thenReturn(moduleManager);
    Module[] modules = new Module[moduleCount];
    when(moduleManager.getModules()).thenReturn(modules);
    stubHostData(machineMemInMB);
    return MemorySettingsPostSyncChecker.getRecommended(project, currentXmxInMB);
  }

  private void stubHostData(int machineMemInMB) {
    HostData.setOsBean(new StubOperatingSystemMXBean() {
      @Override
      public long getTotalPhysicalMemorySize() {
        return machineMemInMB * 1024 * 1024L;
      }
    });
  }
}