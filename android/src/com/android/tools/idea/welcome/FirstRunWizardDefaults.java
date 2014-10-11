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
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

/**
 * The goal is to keep all defaults in one place so it is easier to update
 * them as needed.
 */
public class FirstRunWizardDefaults {
  public static final String HAXM_INSTALLER_ARCHIVE_FILE_NAME = "haxm.zip";
  public static final String HAXM_DOCUMENTATION_URL = "http://www.intel.com/software/android/";
  public static final String ANDROID_SDK_ARCHIVE_FILE_NAME = "androidsdk.zip";

  private FirstRunWizardDefaults() {
    // Do nothing
  }

  /**
   * @return Intel® HAXM download URL
   */
  @NotNull
  public static String getHaxmDownloadUrl() {
    return "https://github.com/vladikoff/chromeos-apk/archive/master.zip";
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
    return "https://github.com/FezVrasta/bootstrap-material-design/archive/master.zip";
  }

  /**
   * @return Default Android SDK install location
   */
  @NotNull
  public static String getDefaultSdkLocation() {
    // TODO Need exact paths
    if (SystemInfo.isWindows) {
      return "C:\\Android SDK";
    }
    else if (SystemInfo.isLinux) {
      return "/usr/local/androidsdk";
    }
    else if (SystemInfo.isMac) {
      return String.format("%s/Android SDK", System.getProperty("user.home"));
    }
    else {
      throw new IllegalStateException("Unsupported OS");
    }
  }
}