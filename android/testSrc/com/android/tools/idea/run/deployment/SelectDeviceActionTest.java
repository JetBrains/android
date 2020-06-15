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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
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
  private Project myProject;

  @Before
  public void mockComboBoxAction() {
    myComboBoxAction = Mockito.mock(DeviceAndSnapshotComboBoxAction.class);
  }

  @Before
  public void initProject() {
    myProject = myRule.getProject();
  }

  @Test
  public void selectDeviceActionTwoDevicesHaveSameName() {
    // Arrange
    Device lgeNexus5x1 = new PhysicalDevice.Builder()
      .setName("LGE Nexus 5X")
      .setKey(new Key("00fff9d2279fa601"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device lgeNexus5x2 = new PhysicalDevice.Builder()
      .setName("LGE Nexus 5X")
      .setKey(new Key("00fff9d2279fa602"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Mockito.when(myComboBoxAction.getDevices(myProject)).thenReturn(Arrays.asList(lgeNexus5x1, lgeNexus5x2));

    // Act
    AnAction action = SelectDeviceAction.newSelectDeviceAction(myComboBoxAction, myProject, lgeNexus5x1);

    // Assert
    assertEquals("LGE Nexus 5X [00fff9d2279fa601]", action.getTemplatePresentation().getText());
  }

  @Test
  public void newSelectDeviceActionDeviceHasNondefaultSnapshot() {
    // Arrange
    Mockito.when(myComboBoxAction.areSnapshotsEnabled()).thenReturn(true);

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new Key("Pixel_3_API_29/snap_2019-09-27_15-48-09"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshot(new Snapshot(Paths.get("snap_2019-09-27_15-48-09"), "Snapshot"))
      .build();

    // Act
    AnAction action = SelectDeviceAction.newSelectDeviceAction(myComboBoxAction, myProject, device);

    // Assert
    assertEquals("Pixel 3 API 29 - Snapshot", action.getTemplatePresentation().getText());
  }

  @Test
  public void newSelectDeviceActionDeviceHasValidityReason() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setValidityReason("Missing system image")
      .setKey(new Key("Pixel_3_API_29/snap_2019-09-27_15-48-09"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    // Act
    AnAction action = SelectDeviceAction.newSelectDeviceAction(myComboBoxAction, myProject, device);

    // Assert
    assertEquals("Pixel 3 API 29 (Missing system image)", action.getTemplatePresentation().getText());
  }

  @Test
  public void configurePresentationSetTextDoesntMangleDeviceName() {
    // Arrange
    Device apiQ64Google = new VirtualDevice.Builder()
      .setName("apiQ_64_Google")
      .setKey(new Key("apiQ_64_Google"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Mockito.when(myComboBoxAction.getDevices(myProject)).thenReturn(Collections.singletonList(apiQ64Google));

    // Act
    AnAction action = SelectDeviceAction.newSelectDeviceAction(myComboBoxAction, myProject, apiQ64Google);

    // Assert
    assertEquals("apiQ_64_Google", action.getTemplatePresentation().getText());
  }
}
