/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea;

import com.android.testutils.TestUtils;
import com.android.tools.asdriver.tests.AndroidStudioInstallation;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.system.CpuArch;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.file.PathUtils;

public class Patcher {
  Path tempDir;
  Path studioDir;
  Path modifiedStudioDir;

  public Patcher(Path tempDir, Path studioDir) {
    this.tempDir = tempDir;
    this.studioDir = studioDir;
    modifiedStudioDir = this.tempDir.resolve("patch_machinery");
  }

  /**
   * Copies the entire Android Studio installation, modifies a file, then creates a patch out of
   * the modification.
   */
  public Path createPatch(String currentBuildNumber, String updatedBuildNumber) throws IOException, InterruptedException {
    System.out.println("Creating patch machinery in " + modifiedStudioDir);
    Files.createDirectories(modifiedStudioDir);

    long startTime = System.currentTimeMillis();
    PathUtils.copyDirectory(studioDir, modifiedStudioDir);
    long elapsedTime = System.currentTimeMillis() - startTime;
    System.out.println("Copying took " + elapsedTime + "ms");
    AndroidStudioInstallation installation = AndroidStudioInstallation.fromDir(tempDir, modifiedStudioDir);
    installation.setBuildNumber(updatedBuildNumber);

    return runPatcher(currentBuildNumber, updatedBuildNumber);
  }

  /**
   * Runs the patcher to produce a patch file from two input directories.
   */
  private Path runPatcher(String currentBuildNumber, String updatedBuildNumber) throws IOException, InterruptedException {
    Path updaterBin = TestUtils.getBinPath("tools/adt/idea/studio/updater");
    Path patchDir = tempDir.resolve("patch");
    Files.createDirectories(patchDir);
    System.out.println("Creating the patch in " + patchDir);

    String platform = "unix";
    if (SystemInfo.isMac) {
      platform = CpuArch.isArm64() ? "mac_arm" : "mac";
    } else if (SystemInfo.isWindows) {
      platform = "win";
    }
    String patchName = String.format("AI-%s-%s-patch-%s.jar", currentBuildNumber, updatedBuildNumber, platform);
    Path patchFile = patchDir.resolve(patchName);
    List<String> params = new ArrayList<>(Arrays.asList(
      updaterBin.toString(),
      "create",
      "old",
      "new",
      studioDir.toString(),
      modifiedStudioDir.toString(),
      patchFile.toString(),
      "--zip_as_binary",
      "--strict"
    ));
    if (SystemInfo.isMac) {
      params.add("--root");
      params.add("Contents");
    }
    ProcessBuilder pb = new ProcessBuilder(params);
    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
    pb.redirectError(ProcessBuilder.Redirect.INHERIT);

    long startTime = System.currentTimeMillis();
    Process p = pb.start();
    p.waitFor(90, TimeUnit.SECONDS);
    long elapsedTime = System.currentTimeMillis() - startTime;
    System.out.println("Creating the patch took " + elapsedTime + "ms");

    if (p.exitValue() != 0) {
      throw new RuntimeException("The patcher exited with code " + p.exitValue());
    }

    return patchFile;
  }
}
