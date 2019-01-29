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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.flags.StudioFlags;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.ui.popup.PopupFactoryImpl.ActionGroupPopup;
import icons.StudioIcons;
import org.jetbrains.android.actions.RunAndroidAvdManagerAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

final class DeviceAndSnapshotComboBoxAction extends ComboBoxAction {
  private final Supplier<Boolean> mySelectDeviceSnapshotComboBoxVisible;
  private final AsyncDevicesGetter myDevicesGetter;
  private final AnAction myOpenAvdManagerAction;

  private List<Device> myDevices;

  private Device mySelectedDevice;
  private String mySelectedSnapshot;

  @SuppressWarnings("unused")
  private DeviceAndSnapshotComboBoxAction() {
    this(() -> StudioFlags.SELECT_DEVICE_SNAPSHOT_COMBO_BOX_VISIBLE.get(), new AsyncDevicesGetter(ApplicationManager.getApplication()));
  }

  @VisibleForTesting
  DeviceAndSnapshotComboBoxAction(@NotNull Supplier<Boolean> selectDeviceSnapshotComboBoxVisible,
                                  @NotNull AsyncDevicesGetter devicesGetter) {
    mySelectDeviceSnapshotComboBoxVisible = selectDeviceSnapshotComboBoxVisible;
    myDevicesGetter = devicesGetter;
    myOpenAvdManagerAction = new RunAndroidAvdManagerAction();

    Presentation presentation = myOpenAvdManagerAction.getTemplatePresentation();

    presentation.setIcon(StudioIcons.Shell.Toolbar.DEVICE_MANAGER);
    presentation.setText("Open AVD Manager");

    myDevices = Collections.emptyList();
  }

  @NotNull
  @VisibleForTesting
  AnAction getOpenAvdManagerAction() {
    return myOpenAvdManagerAction;
  }

  @NotNull
  @VisibleForTesting
  List<Device> getDevices() {
    return myDevices;
  }

  @Nullable
  @VisibleForTesting
  Device getSelectedDevice() {
    return mySelectedDevice;
  }

  void setSelectedDevice(@Nullable Device selectedDevice) {
    mySelectedDevice = selectedDevice;
  }

  @Nullable
  @VisibleForTesting
  String getSelectedSnapshot() {
    return mySelectedSnapshot;
  }

  void setSelectedSnapshot(@Nullable String selectedSnapshot) {
    mySelectedSnapshot = selectedSnapshot;
  }

  @NotNull
  @Override
  protected ComboBoxButton createComboBoxButton(@NotNull Presentation presentation) {
    return new ComboBoxButton(presentation) {
      @Override
      protected JBPopup createPopup(@NotNull Runnable runnable) {
        DataContext context = getDataContext();

        ActionGroup group = createPopupActionGroup(this, context);
        boolean show = shouldShowDisabledActions();
        int count = getMaxRows();
        Condition<AnAction> condition = getPreselectCondition();

        JBPopup popup = new ActionGroupPopup(null, group, context, false, true, show, false, runnable, count, condition, null, true);
        popup.setMinimumSize(new Dimension(getMinWidth(), getMinHeight()));

        return popup;
      }
    };
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(@NotNull JComponent button) {
    DefaultActionGroup group = new DefaultActionGroup();

    Collection<AnAction> actions = newSelectDeviceAndSnapshotActions();
    group.addAll(actions);

    if (!actions.isEmpty()) {
      group.addSeparator();
    }

    group.add(myOpenAvdManagerAction);
    AnAction action = getTroubleshootDeviceConnectionsAction();

    if (action == null) {
      return group;
    }

    group.addSeparator();
    group.add(action);

    return group;
  }

  @NotNull
  private Collection<AnAction> newSelectDeviceAndSnapshotActions() {
    Collection<VirtualDevice> virtualDevices = new ArrayList<>(myDevices.size());
    Collection<Device> physicalDevices = new ArrayList<>(myDevices.size());

    myDevices.forEach(device -> {
      if (device instanceof VirtualDevice) {
        virtualDevices.add((VirtualDevice)device);
      }
      else if (device instanceof PhysicalDevice) {
        physicalDevices.add(device);
      }
      else {
        assert false;
      }
    });

    Collection<AnAction> actions = new ArrayList<>(virtualDevices.size() + 1 + physicalDevices.size());

    virtualDevices.stream()
                  .map(this::newSelectDeviceAndSnapshotAction)
                  .forEach(actions::add);

    if (!virtualDevices.isEmpty() && !physicalDevices.isEmpty()) {
      actions.add(Separator.create());
    }

    physicalDevices.stream()
                   .map(device -> new SelectDeviceAndSnapshotAction(this, device))
                   .forEach(actions::add);

    return actions;
  }

  @NotNull
  private AnAction newSelectDeviceAndSnapshotAction(@NotNull VirtualDevice device) {
    Collection<String> snapshots = device.getSnapshots();

    if (snapshots.isEmpty() || snapshots.equals(VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION)) {
      return new SelectDeviceAndSnapshotAction(this, device);
    }

    return new SnapshotActionGroup(device, this);
  }

  @Nullable
  private static AnAction getTroubleshootDeviceConnectionsAction() {
    AnAction action = ActionManager.getInstance().getAction("DeveloperServices.ConnectionAssistant");

    if (action == null) {
      return null;
    }

    Presentation presentation = action.getTemplatePresentation();

    presentation.setIcon(null);
    presentation.setText("Troubleshoot device connections");

    return action;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Project project = event.getProject();

    if (project == null) {
      return;
    }

    Presentation presentation = event.getPresentation();

    if (!mySelectDeviceSnapshotComboBoxVisible.get()) {
      presentation.setVisible(false);
      return;
    }

    presentation.setVisible(true);
    myDevices = myDevicesGetter.get(project);

    if (myDevices.isEmpty()) {
      mySelectedDevice = null;
      mySelectedSnapshot = null;

      presentation.setIcon(null);
      presentation.setText("No devices");

      return;
    }

    updateSelectedDevice();
    updateSelectedSnapshot();

    presentation.setIcon(mySelectedDevice.getIcon());
    presentation.setText(mySelectedSnapshot == null ? mySelectedDevice.getName() : mySelectedDevice + " - " + mySelectedSnapshot);
  }

  private void updateSelectedDevice() {
    if (mySelectedDevice == null) {
      mySelectedDevice = myDevices.get(0);
      return;
    }

    Object selectedDeviceName = mySelectedDevice.getName();

    Optional<Device> selectedDevice = myDevices.stream()
                                               .filter(device -> device.getName().equals(selectedDeviceName))
                                               .findFirst();

    mySelectedDevice = selectedDevice.orElseGet(() -> myDevices.get(0));
  }

  private void updateSelectedSnapshot() {
    Collection<String> snapshots = mySelectedDevice.getSnapshots();

    if (mySelectedSnapshot == null) {
      Optional<String> selectedDeviceSnapshot = snapshots.stream().findFirst();
      selectedDeviceSnapshot.ifPresent(snapshot -> mySelectedSnapshot = snapshot);

      return;
    }

    if (snapshots.contains(mySelectedSnapshot)) {
      return;
    }

    Optional<String> selectedSnapshot = snapshots.stream().findFirst();
    mySelectedSnapshot = selectedSnapshot.orElse(null);
  }
}
