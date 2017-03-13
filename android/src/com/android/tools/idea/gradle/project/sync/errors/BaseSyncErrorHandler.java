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

import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public abstract class BaseSyncErrorHandler extends SyncErrorHandler {
  @Override
  public final boolean handleError(@NotNull ExternalSystemException error,
                                   @NotNull NotificationData notification,
                                   @NotNull Project project) {
    String text = findErrorMessage(getRootCause(error), project);
    if (text != null) {
      List<NotificationHyperlink> hyperlinks = getQuickFixHyperlinks(project, text);
      GradleSyncMessages.getInstance(project).updateNotification(notification, text, hyperlinks);
      return true;
    }
    return false;
  }

  @Nullable
  protected abstract String findErrorMessage(@NotNull Throwable rootCause, @NotNull Project project);

  @NotNull
  protected List<NotificationHyperlink> getQuickFixHyperlinks(@NotNull Project project, @NotNull String text) {
    return Collections.emptyList();
  }
}
