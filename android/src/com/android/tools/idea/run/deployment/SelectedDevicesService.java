/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.containers.ContainerUtil;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

/**
 * A project scoped service with getters and setters for the device set in the Modify Device Set dialog. The actual point of this is for
 * stubbing and verification in tests.
 */
final class SelectedDevicesService {
  private static final String SELECTED_DEVICES = "SelectDeploymentTargetsDialog.selectedDevices";

  @NotNull
  private final Project myProject;

  @NotNull
  private final Function<Project, PropertiesComponent> myPropertiesComponentGetInstance;

  @SuppressWarnings("unused")
  private SelectedDevicesService(@NotNull Project project) {
    this(project, PropertiesComponent::getInstance);
  }

  @VisibleForTesting
  @NonInjectable
  SelectedDevicesService(@NotNull Project project, @NotNull Function<Project, PropertiesComponent> propertiesComponentGetInstance) {
    myProject = project;
    myPropertiesComponentGetInstance = propertiesComponentGetInstance;
  }

  @NotNull
  static SelectedDevicesService getInstance(@NotNull Project project) {
    return project.getService(SelectedDevicesService.class);
  }

  boolean isSelectionEmpty() {
    return !myPropertiesComponentGetInstance.apply(myProject).isValueSet(SELECTED_DEVICES);
  }

  @NotNull
  List<Device> getSelectedDevices() {
    Collection<Key> keys = getSelectedDeviceKeys();
    return ContainerUtil.filter(AsyncDevicesGetter.getInstance(myProject).get(), device -> keys.contains(device.getKey()));
  }

  void setSelectedDevices(@NotNull List<Device> selectedDevices) {
    setSelectedDeviceKeys(selectedDevices.stream().map(Device::getKey));
  }

  @NotNull
  Set<Key> getSelectedDeviceKeys() {
    String[] keys = myPropertiesComponentGetInstance.apply(myProject).getValues(SELECTED_DEVICES);

    if (keys == null) {
      return Collections.emptySet();
    }

    assert !Arrays.asList(keys).contains("") : Arrays.toString(keys);

    return Arrays.stream(keys)
      .map(Key::new)
      .collect(Collectors.toSet());
  }

  void setSelectedDeviceKeys(@NotNull Set<Key> selectedDeviceKeys) {
    setSelectedDeviceKeys(selectedDeviceKeys.stream());
  }

  private void setSelectedDeviceKeys(@NotNull Stream<Key> stream) {
    String[] array = stream
      .map(Key::toString)
      .toArray(String[]::new);

    PropertiesComponent properties = myPropertiesComponentGetInstance.apply(myProject);

    if (array.length == 0) {
      properties.unsetValue(SELECTED_DEVICES);
    }
    else {
      properties.setValues(SELECTED_DEVICES, array);
    }
  }
}
