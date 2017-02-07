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
package com.android.tools.idea.profilers;

import com.android.annotations.Nullable;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.ddms.adb.AdbService;
import com.android.tools.profilers.ProfilerClient;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class ProfilerService implements Disposable {
  private StudioProfilerDeviceManager myManager;

  private ProfilerService() {
  }

  @Override
  public void dispose() {
    if (myManager != null) {
      myManager.dispose();
    }
  }

  @NotNull
  public ProfilerClient getProfilerClient(@NotNull Project project) throws IOException {
    if (myManager == null) {
      // TODO: Once the bridge API doesn't require a project, rework this to not use it.
      final File adb = AndroidSdkUtils.getAdb(project);
      if (adb == null) {
        // TODO: Handle ADB errors appropriately.
        throw new IllegalStateException("No adb found");
      }
      myManager = new StudioProfilerDeviceManager();
      ListenableFuture<AndroidDebugBridge> future = AdbService.getInstance().getDebugBridge(adb);
      Futures.addCallback(future, new FutureCallback<AndroidDebugBridge>() {
        @Override
        public void onSuccess(@Nullable AndroidDebugBridge bridge) {
        }

        @Override
        public void onFailure(@NotNull Throwable t) {
          // TODO: Handle this error correctly. Is this fired too to the bridge listeners?
        }
      }, EdtExecutor.INSTANCE);
    }
    return myManager.getClient();
  }
}
