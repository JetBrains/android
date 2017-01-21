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
package com.android.tools.idea.gradle.project.sync;

import com.android.tools.idea.gradle.project.sync.errors.SyncErrorHandler;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessages;
import com.android.tools.idea.gradle.util.PositionInFile;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.NonNavigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

import static com.android.tools.idea.gradle.project.sync.messages.SyncMessage.DEFAULT_GROUP;
import static com.intellij.openapi.externalSystem.service.notification.NotificationCategory.ERROR;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.util.text.StringUtil.splitByLines;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

public class SyncErrorHandlerManager {
  @NotNull private final SyncErrorHandler[] myErrorHandlers;
  @NotNull private final Project myProject;

  SyncErrorHandlerManager(@NotNull Project project) {
    this(project, SyncErrorHandler.getExtensions());
  }

  @VisibleForTesting
  SyncErrorHandlerManager(@NotNull Project project, @NotNull SyncErrorHandler[] errorHandlers) {
    myErrorHandlers = errorHandlers;
    myProject = project;
  }

  // Create NotificationData and call sync error handlers
  public void handleError(@NotNull Throwable error) {
    SyncMessages syncMessages = SyncMessages.getInstance(myProject);
    Pair<Throwable, String> rootCauseAndLocation = getRootCauseAndLocation(error);
    Throwable rootCause = rootCauseAndLocation.getFirst();
    String location = rootCauseAndLocation.getSecond();

    ExternalSystemException exception = createExternalSystemException(rootCause);

    String message = ExternalSystemApiUtil.buildErrorMessage(exception);
    PositionInFile positionInFile = createPositionInFile(location);
    NotificationData notificationData = syncMessages.createNotification(DEFAULT_GROUP, message, ERROR, positionInFile);

    for (SyncErrorHandler errorHandler : myErrorHandlers) {
      if (errorHandler.handleError(exception, notificationData, myProject)) {
        break;
      }
    }

    if (notificationData.getNavigatable() == null) {
      notificationData.setNavigatable(NonNavigatable.INSTANCE);
    }
    syncMessages.report(notificationData);
  }

  @NotNull
  private static ExternalSystemException createExternalSystemException(@NotNull Throwable rootCause) {
    String errMessage = rootCause.getMessage();
    if (errMessage == null) {
      StringWriter writer = new StringWriter();
      rootCause.printStackTrace(new PrintWriter(writer));
      errMessage = writer.toString();
    }

    if (!errMessage.isEmpty() && Character.isLowerCase(errMessage.charAt(0))) {
      // Message starts with lower case letter. Sentences should start with uppercase.
      errMessage = "Cause: " + errMessage;
    }
    ExternalSystemException exception = new ExternalSystemException(errMessage);
    exception.initCause(rootCause);
    return exception;
  }

  @Nullable
  private static PositionInFile createPositionInFile(@Nullable String location) {
    PositionInFile positionInFile = null;
    if (isNotEmpty(location)) {
      Pair<String, Integer> pair = SyncErrorHandler.getErrorLocation(location);
      if (pair != null) {
        VirtualFile errorFile = findFileByIoFile(new File(pair.getFirst()), true);
        if (errorFile != null) {
          positionInFile = new PositionInFile(errorFile, pair.getSecond(), -1);
        }
      }
    }
    return positionInFile;
  }

  @NotNull
  private static Pair<Throwable, String> getRootCauseAndLocation(@NotNull Throwable error) {
    Throwable rootCause = error;
    String location = null;
    while (true) {
      if (location == null) {
        location = getLocationFrom(rootCause);
      }
      if (rootCause.getCause() == null || rootCause.getCause().getMessage() == null) {
        break;
      }
      rootCause = rootCause.getCause();
    }
    //noinspection ConstantConditions
    return Pair.create(rootCause, location);
  }

  @Nullable
  private static String getLocationFrom(@NotNull Throwable error) {
    String errorToString = error.toString();
    if (errorToString.contains("LocationAwareException")) {
      // LocationAwareException is never passed, but converted into a PlaceholderException that has the toString value of the original
      // LocationAwareException.
      String location = error.getMessage();
      if (location != null && (location.startsWith("Build file '") || location.startsWith("Settings file '"))) {
        // Only the first line contains the location of the error. Discard the rest.
        String[] lines = splitByLines(location);
        return lines.length > 0 ? lines[0] : null;
      }
    }
    return null;
  }
}