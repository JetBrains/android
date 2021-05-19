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
import com.intellij.execution.configurations.RunConfiguration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeviceAndSnapshotComboBoxDeployableProvider implements DeployableProvider {
  @Nullable
  @Override
  public Deployable getDeployable(@NotNull RunConfiguration runConfiguration) throws ApkProvisionException {
    if (!(runConfiguration instanceof AndroidRunConfigurationBase)) {
      return null;
    }
    AndroidRunConfigurationBase androidRunConfiguration = (AndroidRunConfigurationBase)runConfiguration;

    List<Device> devices = DeviceAndSnapshotComboBoxAction.getInstance().getSelectedDevices(androidRunConfiguration.getProject());

    if (devices.size() != 1) {
      return null;
    }

    ApplicationIdProvider applicationIdProvider = androidRunConfiguration.getApplicationIdProvider();
    if (applicationIdProvider == null) {
      return null;
    }
    return new DeployableDevice(devices.get(0), applicationIdProvider.getPackageName());
  }

  private static final class DeployableDevice implements Deployable {
    @NotNull private final Device myDevice;
    @NotNull private final String myPackageName;

    private DeployableDevice(@NotNull Device device, @NotNull String packageName) {
      myDevice = device;
      myPackageName = packageName;
    }

    @NotNull
    @Override
    public Future<AndroidVersion> getVersion() {
      return myDevice.getAndroidVersion();
    }

    @NotNull
    @Override
    public List<Client> searchClientsForPackage() {
      IDevice iDevice = myDevice.getDdmlibDevice();
      if (iDevice == null) {
        return Collections.emptyList();
      }
      return Deployable.searchClientsForPackage(iDevice, myPackageName);
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
  }
}
