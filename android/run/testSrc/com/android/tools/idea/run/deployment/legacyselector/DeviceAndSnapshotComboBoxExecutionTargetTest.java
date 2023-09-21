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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.execution.common.AndroidExecutionTarget;
import com.android.tools.idea.execution.common.DeployableToDevice;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.DeploymentApplicationService;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.application.ApplicationConfiguration;
import icons.StudioIcons;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class DeviceAndSnapshotComboBoxExecutionTargetTest {
  private final AsyncDevicesGetter myGetter = Mockito.mock(AsyncDevicesGetter.class);

  @Test
  public void isApplicationRunningAsync() throws Exception {
    // Arrange
    var ddmlibDevice = Mockito.mock(IDevice.class);

    var androidDevice = Mockito.mock(AndroidDevice.class);
    Mockito.when(androidDevice.isRunning()).thenReturn(true);
    Mockito.when(androidDevice.getLaunchedDevice()).thenReturn(Futures.immediateFuture(ddmlibDevice));

    var device = new VirtualDevice.Builder()
      .setKey(Keys.PIXEL_4_API_30)
      .setConnectionTime(Instant.parse("2023-07-19T22:34:37.356453499Z"))
      .setName("Pixel 4 API 30")
      .setAndroidDevice(androidDevice)
      .build();

    Mockito.when(myGetter.get()).thenReturn(Optional.of(List.of(device)));

    var service = Mockito.mock(DeploymentApplicationService.class);
    Mockito.when(service.findClient(ddmlibDevice, "com.google.myapplication")).thenReturn(List.of(Mockito.mock(Client.class)));

    var target = new DeviceAndSnapshotComboBoxExecutionTarget(Set.of(new QuickBootTarget(Keys.PIXEL_4_API_30)), myGetter, () -> service);

    // Act
    var future = target.isApplicationRunningAsync("com.google.myapplication");

    // Assert
    // noinspection BlockingMethodInNonBlockingContext
    assertTrue(future.get(60, TimeUnit.SECONDS));
  }

  @Test
  public void getAvailableDeviceCount() {
    // Arrange
    Mockito.when(myGetter.get()).thenReturn(Optional.of(List.of(TestDevices.buildPixel4Api30())));
    var target = new DeviceAndSnapshotComboBoxExecutionTarget(Set.of(new QuickBootTarget(Keys.PIXEL_4_API_30)), myGetter);

    // Act
    var count = target.getAvailableDeviceCount();

    // Assert
    assertEquals(1, count);
  }

  @Test
  public void getDevices() {
    // Arrange
    Key key = new VirtualDeviceName("Pixel_3_API_29");

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(key)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Mockito.when(myGetter.get()).thenReturn(Optional.of(Collections.singletonList(device)));

    AndroidExecutionTarget target = new DeviceAndSnapshotComboBoxExecutionTarget(Collections.singleton(new QuickBootTarget(key)), myGetter);

    // Act
    Object actualDevices = target.getRunningDevices();

    // Assert
    assertEquals(Collections.emptyList(), actualDevices);
  }

  @Test
  public void getId() {
    // Arrange
    var targets = Set.<Target>of(new QuickBootTarget(Keys.PIXEL_4_API_30), new QuickBootTarget(Keys.PIXEL_3_API_30));
    var executionTarget = new DeviceAndSnapshotComboBoxExecutionTarget(targets, myGetter);

    // Act
    var id = executionTarget.getId();

    // Assert
    assertEquals("device_and_snapshot_combo_box_target[" + Keys.PIXEL_3_API_30 + ", " + Keys.PIXEL_4_API_30 + ']', id);
  }

  @Test
  public void getDisplayNameCase0() {
    // Arrange
    var executionTarget = new DeviceAndSnapshotComboBoxExecutionTarget(Set.of(), myGetter);

    // Act
    var name = executionTarget.getDisplayName();

    // Assert
    assertEquals("No Devices", name);
  }

  @Test
  public void getDisplayNameCase1() {
    // Arrange
    Key key = new VirtualDeviceName("Pixel_3_API_29");

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(key)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Mockito.when(myGetter.get()).thenReturn(Optional.of(Collections.singletonList(device)));

    ExecutionTarget target = new DeviceAndSnapshotComboBoxExecutionTarget(Collections.singleton(new QuickBootTarget(key)), myGetter);

    // Act
    Object actualDisplayName = target.getDisplayName();

    // Assert
    assertEquals("Pixel 3 API 29", actualDisplayName);
  }

  @Test
  public void getDisplayNameDefault() {
    // Arrange
    Key key1 = new VirtualDeviceName("Pixel_2_API_29");
    Key key2 = new VirtualDeviceName("Pixel_3_API_29");

    Collection<Target> targets = Sets.newHashSet(new QuickBootTarget(key1), new QuickBootTarget(key2));

    Device device1 = new VirtualDevice.Builder()
      .setName("Pixel 2 API 29")
      .setKey(key1)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device device2 = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(key2)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Mockito.when(myGetter.get()).thenReturn(Optional.of(Arrays.asList(device1, device2)));

    ExecutionTarget target = new DeviceAndSnapshotComboBoxExecutionTarget(targets, myGetter);

    // Act
    Object actualDisplayName = target.getDisplayName();

    // Assert
    assertEquals("Multiple Devices", actualDisplayName);
  }

  @Test
  public void getIconDevicesSizeEquals1() {
    // Arrange
    Mockito.when(myGetter.get()).thenReturn(Optional.of(List.of(TestDevices.buildPixel4Api30())));
    var executionTarget = new DeviceAndSnapshotComboBoxExecutionTarget(Set.of(new QuickBootTarget(Keys.PIXEL_4_API_30)), myGetter);

    // Act
    var icon = executionTarget.getIcon();

    // Assert
    assertEquals(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE, icon);
  }

  @Test
  public void getIcon() {
    // Arrange
    Mockito.when(myGetter.get()).thenReturn(Optional.of(List.of(TestDevices.buildPixel4Api30(), TestDevices.buildPixel3Api30())));

    var targets = Set.<Target>of(new QuickBootTarget(Keys.PIXEL_4_API_30), new QuickBootTarget(Keys.PIXEL_3_API_30));
    var executionTarget = new DeviceAndSnapshotComboBoxExecutionTarget(targets, myGetter);

    // Act
    var icon = executionTarget.getIcon();

    // Assert
    assertEquals(StudioIcons.DeviceExplorer.MULTIPLE_DEVICES, icon);
  }

  @Test
  public void deviceTargetNotSuggestedForConfigurationsThatDoNotDeployToLocalDevice() {
    var target = new DeviceAndSnapshotComboBoxExecutionTarget(Collections.emptyList(), myGetter);

    var androidDeployableLocally = Mockito.mock(AndroidRunConfigurationBase.class);
    Mockito.when(androidDeployableLocally.getUserData(DeployableToDevice.getKEY())).thenReturn(true);

    var androidNonDeployableLocally = Mockito.mock(AndroidRunConfigurationBase.class);
    var nonDeployableLocally = Mockito.mock(ApplicationConfiguration.class);

    assertTrue(target.canRun(androidDeployableLocally));
    assertFalse(target.canRun(androidNonDeployableLocally));
    assertFalse(target.canRun(nonDeployableLocally));
  }
}
