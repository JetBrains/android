/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.notification.errors;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.project.Project;
import junit.framework.TestCase;

import java.util.List;

import static org.easymock.EasyMock.createMock;

/**
 * Tests for {@link NdkLocationNotFoundErrorHandler}
 */
public class NdkLocationNotFoundErrorHandlerTest extends TestCase {
  private ExternalSystemException myError;
  private NotificationData myNotification;
  private Project myProject;

  private NdkLocationNotFoundErrorHandler myErrorHandler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myError = new ExternalSystemException("Test");
    myNotification = new NotificationData("Test", "Testing", NotificationCategory.ERROR, NotificationSource.PROJECT_SYNC);
    myProject = createMock(Project.class);
    myErrorHandler = new NdkLocationNotFoundErrorHandler();
  }

  public void testHandleError() throws Exception {
    List<String> message = ImmutableList.of("NDK location not found. Define location with ndk.dir in the local.properties file " +
                                            "or with an ANDROID_NDK_HOME environment variable.");
    assertTrue(myErrorHandler.handleError(message, myError, myNotification, myProject));
    String notificationMessage = myNotification.getMessage();
    assertEquals(notificationMessage, "Android NDK location is not specified.\n<a href=\"ndk.select\">Select NDK</a>");
    List<String> linkIds = myNotification.getRegisteredListenerIds();
    assertEquals(1, linkIds.size());
    assertEquals("ndk.select", linkIds.get(0));
  }
}
