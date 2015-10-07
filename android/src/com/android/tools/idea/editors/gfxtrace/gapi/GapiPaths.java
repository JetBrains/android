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

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import static com.intellij.idea.IdeaApplication.IDEA_IS_INTERNAL_PROPERTY;

public final class GapiPaths {
  private static final Map<String, String> ABI_REMAP = Collections.unmodifiableMap(new HashMap<String, String>() {{
    put("32-bit (arm)", "armeabi-v7a"); // Not a valid abi, but returned anyway by ClientData.getAbi
    put("64-bit (arm)", "arm64-v8a"); // Not a valid abi, but returned anyway by ClientData.getAbi
    put("armeabi", "armeabi-v7a");// We currently (incorrectly) remap this abi because we don't have the correct .so
  }});

  private static final Map<String, String> ABI_TARGET = Collections.unmodifiableMap(new HashMap<String, String>() {{
    put("armeabi-v7a", "android-arm");
    put("arm64-v8a", "android-arm64");
  }});

  @NotNull private static final String HOST_OS;
  @NotNull private static final String HOST_ARCH;
  @NotNull private static final String HOST_DIR;
  @NotNull private static final String SERVER_EXECUTABLE_NAME;
  @NotNull private static final String GAPII_LIBRARY_NAME;
  @NotNull private static final String PKG_INFO_NAME = "pkginfo.apk";

  static {
    HOST_OS = System.getProperty("os.name");
    HOST_ARCH = System.getProperty("os.arch");
    if (HOST_OS.startsWith("Windows")) {
      SERVER_EXECUTABLE_NAME = "gapis.exe";
    } else {
      SERVER_EXECUTABLE_NAME = "gapis";
    }
    HOST_DIR = abiName(HOST_OS, HOST_ARCH);
    GAPII_LIBRARY_NAME = "libgapii.so";
  }

  @NotNull private static final Object myPathLock = new Object();
  private static Handler myHandler;

  public static boolean isValid() {
    return handler().isValid();
  }

  public static File base() {
    return handler().base();
  }

  public static File gapis() {
    return handler().gapis();
  }

  @NotNull
  static public File findTraceLibrary(@NotNull String abi) throws IOException {
    return handler().findTraceLibrary(abi);
  }

  @NotNull
  static public File findPkgInfoApk() {
    return handler().findPkgInfoApk();
  }

  @NotNull
  private static String abiName(String os, String arch) {
    return (os + '-' + arch).replace(' ', '-').toLowerCase(Locale.ENGLISH);
  }

  private static String remap(Map<String, String> map, String key) {
    String value = map.get(key);
    if (value == null) {
      value = key;
    }
    return value;
  }

  private static Handler handler() {
    synchronized (myPathLock) {
      if (myHandler != null) {
        return myHandler;
      }
      File androidPlugin = PluginPathManager.getPluginHome("android");
      File tools = androidPlugin.getParentFile().getParentFile().getParentFile();
      if (Boolean.getBoolean(IDEA_IS_INTERNAL_PROPERTY)) {
        // Check the default build location for a standard repo checkout
        myHandler = new InternalHandler(new File(tools, "gpu"));
        if (myHandler.isValid()) {
          return myHandler;
        }
        // Check the system GOPATH for the binaries
        String gopath = System.getenv("GOPATH");
        if (gopath != null && gopath.length() > 0) {
          myHandler = new InternalHandler(new File(gopath));
          if (myHandler.isValid()) {
            return myHandler;
          }
        }
        // Check the standard prebuilts checkout for the binaries
        myHandler = new PluginHandler(new File(tools, "adt/idea/android/gapi"));
        if (myHandler.isValid()) {
          return myHandler;
        }
      }
      // check the android plugin directory
      myHandler = new PluginHandler(new File(androidPlugin, "gapi"));
    }
    return myHandler;
  }

  private static abstract class Handler {
    private final File myBaseDirectory;
    private final File myGapisPath;

    Handler(File baseDir, File gapisPath) {
      myBaseDirectory = baseDir;
      myGapisPath = gapisPath;
    }

    public boolean isValid() {
      return myGapisPath.exists();
    }

    @NotNull
    public File base() {
      return myBaseDirectory;
    }

    @NotNull
    public File gapis() {
      return myGapisPath;
    }

    @NotNull
    public abstract File findTraceLibrary(@NotNull String abi) throws IOException;

    @NotNull
    public abstract File  findPkgInfoApk();
  }

  private static class PluginHandler extends Handler {
    PluginHandler(File baseDir) {
      super(baseDir, new File(new File(baseDir, HOST_DIR), SERVER_EXECUTABLE_NAME));
    }

    @Override
    @NotNull
    public File findTraceLibrary(@NotNull String abi) throws IOException {
      abi = remap(ABI_REMAP, abi);
      File abiPath = new File(base(), abiName("android", abi));
      if (!abiPath.exists()) {
        throw new IOException("Unsupported gapii abi '" + abi + "'");
      }
      return new File(abiPath, GAPII_LIBRARY_NAME);
    }

    @Override
    @NotNull
    public File findPkgInfoApk() {
      return new File(new File(base(), "android"), PKG_INFO_NAME);
    }
  }

  private static class InternalHandler extends Handler {
    @NotNull private static final String SERVER_RELATIVE_PATH = "bin";
    @NotNull private static final String GAPII_LIBRARY_FLAVOUR = "release";

    InternalHandler(File baseDir) {
      super(baseDir, new File(new File(baseDir, SERVER_RELATIVE_PATH), SERVER_EXECUTABLE_NAME));
    }

    @Override
    @NotNull
    public File findTraceLibrary(@NotNull String abi) throws IOException {
      abi = remap(ABI_REMAP, abi);
      String lib = remap(ABI_TARGET, abi);
      File abiPath = new File(gapis().getParentFile(), lib);
      if (!abiPath.exists()) {
        throw new IOException("Unsupported gapii abi '" + abi + "'");
      }
      File flavourPath = new File(abiPath, GAPII_LIBRARY_FLAVOUR);
      return new File(flavourPath, GAPII_LIBRARY_NAME);
    }

    @Override
    @NotNull
    public File findPkgInfoApk() {
      return new File(new File(base(), SERVER_RELATIVE_PATH), PKG_INFO_NAME);
    }
  }
}
