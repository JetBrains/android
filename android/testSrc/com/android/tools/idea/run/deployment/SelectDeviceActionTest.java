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

import static org.junit.Assert.assertEquals;

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import java.nio.file.FileSystem;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class SelectDeviceActionTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private DeviceAndSnapshotComboBoxAction myComboBoxAction;

  private Presentation myPresentation;
  private Project myProject;
  private AnActionEvent myEvent;

  @Before
  public void mockComboBoxAction() {
    myComboBoxAction = Mockito.mock(DeviceAndSnapshotComboBoxAction.class);
  }

  @Before
  public void mockEvent() {
    myPresentation = new Presentation();
    myProject = myRule.getProject();

    myEvent = Mockito.mock(AnActionEvent.class);

    Mockito.when(myEvent.getPresentation()).thenReturn(myPresentation);
    Mockito.when(myEvent.getProject()).thenReturn(myProject);
  }

  @Test
  public void updateDeviceNamesAreEqual() {
    // Arrange
    Device device1 = new PhysicalDevice.Builder()
      .setName("LGE Nexus 5X")
      .setKey(new SerialNumber("00fff9d2279fa601"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device device2 = new PhysicalDevice.Builder()
      .setName("LGE Nexus 5X")
      .setKey(new SerialNumber("00fff9d2279fa602"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Mockito.when(myComboBoxAction.getDevices(myProject)).thenReturn(Optional.of(Arrays.asList(device1, device2)));

    AnAction action = SelectDeviceAction.newSelectDeviceAction(device1, myComboBoxAction);

    // Act
    action.update(myEvent);

    // Assert
    assertEquals("LGE Nexus 5X [00fff9d2279fa601]", myPresentation.getText());
  }

  @Test
  public void updateDeviceHasSnapshot() {
    // Arrange
    FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix());

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new NonprefixedKey("Pixel_3_API_29/snap_2018-08-07_16-27-58"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshot(new Snapshot(fileSystem.getPath("snap_2018-08-07_16-27-58"), fileSystem))
      .build();

    Mockito.when(myComboBoxAction.getDevices(myProject)).thenReturn(Optional.of(Collections.singletonList(device)));
    Mockito.when(myComboBoxAction.areSnapshotsEnabled()).thenReturn(true);

    AnAction action = SelectDeviceAction.newSelectDeviceAction(device, myComboBoxAction);

    // Act
    action.update(myEvent);

    // Assert
    assertEquals("Pixel 3 API 29 - snap_2018-08-07_16-27-58", myPresentation.getText());
  }

  @Test
  public void updateDeviceHasValidityReason() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setValidityReason("Missing system image")
      .setKey(new VirtualDeviceName("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Mockito.when(myComboBoxAction.getDevices(myProject)).thenReturn(Optional.of(Collections.singletonList(device)));

    AnAction action = SelectDeviceAction.newSelectDeviceAction(device, myComboBoxAction);

    // Act
    action.update(myEvent);

    // Assert
    assertEquals("Pixel 3 API 29 (Missing system image)", myPresentation.getText());
  }

  @Test
  public void updateDoesntParseMnemonics() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("apiQ_64_Google")
      .setKey(new VirtualDeviceName("apiQ_64_Google"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Mockito.when(myComboBoxAction.getDevices(myProject)).thenReturn(Optional.of(Collections.singletonList(device)));

    AnAction action = SelectDeviceAction.newSelectDeviceAction(device, myComboBoxAction);

    // Act
    action.update(myEvent);

    // Assert
    assertEquals("apiQ_64_Google", myPresentation.getText());
  }
}
