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

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SnapshotActionGroup extends ActionGroup {
  @NotNull
  private final Collection<Device> myDevices;

  @NotNull
  private final DeviceAndSnapshotComboBoxAction myComboBoxAction;

  @NotNull
  private final Project myProject;

  SnapshotActionGroup(@NotNull List<Device> devices, @NotNull DeviceAndSnapshotComboBoxAction comboBoxAction, @NotNull Project project) {
    super(getProperty(devices, Device::getName), null, getProperty(devices, Device::getIcon));
    setPopup(true);

    myDevices = devices;
    myComboBoxAction = comboBoxAction;
    myProject = project;
  }

  @NotNull
  private static <P> P getProperty(@NotNull List<Device> devices, @NotNull Function<Device, P> accessor) {
    P property = accessor.apply(devices.get(0));

    assert devices.subList(1, devices.size()).stream()
      .map(accessor)
      .allMatch(p -> p.equals(property));

    return property;
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent event) {
    return myDevices.stream()
      .map(device -> SelectDeviceAction.newSnapshotActionGroupChild(myComboBoxAction, myProject, device))
      .toArray(AnAction[]::new);
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof SnapshotActionGroup)) {
      return false;
    }

    SnapshotActionGroup group = (SnapshotActionGroup)object;
    return myDevices.equals(group.myDevices) && myComboBoxAction.equals(group.myComboBoxAction) && myProject.equals(group.myProject);
  }

  @Override
  public int hashCode() {
    int hashCode = myDevices.hashCode();

    hashCode = 31 * hashCode + myComboBoxAction.hashCode();
    hashCode = 31 * hashCode + myProject.hashCode();

    return hashCode;
  }
}
