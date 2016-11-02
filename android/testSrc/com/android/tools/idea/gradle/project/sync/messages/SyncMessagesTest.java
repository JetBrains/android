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
import junit.framework.TestCase;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static com.android.tools.idea.gradle.project.sync.messages.GroupNames.*;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.intellij.openapi.externalSystem.service.notification.NotificationSource.PROJECT_SYNC;
import static org.mockito.Mockito.verify;
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

  public void testRemoveCommonGroups() {
    mySyncMessages.removeCommonGroups();
    verify(myNotificationManager).clearNotifications(PROJECT_STRUCTURE_ISSUES, PROJECT_SYNC, GRADLE_SYSTEM_ID);
    verify(myNotificationManager).clearNotifications(MISSING_DEPENDENCIES_BETWEEN_MODULES, PROJECT_SYNC, GRADLE_SYSTEM_ID);
    verify(myNotificationManager).clearNotifications(FAILED_TO_SET_UP_DEPENDENCIES, PROJECT_SYNC, GRADLE_SYSTEM_ID);
    verify(myNotificationManager).clearNotifications(VARIANT_SELECTION_CONFLICTS, PROJECT_SYNC, GRADLE_SYSTEM_ID);
    verify(myNotificationManager).clearNotifications(EXTRA_GENERATED_SOURCES, PROJECT_SYNC, GRADLE_SYSTEM_ID);
    verify(myNotificationManager).clearNotifications(SyncMessage.DEFAULT_GROUP, PROJECT_SYNC, GRADLE_SYSTEM_ID);
  }
}