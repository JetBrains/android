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
package com.android.tools.idea.run.deployment;

import static org.junit.Assert.assertEquals;

import com.android.tools.idea.run.AndroidDevice;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class SelectTargetActionTest {
  @Test
  public void update() {
    // Arrange
    Key deviceKey = new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd");

    FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix());
    Path snapshotKey = fileSystem.getPath("/home/user/.android/avd/Pixel_4_API_30.avd/snapshots/snap_2020-12-07_16-36-58");

    Target target = new BootWithSnapshotTarget(deviceKey, snapshotKey);

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(deviceKey)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .addSnapshot(new Snapshot(snapshotKey))
      .build();

    AnAction action = new SelectTargetAction(target, device, Mockito.mock(DeviceAndSnapshotComboBoxAction.class));

    Presentation presentation = new Presentation();

    AnActionEvent event = Mockito.mock(AnActionEvent.class);
    Mockito.when(event.getPresentation()).thenReturn(presentation);

    // Act
    action.update(event);

    // Assert
    assertEquals("snap_2020-12-07_16-36-58", presentation.getText());
  }
}
