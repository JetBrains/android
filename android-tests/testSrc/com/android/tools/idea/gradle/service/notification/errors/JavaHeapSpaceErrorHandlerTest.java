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
package com.android.tools.idea.gradle.service.notification.errors;

import com.google.common.collect.Lists;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.project.Project;
import junit.framework.TestCase;

import java.util.List;

import static org.easymock.EasyMock.createMock;

/**
 * Tests for {@link JavaHeapSpaceErrorHandler}.
 */
public class JavaHeapSpaceErrorHandlerTest extends TestCase {
  private ExternalSystemException myError;
  private NotificationData myNotification;
  private Project myProject;

  private JavaHeapSpaceErrorHandler myErrorHandler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myError = new ExternalSystemException("Test");
    myNotification = new NotificationData("Test", "Testing", NotificationCategory.ERROR, NotificationSource.PROJECT_SYNC);
    myProject = createMock(Project.class);
    myErrorHandler = new JavaHeapSpaceErrorHandler();
  }

  public void testHandleErrorWithLongMessage() throws Exception {
    List<String> message = Lists.newArrayList(
      "Unable to start the daemon process.",
      "This problem might be caused by incorrect configuration of the daemon.",
      "For example, an unrecognized jvm option is used.",
      "Please refer to the user guide chapter on the daemon at http://gradle.org/docs/1.12/userguide/gradle_daemon.html",
      "Please read below process output to find out more:",
      "-----------------------",
      "Error occurred during initialization of VM",
      "Could not reserve enough space for object heap",
      "Error: Could not create the Java Virtual Machine.",
      "Error: A fatal exception has occurred. Program will exit."
    );

    assertTrue(myErrorHandler.handleError(message, myError, myNotification, myProject));
    String notification = myNotification.getMessage();
    assertTrue(notification.startsWith("Unable to start the daemon process: could not reserve enough space for object heap."));
  }

  public void testHandleErrorWithShortMessage() throws Exception {
    List<String> message = Lists.newArrayList("Out of memory: Java heap space");

    assertTrue(myErrorHandler.handleError(message, myError, myNotification, myProject));
    String notification = myNotification.getMessage();
    assertTrue(notification.startsWith("Out of memory: Java heap space."));
  }
}
