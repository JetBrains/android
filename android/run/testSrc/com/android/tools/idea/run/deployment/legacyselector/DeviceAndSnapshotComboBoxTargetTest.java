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

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.editor.DeployTarget;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.Collections;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class DeviceAndSnapshotComboBoxTargetTest {
  @Test
  public void getDevices() {
    // Arrange
    Target target = Mockito.mock(Target.class);
    Mockito.when(target.getDeviceKey()).thenReturn(Keys.PIXEL_4_API_30);

    Project project = Mockito.mock(Project.class);
    AndroidDevice androidDevice = Mockito.mock(AndroidDevice.class);

    VirtualDevice device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(Keys.PIXEL_4_API_30)
      .setAndroidDevice(androidDevice)
      .build();

    DeviceAndSnapshotComboBoxAction action = Mockito.mock(DeviceAndSnapshotComboBoxAction.class);
    Mockito.when(action.getDevices(project)).thenReturn(Optional.of(Collections.singletonList(device)));

    DeployTarget deployTarget = new DeviceAndSnapshotComboBoxTarget((p, d) -> Collections.singleton(target), () -> action);

    Module module = Mockito.mock(Module.class);
    Mockito.when(module.getProject()).thenReturn(project);

    // Act
    Object futures = deployTarget.getDevices(module.getProject());

    // Assert
    Mockito.verify(target).boot(device, project);
    assertEquals(new DeviceFutures(Collections.singletonList(androidDevice)), futures);
  }
}
