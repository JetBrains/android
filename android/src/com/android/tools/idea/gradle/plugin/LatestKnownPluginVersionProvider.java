/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.plugin;

import static com.android.ide.common.repository.GradleCoordinate.COMPARE_PLUS_HIGHER;
import static com.android.ide.common.repository.MavenRepositories.getHighestInstalledVersion;
import static com.android.tools.idea.gradle.plugin.AndroidPluginInfo.ARTIFACT_ID;
import static com.android.tools.idea.gradle.plugin.AndroidPluginInfo.GROUP_ID;

import com.android.Version;
import com.android.annotations.Nullable;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

public class LatestKnownPluginVersionProvider {
  public static final LatestKnownPluginVersionProvider INSTANCE = new LatestKnownPluginVersionProvider();

  private static final String[] lastEmbeddedResult = new String[1];

  static {
    lastEmbeddedResult[0] = "unseen";
  }

  @NotNull
  public String get() {
    List<File> repoPaths = EmbeddedDistributionPaths.getInstance().findAndroidStudioLocalMavenRepoPaths();
    if (repoPaths.isEmpty()) {
      return getAndroidGradlePluginVersion();
    }
    else {
      Optional<GradleCoordinate> highestValueCoordinate = repoPaths.stream()
        .map(repoPath -> getHighestInstalledVersion(GROUP_ID, ARTIFACT_ID, repoPath.toPath(), null /* filter */, true /* allow preview */))
        .filter(Objects::nonNull)
        .max(COMPARE_PLUS_HIGHER);

      @Nullable String embeddedResult = highestValueCoordinate.map(GradleCoordinate::getRevision).orElse(null);
      @NotNull String version = highestValueCoordinate.map(GradleCoordinate::getRevision).orElse(getAndroidGradlePluginVersion());
      synchronized(lastEmbeddedResult) {
        // We don't expect very many state changes; things should only change if an internal user gets an error from failing to find a
        // -dev version of AGP, following which they might well build and publish such a dev version to the embedded repo and try again.
        if (!Objects.equals(embeddedResult, lastEmbeddedResult[0])) {
          Logger logger = Logger.getInstance(MethodHandles.lookup().lookupClass());
          if (!highestValueCoordinate.isPresent()) {
            logger.info("'" + ARTIFACT_ID + "' plugin missing from the offline Maven repo, will use default " + version);
          }
          else {
            logger.info("'" + ARTIFACT_ID + "' plugin version " + version + " found in offline Maven repo");
          }
        }
        lastEmbeddedResult[0] = embeddedResult;
      }
      return version;
    }
  }

  private static String getAndroidGradlePluginVersion() {
    if (IdeInfo.getInstance().isAndroidStudio()) {
      return Version.ANDROID_GRADLE_PLUGIN_VERSION;
    } else {
      return AndroidGradlePluginVersion.LATEST_STABLE_VERSION;
    }
  }
}
