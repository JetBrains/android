/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.util.PositionInFile;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import com.intellij.pom.NonNavigatable;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.project.messages.SyncMessage.DEFAULT_GROUP;
import static com.intellij.openapi.externalSystem.service.notification.NotificationCategory.ERROR;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.buildErrorMessage;

public class SyncErrorHandlerManager {
  @NotNull private final GradleSyncMessages mySyncMessages;
  @NotNull private final ErrorAndLocation.Factory myCauseAndLocationFactory;
  @NotNull private final SyncErrorHandler[] myErrorHandlers;
  @NotNull private final Project myProject;

  public SyncErrorHandlerManager(@NotNull Project project) {
    this(project, GradleSyncMessages.getInstance(project), new ErrorAndLocation.Factory(), SyncErrorHandler.getExtensions());
  }

  @VisibleForTesting
  SyncErrorHandlerManager(@NotNull Project project,
                          @NotNull GradleSyncMessages syncMessages,
                          @NotNull ErrorAndLocation.Factory causeAndLocationFactory,
                          @NotNull SyncErrorHandler... errorHandlers) {
    mySyncMessages = syncMessages;
    myCauseAndLocationFactory = causeAndLocationFactory;
    myErrorHandlers = errorHandlers;
    myProject = project;
  }

  // Create NotificationData and call sync error handlers
  public void handleError(@NotNull Throwable error) {
    ErrorAndLocation errorAndLocation = myCauseAndLocationFactory.create(error);
    ExternalSystemException errorToReport = errorAndLocation.getError();

    String message = buildErrorMessage(errorToReport);
    PositionInFile positionInFile = errorAndLocation.getPositionInFile();
    NotificationData notificationData = mySyncMessages.createNotification(DEFAULT_GROUP, message, ERROR, positionInFile);

    for (SyncErrorHandler errorHandler : myErrorHandlers) {
      if (errorHandler.handleError(errorToReport, notificationData, myProject)) {
        break;
      }
    }

    if (notificationData.getNavigatable() == null) {
      notificationData.setNavigatable(NonNavigatable.INSTANCE);
    }
    mySyncMessages.report(notificationData);
  }
}