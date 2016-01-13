/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace.gapi;

import com.android.repository.api.LocalPackage;
import com.android.sdklib.repository.local.LocalExtraPkgInfo;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.sdklib.repositoryv2.meta.DetailsTypes;
import com.android.tools.idea.sdkv2.StudioLoggerProgressIndicator;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.SystemProperties;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static com.intellij.idea.IdeaApplication.IDEA_IS_INTERNAL_PROPERTY;

public final class GapiPaths {
  private static final Map<String, String> ABI_REMAP = ImmutableMap.<String, String>builder()
    .put("32-bit (arm)", "armeabi-v7a") // Not a valid abi, but returned anyway by ClientData.getAbi
    .put("64-bit (arm)", "arm64-v8a")   // Not a valid abi, but returned anyway by ClientData.getAbi
    .put("armeabi", "armeabi-v7a")      // We currently (incorrectly) remap this abi because we don't have the correct .so
    .build();

  private static final Map<String, String> ARCH_REMAP = ImmutableMap.<String, String>builder()
    .put("i386", "x86")
    .put("amd64", "x86_64")
    .build();

  private static final Map<String, String> ABI_TARGET = ImmutableMap.<String, String>builder()
    .put("armeabi-v7a", "android-arm")
    .put("arm64-v8a", "android-arm64")
    .put("x86", "android-x86")
    .build();

  @NotNull private static final String HOST_OS;
  @NotNull private static final String HOST_ARCH;
  @NotNull private static final String GAPIS_EXECUTABLE_NAME;
  @NotNull private static final String GAPIR_EXECUTABLE_NAME;
  @NotNull private static final String STRINGS_DIR_NAME = "strings";
  @NotNull private static final String GAPII_LIBRARY_NAME;
  @NotNull private static final String PKG_INFO_NAME = "pkginfo.apk";
  @NotNull private static final String EXE_EXTENSION;
  @NotNull private static final String SDK_VENDOR = "android";
  @NotNull private static final String SDK_PATH = "gapid";
  @NotNull private static final String SDK_PACKAGE_PATH = "extras;android;gapid";
  @NotNull private static final String OS_ANDROID = "android";

  static {
    if (SystemInfo.isWindows) {
      HOST_OS = "windows";
      EXE_EXTENSION = ".exe";
    } else if (SystemInfo.isMac) {
      HOST_OS = "osx";
      EXE_EXTENSION = "";
    } else if (SystemInfo.isLinux) {
      HOST_OS = "linux";
      EXE_EXTENSION = "";
    } else {
      HOST_OS = SystemInfo.OS_NAME;
      EXE_EXTENSION = "";
    }
    HOST_ARCH = remap(ARCH_REMAP, SystemInfo.OS_ARCH);
    GAPIS_EXECUTABLE_NAME = "gapis" + EXE_EXTENSION;
    GAPIR_EXECUTABLE_NAME = "gapir" + EXE_EXTENSION;
    GAPII_LIBRARY_NAME = "libgapii.so";
  }

  @NotNull private static final Object myPathLock = new Object();
  private static File myBaseDir;
  private static File myGapisPath;
  private static File myGapirPath;
  private static File myStringsPath;
  private static File myPkgInfoPath;

  public static boolean isValid() {
    findTools();
    return myGapisPath.exists();
  }

  @NotNull
  public static File base() {
    findTools();
    return myBaseDir;
  }

  @NotNull
  public static File gapis() {
    findTools();
    return myGapisPath;
  }

  @NotNull
  public static File gapir() {
    findTools();
    return myGapirPath;
  }

  @NotNull
  public static File strings() {
    findTools();
    return myStringsPath;
  }

  @NotNull
  static public File findTraceLibrary(@NotNull String abi) throws IOException {
    findTools();
    String remappedAbi = remap(ABI_REMAP, abi);
    File lib = findPath(OS_ANDROID, remappedAbi, GAPII_LIBRARY_NAME);
    if (lib.exists()) {
      return lib;
    }
    remappedAbi = remap(ABI_TARGET, remappedAbi);
    lib = findPath(OS_ANDROID, remappedAbi, GAPII_LIBRARY_NAME);
    if (lib.exists()) {
      return lib;
    }
    throw new IOException("Unsupported " + GAPII_LIBRARY_NAME + " abi '" + abi + "'");
  }

  @NotNull
  static public File findPkgInfoApk() {
    findTools();
    return myPkgInfoPath;
  }

  @NotNull
  static private File findPath(@NotNull String os, String abi, @NotNull String binary) {
    File test;
    File osDir = new File(myBaseDir, os);
    if (abi != null) {
      // base/os/abi/name
      test = new File(new File(osDir, abi), binary);
      if (test.exists()) return test;
      // base/abi/name
      test = new File(new File(myBaseDir, abi), binary);
      if (test.exists()) return test;
    }
    // base/os/name
    test = new File(osDir, binary);
    if (test.exists()) return test;
    // base/name
    return new File(myBaseDir, binary);
  }

  private static String remap(Map<String, String> map, String key) {
    String value = map.get(key);
    if (value == null) {
      value = key;
    }
    return value;
  }

  public static File getSdkPath() {
    AndroidSdkHandler handler = AndroidSdkUtils.tryToChooseSdkHandler();
    LocalPackage info = handler.getLocalPackage(SDK_PACKAGE_PATH, new StudioLoggerProgressIndicator(GapiPaths.class));
    if (info == null) { return null; }
    return info.getLocation();
  }

  private static boolean checkForTools(File dir) {
    if (dir == null) { return false; }
    myBaseDir = dir;
    myGapisPath = findPath(HOST_OS, HOST_ARCH, GAPIS_EXECUTABLE_NAME);
    myGapirPath = findPath(HOST_OS, HOST_ARCH, GAPIR_EXECUTABLE_NAME);
    myPkgInfoPath = findPath(OS_ANDROID, null, PKG_INFO_NAME);
    myStringsPath = new File(myBaseDir, STRINGS_DIR_NAME);
    return myGapisPath.exists();
  }

  private static void findTools() {
    synchronized (myPathLock) {
      if (myGapisPath != null) {
        return;
      }
      if (Boolean.getBoolean(IDEA_IS_INTERNAL_PROPERTY)) {
        // Check the system GOPATH for the binaries
        String gopath = System.getenv("GOPATH");
        if (gopath != null && gopath.length() > 0) {
          if (checkForTools(new File(gopath, "bin"))) {
            return;
          }
        }
      }
      // check for an installed sdk directory
      if (checkForTools(getSdkPath())) {
        return;
      }
      // Fall back to the homedir/gapid and if that fails, leave it in a failing state
      checkForTools(new File(new File(SystemProperties.getUserHome()), SDK_PATH));
    }
  }
}
