/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.android.tools.idea.run.AndroidDevice;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import icons.StudioIcons;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class SnapshotActionGroupTest {
  private final @NotNull DeviceAndSnapshotComboBoxAction myComboBoxAction = Mockito.mock(DeviceAndSnapshotComboBoxAction.class);

  @Test
  public void getChildren() {
    // Arrange
    Key deviceKey = new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd");

    FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix());
    Path snapshotKey = fileSystem.getPath("/home/user/.android/avd/Pixel_4_API_30.avd/snapshots/snap_2020-12-07_16-36-58");

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(deviceKey)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .addSnapshot(new Snapshot(snapshotKey))
      .build();

    ActionGroup group = new SnapshotActionGroup(device, myComboBoxAction);

    // Act
    Object[] actualChildren = group.getChildren(null);

    // Assert
    Object[] expectedChildren = {
      new SelectTargetAction(new ColdBootTarget(deviceKey), device, myComboBoxAction),
      new SelectTargetAction(new QuickBootTarget(deviceKey), device, myComboBoxAction),
      new SelectTargetAction(new BootWithSnapshotTarget(deviceKey, snapshotKey), device, myComboBoxAction)};

    assertArrayEquals(expectedChildren, actualChildren);
  }

  @Test
  public void update() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel_4_API_30")
      .setValidityReason("Missing system image")
      .setKey(new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    AnAction action = new SnapshotActionGroup(device, myComboBoxAction);

    Presentation presentation = new Presentation();

    AnActionEvent event = Mockito.mock(AnActionEvent.class);
    Mockito.when(event.getPresentation()).thenReturn(presentation);

    // Act
    action.update(event);

    // Assert
    assertEquals(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE, presentation.getIcon());
    assertEquals("Pixel_4_API_30 (Missing system image)", presentation.getText());
  }
}
