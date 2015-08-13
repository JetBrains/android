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

import com.android.tools.idea.gradle.service.notification.hyperlink.OpenFileHyperlink;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class GenericErrorHandler extends AbstractSyncErrorHandler {
  @Override
  public boolean handleError(@NotNull List<String> message,
                             @NotNull ExternalSystemException error,
                             @NotNull NotificationData notification,
                             @NotNull Project project) {
    String lastLine = message.get(message.size() - 1);
    if (lastLine != null) {
      Pair<String, Integer> errorLocation = getErrorLocation(lastLine);
      if (errorLocation != null) {
        String filePath = errorLocation.getFirst();
        int line = errorLocation.getSecond();
        updateNotification(notification, project, error.getMessage(), new OpenFileHyperlink(filePath, line - 1));
        return false;
      }
    }
    // Error messages may contain a file path and line number, but we need to add a hyperlink to open the file at those coordinates.
    String filePath = notification.getFilePath();
    if (isNotEmpty(filePath)) {
      int lineIndex = notification.getLine() - 1; // lines are zero based.
      int column = notification.getColumn();
      updateNotification(notification, project, error.getMessage(), new OpenFileHyperlink(filePath, "Open File", lineIndex, column));
    }
    return false; // This error handler should be the last in the list of handlers. It does not matter if returns 'true' or 'false'.
  }
}
