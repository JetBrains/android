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
package com.android.tools.idea.welcome.install;

import com.android.SdkConstants;
import com.android.sdklib.devices.Storage;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
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
  public static final String HAXM_DOCUMENTATION_URL =
    "https://software.intel.com/android/articles/intel-hardware-accelerated-execution-manager";
  public static final String HAXM_WINDOWS_INSTALL_URL =
    "https://software.intel.com/android/articles/installation-instructions-for-intel-hardware-accelerated-execution-manager-windows";
  public static final String HAXM_MAC_INSTALL_URL =
    "https://software.intel.com/android/articles/installation-instructions-for-intel-hardware-accelerated-execution-manager-mac-os-x";
  public static final String KVM_LINUX_INSTALL_URL =
    "https://software.intel.com/blogs/2012/03/12/how-to-start-intel-hardware-assisted-virtualization-hypervisor-on-linux-to-speed-up-intel-android-x86-emulator";
  public static final JavaSdkVersion MIN_JDK_VERSION = JavaSdkVersion.JDK_1_7;

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
   * @return Default Android SDK install location
   */
  @NotNull
  private static File getDefaultSdkLocation() {
    String path = System.getenv(SdkConstants.ANDROID_HOME_ENV);

    if (Strings.isNullOrEmpty(path)) {
      String userHome = System.getProperty("user.home");
      if (SystemInfo.isWindows) {
        path = FileUtil.join(userHome, "AppData", "Local", "Android", "Sdk");
      }
      else if (SystemInfo.isMac) {
        path = FileUtil.join(userHome, "Library", "Android", "sdk");
      }
      else if (SystemInfo.isLinux) {
        path = FileUtil.join(userHome, "Android", "Sdk");
      }
      else {
        Messages.showErrorDialog("Your OS is not officially supported.\n" +
                                 "You can continue, but it is likely you will encounter further problems.",
                                 "Unsupported OS");
        path = "";
      }
    }
    return new File(path);
  }

  /**
   * Returns initial SDK location. That will be the SDK location from the installer
   * handoff file in the handoff case, sdk location location from the preference if set
   * or platform-dependant default path.
   */
  @NotNull
  public static File getInitialSdkLocation(@NotNull FirstRunWizardMode mode) {
    File dest = mode.getSdkLocation();
    if (dest != null) {
      return dest;
    }
    else {
      List<Sdk> sdks = AndroidSdkUtils.getAllAndroidSdks();
      Sdk sdk = Iterables.getFirst(sdks, null);
      if (sdk != null) {
        VirtualFile homeDirectory = sdk.getHomeDirectory();
        if (homeDirectory != null) {
          return VfsUtilCore.virtualToIoFile(homeDirectory);
        }
      }
      return getDefaultSdkLocation();
    }
  }

}