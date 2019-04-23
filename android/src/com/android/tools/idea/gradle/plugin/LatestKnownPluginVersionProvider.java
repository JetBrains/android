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
import static com.android.tools.idea.gradle.plugin.AndroidPluginInfo.GROUP_ID;
import static com.android.tools.idea.gradle.plugin.AndroidPluginInfo.ARTIFACT_ID;

import com.android.builder.model.Version;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
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

  @NotNull
  public String get() {
    FileOp fileOp = FileOpUtils.create();

    List<File> repoPaths = EmbeddedDistributionPaths.getInstance().findAndroidStudioLocalMavenRepoPaths();
    Optional<GradleCoordinate> highestValueCoordinate = repoPaths.stream()
      .map(repoPath -> getHighestInstalledVersion(GROUP_ID, ARTIFACT_ID, repoPath, null /* filter */, true /* allow preview */, fileOp))
      .filter(Objects::nonNull)
      .max(COMPARE_PLUS_HIGHER);

    if (!highestValueCoordinate.isPresent()) {
      String version = Version.ANDROID_GRADLE_PLUGIN_VERSION;
      Logger logger = Logger.getInstance(MethodHandles.lookup().lookupClass());
      logger.info("'" + ARTIFACT_ID + "' plugin missing from the offline Maven repo, will use default " + version);
      return version;
    }

    return highestValueCoordinate.get().getRevision();
  }
}
