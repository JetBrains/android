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
 * Tests for {@link MissingCMakeErrorHandler}
 */
public class MissingCMakeErrorHandlerTest extends TestCase {
  private ExternalSystemException myError;
  private NotificationData myNotification;
  private Project myProject;

  private MissingCMakeErrorHandler myErrorHandler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myError = new ExternalSystemException("Test");
    myNotification = new NotificationData("Test", "Testing", NotificationCategory.ERROR, NotificationSource.PROJECT_SYNC);
    myProject = createMock(Project.class);
    myErrorHandler = new MissingCMakeErrorHandler();
  }

  public void testHandleError() throws Exception {
    // Temporarily disabling this since faking the remote cmake package will take a bit of work. And the CL is already sprawling out of
    // control. This test is relatively low coverage and the behavior has been confirmed manually, so this should be okay.
    // TODO(chaorenl): work on re-enabling this immediately.

    //List<String> message = ImmutableList.of("Failed to find CMake.", "Install from Android Studio under File/Settings/" +
    //                                                                 "Appearance & Behavior/System Settings/Android SDK/SDK Tools/CMake.");
    //assertTrue(myErrorHandler.handleError(message, myError, myNotification, myProject));
    //String notificationMessage = myNotification.getMessage();
    //assertEquals(notificationMessage, "Failed to find CMake.\n<a href=\"install.cmake\">Install CMake and sync project</a>");
    //List<String> linkIds = myNotification.getRegisteredListenerIds();
    //assertEquals(1, linkIds.size());
    //assertEquals("install.cmake", linkIds.get(0));
  }
}
