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
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.project.sync.hyperlink.FixAndroidGradlePluginVersionHyperlink;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.android.tools.idea.gradle.plugin.AndroidPluginInfo.searchInBuildFilesOnly;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class OldAndroidPluginErrorHandler extends BaseSyncErrorHandler {
  private static final Pattern PATTERN =
    Pattern.compile("The android gradle plugin version .+ is too old, please update to the latest version.");

  @Override
  @Nullable
  protected String findErrorMessage(@NotNull Throwable rootCause, @NotNull Project project) {
    // The android gradle plugin version 2.3.0-alpha1 is too old, please update to the latest version.
    // To override this check from the command line please set the ANDROID_DAILY_OVERRIDE environment variable to "e3353206c64a2c010454e8bb4f2d7187b56c198"
    String text = rootCause.getMessage();
    if (isMatching(text)) {
      updateUsageTracker();
      // This way we remove extra lines and spaces from original message.
      return Joiner.on('\n').join(Splitter.on('\n').omitEmptyStrings().trimResults().splitToList(text));
    }
    return null;
  }

  @VisibleForTesting
  static boolean isMatching(@Nullable String text) {
    if (isNotEmpty(text)) {
      String firstLine = getFirstLineMessage(text);
      if (firstLine.startsWith("Plugin is too old, please update to a more recent version")) {
        return true;
      }

      return PATTERN.matcher(firstLine).matches();
    }
    return false;
  }

  @Override
  @NotNull
  protected List<NotificationHyperlink> getQuickFixHyperlinks(@NotNull Project project, @NotNull String text) {
    List<NotificationHyperlink> hyperlinks = new ArrayList<>();
    hyperlinks.add(new FixAndroidGradlePluginVersionHyperlink());
    AndroidPluginInfo result = searchInBuildFilesOnly(project);
    if (result != null && result.getPluginBuildFile() != null) {
      hyperlinks.add(new OpenFileHyperlink(result.getPluginBuildFile().getPath()));
    }
    return hyperlinks;
  }
}