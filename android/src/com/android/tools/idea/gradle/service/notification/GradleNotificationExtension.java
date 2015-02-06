/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.notification;

import com.android.tools.idea.gradle.messages.AbstractNavigatable;
import com.android.tools.idea.gradle.service.notification.errors.*;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationExtension;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Arrays;
import java.util.List;

public class GradleNotificationExtension implements ExternalSystemNotificationExtension {
  private final List<AbstractSyncErrorHandler> myHandlers;

  public GradleNotificationExtension() {
    this(Arrays.asList(AbstractSyncErrorHandler.EP_NAME.getExtensions()));
  }

  @VisibleForTesting
  GradleNotificationExtension(@NotNull List<AbstractSyncErrorHandler> errorHandlers) {
    myHandlers = errorHandlers;
  }

  @Override
  @NotNull
  public ProjectSystemId getTargetExternalSystemId() {
    return GradleConstants.SYSTEM_ID;
  }

  @Override
  public void customize(@NotNull NotificationData notification, @NotNull Project project, @Nullable Throwable error) {
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
      notification.setNavigatable(AbstractNavigatable.NOT_NAVIGATABLE);
    }
  }

  private void handleError(ExternalSystemException error, NotificationData notification, Project project) {
    String msg = error.getMessage();
    if (StringUtil.isEmpty(msg)) {
      return;
    }
    List<String> lines = splitLines(msg);
    for (AbstractSyncErrorHandler handler : myHandlers) {
      if (handler.handleError(lines, error, notification, project)) {
        return;
      }
    }
  }

  @NotNull
  private static List<String> splitLines(@NotNull String s) {
    return Lists.newArrayList(Splitter.on('\n').split(s));
  }
}
