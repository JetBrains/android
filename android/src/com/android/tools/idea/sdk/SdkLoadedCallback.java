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
package com.android.tools.idea.sdk;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.NotNull;

/**
 * Callback for when SDK load is completed/partially completed. Can optionally run on the dispatch thread.
 */
abstract public class SdkLoadedCallback {
  private boolean myUseDispatchThread;

  /**
   * @param useDispatchThread If true doRun will be invoked on the dispatch thread only.
   */
  public SdkLoadedCallback(boolean useDispatchThread) {
    myUseDispatchThread = useDispatchThread;
  }

  // Internal, should only be called by SdkState
  final void run(@NotNull final SdkPackages packages) {
    Runnable r = new Runnable() {
      @Override
      public void run() {
        doRun(packages);
      }
    };
    if (!myUseDispatchThread) {
      r.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(r, ModalityState.any());
    }
  }

  /**
   * @param packages The packages that have been loaded so far. For localComplete callbacks, this will only include
   *                 local packages.
   */
  abstract public void doRun(@NotNull SdkPackages packages);
}
