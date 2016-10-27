/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.notification;

import com.android.tools.idea.gradle.service.notification.errors.AbstractSyncErrorHandler;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.project.Project;
import junit.framework.TestCase;

import java.util.Arrays;
import java.util.List;

import static org.easymock.classextension.EasyMock.*;

/**
 * Tests for {@link com.android.tools.idea.gradle.service.notification.GradleNotificationExtension}.
 */
public class GradleNotificationExtensionTest extends TestCase {
  private AbstractSyncErrorHandler myHandler1;
  private AbstractSyncErrorHandler myHandler2;
  private NotificationData myNotification;
  private Project myProject;

  private GradleNotificationExtension myNotificationExtension;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myHandler1 = createMock(AbstractSyncErrorHandler.class);
    myHandler2 = createMock(AbstractSyncErrorHandler.class);
    myNotification = new NotificationData("Title", "Message", NotificationCategory.ERROR, NotificationSource.PROJECT_SYNC);
    myProject = createMock(Project.class);

    myNotificationExtension = new GradleNotificationExtension(Arrays.asList(myHandler1, myHandler2));
  }

  public void testCustomizeWithExternalSystemException() throws Exception {
    List<String> message = Arrays.asList("Testing");
    //noinspection ThrowableInstanceNeverThrown
    ExternalSystemException error = new ExternalSystemException("Testing");
    // myHandler1 returns 'true', myHandler2 should not be invoked.
    expect(myHandler1.handleError(message, error, myNotification, myProject)).andStubReturn(true);
    replay(myHandler1, myHandler2);

    myNotificationExtension.customize(myNotification, myProject, error);

    verify(myHandler1, myHandler2);

    // myHandler1 returns 'false', myHandler2 should be invoked;
    reset(myHandler1, myHandler2);
    expect(myHandler1.handleError(message, error, myNotification, myProject)).andStubReturn(false);
    expect(myHandler2.handleError(message, error, myNotification, myProject)).andStubReturn(true);
    replay(myHandler1, myHandler2);

    myNotificationExtension.customize(myNotification, myProject, error);

    verify(myHandler1, myHandler2);
  }
}
