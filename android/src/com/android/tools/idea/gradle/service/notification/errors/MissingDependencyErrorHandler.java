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

import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.SearchInBuildFilesHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.ToggleOfflineModeHyperlink;
import com.google.common.collect.Lists;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MissingDependencyErrorHandler extends AbstractSyncErrorHandler {
  private static final Pattern MISSING_MATCHING_DEPENDENCY_PATTERN = Pattern.compile("Could not find any version that matches (.*)\\.");
  private static final Pattern MISSING_DEPENDENCY_PATTERN = Pattern.compile("Could not find (.*)\\.");

  @Override
  public boolean handleError(@NotNull List<String> message,
                             @NotNull ExternalSystemException error,
                             @NotNull NotificationData notification,
                             @NotNull Project project) {
    String firstLine = message.get(0);

    Matcher matcher = MISSING_MATCHING_DEPENDENCY_PATTERN.matcher(firstLine);
    if (matcher.matches()) {
      String dependency = matcher.group(1);
      handleMissingDependency(notification, project, firstLine, dependency);
      return true;
    }

    String lastLine = message.get(message.size() - 1);

    matcher = MISSING_DEPENDENCY_PATTERN.matcher(firstLine);
    if (matcher.matches() && message.size() > 1 && message.get(1).startsWith("Required by:")) {
      String dependency = matcher.group(1);
      NotificationHyperlink[] hyperlinks = EMPTY;
      if (StringUtil.isNotEmpty(dependency)) {
        if (lastLine != null) {
          Pair<String, Integer> errorLocation = getErrorLocation(lastLine);
          if (errorLocation != null) {
            // We have a location in file, show the "Open File" hyperlink.
            String filePath = errorLocation.getFirst();
            int line = errorLocation.getSecond();
            hyperlinks = new NotificationHyperlink[] {new OpenFileHyperlink(filePath, line - 1)};
          }
        }
        handleMissingDependency(notification, project, error.getMessage(), dependency, hyperlinks);
        return true;
      }
    }

    for (String line : message) {
      // This happens when Gradle cannot find the Android Gradle plug-in in Maven Central or jcenter.
      if (line == null) {
        continue;
      }
      matcher = MISSING_MATCHING_DEPENDENCY_PATTERN.matcher(line);
      if (matcher.matches()) {
        String dependency = matcher.group(1);
        handleMissingDependency(notification, project, line, dependency);
        return true;
      }
    }

    return false;
  }

  private static void handleMissingDependency(@NotNull NotificationData notification,
                                              @NotNull Project project,
                                              @NotNull String msg,
                                              @NotNull String dependency,
                                              @NotNull NotificationHyperlink... additionalHyperlinks) {
    List<NotificationHyperlink> hyperlinks = Lists.newArrayList(additionalHyperlinks);
    ToggleOfflineModeHyperlink disableOfflineMode = ToggleOfflineModeHyperlink.disableOfflineMode(project);
    if (disableOfflineMode != null) {
      hyperlinks.add(0, disableOfflineMode);
    }
    hyperlinks.add(new SearchInBuildFilesHyperlink(dependency));
    updateNotification(notification, project, msg, hyperlinks);
  }
}
