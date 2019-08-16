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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public final class SelectDeviceAndSnapshotActionTest {
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
  public void selectDeviceAndSnapshotActionSnapshotsIsEmpty() {
    Device device = new VirtualDevice.Builder()
      .setName(TestDevices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    SelectDeviceAndSnapshotAction action = new SelectDeviceAndSnapshotAction.Builder()
      .setComboBoxAction(myComboBoxAction)
      .setProject(myProject)
      .setDevice(device)
      .build();

    assertNull(action.getSnapshot());
  }

  @Test
  public void selectDeviceAndSnapshotActionSnapshotsEqualsDefaultSnapshotCollection() {
    Mockito.when(myComboBoxAction.areSnapshotsEnabled()).thenReturn(true);

    Device device = new VirtualDevice.Builder()
      .setName(TestDevices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshot(Snapshot.DEFAULT)
      .build();

    SelectDeviceAndSnapshotAction action = new SelectDeviceAndSnapshotAction.Builder()
      .setComboBoxAction(myComboBoxAction)
      .setProject(myProject)
      .setDevice(device)
      .build();

    assertEquals(Snapshot.DEFAULT, action.getSnapshot());
  }

  @Test
  public void selectDeviceAndSnapshotActionThrowsIllegalArgumentException() {
    Mockito.when(myComboBoxAction.areSnapshotsEnabled()).thenReturn(true);

    Device device = new VirtualDevice.Builder()
      .setName(TestDevices.PIXEL_2_XL_API_28)
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshot(new Snapshot("snap_2018-08-07_16-27-58"))
      .build();

    try {
      new SelectDeviceAndSnapshotAction.Builder()
        .setComboBoxAction(myComboBoxAction)
        .setProject(myProject)
        .setDevice(device)
        .build();

      fail();
    }
    catch (IllegalArgumentException ignored) {
    }
  }

  @Test
  public void selectDeviceAndSnapshotActionTwoDevicesHaveSameName() {
    // Arrange
    Device lgeNexus5x1 = new PhysicalDevice.Builder()
      .setName("LGE Nexus 5X")
      .setKey("00fff9d2279fa601")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device lgeNexus5x2 = new PhysicalDevice.Builder()
      .setName("LGE Nexus 5X")
      .setKey("00fff9d2279fa602")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Mockito.when(myComboBoxAction.getDevices(myProject)).thenReturn(Arrays.asList(lgeNexus5x1, lgeNexus5x2));

    // Act
    AnAction action = new SelectDeviceAndSnapshotAction.Builder()
      .setComboBoxAction(myComboBoxAction)
      .setProject(myProject)
      .setDevice(lgeNexus5x1)
      .build();

    // Assert
    assertEquals("LGE Nexus 5X [00fff9d2279fa601]", action.getTemplatePresentation().getText());
  }

  @Test
  public void configurePresentationSetTextDoesntMangleDeviceName() {
    // Arrange
    Device apiQ64Google = new VirtualDevice.Builder()
      .setName("apiQ_64_Google")
      .setKey("apiQ_64_Google")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Mockito.when(myComboBoxAction.getDevices(myProject)).thenReturn(Collections.singletonList(apiQ64Google));

    // Act
    AnAction action = new SelectDeviceAndSnapshotAction.Builder()
      .setComboBoxAction(myComboBoxAction)
      .setProject(myProject)
      .setDevice(apiQ64Google)
      .build();

    // Assert
    assertEquals("apiQ_64_Google", action.getTemplatePresentation().getText());
  }
}
