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

import com.android.tools.idea.gradle.project.sync.hyperlink.EnableEmbeddedRepoHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.SearchInBuildFilesHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.ToggleOfflineModeHyperlink;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.tools.idea.gradle.project.sync.hyperlink.EnableEmbeddedRepoHyperlink.shouldEnableEmbeddedRepo;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class MissingDependencyErrorHandler extends SyncErrorHandler {
  private static final Pattern MISSING_MATCHING_DEPENDENCY_PATTERN = Pattern.compile("Could not find any version that matches (.*)\\.");
  public static final Pattern MISSING_DEPENDENCY_PATTERN = Pattern.compile("Could not find (.*)\\.");

  @Override
  public boolean handleError(@NotNull ExternalSystemException error, @NotNull NotificationData notification, @NotNull Project project) {
    //noinspection ThrowableResultOfMethodCallIgnored
    Throwable rootCause = getRootCause(error);
    String text = rootCause.getMessage();
    List<String> message = getMessageLines(text);
    if (message.isEmpty()) {
      return false;
    }
    String firstLine = message.get(0);

    Matcher matcher = MISSING_MATCHING_DEPENDENCY_PATTERN.matcher(firstLine);
    if (matcher.matches()) {
      String dependency = matcher.group(1);
      handleMissingDependency(notification, project, firstLine, dependency, Collections.emptyList());
      return true;
    }

    String lastLine = message.get(message.size() - 1);

    matcher = MISSING_DEPENDENCY_PATTERN.matcher(firstLine);
    if (matcher.matches() && message.size() > 1 && message.get(1).startsWith("Required by:")) {
      String dependency = matcher.group(1);
      List<NotificationHyperlink> hyperlinks = new ArrayList<>();
      if (isNotEmpty(dependency)) {
        if (lastLine != null) {
          Pair<String, Integer> errorLocation = getErrorLocation(lastLine);
          if (errorLocation != null) {
            // We have a location in file, show the "Open File" hyperlink.
            String filePath = errorLocation.getFirst();
            int line = errorLocation.getSecond();
            hyperlinks.add(new OpenFileHyperlink(filePath, line - 1));
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
        handleMissingDependency(notification, project, line, dependency, Collections.emptyList());
        return true;
      }
    }

    return false;
  }

  private static void handleMissingDependency(@NotNull NotificationData notification,
                                              @NotNull Project project,
                                              @NotNull String msg,
                                              @NotNull String dependency,
                                              @NotNull List<NotificationHyperlink> additionalHyperlinks) {
    List<NotificationHyperlink> hyperlinks = new ArrayList<>(additionalHyperlinks);
    ToggleOfflineModeHyperlink disableOfflineMode = ToggleOfflineModeHyperlink.disableOfflineMode(project);
    if (disableOfflineMode != null) {
      hyperlinks.add(0, disableOfflineMode);
    }
    hyperlinks.add(new SearchInBuildFilesHyperlink(dependency));

    // Offer to turn on embedded offline repo if the missing dependency can be found there.
    if (shouldEnableEmbeddedRepo(dependency)) {
      hyperlinks.add(new EnableEmbeddedRepoHyperlink());
    }
    GradleSyncMessages.getInstance(project).updateNotification(notification, msg, hyperlinks);
  }
}