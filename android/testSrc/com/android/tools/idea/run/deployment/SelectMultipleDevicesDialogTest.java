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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.deployment.DevicesSelectedService.PersistentStateComponent;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ValidationInfo;
import java.nio.file.FileSystem;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class SelectMultipleDevicesDialogTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private SelectMultipleDevicesDialog myDialog;

  private void initDialog(@NotNull List<Device> devices,
                          @NotNull BooleanSupplier selectDeviceSnapshotComboBoxSnapshotsEnabledGet,
                          @NotNull Function<Project, DevicesSelectedService> devicesSelectedServiceGetInstance) {
    Application application = ApplicationManager.getApplication();

    application.invokeAndWait(() -> myDialog = new SelectMultipleDevicesDialog(myRule.getProject(),
                                                                               devices,
                                                                               selectDeviceSnapshotComboBoxSnapshotsEnabledGet,
                                                                               devicesSelectedServiceGetInstance));
  }

  @After
  public void disposeOfDialog() {
    ApplicationManager.getApplication().invokeAndWait(myDialog::disposeIfNeeded);
  }

  @Test
  public void selectMultipleDevicesDialog() {
    // Arrange
    List<Device> devices = Collections.emptyList();
    DevicesSelectedService service = Mockito.mock(DevicesSelectedService.class);

    // Act
    initDialog(devices, () -> true, project -> service);

    // Assert
    assertEquals("Select Multiple Devices", myDialog.getTitle());
  }

  @Test
  public void initTableRunOnMultipleDevicesActionIsEnabled() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 29")
      .setKey(new VirtualDeviceName("Pixel_4_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setType(Device.Type.PHONE)
      .build();

    DevicesSelectedService service = Mockito.mock(DevicesSelectedService.class);
    initDialog(Collections.singletonList(device), () -> false, project -> service);

    // Act
    myDialog.getTable().setSelected(true, 0);

    // Assert
    assertTrue(myDialog.getOKAction().isEnabled());
  }

  @Test
  public void initTable() {
    // Arrange
    Key key = new VirtualDeviceName("Pixel_4_API_29");

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 29")
      .setKey(key)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setType(Device.Type.PHONE)
      .build();

    Clock clock = Clock.fixed(Instant.parse("2018-11-28T01:15:27Z"), ZoneId.of("America/Los_Angeles"));

    DevicesSelectedService service = new DevicesSelectedService(new PersistentStateComponent(), clock);
    service.setTargetsSelectedWithDialog(Collections.singleton(new QuickBootTarget(key)));

    initDialog(Collections.singletonList(device), () -> false, project -> service);

    // Act
    myDialog.getTable().setSelected(false, 0);

    // Assert
    assertTrue(myDialog.getOKAction().isEnabled());
  }

  @Test
  public void initOkActionDoesntDisableActionAfterTableModelEventsAreHandled() {
    // Arrange
    Key key = new VirtualDeviceName("Pixel_4_API_30");

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(key)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setType(Device.Type.PHONE)
      .build();

    List<Device> devices = Collections.singletonList(device);

    DevicesSelectedService service = Mockito.mock(DevicesSelectedService.class);
    Mockito.when(service.getTargetsSelectedWithDialog(devices)).thenReturn(Collections.singleton(new QuickBootTarget(key)));

    // Act
    initDialog(devices, () -> false, project -> service);

    // Assert
    assertTrue(myDialog.getOKAction().isEnabled());
  }

  @Test
  public void doOkAction() {
    // Arrange
    Key key = new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd");

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(key)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setType(Device.Type.PHONE)
      .build();

    DevicesSelectedService service = Mockito.mock(DevicesSelectedService.class);
    initDialog(Collections.singletonList(device), () -> false, project -> service);

    // Act
    myDialog.getTable().setSelected(true, 0);
    ApplicationManager.getApplication().invokeAndWait(() -> myDialog.getOKAction().actionPerformed(null));

    // Assert
    Mockito.verify(service).setTargetsSelectedWithDialog(Collections.singleton(new QuickBootTarget(key)));
  }

  @Test
  public void doValidate() {
    // Arrange
    FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix());

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setType(Device.Type.PHONE)
      .addSnapshot(new Snapshot(fileSystem.getPath("/home/user/.android/avd/Pixel_4_API_30.avd/snapshots/snap_2020-12-07_16-36-58")))
      .setSelectDeviceSnapshotComboBoxSnapshotsEnabled(true)
      .build();

    DevicesSelectedService service = Mockito.mock(DevicesSelectedService.class);
    initDialog(Collections.singletonList(device), () -> true, project -> service);

    SelectMultipleDevicesDialogTable table = myDialog.getTable();
    table.setSelected(true, 0);
    table.setSelected(true, 1);

    // Act
    ValidationInfo validation = myDialog.doValidate();

    // Assert
    assert validation != null;

    assertEquals("Some of the selected targets are for the same device. Each target should be for a different device.", validation.message);
    assertNull(validation.component);
  }
}
