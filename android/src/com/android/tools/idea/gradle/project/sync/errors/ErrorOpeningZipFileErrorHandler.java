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

import com.android.annotations.Nullable;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.SyncProjectWithExtraCommandLineOptionsHyperlink;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

// See https://code.google.com/p/android/issues/detail?id=76797
public class ErrorOpeningZipFileErrorHandler extends SyncErrorHandler {
  @Override
  public boolean handleError(@NotNull ExternalSystemException error, @NotNull NotificationData notification, @NotNull Project project) {
    String text = findErrorMessage(getRootCause(error));
    if (text != null) {
      List<NotificationHyperlink> hyperlinks = new ArrayList<>();
      NotificationHyperlink syncProjectHyperlink = SyncProjectWithExtraCommandLineOptionsHyperlink.syncProjectRefreshingDependencies();
      hyperlinks.add(syncProjectHyperlink);
      String newText = text + syncProjectHyperlink.toHtml();
      GradleSyncMessages.getInstance(project).updateNotification(notification, newText, hyperlinks);
      return true;
    }
    return false;
  }

  @Nullable
  private String findErrorMessage(@NotNull Throwable rootCause) {
    String text = rootCause.getMessage();
    if (isNotEmpty(text) && text.contains("error in opening zip file")) {
      updateUsageTracker();
      return "Failed to open zip file.\n" +
             "Gradle's dependency cache may be corrupt (this sometimes occurs after a network connection timeout.)\n";
    }
    return null;
  }
}