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

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.deployable.Deployable;
import com.android.tools.idea.run.deployable.DeployableProvider;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.serviceContainer.NonInjectable;
import java.awt.EventQueue;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeviceAndSnapshotComboBoxDeployableProvider implements DeployableProvider {
  private final @NotNull Supplier<@NotNull DeviceAndSnapshotComboBoxAction> myDeviceAndSnapshotComboBoxActionGetInstance;
  private final @NotNull Supplier<@NotNull Logger> myLoggerGetInstance;

  private boolean myStackTraceLoggedOnce;

  @SuppressWarnings("unused")
  private DeviceAndSnapshotComboBoxDeployableProvider() {
    this(DeviceAndSnapshotComboBoxAction::getInstance, () -> Logger.getInstance(DeviceAndSnapshotComboBoxDeployableProvider.class));
  }

  @VisibleForTesting
  @NonInjectable
  DeviceAndSnapshotComboBoxDeployableProvider(@NotNull Supplier<@NotNull DeviceAndSnapshotComboBoxAction> deviceAndSnapshotComboBoxActionGetInstance,
                                              @NotNull Supplier<@NotNull Logger> loggerGetInstance) {
    myDeviceAndSnapshotComboBoxActionGetInstance = deviceAndSnapshotComboBoxActionGetInstance;
    myLoggerGetInstance = loggerGetInstance;
  }

  @Override
  public @Nullable Deployable getDeployable(@NotNull RunConfiguration runConfiguration) {
    if (!(runConfiguration instanceof AndroidRunConfigurationBase androidRunConfiguration)) {
      return null;
    }

    List<Device> devices = myDeviceAndSnapshotComboBoxActionGetInstance.get().getSelectedDevices(androidRunConfiguration.getProject());

    if (devices.size() != 1) {
      return null;
    }

    ApplicationIdProvider applicationIdProvider = androidRunConfiguration.getApplicationIdProvider();
    if (applicationIdProvider == null) {
      return null;
    }

    return getPackageName(applicationIdProvider)
      .map(name -> new DeployableDevice(devices.get(0), name))
      .orElse(null);
  }

  private @NotNull Optional<@NotNull String> getPackageName(@NotNull ApplicationIdProvider provider) {
    try {
      String name = provider.getPackageName();
      myStackTraceLoggedOnce = false;

      return Optional.of(name);
    }
    catch (ApkProvisionException exception) {
      if (!myStackTraceLoggedOnce) {
        myLoggerGetInstance.get().warn(exception);
        myStackTraceLoggedOnce = true;
      }
      else {
        myLoggerGetInstance.get().warn("An ApkProvisionException has been thrown more than once: " + exception);
      }

      return Optional.empty();
    }
  }

  @VisibleForTesting
  static final class DeployableDevice implements Deployable {
    @NotNull private final Device myDevice;
    @NotNull private final String myPackageName;

    @VisibleForTesting
    DeployableDevice(@NotNull Device device, @NotNull String packageName) {
      myDevice = device;
      myPackageName = packageName;
    }

    @NotNull
    @Override
    public Future<AndroidVersion> getVersion() {
      return myDevice.getAndroidVersion();
    }

    @Override
    public @NotNull ListenableFuture<@NotNull List<@NotNull Client>> searchClientsForPackageAsync() {
      var future = myDevice.getDdmlibDeviceAsync();

      // I intend for device -> Deployable.searchClientsForPackage(device, myPackageName) to execute on the EDT. If I use EdtExecutorService
      // .getInstance() (which does an invokeLater), DeviceAndSnapshotComboBoxDeployableProvider.searchClientsForPackage will block forever
      // because it'll wait for Deployable.searchClientsForPackage here which has been scheduled for after the wait. Hence, MoreExecutors
      // .directExecutor().

      // noinspection UnstableApiUsage
      return Futures.transform(future, device -> Deployable.searchClientsForPackage(device, myPackageName), MoreExecutors.directExecutor());
    }

    @NotNull
    @Override
    public List<Client> searchClientsForPackage() {
      if (EventQueue.isDispatchThread()) {
        Loggers.errorConditionally(DeviceAndSnapshotComboBoxDeployableProvider.class,
                                   "Blocking Future::get call on the EDT http://b/261492787");
      }

      return Futures.getUnchecked(searchClientsForPackageAsync());
    }

    @Override
    public boolean isOnline() {
      IDevice iDevice = myDevice.getDdmlibDevice();
      if (iDevice == null) {
        return false;
      }
      return iDevice.isOnline();
    }

    @Override
    public boolean isUnauthorized() {
      IDevice iDevice = myDevice.getDdmlibDevice();
      if (iDevice == null) {
        return false;
      }
      return iDevice.getState() == IDevice.DeviceState.UNAUTHORIZED;
    }

    @Override
    public int hashCode() {
      return 31 * myDevice.hashCode() + myPackageName.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object object) {
      if (!(object instanceof DeployableDevice device)) {
        return false;
      }

      return myDevice.equals(device.myDevice) && myPackageName.equals(device.myPackageName);
    }
  }
}
