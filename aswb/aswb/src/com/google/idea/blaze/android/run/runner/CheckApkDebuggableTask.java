/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.runner;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.execution.common.RunConfigurationNotifier;
import com.android.tools.idea.run.blaze.BlazeLaunchContext;
import com.android.tools.idea.run.blaze.BlazeLaunchTask;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceFile;
import com.google.devrel.gmscore.tools.apk.arsc.Chunk;
import com.google.devrel.gmscore.tools.apk.arsc.XmlAttribute;
import com.google.devrel.gmscore.tools.apk.arsc.XmlChunk;
import com.google.devrel.gmscore.tools.apk.arsc.XmlStartElementChunk;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipFile;
import org.jetbrains.annotations.NotNull;

/** Checks APKs to see if they are debuggable and warn the user if they aren't. */
public class CheckApkDebuggableTask implements BlazeLaunchTask {
  private static final String ID = "APK_DEBUGGABILITY_CHECKER";
  private final BlazeAndroidDeployInfo deployInfo;

  public CheckApkDebuggableTask(Project project, BlazeAndroidDeployInfo deployInfo) {
    this.deployInfo = deployInfo;
  }

  @Override
  public void run(@NotNull BlazeLaunchContext launchContext) throws ExecutionException {
    checkApkDebuggableTaskDelegate(
        launchContext.getEnv().getProject(), deployInfo, launchContext.getDevice());
  }

  /**
   * Checks if all APKs in the deploy info are debuggable and output an error message to console if
   * any of them aren't. This check doesn't apply if the target device is a running a debug build
   * android (e.g. userdebug build) and will simply return.
   */
  @VisibleForTesting
  public static void checkApkDebuggableTaskDelegate(
      Project project, BlazeAndroidDeployInfo deployInfo, IDevice device)
      throws ExecutionException {
    if (isDebugDevice(device)) {
      return;
    }
    try {
      ImmutableList<String> nonDebuggableApkNames =
          getNonDebuggableDeployApks(deployInfo).stream()
              .map(File::getName)
              .collect(toImmutableList());
      if (nonDebuggableApkNames.isEmpty()) {
        return;
      }
      // Use "and" as delimiter because there won't be more than 2 APKs, so "and" makes more sense.
      String message =
          "The \"android:debuggable\" attribute is not set to \"true\" in "
              + String.join(" and ", nonDebuggableApkNames)
              + ". Debugger may not attach properly or attach at all."
              + " Please ensure \"android:debuggable\" attribute is set to true or"
              + " overridden to true via manifest overrides.";
      RunConfigurationNotifier.INSTANCE.notifyWarning(project, "", message);
    } catch (IOException e) {
      throw new ExecutionException("Could not read deploy apks: " + e.getMessage());
    }
  }

  private static ImmutableList<File> getNonDebuggableDeployApks(BlazeAndroidDeployInfo deployInfo)
      throws IOException {
    ImmutableList.Builder<File> nonDebuggableApks = ImmutableList.builder();
    for (File apk : deployInfo.getApksToDeploy()) {
      if (!isApkDebuggable(apk)) {
        nonDebuggableApks.add(apk);
      }
    }
    return nonDebuggableApks.build();
  }

  /** Returns true if the device is a debug build device. */
  private static boolean isDebugDevice(IDevice device) {
    String roDebuggable = device.getProperty("ro.debuggable");
    return (roDebuggable != null && roDebuggable.equals("1"));
  }

  @VisibleForTesting
  public static boolean isApkDebuggable(File apk) throws IOException {
    try (ZipFile zipFile = new ZipFile(apk);
        InputStream stream = zipFile.getInputStream(zipFile.getEntry("AndroidManifest.xml"))) {
      BinaryResourceFile file = BinaryResourceFile.fromInputStream(stream);
      List<Chunk> chunks = file.getChunks();

      if (chunks.isEmpty()) {
        throw new IllegalArgumentException("Invalid APK, empty manifest");
      }

      if (!(chunks.get(0) instanceof XmlChunk)) {
        throw new IllegalArgumentException("APK manifest chunk[0] != XmlChunk");
      }

      XmlChunk xmlChunk = (XmlChunk) chunks.get(0);
      for (Chunk chunk : xmlChunk.getChunks().values()) {
        if (!(chunk instanceof XmlStartElementChunk)) {
          continue;
        }

        XmlStartElementChunk startChunk = (XmlStartElementChunk) chunk;
        if (startChunk.getName().equals("application")) {
          for (XmlAttribute attribute : startChunk.getAttributes()) {
            if (attribute.name().equals("debuggable")) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }
}
