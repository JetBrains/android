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
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.deployable.Deployable;
import com.android.tools.idea.run.deployable.DeployableProvider;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeviceAndSnapshotComboBoxDeployableProvider implements DeployableProvider {
  @NotNull private final Project myProject;
  @NotNull private final ApplicationIdProvider myApplicationIdProvider;

  public DeviceAndSnapshotComboBoxDeployableProvider(@NotNull Project project, @NotNull ApplicationIdProvider applicationIdProvider) {
    myApplicationIdProvider = applicationIdProvider;
    myProject = project;
  }

  @Override
  public boolean isDependentOnUserInput() {
    return false;
  }

  @Nullable
  @Override
  public Deployable getDeployable() throws ApkProvisionException {
    ActionManager manager = ActionManager.getInstance();
    Device device = ((DeviceAndSnapshotComboBoxAction)manager.getAction("DeviceAndSnapshotComboBox")).getSelectedDevice(myProject);

    if (device == null) {
      return null;
    }

    return new DeployableDevice(device, myApplicationIdProvider.getPackageName());
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
  }
}
