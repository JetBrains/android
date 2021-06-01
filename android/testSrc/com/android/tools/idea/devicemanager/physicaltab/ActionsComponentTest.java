/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.physicaltab;

import com.android.tools.idea.devicemanager.physicaltab.PhysicalDevice.ConnectionType;
import com.intellij.openapi.project.Project;
import java.util.function.BiConsumer;
import javax.swing.AbstractButton;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class ActionsComponentTest {
  @SuppressWarnings("unchecked")
  private final @NotNull BiConsumer<@NotNull Project, @NotNull String> myOpenAndShowDevice = Mockito.mock(BiConsumer.class);

  @Test
  public void activateDeviceFileExplorerWindowProjectIsNull() {
    // Arrange
    ActionsComponent component = new ActionsComponent(null, null, myOpenAndShowDevice, EditDeviceNameDialog::new);
    AbstractButton button = component.getActivateDeviceFileExplorerWindowButton();

    // Act
    button.doClick();

    // Assert
    Mockito.verify(myOpenAndShowDevice, Mockito.never()).accept(ArgumentMatchers.any(), ArgumentMatchers.any());
  }

  @Test
  public void activateDeviceFileExplorerWindowDeviceIsNull() {
    // Arrange
    ActionsComponent component = new ActionsComponent(Mockito.mock(Project.class), null, myOpenAndShowDevice, EditDeviceNameDialog::new);
    AbstractButton button = component.getActivateDeviceFileExplorerWindowButton();

    // Act
    button.doClick();

    // Assert
    Mockito.verify(myOpenAndShowDevice, Mockito.never()).accept(ArgumentMatchers.any(), ArgumentMatchers.any());
  }

  @Test
  public void activateDeviceFileExplorerWindowDeviceIsntOnline() {
    // Arrange
    ActionsComponent component = new ActionsComponent(Mockito.mock(Project.class), null, myOpenAndShowDevice, EditDeviceNameDialog::new);
    component.setDevice(TestPhysicalDevices.GOOGLE_PIXEL_3);

    AbstractButton button = component.getActivateDeviceFileExplorerWindowButton();

    // Act
    button.doClick();

    // Assert
    Mockito.verify(myOpenAndShowDevice, Mockito.never()).accept(ArgumentMatchers.any(), ArgumentMatchers.any());
  }

  @Test
  public void activateDeviceFileExplorerWindow() {
    // Arrange
    Project project = Mockito.mock(Project.class);

    PhysicalDevice device = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("86UX00F4R"))
      .setName("Google Pixel 3")
      .setTarget("Android 12 Preview")
      .setApi("S")
      .addConnectionType(ConnectionType.USB)
      .build();

    ActionsComponent component = new ActionsComponent(project, null, myOpenAndShowDevice, EditDeviceNameDialog::new);
    component.setDevice(device);

    AbstractButton button = component.getActivateDeviceFileExplorerWindowButton();

    // Act
    button.doClick();

    // Assert
    Mockito.verify(myOpenAndShowDevice).accept(project, device.getKey().toString());
  }

  @Test
  public void editDeviceNameNotDialogShowAndGet() {
    // Arrange
    PhysicalDeviceTableModel model = Mockito.mock(PhysicalDeviceTableModel.class);

    NewEditDeviceNameDialog newEditDeviceNameDialog = Mockito.mock(NewEditDeviceNameDialog.class);
    Mockito.when(newEditDeviceNameDialog.apply(null, "", "Google Pixel 3")).thenReturn(Mockito.mock(EditDeviceNameDialog.class));

    ActionsComponent component = new ActionsComponent(null, model, myOpenAndShowDevice, newEditDeviceNameDialog);
    component.setDevice(TestPhysicalDevices.GOOGLE_PIXEL_3);

    AbstractButton button = component.getEditDeviceNameButton();

    // Act
    button.doClick();

    // Assert
    Mockito.verify(model, Mockito.never()).setNameOverride(ArgumentMatchers.any(), ArgumentMatchers.any());
  }

  @Test
  public void editDeviceName() {
    // Arrange
    PhysicalDeviceTableModel model = Mockito.mock(PhysicalDeviceTableModel.class);

    EditDeviceNameDialog dialog = Mockito.mock(EditDeviceNameDialog.class);

    Mockito.when(dialog.showAndGet()).thenReturn(true);
    Mockito.when(dialog.getNameOverride()).thenReturn("Google Pixel 5");

    NewEditDeviceNameDialog newEditDeviceNameDialog = Mockito.mock(NewEditDeviceNameDialog.class);
    Mockito.when(newEditDeviceNameDialog.apply(null, "", "Google Pixel 3")).thenReturn(dialog);

    ActionsComponent component = new ActionsComponent(null, model, myOpenAndShowDevice, newEditDeviceNameDialog);
    component.setDevice(TestPhysicalDevices.GOOGLE_PIXEL_3);

    AbstractButton button = component.getEditDeviceNameButton();

    // Act
    button.doClick();

    // Assert
    Mockito.verify(model).setNameOverride(new SerialNumber("86UX00F4R"), "Google Pixel 5");
  }
}
