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

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.LaunchCompatibility;
import com.android.tools.idea.run.LaunchCompatibility.State;
import com.android.tools.idea.run.editor.DeployTargetProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class DeviceAndSnapshotComboBoxTargetProviderTest {
  @Test
  public void showErrorMessage() {
    // Arrange
    Key key30 = new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd");
    Key key29 = new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_29.avd");

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(key30)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device deviceWithError = new VirtualDevice.Builder()
      .setName("Pixel 4 API 29")
      .setKey(key29)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setLaunchCompatibility(new LaunchCompatibility(State.ERROR, "error"))
      .build();

    List<Device> devices = Arrays.asList(deviceWithError, device);
    DialogWrapper errorDialog = Mockito.mock(DialogWrapper.class);
    Project project = Mockito.mock(Project.class);

    DialogSupplier errorDialogSupplier = Mockito.mock(DialogSupplier.class);
    Mockito.when(errorDialogSupplier.get(any(Project.class), anyList())).thenReturn(errorDialog);

    Set<Target> targets = new HashSet<>(Arrays.asList(new QuickBootTarget(key29), new QuickBootTarget(key30)));

    DeviceAndSnapshotComboBoxAction action = Mockito.mock(DeviceAndSnapshotComboBoxAction.class);
    Mockito.when(action.getSelectedDevices(project)).thenReturn(devices);
    Mockito.when(action.getDevices(project)).thenReturn(Optional.of(devices));
    Mockito.when(action.getSelectedTargets(project)).thenReturn(targets);

    DeployTargetProvider provider = new DeviceAndSnapshotComboBoxTargetProvider(() -> action, errorDialogSupplier);

    Module module = Mockito.mock(Module.class);
    Mockito.when(module.getProject()).thenReturn(project);

    AndroidFacet facet = Mockito.mock(AndroidFacet.class);
    Mockito.when(facet.getModule()).thenReturn(module);

    boolean requiresRuntimePrompt = provider.requiresRuntimePrompt(project);
    assertTrue(requiresRuntimePrompt);

    // Act
    Object deployTarget = provider.showPrompt(facet);

    // Assert
    assert deployTarget == null;
    Mockito.verify(errorDialogSupplier).get(project, Collections.singletonList(deviceWithError));
  }
}
