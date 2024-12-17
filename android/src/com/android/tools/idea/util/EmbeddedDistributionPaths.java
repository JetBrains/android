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
package com.android.tools.idea.util;

import static com.android.tools.idea.sdk.IdeSdks.MAC_JDK_CONTENT_PATH;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.system.CpuArch;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service
public final class EmbeddedDistributionPaths {
  @NotNull
  public static EmbeddedDistributionPaths getInstance() {
    return ApplicationManager.getApplication().getService(EmbeddedDistributionPaths.class);
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
      String embeddedJdkPath = System.getProperty("embedded.jdk.path", "prebuilts/studio/jdk/jbr-next").trim();
      Path jdkDir = getJdkRootPathFromSourcesRoot(embeddedJdkPath);

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
  public static Path getJdkRootPathFromSourcesRoot(String embeddedJdkPath) {
    Path jdkRootPath = StudioPathManager.resolvePathFromSourcesRoot(embeddedJdkPath);
    if (SystemInfo.isWindows) {
      if (embeddedJdkPath.endsWith("jdk8")) { // our prebuilt JDK 1.8: has distinct win32/win64.  In practice we will want win64 always.
        jdkRootPath = jdkRootPath.resolve("win64");
      }
      else {
        jdkRootPath = jdkRootPath.resolve("win");
      }
    }
    else if (SystemInfo.isLinux) {
      jdkRootPath = jdkRootPath.resolve("linux");
    }
    else if (SystemInfo.isMac && CpuArch.isArm64()) {
      jdkRootPath = jdkRootPath.resolve("mac-arm64");
    }
    else if (SystemInfo.isMac) {
      jdkRootPath = jdkRootPath.resolve("mac");
    }
    Path jdkDir = getSystemSpecificJdkPath(jdkRootPath);
    return jdkDir;
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
