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
package com.android.tools.idea.run.deployment.legacyselector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.LaunchCompatibility;
import com.android.tools.idea.run.LaunchCompatibility.State;
import com.android.tools.idea.run.deployment.legacyselector.Device.Type;
import com.android.tools.idea.run.editor.DeployTarget;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class DeviceAndSnapshotComboBoxTargetProviderTest {
  @NotNull
  private final DeviceAndSnapshotComboBoxAction myAction = Mockito.mock(DeviceAndSnapshotComboBoxAction.class);

  @NotNull
  private final DialogWrapper myDialog = Mockito.mock(DialogWrapper.class);

  @NotNull
  private final Project myProject = Mockito.mock(Project.class);

  @Test
  public void requiresRuntimePrompt() {
    // Arrange
    var provider = new DeviceAndSnapshotComboBoxTargetProvider(() -> myAction, (project, devices) -> myDialog, () -> null);

    // Act
    var requires = provider.requiresRuntimePrompt(myProject);

    // Assert
    assertFalse(requires);
  }

  @Test
  public void showErrorMessage() {
    // Arrange
    Device deviceWithError = new VirtualDevice.Builder()
      .setName("Pixel 4 API 29")
      .setType(Type.PHONE)
      .setLaunchCompatibility(new LaunchCompatibility(State.ERROR, "error"))
      .setKey(Keys.PIXEL_4_API_29)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    List<Device> devices = Arrays.asList(deviceWithError, TestDevices.buildPixel4Api30());

    DialogSupplier errorDialogSupplier = Mockito.mock(DialogSupplier.class);
    Mockito.when(errorDialogSupplier.get(any(Project.class), anyList())).thenReturn(myDialog);

    Set<Target> targets = new HashSet<>(Arrays.asList(new QuickBootTarget(Keys.PIXEL_4_API_29), new QuickBootTarget(Keys.PIXEL_4_API_30)));

    Mockito.when(myAction.getSelectedDevices(myProject)).thenReturn(devices);
    Mockito.when(myAction.getDevices(myProject)).thenReturn(Optional.of(devices));
    Mockito.when(myAction.getSelectedTargets(myProject)).thenReturn(Optional.of(targets));

    var provider = new DeviceAndSnapshotComboBoxTargetProvider(() -> myAction, errorDialogSupplier, () -> null);

    Module module = Mockito.mock(Module.class);
    Mockito.when(module.getProject()).thenReturn(myProject);

    var requiresRuntimePrompt = provider.requiresRuntimePrompt(myProject);
    assertTrue(requiresRuntimePrompt);

    // Act
    var deployTarget = provider.showPrompt(myProject);

    // Assert
    assert deployTarget == null;
    Mockito.verify(errorDialogSupplier).get(myProject, List.of(deviceWithError));
  }

  @Test
  public void showPrompt() {
    // Arrange
    var expectedTarget = Mockito.mock(DeployTarget.class);
    var provider = new DeviceAndSnapshotComboBoxTargetProvider(() -> myAction, (project, devices) -> myDialog, () -> expectedTarget);

    // Act
    var actualTarget = provider.showPrompt(myProject);

    // Assert
    assertEquals(expectedTarget, actualTarget);
  }
}
