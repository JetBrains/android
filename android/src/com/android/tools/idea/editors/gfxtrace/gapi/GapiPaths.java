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

import java.io.File;

import static com.intellij.idea.IdeaApplication.IDEA_IS_INTERNAL_PROPERTY;

public class GapiPaths {
  private static final String SERVER_EXECUTABLE_NAME = "gapis";
  private static final String SERVER_RELATIVE_PATH = "bin";

  private static final Object myPathLock = new Object();
  private static GapiPaths myPaths;

  public final File myGapisRoot;
  public final File myServerDirectory;
  public final File myGapisPath;

  public GapiPaths(File gapisRoot, File serverDirectory, File gapisPath) {
    myGapisRoot = gapisRoot;
    myServerDirectory = serverDirectory;
    myGapisPath = gapisPath;
  }

  public boolean isValid() {
    return myGapisPath.exists();
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
      if (!internal.isValid()) {
        // Check the GOPATH in case it is non standard
        String gopath = System.getenv("GOPATH");
        if (gopath != null && gopath.length() > 0) {
          internal = create(new File(gopath));
        }
      }
      // TODO: Check the prebuilts location
      if (internal.isValid()) {
        result = internal;
      }
    }
    return result;
  }


  public static File getBinaryPath() {
    return get().myServerDirectory;
  }

  public static GapiPaths get() {
    synchronized (myPathLock) {
      if (myPaths == null || !myPaths.isValid()) {
        myPaths = find();
      }
      return myPaths;
    }
  }

}
