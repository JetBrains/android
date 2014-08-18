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

import com.android.tools.idea.gradle.service.notification.hyperlink.FileBugHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.ShowLogHyperlink;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static org.jetbrains.plugins.gradle.service.project.AbstractProjectImportErrorHandler.UNEXPECTED_ERROR_FILE_BUG;

public class UnexpectedErrorHandler extends AbstractSyncErrorHandler {
  @Override
  public boolean handleError(@NotNull List<String> message,
                             @NotNull ExternalSystemException error,
                             @NotNull NotificationData notification,
                             @NotNull Project project) {
    String lastLine = message.get(message.size() - 1);
    if (lastLine != null && lastLine.contains(UNEXPECTED_ERROR_FILE_BUG)) {
      updateNotification(notification, project, error.getMessage(), new FileBugHyperlink(), new ShowLogHyperlink());
      return true;
    }
    return false;
  }
}
