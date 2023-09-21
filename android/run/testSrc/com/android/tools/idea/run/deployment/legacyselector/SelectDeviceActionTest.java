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
package com.android.tools.idea.run.deployment.legacyselector;

import static org.junit.Assert.assertEquals;

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.LaunchCompatibility;
import com.android.tools.idea.run.LaunchCompatibility.State;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import icons.StudioIcons;
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
      .setKey(new SerialNumber("00fff9d2279fa601"))
      .setIcon(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE)
      .setName("LGE Nexus 5X")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device device2 = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("00fff9d2279fa602"))
      .setIcon(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE)
      .setName("LGE Nexus 5X")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Mockito.when(myComboBoxAction.getDevices(myProject)).thenReturn(Optional.of(Arrays.asList(device1, device2)));

    AnAction action = new SelectDeviceAction(device1, myComboBoxAction);

    // Act
    action.update(myEvent);

    // Assert
    assertEquals("LGE Nexus 5X [00fff9d2279fa601]", myPresentation.getText());
  }

  @Test
  public void updateDeviceHasValidityReason() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setLaunchCompatibility(new LaunchCompatibility(State.ERROR, "Missing system image"))
      .setKey(new VirtualDeviceName("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setType(Device.Type.PHONE)
      .build();

    Mockito.when(myComboBoxAction.getDevices(myProject)).thenReturn(Optional.of(Collections.singletonList(device)));

    AnAction action = new SelectDeviceAction(device, myComboBoxAction);

    // Act
    action.update(myEvent);

    // Assert
    assertEquals("Pixel 3 API 29", myPresentation.getText());
  }

  @Test
  public void updateDoesntParseMnemonics() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("apiQ_64_Google")
      .setKey(new VirtualDeviceName("apiQ_64_Google"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setType(Device.Type.PHONE)
      .build();

    Mockito.when(myComboBoxAction.getDevices(myProject)).thenReturn(Optional.of(Collections.singletonList(device)));

    AnAction action = new SelectDeviceAction(device, myComboBoxAction);

    // Act
    action.update(myEvent);

    // Assert
    assertEquals("apiQ_64_Google", myPresentation.getText());
  }

  @Test
  public void actionPerformed() {
    // Arrange
    AnAction action = new SelectDeviceAction(TestDevices.buildPixel4Api30(), myComboBoxAction);

    // Act
    action.actionPerformed(myEvent);

    // Assert
    Mockito.verify(myComboBoxAction).setTargetSelectedWithComboBox(myProject, new QuickBootTarget(Keys.PIXEL_4_API_30));
  }
}
