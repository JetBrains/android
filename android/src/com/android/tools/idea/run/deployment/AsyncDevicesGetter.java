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
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.adb.AdbService;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class AsyncDevicesGetter {
  private final Worker<Collection<String>> myConnectedDeviceNamesWorker = new Worker<>();
  private final Worker<Collection<AvdInfo>> myAvdsWorker = new Worker<>();

  @NotNull
  List<Device> get(@NotNull Project project) {
    Supplier<SwingWorker<Collection<String>, Void>> supplier = () -> new ConnectedDeviceNamesWorkerDelegate(project);
    Collection<String> connectedDeviceNames = myConnectedDeviceNamesWorker.get(supplier, Collections.emptySet());

    return myAvdsWorker.get(AvdsWorkerDelegate::new, Collections.emptyList()).stream()
                       .map(avd -> new Device(connectedDeviceNames.contains(avd.getName()), AvdManagerConnection.getAvdDisplayName(avd)))
                       .collect(Collectors.toList());
  }

  private static final class ConnectedDeviceNamesWorkerDelegate extends SwingWorker<Collection<String>, Void> {
    private final Project myProject;

    private ConnectedDeviceNamesWorkerDelegate(@NotNull Project project) {
      myProject = project;
    }

    @NotNull
    @Override
    protected Collection<String> doInBackground() {
      return getDevices().stream()
                         .filter(IDevice::isEmulator)
                         .map(IDevice::getAvdName)
                         .collect(Collectors.toSet());
    }

    @NotNull
    private Collection<IDevice> getDevices() {
      File adb = AndroidSdkUtils.getAdb(myProject);

      if (adb == null) {
        return Collections.emptyList();
      }

      Future<AndroidDebugBridge> future = AdbService.getInstance().getDebugBridge(adb);

      if (!future.isDone()) {
        return Collections.emptyList();
      }

      try {
        return Arrays.asList(future.get().getDevices());
      }
      catch (InterruptedException exception) {
        // This should never happen. The future is done and can no longer be interrupted.
        throw new AssertionError(exception);
      }
      catch (ExecutionException exception) {
        Logger.getInstance(ConnectedDeviceNamesWorkerDelegate.class).warn(exception);
        return Collections.emptyList();
      }
    }
  }

  private static final class AvdsWorkerDelegate extends SwingWorker<Collection<AvdInfo>, Void> {
    @NotNull
    @Override
    protected Collection<AvdInfo> doInBackground() {
      return AvdManagerConnection.getDefaultAvdManagerConnection().getAvds(true);
    }
  }
}
