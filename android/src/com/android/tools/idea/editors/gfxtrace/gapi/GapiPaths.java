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
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.SystemProperties;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static com.intellij.idea.IdeaApplication.IDEA_IS_INTERNAL_PROPERTY;

public final class GapiPaths {
  public static final Version REQUIRED_GAPI_VERSION = Version.VERSION_3;

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
    .put("armeabi-v7a", "android-armv7a")
    .put("arm64-v8a", "android-armv8a")
    .put("x86", "android-x86")
    .build();

  @NotNull private static final String HOST_OS;
  @NotNull private static final String HOST_ARCH;
  @NotNull private static final String GAPIS_EXECUTABLE_NAME;
  @NotNull private static final String GAPIR_EXECUTABLE_NAME;
  @NotNull private static final String STRINGS_DIR_NAME = "strings";
  @NotNull private static final String GAPII_LIBRARY_NAME = "libgapii.so";
  @NotNull private static final String INTERCEPTOR_LIBRARY_NAME = "libinterceptor.so";
  @NotNull private static final String PKG_INFO_NAME = "pkginfo.apk";
  @NotNull private static final String EXE_EXTENSION;
  @NotNull private static final String SDK_PATH = "gapid";
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
  private static File findLibrary(@NotNull String libraryName, @NotNull String abi) throws IOException {
    findTools();
    String remappedAbi = remap(ABI_REMAP, abi);
    File lib = findPath(OS_ANDROID, remappedAbi, libraryName);
    if (lib.exists()) {
      return lib;
    }
    remappedAbi = remap(ABI_TARGET, remappedAbi);
    lib = findPath(OS_ANDROID, remappedAbi, libraryName);
    if (lib.exists()) {
      return lib;
    }
    throw new IOException("Unsupported " + libraryName + " abi '" + abi + "'");
  }

  @NotNull
  public static File findTraceLibrary(@NotNull String abi) throws IOException {
    return findLibrary(GAPII_LIBRARY_NAME, abi);
  }

  @NotNull
  public static File findInterceptorLibrary(@NotNull String abi) throws IOException {
    return findLibrary(INTERCEPTOR_LIBRARY_NAME, abi);
  }

  @NotNull
  public static File findPkgInfoApk() {
    findTools();
    return myPkgInfoPath;
  }

  /**
   * @return a {@link Collection} of SDK components to install, or the empty collection if we're up-to-date.
   */
  public static Collection<String> getMissingSdkComponents() {
    // If we have found a valid install, ...
    if (isValid()) {
      LocalPackage gapi = GapiPaths.getLocalPackage();
      // ... and if the installed package is compatible, we don't need a new install.
      if (gapi == null || REQUIRED_GAPI_VERSION.isCompatible(gapi.getVersion())) {
        return Collections.emptyList();
      }
    }
    return ImmutableList.of(REQUIRED_GAPI_VERSION.getSdkPackagePath());
  }

  @NotNull
  private static File findPath(@NotNull String os, String abi, @NotNull String binary) {
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

  @Nullable("gapi is not installed")
  private static LocalPackage getLocalPackage() {
    AndroidSdkHandler handler = AndroidSdkUtils.tryToChooseSdkHandler();
    return handler.getLocalPackage(REQUIRED_GAPI_VERSION.getSdkPackagePath(), new StudioLoggerProgressIndicator(GapiPaths.class));
  }

  @Nullable("gapi is not installed")
  private static File getSdkPath() {
    LocalPackage info = getLocalPackage();
    return info == null ? null : info.getLocation();
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
      if (myGapisPath != null && myGapisPath.exists()) {
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
