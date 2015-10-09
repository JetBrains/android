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
import java.util.Map;

import static com.intellij.idea.IdeaApplication.IDEA_IS_INTERNAL_PROPERTY;

public final class GapiPaths {
  @NotNull private static final String SERVER_EXECUTABLE_NAME = "gapis";
  @NotNull private static final String SERVER_RELATIVE_PATH = "bin";
  @NotNull private static final String GAPII_LIBRARY_FLAVOUR = "release";
  @NotNull private static final String GAPII_LIBRARY_NAME = "libgapii.so";

  private static final Map<String, String> ABI_TO_LIB = Collections.unmodifiableMap(new HashMap<String, String>() {{
    put("32-bit (arm)", "android-arm"); // Not a valid abi, but returned anyway by ClientData.getAbi
    put("64-bit (arm)", "android-arm64"); // Not a valid abi, but returned anyway by ClientData.getAbi
    put("armeabi", "android-arm");
    put("armeabi-v7a", "android-arm");
    put("arm64-v8a", "android-arm64");
  }});

  private static final Object myPathLock = new Object();
  private static GapiPaths myPaths;

  public final File myGapisRoot;
  public final File myServerDirectory;
  public final File myGapisPath;

  public static boolean isValid() {
    return gapis().exists();
  }

  public static File gapis() {
    return get().myGapisPath;
  }

  @NotNull
  static public File findTraceLibrary(@NotNull String abi) throws IOException {
    File binaryPath = get().myServerDirectory;
    if (binaryPath == null) {
      throw new IOException("No gapii libraries available");
    }
    String lib = ABI_TO_LIB.get(abi);
    if (lib == null) {
      throw new IOException("Unsupported gapii abi '" + abi + "'");
    }
    File architecturePath = new File(binaryPath, lib);
    File flavourPath = new File(architecturePath, GAPII_LIBRARY_FLAVOUR);
    return new File(flavourPath, GAPII_LIBRARY_NAME);
  }

  public GapiPaths(File gapisRoot, File serverDirectory, File gapisPath) {
    myGapisRoot = gapisRoot;
    myServerDirectory = serverDirectory;
    myGapisPath = gapisPath;
  }

  private static GapiPaths create(File root) {
    File bin = new File(root, SERVER_RELATIVE_PATH);
    File gapis = new File(bin, SERVER_EXECUTABLE_NAME);
    return new GapiPaths(root, bin, gapis);
  }

  private static GapiPaths find() {
    File androidPlugin = PluginPathManager.getPluginHome("android");
    GapiPaths result = create(androidPlugin);
    if (Boolean.getBoolean(IDEA_IS_INTERNAL_PROPERTY)) {
      // Check the default build location for a standard repo checkout
      GapiPaths internal = create(new File(androidPlugin.getParentFile().getParentFile().getParentFile(), "gpu"));
      if (!internal.myGapisPath.exists()) {
        // Check the GOPATH in case it is non standard
        String gopath = System.getenv("GOPATH");
        if (gopath != null && gopath.length() > 0) {
          internal = create(new File(gopath));
        }
      }
      // TODO: Check the prebuilts location
      if (internal.myGapisPath.exists()) {
        result = internal;
      }
    }
    return result;
  }

  public static GapiPaths get() {
    synchronized (myPathLock) {
      if (myPaths == null || !myPaths.myGapisPath.exists()) {
        myPaths = find();
      }
      return myPaths;
    }
  }

}
