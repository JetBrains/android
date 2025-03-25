/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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

import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.BuildToolInfo.PathId;
import com.android.tools.sdk.AndroidPlatform;
import com.google.idea.blaze.android.sync.sdk.SdkUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.openapi.project.Project;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** A collection of utilities for extracting information from APKs using aapt. */
public final class AaptUtil {
  static final Pattern PACKAGE_PATTERN = Pattern.compile("^package:\\s+name='([\\w.]+)'");

  /** exception thrown by this class */
  public static class AaptUtilException extends Exception {
    AaptUtilException(String message) {
      super(message);
    }

    AaptUtilException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private AaptUtil() {}

  /** Determines the manifest package name for the given APK. */
  public static String getApkManifestPackage(Project project, File apk) throws AaptUtilException {
    MatchResult packageResult = getAaptBadging(project, apk, PACKAGE_PATTERN);
    if (packageResult == null) {
      throw new AaptUtilException(
          "No match found in `aapt dump badging` for package manifest pattern.");
    }
    return packageResult.group(1);
  }

  /**
   * Uses aapt to dump badging information for the given apk, and extracts information from the
   * output matching the given pattern.
   */
  @Nullable
  private static MatchResult getAaptBadging(Project project, File apk, Pattern pattern)
      throws AaptUtilException {
    if (!apk.exists()) {
      throw new AaptUtilException("apk file does not exist: " + apk);
    }
    AndroidPlatform androidPlatform = SdkUtil.getAndroidPlatform(project);
    if (androidPlatform == null) {
      throw new AaptUtilException(
          "Could not find Android platform sdk for project " + project.getName());
    }
    BuildToolInfo toolInfo = androidPlatform.getSdkData().getLatestBuildTool(true);
    if (toolInfo == null) {
      throw new AaptUtilException(
          "Could not find Android sdk build-tools for project " + project.getName());
    }
    String aapt = toolInfo.getPath(PathId.AAPT);
    GeneralCommandLine commandLine =
        new GeneralCommandLine(aapt, "dump", "badging", apk.getAbsolutePath());
    OSProcessHandler handler;
    try {
      handler = new OSProcessHandler(commandLine);
    } catch (ExecutionException e) {
      throw new AaptUtilException("Could not execute aapt to extract apk information.", e);
    }

    // The wrapped stream is closed by the process handler.
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(handler.getProcess().getInputStream()));
    try {
      return matchPattern(reader, pattern);
    } catch (IOException e) {
      throw new AaptUtilException("Could not read aapt output.", e);
    }
  }

  @Nullable
  static MatchResult matchPattern(BufferedReader reader, Pattern pattern) throws IOException {
    String line;
    while ((line = reader.readLine()) != null) {
      Matcher matcher = pattern.matcher(line);
      if (matcher.find()) {
        return matcher.toMatchResult();
      }
    }
    return null;
  }
}
