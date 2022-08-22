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
package com.android.tools.idea.gradle.util;

import static com.android.tools.idea.ui.GuiTestingService.isInTestingMode;
import static com.android.tools.idea.sdk.IdeSdks.MAC_JDK_CONTENT_PATH;
import static com.intellij.openapi.util.io.FileUtil.toCanonicalPath;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.util.StudioPathManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EmbeddedDistributionPaths {
  @NotNull
  public static EmbeddedDistributionPaths getInstance() {
    return ApplicationManager.getApplication().getService(EmbeddedDistributionPaths.class);
  }

  @NotNull
  public List<File> findAndroidStudioLocalMavenRepoPaths() {
    if (!StudioFlags.USE_DEVELOPMENT_OFFLINE_REPOS.get() && !isInTestingMode()) {
      return ImmutableList.of();
    }
    return doFindAndroidStudioLocalMavenRepoPaths();
  }

  @VisibleForTesting
  @NotNull
  static List<File> doFindAndroidStudioLocalMavenRepoPaths() {
    List<File> repoPaths = new ArrayList<>();
    // Add prebuilt offline repo
    String studioCustomRepo = System.getenv("STUDIO_CUSTOM_REPO");
    if (studioCustomRepo != null) {
      try {
        Path customRepoPath = Paths.get(studioCustomRepo).toRealPath();
        if (!Files.isDirectory(customRepoPath)) {
          throw new IllegalArgumentException("Invalid path in STUDIO_CUSTOM_REPO environment variable");
        }
        repoPaths.add(customRepoPath.toFile());
      }
      catch (IOException e) {
        throw new IllegalArgumentException("Invalid path in STUDIO_CUSTOM_REPO environment variable", e);
      }
    }

    if (StudioPathManager.isRunningFromSources()) {
      // Repo path candidates, the path should be relative to tools/idea.
      List<String> repoCandidates = new ArrayList<>();

      if (studioCustomRepo == null) {
        repoCandidates.add("out/repo");
      }

      // Add locally published offline studio repo
      repoCandidates.add("out/studio/repo");
      // Add prebuilts repo.
      repoCandidates.add("prebuilts/tools/common/m2/repository");
      repoCandidates.add(System.getProperty("java.io.tmpdir") + "/offline-maven-repo");
      // TODO: Test repo locations are dynamic and are given via .manifest files, we should not hardcode here
      repoCandidates.add("../maven/repository");

      for (String candidate : repoCandidates) {
        Path candidateDir = StudioPathManager.resolvePathFromSourcesRoot(candidate);
        if (Files.isDirectory(candidateDir)) {
          repoPaths.add(candidateDir.toFile());
        }
      }
    }

    return ImmutableList.copyOf(repoPaths);
  }

  @NotNull
  public File findEmbeddedProfilerTransform() {
    if (StudioPathManager.isRunningFromSources()) {
      // Development build
      return StudioPathManager.resolvePathFromSourcesRoot("bazel-bin/tools/base/profiler/transform/profilers-transform.jar").toFile();
    } else {
      return new File(PathManager.getHomePath(), "plugins/android/resources/profilers-transform.jar");
    }
  }

  /**
   * @return the directory in the source repository that contains the checked-in Gradle distributions. In Android Studio production builds,
   * it returns null.
   */
  @Nullable
  public File findEmbeddedGradleDistributionPath() {
    if (StudioPathManager.isRunningFromSources()) {
      // Development build.
      Path distribution = StudioPathManager.resolvePathFromSourcesRoot("tools/external/gradle");
      if (Files.isDirectory(distribution)) {
        return distribution.toFile();
      }
    }
    return null;
  }

  /**
   * @param gradleVersion the version of Gradle to search for
   * @return The archive file for the requested Gradle distribution, if exists, that is checked into the source repository. In Android
   * Studio production builds, this method always returns null.
   */
  @Nullable
  public File findEmbeddedGradleDistributionFile(@NotNull String gradleVersion) {
    File distributionPath = findEmbeddedGradleDistributionPath();
    if (distributionPath != null) {
      File allDistributionFile = new File(distributionPath, "gradle-" + gradleVersion + "-all.zip");
      if (allDistributionFile.isFile() && allDistributionFile.exists()) {
        return allDistributionFile;
      }

      File binDistributionFile = new File(distributionPath, "gradle-" + gradleVersion + "-bin.zip");
      if (binDistributionFile.isFile() && binDistributionFile.exists()) {
        return binDistributionFile;
      }
    }
    return null;
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(EmbeddedDistributionPaths.class);
  }

  @Nullable
  private static File getDefaultRootDirPath() {
    String ideHomePath = getIdeHomePath();
    File rootDirPath = new File(ideHomePath, "gradle");
    return rootDirPath.isDirectory() ? rootDirPath : null;
  }

  @Nullable
  public Path tryToGetEmbeddedJdkPath() {
    try {
      return getEmbeddedJdkPath();
    }
    catch (Throwable t) {
      Logger.getInstance(EmbeddedDistributionPaths.class).warn("Failed to find a valid embedded JDK", t);
      return null;
    }
  }

  @NotNull
  public Path getEmbeddedJdkPath() {
    if (StudioPathManager.isRunningFromSources()) {
      // If AndroidStudio runs from IntelliJ IDEA sources
      if (System.getProperty("android.test.embedded.jdk") != null) {
        Path jdkDir = Paths.get(System.getProperty("android.test.embedded.jdk"));
        return jdkDir;
      }

      // Development build.
      Path jdkRootPath = StudioPathManager.resolvePathFromSourcesRoot("prebuilts/studio/jdk/jdk11");
      if (SystemInfo.isWindows) {
        jdkRootPath = jdkRootPath.resolve("win");
      }
      else if (SystemInfo.isLinux) {
        jdkRootPath = jdkRootPath.resolve("linux");
      }
      else if (SystemInfo.isMac) {
        jdkRootPath = jdkRootPath.resolve("mac");
      }
      Path jdkDir = getSystemSpecificJdkPath(jdkRootPath);

      // Resolve real path
      //
      // Gradle prior to 6.9 don't work well with symlinks
      // see https://discuss.gradle.org/t/gradle-daemon-different-context/2146/3
      // see https://github.com/gradle/gradle/issues/12840
      //
      // [WARNING] This effective escapes Bazel's sandbox. Remove as soon as possible.
      try {
        Path wellKnownJdkFile = jdkDir.resolve("release");
        jdkDir = wellKnownJdkFile.toRealPath().getParent();
      }
      catch (IOException ignore) {
      }
      return jdkDir;
    } else {
      // Release build.
      String ideHomePath = getIdeHomePath();
      Path jdkRootPath = Paths.get(ideHomePath, "jbr");
      return getSystemSpecificJdkPath(jdkRootPath);
    }
  }

  @NotNull
  private static Path getSystemSpecificJdkPath(Path jdkRootPath) {
    if (SystemInfo.isMac) {
      jdkRootPath = jdkRootPath.resolve(MAC_JDK_CONTENT_PATH);
    }
    if (!Files.isDirectory(jdkRootPath)) {
      throw new Error(String.format("Incomplete or corrupted installation - \"%s\" directory does not exist", jdkRootPath));
    }
    return jdkRootPath;
  }

  @NotNull
  private static String getIdeHomePath() {
    return toSystemDependentName(PathManager.getHomePath());
  }
}
