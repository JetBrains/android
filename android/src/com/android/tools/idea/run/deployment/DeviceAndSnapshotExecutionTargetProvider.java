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
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.deployable.Deployable;
import com.intellij.execution.DefaultExecutionTarget;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.ExecutionTargetProvider;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.Collections;
import java.util.List;
import javax.swing.Icon;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeviceAndSnapshotExecutionTargetProvider extends ExecutionTargetProvider {
  @NotNull
  @Override
  public List<ExecutionTarget> getTargets(@NotNull Project project, @NotNull RunnerAndConfigurationSettings configuration) {
    ActionManager manager = ActionManager.getInstance();
    DeviceAndSnapshotComboBoxAction action = (DeviceAndSnapshotComboBoxAction)manager.getAction("DeviceAndSnapshotComboBox");

    if (!StudioFlags.SELECT_DEVICE_SNAPSHOT_COMBO_BOX_VISIBLE.get()) {
      return Collections.singletonList(DefaultExecutionTarget.INSTANCE);
    }

    RunnerAndConfigurationSettings settings = RunManager.getInstance(project).getSelectedConfiguration();
    if (settings != null && settings.getConfiguration() instanceof AndroidRunConfigurationBase) {
      AndroidRunConfigurationBase config = (AndroidRunConfigurationBase)settings.getConfiguration();
      Module module = config.getConfigurationModule().getModule();
      if (module != null) {
        AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet != null) {
          // We always return a ExecutionTarget as long as we have valid config and package names.
          // This is because we don't want to maintain listener states with ddmlib, and we don't
          // need to keep track of online/offline states, and we let things that need the states
          // to query on their own.
          Device device = action.getSelectedDevice(project);
          if (device != null) {
            try {
              String packageName = config.getApplicationIdProvider(facet).getPackageName();
              return Collections.singletonList(new Target(device, packageName));
            }
            catch (ApkProvisionException ignored) {
              // There's nothing much we can do if we're having issues loading or parsing the manifest.
              // The application most likely won't run in this case anyway, so we'll fall back to the
              // default execution target in this case.
            }
          }
        }
      }
    }

    return Collections.singletonList(DefaultExecutionTarget.INSTANCE);
  }

  static class Target extends AndroidExecutionTarget {
    @NotNull private final Device myDevice;
    @NotNull private final String myPackageName;
    @NotNull private final String myId;

    private Target(@NotNull Device device, @NotNull String packageName) {
      myDevice = device;
      myPackageName = packageName;
      myId = "device_and_snapshot_execution_target|" + myDevice.getName();
    }

    @NotNull
    @Override
    public String getId() {
      return myId;
    }

    @NotNull
    @Override
    public String getDisplayName() {
      return myDevice.getName();
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return myDevice.getIcon();
    }

    @Override
    public boolean canRun(@NotNull RunnerAndConfigurationSettings configuration) {
      return true;
    }

    @Override
    public boolean isApplicationRunning() {
      IDevice iDevice = myDevice.getDdmlibDevice();
      if (iDevice == null || !iDevice.isOnline()) {
        return false;
      }
      List<Client> clients = Deployable.searchClientsForPackage(iDevice, myPackageName);
      return !clients.isEmpty();
    }

    @Nullable
    @Override
    public IDevice getIDevice() {
      return myDevice.getDdmlibDevice();
    }

    @NotNull
    Device getDevice() {
      return myDevice;
    }
  }
}
