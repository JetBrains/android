/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.screensharing;

import android.annotation.SuppressLint;
import android.util.Log;

public class Main {
  @SuppressLint("UnsafeDynamicallyLoadedCode")
  public static void main(String[] args) {
    try {
      System.load("/data/local/tmp/.studio/libscreen-sharing-agent.so");
    }
    catch (Throwable e) {
      Log.e("ScreenSharing", "Unable to load libscreen-sharing-agent.so - " + e.getMessage());
    }
    nativeMain(args);
  }

  private static native void nativeMain(String[] args);
}
