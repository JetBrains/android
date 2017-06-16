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
package com.android.tools.idea.gradle.project.sync.idea.notification;

import com.android.tools.idea.gradle.project.sync.errors.SyncErrorHandler;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import static com.intellij.openapi.externalSystem.service.notification.NotificationCategory.ERROR;
import static com.intellij.openapi.externalSystem.service.notification.NotificationSource.PROJECT_SYNC;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link GradleNotificationExtension}.
 */
public class GradleNotificationExtensionTest extends IdeaTestCase {
  @Mock private SyncErrorHandler myHandler1;
  @Mock private SyncErrorHandler myHandler2;
  @Mock private GradleSyncMessages mySyncMessages;

  private NotificationData myNotification;
  private GradleNotificationExtension myNotificationExtension;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myNotification = new NotificationData("Title", "Message", ERROR, PROJECT_SYNC);
    IdeComponents.replaceService(getProject(), GradleSyncMessages.class, mySyncMessages);
    myNotificationExtension = new GradleNotificationExtension(myHandler1, myHandler2);
  }

  public void testCustomizeWithExternalSystemException() throws Exception {
    ExternalSystemException error = new ExternalSystemException("Testing");

    // myHandler1 returns 'true', myHandler2 should not be invoked.
    when(myHandler1.handleError(error, myNotification, myProject)).thenReturn(true);

    myNotificationExtension.customize(myNotification, myProject, error);

    verify(mySyncMessages, times(1)).removeProjectMessages();
    verify(myHandler1, times(1)).handleError(error, myNotification, myProject);
    verify(myHandler2, never()).handleError(error, myNotification, myProject);
  }
}
