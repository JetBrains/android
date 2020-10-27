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
import com.intellij.openapi.actionSystem.Presentation;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SnapshotActionGroup extends ActionGroup {
  @NotNull
  private final List<Device> myDevices;

  SnapshotActionGroup(@NotNull List<Device> devices) {
    setPopup(true);
    myDevices = devices;
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent event) {
    DeviceAndSnapshotComboBoxAction action = DeviceAndSnapshotComboBoxAction.getInstance();

    return myDevices.stream()
      .map(device -> SelectDeviceAction.newSnapshotActionGroupChild(device, action))
      .toArray(AnAction[]::new);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();

    presentation.setIcon(getIcon());
    presentation.setText(getText(), false);
  }

  @NotNull
  private Icon getIcon() {
    Optional<Icon> icon = myDevices.stream()
      .filter(Device::isConnected)
      .map(Device::getIcon)
      .findFirst();

    return icon.orElse(myDevices.get(0).getIcon());
  }

  @NotNull
  private String getText() {
    String name = getProperty(Device::getName);
    assert name != null;

    return Devices.getText(name, getProperty(Device::getValidityReason));
  }

  @Nullable
  private <P> P getProperty(@NotNull Function<Device, P> accessor) {
    P property = accessor.apply(myDevices.get(0));

    assert myDevices.subList(1, myDevices.size()).stream()
      .map(accessor)
      .allMatch(Predicate.isEqual(property));

    return property;
  }
}
