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

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.tools.idea.gradle.project.sync.common.CommandLineArgs.isInTestingMode;
import static com.android.tools.idea.sdk.IdeSdks.MAC_JDK_CONTENT_PATH;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.io.FileUtil.toCanonicalPath;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.flags.StudioFlags;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.jetbrains.android.download.AndroidProfilerDownloader;
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
  List<File> doFindAndroidStudioLocalMavenRepoPaths() {
    List<File> repoPaths = new ArrayList<>();
    // Repo path candidates, the path should be relative to tools/idea.
    List<String> repoCandidates = new ArrayList<>();
    // Add prebuilt offline repo
    String studioCustomRepo = System.getenv("STUDIO_CUSTOM_REPO");
    if (studioCustomRepo != null) {
      List<File> pathsFromEnv = repoPathsFromString(studioCustomRepo, File::isDirectory);
      repoPaths.addAll(pathsFromEnv);
    }
    else {
      repoCandidates.add("/../../out/repo");
    }

    // Add locally published offline studio repo
    repoCandidates.add("/../../out/studio/repo");
    // Add prebuilts repo.
    repoCandidates.add("/../../prebuilts/tools/common/m2/repository");

    for (String candidate : repoCandidates) {
      File offlineRepo = new File(toCanonicalPath(PathManager.getHomePath() + toSystemDependentName(candidate)));
      if (offlineRepo.isDirectory()) {
        repoPaths.add(offlineRepo);
      }
    }

    return ImmutableList.copyOf(repoPaths);
  }

  @NotNull
  @VisibleForTesting
  List<File> repoPathsFromString(@NotNull String studioCustomRepo, @NotNull Predicate<File> isValid) {
    String pathSeparator = System.getProperty("path.separator", ";");
    List<File> paths = new ArrayList<>();
    for (String customRepo : studioCustomRepo.split(pathSeparator)) {
      File customRepoPath = new File(toCanonicalPath(toSystemDependentName(customRepo)));
      if (!isValid.test(customRepoPath)) {
        Logger.getInstance(EmbeddedDistributionPaths.class).warn("Invalid path in STUDIO_CUSTOM_REPO environment variable: " + customRepoPath);
      }
      paths.add(customRepoPath);
    }
    return paths;
  }

  @NotNull
  public File findEmbeddedProfilerTransform() {
    String path = "plugins/android/resources/profilers-transform.jar";
    File file = new File(PathManager.getHomePath(), path);
    if (file.exists()) {
      return file;
    }

    file  = getOptionalIjPath(path);
    if (file != null && file.exists()) {
      return file;
    }

    // Development build
    assert IdeInfo.getInstance().isAndroidStudio(): "Bazel paths exist only in AndroidStudio development mode";
    String relativePath = toSystemDependentName("/../../bazel-bin/tools/base/profiler/transform/profilers-transform.jar");
    return new File(PathManager.getHomePath() + relativePath);
  }

  public String findEmbeddedInstaller() {
    String path = "plugins/android/resources/installer";
    File file = new File(PathManager.getHomePath(), path);
    if (file.exists()) {
      return file.getAbsolutePath();
    }

    file = getOptionalIjPath(path);
    if (file != null && file.exists()) {
      return file.getAbsolutePath();
    }
      // Development mode
    assert IdeInfo.getInstance().isAndroidStudio(): "Bazel paths exist only in AndroidStudio development mode";
    return new File(PathManager.getHomePath(), "../../bazel-bin/tools/base/deploy/installer/android-installer").getAbsolutePath();
  }

  @Nullable
  private File getOptionalIjPath(String path) {
    // IJ does not bundle some large resources from android plugin, and downloads them on demand.
    AndroidProfilerDownloader.getInstance().makeSureComponentIsInPlace();
    return AndroidProfilerDownloader.getInstance().getHostDir(path);
  }

  @Nullable
  public File findEmbeddedGradleDistributionPath() {
    File distributionPath = getDefaultRootDirPath();
    if (distributionPath != null) {
      // Release build
      Logger log = getLog();
      File embeddedPath = new File(distributionPath, "gradle-" + GRADLE_LATEST_VERSION);
      log.info("Looking for embedded Gradle distribution at '" + embeddedPath.getPath() + "'");
      if (embeddedPath.isDirectory()) {
        log.info("Found embedded Gradle " + GRADLE_LATEST_VERSION);
        return embeddedPath;
      }
      log.info("Unable to find embedded Gradle " + GRADLE_LATEST_VERSION);
      return null;
    }

    // Development build.
    String ideHomePath = getIdeHomePath();
    String relativePath = toSystemDependentName("/../../tools/external/gradle");
    distributionPath = new File(toCanonicalPath(ideHomePath + relativePath));
    if (distributionPath.isDirectory()) {
      return distributionPath;
    }

    // Development build.
    String localDistributionPath = System.getProperty("local.gradle.distribution.path");
    if (localDistributionPath != null) {
      distributionPath = new File(toCanonicalPath(localDistributionPath));
      if (distributionPath.isDirectory()) {
        return distributionPath;
      }
    }

    return null;
  }

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

  public @NotNull Path getEmbeddedJdkPath() {
    String ideHomePath = getIdeHomePath();

    Path jdkRootPath = Paths.get(ideHomePath, SystemInfo.isMac ? join("jre", "jdk") : "jre");
    if (Files.isDirectory(jdkRootPath)) {
      // Release build.
      return getSystemSpecificJdkPath(jdkRootPath);
    }

    // If AndroidStudio runs from IntelliJ IDEA sources
    if (System.getProperty("android.test.embedded.jdk") != null) {
      Path jdkDir = Paths.get(System.getProperty("android.test.embedded.jdk"));
      assert Files.exists(jdkDir);
      return jdkDir;
    }

    // Development build.
    String jdkDevPath = System.getProperty("studio.dev.jdk", ideHomePath + "/../../prebuilts/studio/jdk");
    String relativePath = toSystemDependentName(jdkDevPath);
    jdkRootPath = Paths.get(toCanonicalPath(relativePath), "jdk11");
    if (SystemInfo.isWindows) {
      jdkRootPath = jdkRootPath.resolve("win64");
    }
    else if (SystemInfo.isLinux) {
      jdkRootPath = jdkRootPath.resolve("linux");
    }
    else if (SystemInfo.isMac) {
      jdkRootPath = jdkRootPath.resolve("mac");
    }
    return getSystemSpecificJdkPath(jdkRootPath);
  }

  private static @NotNull Path getSystemSpecificJdkPath(Path jdkRootPath) {
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
