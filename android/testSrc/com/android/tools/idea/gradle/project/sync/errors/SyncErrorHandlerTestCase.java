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

import com.android.tools.idea.gradle.notification.QuickFixNotificationListener;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessagesStub;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.openapi.externalSystem.service.notification.NotificationSource.PROJECT_SYNC;

public abstract class SyncErrorHandlerTestCase extends IdeaTestCase {
  protected SyncMessagesStub myMessagesStub;
  protected NotificationDataStub myNotification;
  protected Project myProject;

  protected SyncErrorHandler myErrorHandler;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myMessagesStub = SyncMessagesStub.replaceSyncMessagesService(getProject());
    myNotification = createNotification();
    myProject = getProject();
    myErrorHandler = createErrorHandler();
  }

  @NotNull
  private static NotificationDataStub createNotification() {
    return new NotificationDataStub("Gradle Sync", "", NotificationCategory.ERROR, PROJECT_SYNC);
  }

  @NotNull
  protected abstract SyncErrorHandler createErrorHandler();

  public void testHandleErrorWithNotificationDataAndInvalidError() {
    ExternalSystemException error = new ExternalSystemException("Hello World!");

    // Verify that the error handler does not handle irrelevant exception.
    boolean result = myErrorHandler.handleError(error, myNotification, myProject);
    assertFalse(result);

    // Verify that SyncMessage is not updated.
    SyncMessage message = myMessagesStub.getFirstReportedMessage();
    assertNull(message);
  }

  @NotNull
  protected ExternalSystemException createExternalSystemException(Throwable cause) {
    ExternalSystemException error = new ExternalSystemException(cause);
    error.initCause(cause);
    return error;
  }

  public static class NotificationDataStub extends NotificationData {
    @NotNull private final Map<String, QuickFixNotificationListener> myListenersById = new HashMap<>();

    public NotificationDataStub(@NotNull String title,
                                @NotNull String message,
                                @NotNull NotificationCategory notificationCategory,
                                @NotNull NotificationSource notificationSource) {
      super(title, message, notificationCategory, notificationSource);
    }

    @Override
    public void setListener(@NotNull String listenerId, @NotNull NotificationListener listener) {
      super.setListener(listenerId, listener);
      if (listener instanceof QuickFixNotificationListener) {
        myListenersById.put(listenerId, (QuickFixNotificationListener)listener);
      }
    }

    @NotNull
    public Map<String, QuickFixNotificationListener> getListenersById() {
      return myListenersById;
    }
  }
}
