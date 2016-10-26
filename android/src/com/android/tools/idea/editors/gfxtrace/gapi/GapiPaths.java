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
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

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

  @NotNull private static final String HOST_OS;
  @NotNull private static final String HOST_ARCH;
  @NotNull private static final String GAPIS_EXECUTABLE_NAME;
  @NotNull private static final String GAPIR_EXECUTABLE_NAME;
  @NotNull private static final String STRINGS_DIR_NAME = "strings";
  @NotNull private static final String GAPII_LIBRARY_NAME = "libgapii.so";
  @NotNull private static final String INTERCEPTOR_LIBRARY_NAME = "libinterceptor.so";
  @NotNull private static final String PKG_INFO_NAME = "pkginfo.apk";
  @NotNull private static final String EXE_EXTENSION;
  @NotNull private static final String USER_HOME_GAPID_ROOT = "gapid";
  @NotNull private static final String GAPID_PKG_SUBDIR = "pkg";
  @NotNull private static final String GAPID_ROOT_ENV_VAR = "GAPID";
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
    HOST_ARCH = ARCH_REMAP.getOrDefault(SystemInfo.OS_ARCH, SystemInfo.OS_ARCH);
    GAPIS_EXECUTABLE_NAME = "gapis" + EXE_EXTENSION;
    GAPIR_EXECUTABLE_NAME = "gapir" + EXE_EXTENSION;
  }

  private static File myBaseDir;
  private static File myGapisPath;
  private static File myGapirPath;
  private static File myStringsPath;
  private static File myPkgInfoPath;

  public static synchronized boolean isValid() {
    if (myGapisPath != null && allExist()) {
      return true;
    }
    findTools();
    return allExist();
  }

  @NotNull
  public static synchronized File base() {
    isValid();
    return myBaseDir;
  }

  @NotNull
  public static synchronized File gapis() {
    isValid();
    return myGapisPath;
  }

  @NotNull
  public static synchronized File gapir() {
    isValid();
    return myGapirPath;
  }

  @NotNull
  public static synchronized File strings() {
    isValid();
    return myStringsPath;
  }

  @NotNull
  public static synchronized File pkgInfoApk() {
    isValid();
    return myPkgInfoPath;
  }

  @NotNull
  private static File findLibrary(@NotNull String libraryName, @NotNull String abi) throws IOException {
    isValid();
    File lib = FileUtils.join(myBaseDir, OS_ANDROID, ABI_REMAP.getOrDefault(abi, abi), libraryName);
    if (lib.exists()) {
      return lib;
    }
    throw new IOException("Unsupported " + libraryName + " abi '" + abi + "'");
  }

  @NotNull
  public static synchronized File findTraceLibrary(@NotNull String abi) throws IOException {
    return findLibrary(GAPII_LIBRARY_NAME, abi);
  }

  @NotNull
  public static synchronized File findInterceptorLibrary(@NotNull String abi) throws IOException {
    return findLibrary(INTERCEPTOR_LIBRARY_NAME, abi);
  }

  /**
   * @return a {@link Collection} of SDK components to install, or the empty collection if we're up-to-date.
   */
  public static Collection<String> getMissingSdkComponents() {
    // If we have found a valid install, ...
    if (isValid()) {
      LocalPackage gapi = getLocalPackage();
      // ... and if the installed package is compatible, we don't need a new install.
      if (gapi == null || REQUIRED_GAPI_VERSION.isCompatible(gapi.getVersion())) {
        return Collections.emptyList();
      }
    }
    return ImmutableList.of(REQUIRED_GAPI_VERSION.getSdkPackagePath());
  }

  @Nullable/*gapi is not installed*/
  private static LocalPackage getLocalPackage() {
    AndroidSdkHandler handler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    return handler.getLocalPackage(REQUIRED_GAPI_VERSION.getSdkPackagePath(), new StudioLoggerProgressIndicator(GapiPaths.class));
  }

  @Nullable/*gapi is not installed*/
  private static File getSdkPath() {
    LocalPackage info = getLocalPackage();
    return info == null ? null : info.getLocation();
  }

  private static boolean checkForTools(File dir) {
    if (dir == null) { return false; }
    myBaseDir = dir;
    myGapisPath = FileUtils.join(dir, HOST_OS, HOST_ARCH, GAPIS_EXECUTABLE_NAME);
    myGapirPath = FileUtils.join(dir, HOST_OS, HOST_ARCH, GAPIR_EXECUTABLE_NAME);
    myPkgInfoPath = FileUtils.join(dir, OS_ANDROID, PKG_INFO_NAME);
    myStringsPath = new File(dir, STRINGS_DIR_NAME);
    return allExist();
  }

  private static boolean allExist() {
    // We handle a missing strings dir explicitly, so ignore if it's missing.
    return myGapisPath.exists() && myGapirPath.exists() && myPkgInfoPath.exists();
  }

  private static File pathJoin(String... components) {
    File f = null;
    for (String s : components) {
      f = f == null ? new File(s) : new File(f, s);
    }
    return f;
  }

  private static void findTools() {
    ImmutableList.<Supplier<File>>of(
      () -> {
        String gapidRoot = System.getenv(GAPID_ROOT_ENV_VAR);
        return gapidRoot != null && gapidRoot.length() > 0 ? new File(gapidRoot) : null;
      },
      () -> pathJoin(SystemProperties.getUserHome(), USER_HOME_GAPID_ROOT),
      () -> pathJoin(SystemProperties.getUserHome(), USER_HOME_GAPID_ROOT, GAPID_PKG_SUBDIR),
      GapiPaths::getSdkPath
    ).stream().filter(p -> checkForTools(p.get())).findFirst();
  }
}
