/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.deployment;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.adb.AdbService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

final class ConnectedDevicesWorkerDelegate extends SwingWorker<Collection<IDevice>, Void> {
  private final Project myProject;

  ConnectedDevicesWorkerDelegate(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  protected Collection<IDevice> doInBackground() {
    File adb = AndroidSdkUtils.getAdb(myProject);

    if (adb == null) {
      return Collections.emptyList();
    }

    Future<AndroidDebugBridge> futureBridge = AdbService.getInstance().getDebugBridge(adb);

    if (!futureBridge.isDone()) {
      return Collections.emptyList();
    }

    try {
      return Arrays.asList(futureBridge.get().getDevices());
    }
    catch (InterruptedException exception) {
      // This should never happen. The future is done and can no longer be interrupted.
      throw new AssertionError(exception);
    }
    catch (ExecutionException exception) {
      Logger.getInstance(ConnectedDevicesWorkerDelegate.class).warn(exception);
      return Collections.emptyList();
    }
  }
}
