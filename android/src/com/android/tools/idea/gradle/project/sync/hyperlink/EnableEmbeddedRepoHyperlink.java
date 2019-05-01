/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.hyperlink;

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.project.settings.AndroidStudioGradleIdeSettings;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_EMBEDDED_REPO_ENABLED_BY_QUICKFIX;
import static java.io.File.separator;

/**
 * A {@link NotificationHyperlink} that offers the user to enable embedded offline Maven repository.
 */
public class EnableEmbeddedRepoHyperlink extends NotificationHyperlink {
  public EnableEmbeddedRepoHyperlink() {
    super("enable.embedded.maven.repository", "Enable embedded Maven repository and sync project");
  }

  @Override
  protected void execute(@NotNull Project project) {
    AndroidStudioGradleIdeSettings.getInstance().setEmbeddedMavenRepoEnabled(true);
    GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(project, TRIGGER_EMBEDDED_REPO_ENABLED_BY_QUICKFIX);
  }

  /**
   * @param missingDependency the unresolved dependency.
   * @return {@code true} if embedded Maven repository is disabled, and the missing dependency can be found there.
   */
  public static boolean shouldEnableEmbeddedRepo(@NotNull String missingDependency) {
    GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString(missingDependency);
    if (coordinate == null) {
      return false;
    }
    if (AndroidStudioGradleIdeSettings.getInstance().isEmbeddedMavenRepoEnabled()) {
      return false;
    }

    String subPathWithoutExtension = getPathWithoutExtensionFromCoordinate(coordinate);
    List<String> fileExtensions = Arrays.asList(".jar", ".aar");
    List<File> repoPaths = EmbeddedDistributionPaths.getInstance().findAndroidStudioLocalMavenRepoPaths();
    for (File repoPath : repoPaths) {
      for (String extension : fileExtensions) {
        File expectedPath = new File(repoPath, subPathWithoutExtension + extension);
        if (expectedPath.exists()) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * @return the relative path of a GradleCoordinate without file extension, for example, "com/android/tools/build/gradle/3.1.0-dev/gradle-3.1.0-dev".
   */
  @VisibleForTesting
  @NotNull
  static String getPathWithoutExtensionFromCoordinate(@NotNull GradleCoordinate coordinate) {
    StringBuilder path = new StringBuilder();
    String groupId = coordinate.getGroupId();
    String artifactId = coordinate.getArtifactId();
    String revision = coordinate.getRevision();
    String fileNamePrefix = "";

    if (groupId != null) {
      path.append(groupId.replace(".", separator)).append(separator);
    }
    if (artifactId != null) {
      path.append(artifactId.replace(".", separator)).append(separator);
      fileNamePrefix = artifactId + "-";
    }
    path.append(revision).append(separator);
    path.append(fileNamePrefix).append(revision);
    return path.toString();
  }
}
