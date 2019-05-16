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

import static com.android.ide.common.repository.GradleVersion.tryParseAndroidGradlePluginVersion;
import static com.android.tools.idea.gradle.plugin.AndroidPluginInfo.searchInBuildFilesOnly;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.MISSING_BUILD_TOOLS;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider;
import com.android.tools.idea.gradle.project.sync.hyperlink.FixAndroidGradlePluginVersionHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.InstallBuildToolsHyperlink;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MissingBuildToolsErrorHandler extends BaseSyncErrorHandler {
  private final Pattern MISSING_BUILD_TOOLS_PATTERN = Pattern.compile("(Cause: )?([Ff])ailed to find Build Tools revision (.*)");

  @Override
  @Nullable
  protected String findErrorMessage(@NotNull Throwable rootCause, @NotNull Project project) {
    String text = rootCause.getMessage();
    if (isNotEmpty(text)) {
      Matcher matcher = MISSING_BUILD_TOOLS_PATTERN.matcher(getFirstLineMessage(text));
      if ((rootCause instanceof IllegalStateException || rootCause instanceof ExternalSystemException) && matcher.matches()) {
        updateUsageTracker(project, MISSING_BUILD_TOOLS);
        return text;
      }
    }
    return null;
  }

  @Override
  @NotNull
  protected List<NotificationHyperlink> getQuickFixHyperlinks(@NotNull Project project, @NotNull String text) {
    Matcher matcher = MISSING_BUILD_TOOLS_PATTERN.matcher(getFirstLineMessage(text));
    if (matcher.matches()) {
      String version = matcher.group(3);
      GradleVersion currentAGPVersion = null;
      AndroidPluginInfo result = searchInBuildFilesOnly(project);
      if (result != null) {
        currentAGPVersion = result.getPluginVersion();
      }
      GradleVersion recommendedAGPVersion = tryParseAndroidGradlePluginVersion(LatestKnownPluginVersionProvider.INSTANCE.get());
      return getQuickFixHyperlinks(version, currentAGPVersion, recommendedAGPVersion, GradleUtil.hasKtsBuildFiles(project));
    }
    return Collections.emptyList();
  }

  @VisibleForTesting
  static List<NotificationHyperlink> getQuickFixHyperlinks(@NotNull String version,
                                                           @Nullable GradleVersion currentAGPVersion,
                                                           @Nullable GradleVersion recommendedAGPVersion,
                                                           boolean hasKTSBuildFiles) {
    List<NotificationHyperlink> hyperlinks = new ArrayList<>();
    hyperlinks.add(new InstallBuildToolsHyperlink(version));
    //TODO(b/130224064): need to remove check when kts fully supported
    if (hasKTSBuildFiles) {
      return hyperlinks;
    }
    if (currentAGPVersion == null || recommendedAGPVersion == null || currentAGPVersion.compareTo(recommendedAGPVersion) < 0) {
      hyperlinks.add(new FixAndroidGradlePluginVersionHyperlink());
    }
    return hyperlinks;
  }
}