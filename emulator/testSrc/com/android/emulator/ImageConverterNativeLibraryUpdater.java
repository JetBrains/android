/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.emulator;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.system.CpuArch;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jetbrains.annotations.NotNull;

/**
 * Updates the libimage_converter library.
 * Usage: bazel run //tools/adt/idea/emulator:update_libimage_converter
 */
public class ImageConverterNativeLibraryUpdater {

  public static void main(@NotNull String @NotNull [] args) {
    try {
      Path workspaceRoot = getWorkspaceRoot();
      Path builtFromSource = Paths.get("tools/adt/idea/emulator/native", getLibName()).toAbsolutePath();
      if (Files.notExists(builtFromSource)) {
        // Running outside of Bazel.
        builtFromSource = workspaceRoot.resolve("bazel-bin/tools/adt/idea/emulator/native").resolve(getLibName());
        if (Files.notExists(builtFromSource)) {
          fatalError(builtFromSource + " does not exist; run \"bazel build //tools/adt/idea/emulator/native:libimage_converter\"");
        }
      }
      byte[] builtFromSourceContents = Files.readAllBytes(builtFromSource);

      Path prebuilt = workspaceRoot.resolve("tools/adt/idea/emulator/native").resolve(getPlatformName()).resolve(getLibName());
      Files.write(prebuilt, builtFromSourceContents);
      System.out.println(prebuilt + " updated");
    }
    catch (Throwable e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static void fatalError(@NotNull String message) {
    System.err.println(message);
    System.exit(1);
  }

  private static @NotNull Path getWorkspaceRoot() throws IOException {
    Path currDir = Paths.get("").toAbsolutePath();
    while (currDir != null) {
      if (Files.exists(currDir.resolve("WORKSPACE"))) {
        return currDir;
      }
      // Bazel puts a "DO_NOT_BUILD_HERE" file inside the "execroot" directory, when using `bazel run` or `bazel test`.
      // It contains a single line with the absolute path of the workspace.
      Path doNotBuildHere = currDir.resolve("DO_NOT_BUILD_HERE");
      if (Files.exists(doNotBuildHere)) {
        return Paths.get(new String(Files.readAllBytes(doNotBuildHere), UTF_8));
      }
      currDir = currDir.getParent();
    }
    fatalError("Can't find the \"WORKSPACE\" or the \"DO_NOT_BUILD_HERE\" file in any of the parent directories");
    return Paths.get("");
  }

  private static @NotNull String getLibName() {
    return System.mapLibraryName("image_converter");
  }

  private static @NotNull String getPlatformName() {
    if (SystemInfo.isWindows) {
      return "win";
    }
    if (SystemInfo.isMac) {
      return CpuArch.isArm64() ? "mac_arm" : "mac";
    }
    if (SystemInfo.isLinux) {
      return "linux";
    }
    fatalError("Unable to determine the current platform");
    return "";
  }
}

