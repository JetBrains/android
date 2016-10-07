/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.rendering.webp;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.SystemInfo;

import java.io.File;

import static com.android.tools.idea.rendering.webp.WebpImageReaderSpi.getLogger;

public class NativeLibHelper {

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized") private static boolean sJniLibLoaded;
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized") private static boolean sJniLibLoadAttempted;

  private NativeLibHelper() {
  }

  static boolean loadNativeLibraryIfNeeded() {
    if (!sJniLibLoadAttempted) {
      try {
        loadNativeLibrary();
      }
      catch (UnsatisfiedLinkError e) {
        getLogger().warn(e);
      }
    }
    return sJniLibLoaded;
  }

  private synchronized static void loadNativeLibrary() {
    if (sJniLibLoadAttempted) {
      // Already attempted to load, nothing to do here.
      return;
    }
    try {
      String libFileName = getLibName();
      File pluginPath = getLibLocation();
      File libPath = new File(pluginPath, libFileName);
      if (!libPath.exists()) {
        throw new UnsatisfiedLinkError(String.format("'%1$s' not found at '%2$s'", libFileName, libPath.getAbsolutePath()));
      }
      System.load(libPath.getAbsolutePath());
    }
    finally {
      sJniLibLoadAttempted = true;
    }
    sJniLibLoaded = true;
  }

  /**
   * Copied from {@link com.intellij.util.lang.UrlClassLoader}.
   */
  public static String getLibName() {
    String baseName = SystemInfo.is64Bit ? "webp_jni64" : "webp_jni";
    String fileName = System.mapLibraryName(baseName);
    if (SystemInfo.isMac) {
      fileName = fileName.replace(".jnilib", ".dylib");
    }
    return fileName;
  }

  public static File getLibLocation() {
    // A terrible hack for dev environment.
    String adtPath = PathManager.getHomePath() + "/../adt/idea/android/lib/libwebp/" + getPlatformName();
    File adtFile = new File(adtPath);
    return adtFile.exists() ? adtFile : new File(PluginPathManager.getPluginHome("android"), "lib");
  }

  private static String getPlatformName() {
    if (SystemInfo.isWindows) return "win";
    else if (SystemInfo.isMac) return "mac";
    else if (SystemInfo.isLinux) return "linux";
    else return "";
  }
}
