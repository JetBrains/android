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
package com.android.tools.idea.welcome;

import com.android.sdklib.devices.Storage;
import com.google.common.collect.Iterables;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.util.PathUtil;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * The goal is to keep all defaults in one place so it is easier to update
 * them as needed.
 */
public class FirstRunWizardDefaults {
  public static final String HAXM_DOCUMENTATION_URL = "http://www.intel.com/software/android/";

  private FirstRunWizardDefaults() {
    // Do nothing
  }

  /**
   * @return Recommended memory allocation given the computer RAM size
   */
  public static int getRecommendedHaxmMemory(long memorySize) {
    final long GB = Storage.Unit.GiB.getNumberOfBytes();
    final long defaultMemory;
    if (memorySize > 4 * GB) {
      defaultMemory = 2 * GB;
    }
    else {
      if (memorySize > 2 * GB) {
        defaultMemory = GB;
      }
      else {
        defaultMemory = GB / 2;
      }
    }
    return (int)(defaultMemory / Haxm.UI_UNITS.getNumberOfBytes());
  }

  /**
   * @return Android SDK download URL
   */
  @NotNull
  public static String getSdkDownloadUrl() {
    String url = System.getProperty("android.sdkurl");
    if (!StringUtil.isEmptyOrSpaces(url)) {
      File file = new File(url);
      if (file.isFile()) {
        // Can't use any path => URL utilities as they don't add two slashes
        // after the protocol as required by IJ downloader
        return LocalFileSystem.PROTOCOL_PREFIX + PathUtil.toSystemIndependentName(file.getAbsolutePath());
      }
      else {
        System.err.println("File " + file.getAbsolutePath() + " does not exist.");
      }
    }
    String downloadUrl = AndroidSdkUtils.getSdkDownloadUrl();
    if (downloadUrl == null) {
      throw new IllegalStateException("Unsupported OS");
    }
    return downloadUrl;
  }

  /**
   * @return Default Android SDK install location
   */
  @NotNull
  public static String getDefaultSdkLocation() {
    List<Sdk> sdks = AndroidSdkUtils.getAllAndroidSdks();
    Sdk sdk = Iterables.getFirst(sdks, null);
    if (sdk != null && !StringUtil.isEmptyOrSpaces(sdk.getHomePath())) {
      return sdk.getHomePath();
    }
    // TODO Need exact paths
    String userHome = System.getProperty("user.home");
    if (SystemInfo.isWindows) {
      return FileUtil.join(userHome, "AppData", "Local", "Android", "Sdk");
    }
    else if (SystemInfo.isMac) {
      return FileUtil.join(userHome, "Library", "Android", "sdk");
    }
    else if (SystemInfo.isLinux) {
      return FileUtil.join(userHome, "Android", "Sdk");
    }
    else {
      throw new IllegalStateException("Unsupported OS");
    }
  }
}