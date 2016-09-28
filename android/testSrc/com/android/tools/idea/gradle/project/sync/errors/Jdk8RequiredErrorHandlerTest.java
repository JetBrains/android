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
package com.android.tools.idea.gradle.project.sync.errors;

import com.android.tools.idea.gradle.project.sync.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessagesStub;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.externalSystem.service.notification.NotificationSource.PROJECT_SYNC;

/**
 * Tests for {@link Jdk8RequiredErrorHandler}.
 */
public class Jdk8RequiredErrorHandlerTest extends AndroidGradleTestCase {
  private SyncMessagesStub myMessagesStub;
  private Jdk8RequiredErrorHandler myErrorHandler;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myMessagesStub = SyncMessagesStub.replaceSyncMessagesService(getProject());
    myErrorHandler = new Jdk8RequiredErrorHandler();
  }

  public void testHandleErrorWithSyncMessage() {
    //noinspection ThrowableInstanceNeverThrown
    UnsupportedClassVersionError error = createValidError();
    Project project = getProject();

    boolean result = myErrorHandler.handleError(error, project);
    // Verify that the error handler was capable of handling the exception.
    assertTrue(result);

    // Verity that SyncMessages was invoked to show an error message.
    SyncMessage message = myMessagesStub.getReportedMessage();
    assertNotNull(message);

    String[] text = message.getText();
    assertThat(text).hasLength(1);

    String line = text[0];
    assertThat(line).endsWith("Please use JDK 8 or newer.");
  }

  public void testHandleErrorWithInvalidError() {
    boolean result = myErrorHandler.handleError(new RuntimeException("Hello World!"), getProject());
    assertFalse(result);

    SyncMessage message = myMessagesStub.getReportedMessage();
    assertNull(message);
  }

  public void testHandleErrorWithNotificationData() {
    //noinspection ThrowableResultOfMethodCallIgnored
    UnsupportedClassVersionError cause = createValidError();
    ExternalSystemException error = new ExternalSystemException(cause);
    NotificationData notification = createNotification();

    Project project = getProject();

    boolean result = myErrorHandler.handleError(Collections.emptyList(), error, notification, project);
    // Verify that the error handler was capable of handling the exception.
    assertTrue(result);

    // Verity that SyncMessages was invoked to update NotificationData.
    String message = notification.getMessage();
    assertThat(message).contains("Please use JDK 8 or newer.");
  }

  @NotNull
  private static UnsupportedClassVersionError createValidError() {
    return new UnsupportedClassVersionError("com/android/jack/api/ConfigNotSupportedException : Unsupported major.minor version 52.0");
  }

  public void testHandleErrorWithNotificationDataAndInvalidError() {
    ExternalSystemException error = new ExternalSystemException("Hello World!");
    NotificationData notification = createNotification();

    Project project = getProject();

    boolean result = myErrorHandler.handleError(Collections.emptyList(), error, notification, project);
    assertFalse(result);
  }

  @NotNull
  private static NotificationData createNotification() {
    return new NotificationData("Gradle Sync", "", NotificationCategory.ERROR, PROJECT_SYNC);
  }
}