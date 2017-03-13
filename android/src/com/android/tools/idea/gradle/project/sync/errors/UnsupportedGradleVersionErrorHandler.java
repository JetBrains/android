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

import com.android.tools.idea.gradle.project.sync.hyperlink.CreateGradleWrapperHyperlink;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenGradleSettingsHyperlink;
import com.android.tools.idea.gradle.util.GradleProjectSettingsFinder;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.intellij.openapi.project.Project;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.tools.idea.gradle.project.sync.hyperlink.FixGradleVersionInWrapperHyperlink.createIfProjectUsesGradleWrapper;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.UNSUPPORTED_GRADLE_VERSION;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static org.jetbrains.plugins.gradle.service.project.AbstractProjectImportErrorHandler.FIX_GRADLE_VERSION;

public class UnsupportedGradleVersionErrorHandler extends BaseSyncErrorHandler {
  private static final Pattern UNSUPPORTED_GRADLE_VERSION_PATTERN_1 =
    Pattern.compile("Minimum supported Gradle version is (.*)\\. Current version is.*?");
  private static final Pattern UNSUPPORTED_GRADLE_VERSION_PATTERN_2 = Pattern.compile("Gradle version (.*) is required.*?");

  @Override
  @Nullable
  protected String findErrorMessage(@NotNull Throwable rootCause, @NotNull Project project) {
    String newMsg = "";
    if (isOldGradleVersion(rootCause)) {
      newMsg = "The project is using an unsupported version of Gradle.\n" + FIX_GRADLE_VERSION;
    }
    String text = rootCause.getMessage();
    if (text != null) {
      if (UNSUPPORTED_GRADLE_VERSION_PATTERN_1.matcher(text).matches() || UNSUPPORTED_GRADLE_VERSION_PATTERN_2.matcher(text).matches()) {
        int i = text.indexOf("If using the gradle wrapper");
        if (i != -1) {
          // Remove portion "If using the gradle wrapper".
          text = text.substring(0, i);
          text = text.trim();
        }
        if (!text.endsWith(".")) {
          text += ".";
        }
        newMsg = text + EMPTY_LINE + "Please fix the project's Gradle settings.";
      }
    }
    if (isNotEmpty(newMsg)) {
      updateUsageTracker(UNSUPPORTED_GRADLE_VERSION);
      return newMsg;
    }
    return null;
  }

  private static boolean isOldGradleVersion(@NotNull Throwable error) {
    String msg = error.getMessage();
    if (error instanceof UnsupportedVersionException) {
      return true;
    }
    if (error instanceof UnsupportedMethodException) {
      if (msg != null && msg.contains("GradleProject.getBuildScript")) {
        return true;
      }
    }
    if (error instanceof ClassNotFoundException) {
      if (msg != null && msg.contains(ToolingModelBuilderRegistry.class.getName())) {
        return true;
      }
    }
    return false;
  }

  @Override
  @NotNull
  protected List<NotificationHyperlink> getQuickFixHyperlinks(@NotNull Project project, @NotNull String text) {
    String gradleVersion = getSupportedGradleVersion(getFirstLineMessage(text));
    return getQuickFixHyperlinksWithGradleVersion(project, gradleVersion);
  }

  @Nullable
  private static String getSupportedGradleVersion(@NotNull String message) {
    return getSupportedGradleVersion(message, UNSUPPORTED_GRADLE_VERSION_PATTERN_1, UNSUPPORTED_GRADLE_VERSION_PATTERN_2);
  }

  @Nullable
  private static String getSupportedGradleVersion(@NotNull String message, @NotNull Pattern... patterns) {
    for (Pattern pattern : patterns) {
      Matcher matcher = pattern.matcher(message);
      if (matcher.matches()) {
        String version = matcher.group(1);
        if (isNotEmpty(version)) {
          return version;
        }
      }
    }
    return null;
  }

  @NotNull
  public static List<NotificationHyperlink> getQuickFixHyperlinksWithGradleVersion(@NotNull Project project,
                                                                                   @Nullable String gradleVersion) {
    List<NotificationHyperlink> hyperlinks = new ArrayList<>();
    GradleWrapper gradleWrapper = GradleWrapper.find(project);
    if (gradleWrapper != null) {
      // It is very likely that we need to fix the model version as well. Do everything in one shot.
      NotificationHyperlink hyperlink = createIfProjectUsesGradleWrapper(project, gradleVersion);
      if (hyperlink != null) {
        hyperlinks.add(hyperlink);
      }
    }
    else {
      GradleProjectSettings gradleProjectSettings = GradleProjectSettingsFinder.getInstance().findGradleProjectSettings(project);
      if (gradleProjectSettings != null && gradleProjectSettings.getDistributionType() == DistributionType.LOCAL) {
        hyperlinks.add(new CreateGradleWrapperHyperlink());
      }
    }
    hyperlinks.add(new OpenGradleSettingsHyperlink());
    return hyperlinks;
  }
}