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

import static com.android.ddmlib.IDevice.HardwareFeature.TV;
import static com.android.ddmlib.IDevice.HardwareFeature.WATCH;
import static com.intellij.icons.AllIcons.General.WarningDecorator;
import static icons.StudioIcons.Common.ERROR_DECORATOR;
import static icons.StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE;
import static icons.StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_TV;
import static icons.StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_WEAR;
import static org.junit.Assert.assertEquals;

import com.android.testutils.ImageDiffUtil;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.LaunchCompatibility;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.IconManager;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.ImageUtil;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import javax.swing.Icon;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class VirtualDeviceTest {
  private static final Key DEVICE_KEY = new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd");

  private static void assertIconSimilar(Icon expectedIcon, Icon actualIcon) throws IOException {
    BufferedImage expectedIconImage = ImageUtil.toBufferedImage(IconUtil.toImage(expectedIcon, ScaleContext.createIdentity()));
    BufferedImage actualIconImage = ImageUtil.toBufferedImage(IconUtil.toImage(actualIcon, ScaleContext.createIdentity()));
    ImageDiffUtil.assertImageSimilar("icon", expectedIconImage, actualIconImage, 0);
  }

  @Before
  public void activateIconLoader() throws Throwable {
    IconManager.Companion.activate(null);
    IconLoader.activate();
  }

  @After
  public void deactivateIconLoader() {
    IconManager.Companion.deactivate();
    IconLoader.deactivate();
    IconLoader.clearCacheInTests();
  }

  @Test
  public void getDefaultTarget() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(DEVICE_KEY)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setType(Device.Type.PHONE)
      .setSelectDeviceSnapshotComboBoxSnapshotsEnabled(false)
      .build();

    // Act
    Object target = device.getDefaultTarget();

    // Assert
    assertEquals(new QuickBootTarget(DEVICE_KEY), target);
  }

  @Test
  public void getTargetsSelectDeviceSnapshotComboBoxSnapshotsIsntEnabled() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(DEVICE_KEY)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setType(Device.Type.PHONE)
      .setSelectDeviceSnapshotComboBoxSnapshotsEnabled(false)
      .build();

    // Act
    Object targets = device.getTargets();

    // Assert
    assertEquals(Collections.singletonList(new QuickBootTarget(DEVICE_KEY)), targets);
  }

  @Test
  public void getTargetsIsConnected() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(DEVICE_KEY)
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setType(Device.Type.PHONE)
      .build();

    // Act
    Object targets = device.getTargets();

    // Assert
    assertEquals(Collections.singletonList(new RunningDeviceTarget(DEVICE_KEY)), targets);
  }

  @Test
  public void getTargets() {
    // Arrange
    FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix());
    Path snapshotKey = fileSystem.getPath("/home/user/.android/avd/Pixel_4_API_30.avd/snapshots/snap_2020-12-17_12-26-30");

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(DEVICE_KEY)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setType(Device.Type.PHONE)
      .addSnapshot(new Snapshot(snapshotKey))
      .setSelectDeviceSnapshotComboBoxSnapshotsEnabled(true)
      .build();

    // Act
    Object actualTargets = device.getTargets();

    // Assert
    Object expectedTargets = Arrays.asList(new ColdBootTarget(DEVICE_KEY),
                                           new QuickBootTarget(DEVICE_KEY),
                                           new BootWithSnapshotTarget(DEVICE_KEY, snapshotKey));

    assertEquals(expectedTargets, actualTargets);
  }

  @Test
  public void testGetConnectedPhoneWithoutErrorOrWarningIcon() throws IOException {
    AndroidDevice phoneAndroidDevice = Mockito.mock(AndroidDevice.class);

    Device connectedPhoneWithoutErrorOrWarning = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(DEVICE_KEY)
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(phoneAndroidDevice)
      .setType(Device.Type.PHONE)
      .build();

    assertIconSimilar(ExecutionUtil.getLiveIndicator(VIRTUAL_DEVICE_PHONE), connectedPhoneWithoutErrorOrWarning.getIcon());
  }

  @Test
  public void testGetNotConnectedWearIcon() throws IOException {
    AndroidDevice wearAndroidDevice = Mockito.mock(AndroidDevice.class);
    Mockito.when(wearAndroidDevice.supportsFeature(WATCH)).thenReturn(true);

    Device notConnectedWear = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(DEVICE_KEY)
      .setAndroidDevice(wearAndroidDevice)
      .setType(Device.Type.WEAR)
      .build();

    assertIconSimilar(VIRTUAL_DEVICE_WEAR, notConnectedWear.getIcon());
  }

  @Test
  public void testGetConnectedWearWithErrorIcon() throws IOException {
    AndroidDevice wearAndroidDevice = Mockito.mock(AndroidDevice.class);
    Mockito.when(wearAndroidDevice.supportsFeature(WATCH)).thenReturn(true);

    Device connectedWearWithError = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(DEVICE_KEY)
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(wearAndroidDevice)
      .setType(Device.Type.WEAR)
      .setLaunchCompatibility(new LaunchCompatibility(LaunchCompatibility.State.ERROR, "error"))
      .build();

    assertIconSimilar(
      new LayeredIcon(ExecutionUtil.getLiveIndicator(VIRTUAL_DEVICE_WEAR), ERROR_DECORATOR),
      connectedWearWithError.getIcon()
    );
  }

  @Test
  public void testGetNotConnectedTvWithWarningIcon() throws IOException {
    AndroidDevice tvAndroidDevice = Mockito.mock(AndroidDevice.class);
    Mockito.when(tvAndroidDevice.supportsFeature(TV)).thenReturn(true);

    Device notConnectedTvWithWarning = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(DEVICE_KEY)
      .setAndroidDevice(tvAndroidDevice)
      .setType(Device.Type.TV)
      .setLaunchCompatibility(new LaunchCompatibility(LaunchCompatibility.State.WARNING, "warning"))
      .build();

    assertIconSimilar(new LayeredIcon(VIRTUAL_DEVICE_TV, WarningDecorator), notConnectedTvWithWarning.getIcon());
  }
}
