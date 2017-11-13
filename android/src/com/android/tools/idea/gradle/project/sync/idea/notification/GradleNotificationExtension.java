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
package com.android.tools.idea.gradle.project.sync.idea.notification;

import com.android.tools.idea.gradle.project.sync.errors.SyncErrorHandler;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationExtension;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import com.intellij.pom.NonNavigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;

public class GradleNotificationExtension implements ExternalSystemNotificationExtension {
  @NotNull private final SyncErrorHandler[] myErrorHandlers;

  @SuppressWarnings("unused")
  public GradleNotificationExtension() {
    this(SyncErrorHandler.getExtensions());
  }

  @VisibleForTesting
  @TestOnly
  GradleNotificationExtension(@NotNull SyncErrorHandler... errorHandlers) {
    myErrorHandlers = errorHandlers;
  }

  @Override
  @NotNull
  public ProjectSystemId getTargetExternalSystemId() {
    return GRADLE_SYSTEM_ID;
  }

  @Override
  public void customize(@NotNull NotificationData notification, @NotNull Project project, @Nullable Throwable error) {
    // See https://code.google.com/p/android/issues/detail?id=226786
    GradleSyncMessages.getInstance(project).removeProjectMessages();

    Throwable cause = error;
    if (error instanceof UndeclaredThrowableException) {
      cause = ((UndeclaredThrowableException)error).getUndeclaredThrowable();
      if (cause instanceof InvocationTargetException) {
        cause = ((InvocationTargetException)cause).getTargetException();
      }
    }
    if (cause instanceof ExternalSystemException) {
      handleError((ExternalSystemException)cause, notification, project);
    }
    if (notification.getNavigatable() == null) {
      notification.setNavigatable(NonNavigatable.INSTANCE);
    }
  }

  private void handleError(@NotNull ExternalSystemException error, @NotNull NotificationData notification, @NotNull Project project) {
    for (SyncErrorHandler errorHandler : myErrorHandlers) {
      if (errorHandler.handleError(error, notification, project)) {
        return;
      }
    }
  }
}
