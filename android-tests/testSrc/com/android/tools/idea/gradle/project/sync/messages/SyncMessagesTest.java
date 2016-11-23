/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.messages;

import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.intellij.openapi.externalSystem.service.notification.NotificationCategory.ERROR;
import static com.intellij.openapi.externalSystem.service.notification.NotificationSource.PROJECT_SYNC;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link SyncMessages}.
 */
public class SyncMessagesTest extends IdeaTestCase {
  @Mock private ExternalSystemNotificationManager myNotificationManager;

  private SyncMessages mySyncMessages;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    initMocks(this);

    mySyncMessages = new SyncMessages(getProject(), myNotificationManager);
  }

  public void testGetErrorCount() {
    int expectedCount = 6;
    when(myNotificationManager.getMessageCount(PROJECT_SYNC, ERROR, GRADLE_SYSTEM_ID)).thenReturn(expectedCount);

    int actualCount = mySyncMessages.getErrorCount();
    assertEquals(expectedCount, actualCount);

    verify(myNotificationManager).getMessageCount(PROJECT_SYNC, ERROR, GRADLE_SYSTEM_ID);
  }

  public void testGetMessageCount() {
    String group = "Test";

    int expectedCount = 6;
    when(myNotificationManager.getMessageCount(group, PROJECT_SYNC, null, GRADLE_SYSTEM_ID)).thenReturn(expectedCount);

    int actualCount = mySyncMessages.getMessageCount(group);
    assertEquals(expectedCount, actualCount);

    verify(myNotificationManager).getMessageCount(group, PROJECT_SYNC, null, GRADLE_SYSTEM_ID);
  }

  public void testIsEmptyWithMessages() {
    when(myNotificationManager.getMessageCount(PROJECT_SYNC, null, GRADLE_SYSTEM_ID)).thenReturn(6);

    assertFalse(mySyncMessages.isEmpty());

    verify(myNotificationManager).getMessageCount(PROJECT_SYNC, null, GRADLE_SYSTEM_ID);
  }

  public void testIsEmptyWithoutMessages() {
    when(myNotificationManager.getMessageCount(PROJECT_SYNC, null, GRADLE_SYSTEM_ID)).thenReturn(0);

    assertTrue(mySyncMessages.isEmpty());

    verify(myNotificationManager).getMessageCount(PROJECT_SYNC, null, GRADLE_SYSTEM_ID);
  }

  public void testRemoveProjectMessages() {
    mySyncMessages.removeProjectMessages();
    verify(myNotificationManager).clearNotifications("Project Structure Issues", PROJECT_SYNC, GRADLE_SYSTEM_ID);
    verify(myNotificationManager).clearNotifications("Missing Dependencies", PROJECT_SYNC, GRADLE_SYSTEM_ID);
    verify(myNotificationManager).clearNotifications("Variant Selection Conflicts", PROJECT_SYNC, GRADLE_SYSTEM_ID);
    verify(myNotificationManager).clearNotifications("Generated Sources", PROJECT_SYNC, GRADLE_SYSTEM_ID);
    verify(myNotificationManager).clearNotifications("Gradle Sync Issues", PROJECT_SYNC, GRADLE_SYSTEM_ID);
  }
}