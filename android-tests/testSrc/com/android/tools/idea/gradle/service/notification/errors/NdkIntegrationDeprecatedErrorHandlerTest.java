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
 * Tests for {@link NdkIntegrationDeprecatedErrorHandler}
 */
public class NdkIntegrationDeprecatedErrorHandlerTest extends TestCase {
  private ExternalSystemException myError;
  private NotificationData myNotification;
  private Project myProject;

  private NdkIntegrationDeprecatedErrorHandler myErrorHandler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myError = new ExternalSystemException("Test");
    myNotification = new NotificationData("Test", "Testing", NotificationCategory.ERROR, NotificationSource.PROJECT_SYNC);
    myProject = createMock(Project.class);
    myErrorHandler = new NdkIntegrationDeprecatedErrorHandler();
  }

  public void testHandleError() throws Exception {
    List<String> message = ImmutableList.of("Error: NDK integration is deprecated in the current plugin.  Consider trying the new " +
                                            "experimental plugin.  For details, see " +
                                            "http://tools.android.com/tech-docs/new-build-system/gradle-experimental.  " +
                                            "Set \"android.useDeprecatedNdk=true\" in gradle.properties to continue using the current " +
                                            "NDK integration.");
    assertTrue(myErrorHandler.handleError(message, myError, myNotification, myProject));
    String notificationMessage = myNotification.getMessage();
    assertEquals(notificationMessage, "NDK integration is deprecated in the current plugin.\n" +
                                      "<a href=\"http://tools.android.com/tech-docs/new-build-system/gradle-experimental\">" +
                                      "Consider trying the new experimental plugin</a><br><a href=\"useDeprecatedNdk\">" +
                                      "Set \"android.useDeprecatedNdk=true\" in gradle.properties to continue using the current NDK " +
                                      "integration</a>");
    List<String> linkIds = myNotification.getRegisteredListenerIds();
    assertEquals(2, linkIds.size());
    assertEquals("http://tools.android.com/tech-docs/new-build-system/gradle-experimental", linkIds.get(0));
    assertEquals("useDeprecatedNdk", linkIds.get(1));
  }
}
