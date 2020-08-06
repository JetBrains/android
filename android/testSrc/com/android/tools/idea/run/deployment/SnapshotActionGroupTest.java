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

import static org.junit.Assert.assertEquals;

import com.android.tools.idea.run.AndroidDevice;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class SnapshotActionGroupTest {
  private Presentation myPresentation;
  private AnActionEvent myEvent;

  @Before
  public void mockEvent() {
    myPresentation = new Presentation();

    myEvent = Mockito.mock(AnActionEvent.class);
    Mockito.when(myEvent.getPresentation()).thenReturn(myPresentation);
  }

  @Test
  public void update() {
    // Arrange
    Device device1 = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new NonprefixedKey("Pixel_3_API_29/snap_2019-09-27_15-48-09"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device device2 = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new NonprefixedKey("Pixel_3_API_29/snap_2019-09-27_15-49-04"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    AnAction action = new SnapshotActionGroup(Arrays.asList(device1, device2));

    // Act
    action.update(myEvent);

    // Assert
    assertEquals("Pixel 3 API 29", myPresentation.getText());
  }

  @Test
  public void updateDevicesHaveValidityReasons() {
    // Arrange
    Device device1 = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setValidityReason("Missing system image")
      .setKey(new NonprefixedKey("Pixel_3_API_29/snap_2019-09-27_15-48-09"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device device2 = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setValidityReason("Missing system image")
      .setKey(new NonprefixedKey("Pixel_3_API_29/snap_2019-09-27_15-49-04"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    AnAction action = new SnapshotActionGroup(Arrays.asList(device1, device2));

    // Act
    action.update(myEvent);

    // Assert
    assertEquals("Pixel 3 API 29 (Missing system image)", myPresentation.getText());
  }
}
