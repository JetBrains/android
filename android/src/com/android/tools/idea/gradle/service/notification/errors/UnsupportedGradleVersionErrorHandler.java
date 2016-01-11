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

import com.android.tools.idea.gradle.service.notification.hyperlink.CreateGradleWrapperHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenGradleSettingsHyperlink;
import com.google.common.collect.Lists;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.tools.idea.gradle.service.notification.hyperlink.FixGradleVersionInWrapperHyperlink.createIfProjectUsesGradleWrapper;
import static com.android.tools.idea.gradle.util.GradleUtil.findWrapperPropertiesFile;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleProjectSettings;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static org.jetbrains.plugins.gradle.service.project.AbstractProjectImportErrorHandler.FIX_GRADLE_VERSION;
import static org.jetbrains.plugins.gradle.service.project.AbstractProjectImportErrorHandler.OPEN_GRADLE_SETTINGS;

public class UnsupportedGradleVersionErrorHandler extends AbstractSyncErrorHandler {
  private static final Pattern UNSUPPORTED_GRADLE_VERSION_PATTERN = Pattern.compile("Gradle version (.*) is required.*?");

  @Override
  public boolean handleError(@NotNull List<String> message,
                             @NotNull ExternalSystemException error,
                             @NotNull NotificationData notification,
                             @NotNull Project project) {
    String firstLine = message.get(0);
    List<NotificationHyperlink> hyperlinks = null;
    String supportedGradleVersion = getSupportedGradleVersion(firstLine);
    if (isNotEmpty(supportedGradleVersion)) {
      hyperlinks = getQuickFixHyperlinks(project, supportedGradleVersion);
    }
    else {
      String lastLine = message.get(message.size() - 1);
      if (OPEN_GRADLE_SETTINGS.equals(lastLine) || lastLine.contains(FIX_GRADLE_VERSION)) {
        hyperlinks = getQuickFixHyperlinks(project, null);
      }
    }

    if (hyperlinks != null) {
      updateNotification(notification, project, error.getMessage(), hyperlinks);
      return true;
    }

    return false;
  }

  @Nullable
  public static String getSupportedGradleVersion(@NotNull String message) {
    Matcher matcher = UNSUPPORTED_GRADLE_VERSION_PATTERN.matcher(message);
    if (matcher.matches()) {
      String version = matcher.group(1);
      if (isNotEmpty(version)) {
        return version;
      }
    }
    return null;
  }

  @NotNull
  public static List<NotificationHyperlink> getQuickFixHyperlinks(@NotNull Project project, @Nullable String gradleVersion) {
    List<NotificationHyperlink> hyperlinks = Lists.newArrayList();
    File wrapperPropertiesFile = findWrapperPropertiesFile(project);
    if (wrapperPropertiesFile != null) {
      // It is very likely that we need to fix the model version as well. Do everything in one shot.
      NotificationHyperlink hyperlink = createIfProjectUsesGradleWrapper(project, gradleVersion);
      if (hyperlink != null) {
        hyperlinks.add(hyperlink);
      }
    }
    else {
      GradleProjectSettings gradleProjectSettings = getGradleProjectSettings(project);
      if (gradleProjectSettings != null && gradleProjectSettings.getDistributionType() == DistributionType.LOCAL) {
        hyperlinks.add(new CreateGradleWrapperHyperlink());
      }
    }
    hyperlinks.add(new OpenGradleSettingsHyperlink());
    return hyperlinks;
  }
}
