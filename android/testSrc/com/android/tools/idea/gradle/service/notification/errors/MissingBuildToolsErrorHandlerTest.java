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
 * Tests for {@link MissingBuildToolsErrorHandler}
 */
public class MissingBuildToolsErrorHandlerTest extends TestCase {
  private NotificationData myNotification;
  private Project myProject;

  private MissingBuildToolsErrorHandler myErrorHandler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myNotification = new NotificationData("Test", "Testing", NotificationCategory.ERROR, NotificationSource.PROJECT_SYNC);
    myProject = createMock(Project.class);
    myErrorHandler = new MissingBuildToolsErrorHandler();
  }


  public void testHandleErrorOldMessage() throws Exception {
    ExternalSystemException error = new ExternalSystemException("failed to find Build Tools revision 24.0.0 rc4");
    List<String> message = ImmutableList.of("failed to find Build Tools revision 24.0.0 rc4"); // With Gradle plugin 2.1 or earlier.
    assertTrue(myErrorHandler.handleError(message, error, myNotification, myProject));
    String notificationMessage = myNotification.getMessage();
    assertEquals(notificationMessage, "failed to find Build Tools revision 24.0.0 rc4\n" +
                                      "<a href=\"install.build.tools\">Install Build Tools 24.0.0 rc4 and sync project</a>");
    List<String> linkIds = myNotification.getRegisteredListenerIds();
    assertEquals(1, linkIds.size());
    assertEquals("install.build.tools", linkIds.get(0));
  }

  public void testHandleErrorUpdatedMessage() throws Exception {
    ExternalSystemException error = new ExternalSystemException("Failed to find Build Tools revision 24.0.0 rc4");
    List<String> message = ImmutableList.of("Failed to find Build Tools revision 24.0.0 rc4"); // With Gradle plugin 2.2 or later.
    assertTrue(myErrorHandler.handleError(message, error, myNotification, myProject));
    String notificationMessage = myNotification.getMessage();
    assertEquals(notificationMessage, "Failed to find Build Tools revision 24.0.0 rc4\n" +
                                      "<a href=\"install.build.tools\">Install Build Tools 24.0.0 rc4 and sync project</a>");
    List<String> linkIds = myNotification.getRegisteredListenerIds();
    assertEquals(1, linkIds.size());
    assertEquals("install.build.tools", linkIds.get(0));
  }
}
