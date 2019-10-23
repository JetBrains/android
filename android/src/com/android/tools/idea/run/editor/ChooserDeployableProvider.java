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
package com.android.tools.idea.run.editor;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.DeploymentApplicationService;
import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.deployable.Deployable;
import com.android.tools.idea.run.deployable.DeployableProvider;
import com.google.common.util.concurrent.Futures;
import com.intellij.execution.executors.DefaultRunExecutor;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link DeployableProvider} binding for {@link ShowChooserTargetProvider}.
 */
public final class ChooserDeployableProvider implements DeployableProvider {
  @NotNull private final AndroidRunConfigurationBase myAndroidRunConfigurationBase;
  @NotNull private final AndroidFacet myAndroidFacet;
  @NotNull private final ShowChooserTargetProvider myTargetProvider;

  public ChooserDeployableProvider(@NotNull AndroidRunConfigurationBase androidRunConfigurationBase,
                                   @NotNull AndroidFacet androidFacet,
                                   @NotNull ShowChooserTargetProvider targetProvider) {
    myAndroidRunConfigurationBase = androidRunConfigurationBase;
    myAndroidFacet = androidFacet;
    myTargetProvider = targetProvider;
  }

  @Override
  public boolean isDependentOnUserInput() {
    DeployTarget deployTarget = myTargetProvider.getCachedDeployTarget(
      DefaultRunExecutor.getRunExecutorInstance(),
      myAndroidFacet,
      myAndroidRunConfigurationBase.getDeviceCount(true),
      myAndroidRunConfigurationBase.getDeployTargetContext().getDeployTargetStates(),
      myAndroidRunConfigurationBase.getUniqueID());

    return deployTarget == null;
  }

  @Nullable
  @Override
  public Deployable getDeployable() {
    DeployTarget deployTarget = myTargetProvider.getCachedDeployTarget(
      DefaultRunExecutor.getRunExecutorInstance(),
      myAndroidFacet,
      myAndroidRunConfigurationBase.getDeviceCount(true),
      myAndroidRunConfigurationBase.getDeployTargetContext().getDeployTargetStates(),
      myAndroidRunConfigurationBase.getUniqueID());

    if (deployTarget == null) {
      return null;
    }

    // We have a default deploy target, so test to see if the app is already running and if the debugger is attached.
    DeployTargetState deployTargetState = myAndroidRunConfigurationBase.getDeployTargetContext().getDeployTargetState(myTargetProvider);
    //noinspection unchecked
    DeviceFutures deviceFutures = deployTarget.getDevices(
      deployTargetState, myAndroidFacet, myAndroidRunConfigurationBase.getDeviceCount(true), true,
      myAndroidRunConfigurationBase.getUniqueID());
    if (deviceFutures == null) {
      // Since no active devices are available, it means the app isn't already running, so user will have to Run/Debug first.
      return null;
    }

    try {
      String packageName = myAndroidRunConfigurationBase.getApplicationIdProvider(myAndroidFacet).getPackageName();
      List<AndroidDevice> devices = deviceFutures.getDevices();
      if (devices.isEmpty()) {
        return null;
      }
      return new ChooserDeployable(packageName, devices.get(0));
    }
    catch (ApkProvisionException e) {
      return null;
    }
  }

  private static class ChooserDeployable implements Deployable {
    @NotNull private final String myPackageName;
    @NotNull private final AndroidDevice myAndroidDevice;

    private ChooserDeployable(@NotNull String packageName, @NotNull AndroidDevice androidDevice) {
      myPackageName = packageName;
      myAndroidDevice = androidDevice;
    }

    @NotNull
    @Override
    public Future<AndroidVersion> getVersion() {
      if (myAndroidDevice.isRunning()) {
        try {
          return DeploymentApplicationService.getInstance().getVersion(myAndroidDevice.getLaunchedDevice().get());
        }
        catch (InterruptedException | ExecutionException e) {
          return Futures.immediateFuture(AndroidVersion.DEFAULT);
        }
      }
      else {
        // When !AndroidDevice.isRunning(), getVersion() returns immediately since the information is read from AvdInfo (always available).
        return Futures.immediateFuture(myAndroidDevice.getVersion());
      }
    }

    @Override
    public boolean isApplicationRunningOnDeployable() {
      if (!myAndroidDevice.isRunning()) {
        return false;
      }

      try {
        // The get() operation could take a long time and this could get called on the EDT, so we'll only do a quick check.
        IDevice device = myAndroidDevice.getLaunchedDevice().get(0, TimeUnit.NANOSECONDS);
        if (device == null || !device.isOnline()) {
          return false;
        }
        // If the app doesn't have a Client associated with it, it's not running and should return false.
        List<Client> clients = Deployable.searchClientsForPackage(device, myPackageName);
        return !clients.isEmpty();
      }
      catch (InterruptedException | ExecutionException | TimeoutException e) {
        return false;
      }
    }

    @Override
    public boolean isOnline() {
      if (!myAndroidDevice.isRunning()) {
        return false;
      }

      Future<IDevice> iDeviceFuture = myAndroidDevice.getLaunchedDevice();
      if (!iDeviceFuture.isDone() || iDeviceFuture.isCancelled()) {
        return false;
      }

      try {
        return iDeviceFuture.get().isOnline();
      }
      catch (Exception e) {
        return false;
      }
    }

    @Override
    public boolean isUnauthorized() {
      if (!myAndroidDevice.isRunning()) {
        return false;
      }

      Future<IDevice> iDeviceFuture = myAndroidDevice.getLaunchedDevice();
      if (!iDeviceFuture.isDone() || iDeviceFuture.isCancelled()) {
        return false;
      }

      try {
        return iDeviceFuture.get().getState() == IDevice.DeviceState.UNAUTHORIZED;
      }
      catch (Exception e) {
        return false;
      }
    }
  }
}
