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
package com.android.tools.idea.avdmanager;

import static org.junit.Assert.assertEquals;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DeviceSkinUpdaterTest {
  private final FileSystem myFileSystem = Jimfs.newFileSystem(Configuration.unix());

  private final Path myStudioSkins =
    myFileSystem.getPath("/home/juancnuno/Development/studio-master-dev/tools/idea/../adt/idea/artwork/resources/device-art-resources");

  private final Path mySdkSkins = myFileSystem.getPath("/home/juancnuno/Android/Sdk/skins");

  @Test
  public void updateSkinsDeviceToStringIsEmpty() {
    // Arrange
    Path device = myFileSystem.getPath("");

    // Act
    Object deviceSkins = DeviceSkinUpdater.updateSkins(device, Collections.emptyList(), null, null);

    // Assert
    assertEquals(device, deviceSkins);
  }

  @Test
  public void updateSkinsDeviceIsAbsolute() {
    // Arrange
    Path device = myFileSystem.getPath("/home/juancnuno/Android/Sdk/platforms/android-32/skins/HVGA");

    // Act
    Object deviceSkins = DeviceSkinUpdater.updateSkins(device, Collections.emptyList(), null, null);

    // Assert
    assertEquals(device, deviceSkins);
  }

  @Test
  public void updateSkinsDeviceEqualsNoSkin() {
    // Arrange
    var device = SkinUtils.noSkin(myFileSystem);

    // Act
    Object deviceSkins = DeviceSkinUpdater.updateSkins(device, Collections.emptyList(), null, null);

    // Assert
    assertEquals(device, deviceSkins);
  }

  @Test
  public void updateSkinsImageSkinIsPresent() {
    // Arrange
    Path device = myFileSystem.getPath("AndroidWearRound480x480");

    Path imageSkins =
      myFileSystem.getPath("/home/juancnuno/Android/Sdk/system-images/android-28/android-wear/x86/skins/AndroidWearRound480x480");

    // Act
    Object deviceSkins = DeviceSkinUpdater.updateSkins(device, Collections.singletonList(imageSkins), null, null);

    // Assert
    assertEquals(imageSkins, deviceSkins);
  }

  @Test
  public void updateSkinsStudioSkinsIsNullAndSdkSkinsIsNull() {
    // Arrange
    Path device = myFileSystem.getPath("pixel_4");

    // Act
    Object deviceSkins = DeviceSkinUpdater.updateSkins(device, Collections.emptyList(), null, null);

    // Assert
    assertEquals(device, deviceSkins);
  }

  @Test
  public void updateSkinsStudioSkinsIsNull() {
    // Arrange
    Path device = myFileSystem.getPath("pixel_4");

    // Act
    Object deviceSkins = DeviceSkinUpdater.updateSkins(device, Collections.emptyList(), null, mySdkSkins);

    // Assert
    assertEquals(mySdkSkins.resolve(device), deviceSkins);
  }

  @Test
  public void updateSkinsSdkSkinsIsNull() {
    // Arrange
    Path device = myFileSystem.getPath("pixel_4");

    // Act
    Object deviceSkins = DeviceSkinUpdater.updateSkins(device, Collections.emptyList(), myStudioSkins, null);

    // Assert
    assertEquals(myStudioSkins.resolve(device), deviceSkins);
  }

  @Test
  public void updateSkinsImplSdkDeviceSkinsAreUpToDate() throws IOException {
    // Arrange
    Path device = myFileSystem.getPath("pixel_4");
    Path sdkDeviceSkins = mySdkSkins.resolve(device);
    Files.createDirectories(sdkDeviceSkins);

    DeviceSkinUpdater updater = new DeviceSkinUpdater(myStudioSkins, mySdkSkins);

    // Act
    Object deviceSkins = updater.updateSkinsImpl(device);

    // Assert
    assertEquals(sdkDeviceSkins, deviceSkins);
  }

  @Test
  public void updateSkinsImpl() throws IOException {
    // Arrange
    Path device = myFileSystem.getPath("pixel_4");
    Files.createDirectories(myStudioSkins.resolve(device));

    DeviceSkinUpdater updater = new DeviceSkinUpdater(myStudioSkins, mySdkSkins);

    // Act
    Object deviceSkins = updater.updateSkinsImpl(device);

    // Assert
    assertEquals(mySdkSkins.resolve(device), deviceSkins);
  }

  @Test
  public void updateSkinsImplFilesListThrowsNoSuchFileException() {
    // Arrange
    Path device = myFileSystem.getPath("pixel_4");

    DeviceSkinUpdater updater = new DeviceSkinUpdater(myStudioSkins, mySdkSkins);

    // Act
    Object deviceSkins = updater.updateSkinsImpl(device);

    // Assert
    assertEquals(myStudioSkins.resolve(device), deviceSkins);
  }

  @Test
  public void updateSkinsImplDeviceEqualsWearSmallRound() {
    // Arrange
    Path device = myFileSystem.getPath("WearSmallRound");

    DeviceSkinUpdater updater = new DeviceSkinUpdater(myStudioSkins, mySdkSkins);

    // Act
    Object deviceSkins = updater.updateSkinsImpl(device);

    // Assert
    assertEquals(myStudioSkins.resolve("wearos_small_round"), deviceSkins);
  }

  @Test
  public void updateSkinsImplDeviceEqualsWearLargeRound() {
    // Arrange
    Path device = myFileSystem.getPath("WearLargeRound");

    DeviceSkinUpdater updater = new DeviceSkinUpdater(myStudioSkins, mySdkSkins);

    // Act
    Object deviceSkins = updater.updateSkinsImpl(device);

    // Assert
    assertEquals(myStudioSkins.resolve("wearos_large_round"), deviceSkins);
  }

  @Test
  public void updateSkinsImplDeviceEqualsAndroidWearSquare() {
    // Arrange
    Path device = myFileSystem.getPath("WearSquare");

    DeviceSkinUpdater updater = new DeviceSkinUpdater(myStudioSkins, mySdkSkins);

    // Act
    Object deviceSkins = updater.updateSkinsImpl(device);

    // Assert
    assertEquals(myStudioSkins.resolve("wearos_square"), deviceSkins);
  }

  @Test
  public void updateSkinsImplSdkLayoutDoesntExist() throws IOException {
    // Arrange
    Path device = myFileSystem.getPath("pixel_4");
    Path sdkDeviceSkins = mySdkSkins.resolve(device);
    Files.createDirectories(sdkDeviceSkins);

    Path studioDeviceSkins = myStudioSkins.resolve(device);
    Files.createDirectories(studioDeviceSkins);
    Files.createFile(studioDeviceSkins.resolve("layout"));

    DeviceSkinUpdater updater = new DeviceSkinUpdater(myStudioSkins, mySdkSkins);

    // Act
    Object deviceSkins = updater.updateSkinsImpl(device);

    // Assert
    assertEquals(sdkDeviceSkins, deviceSkins);
  }

  @Test
  public void updateSkinsImplStudioLayoutLastModifiedTimeIsBeforeSdkLayoutLastModifiedTime() throws IOException {
    // Arrange
    Path device = myFileSystem.getPath("pixel_4");
    Path sdkDeviceSkins = mySdkSkins.resolve(device);
    Files.createDirectories(sdkDeviceSkins);

    Path studioDeviceSkins = myStudioSkins.resolve(device);
    Files.createDirectories(studioDeviceSkins);

    Path studioLayout = studioDeviceSkins.resolve("layout");
    Files.createFile(studioLayout);
    Files.setLastModifiedTime(studioLayout, FileTime.from(Instant.parse("2020-08-26T20:39:22.922Z")));

    Path sdkLayout = sdkDeviceSkins.resolve("layout");
    Files.createFile(sdkLayout);
    Files.setLastModifiedTime(sdkLayout, FileTime.from(Instant.parse("2020-08-26T20:39:22.923Z")));

    DeviceSkinUpdater updater = new DeviceSkinUpdater(myStudioSkins, mySdkSkins);

    // Act
    Object deviceSkins = updater.updateSkinsImpl(device);

    // Assert
    assertEquals(sdkDeviceSkins, deviceSkins);
  }

  @Test
  public void updateSkinsImplStudioLayoutLastModifiedTimeIsAfterSdkLayoutLastModifiedTime() throws IOException {
    // Arrange
    Path device = myFileSystem.getPath("pixel_4");
    Path sdkDeviceSkins = mySdkSkins.resolve(device);
    Files.createDirectories(sdkDeviceSkins);

    Path studioDeviceSkins = myStudioSkins.resolve(device);
    Files.createDirectories(studioDeviceSkins);

    Path studioLayout = studioDeviceSkins.resolve("layout");
    Files.createFile(studioLayout);
    Files.setLastModifiedTime(studioLayout, FileTime.from(Instant.parse("2020-08-26T20:39:22.924Z")));

    Path sdkLayout = sdkDeviceSkins.resolve("layout");
    Files.createFile(sdkLayout);
    Files.setLastModifiedTime(sdkLayout, FileTime.from(Instant.parse("2020-08-26T20:39:22.923Z")));

    DeviceSkinUpdater updater = new DeviceSkinUpdater(myStudioSkins, mySdkSkins);

    // Act
    Object deviceSkins = updater.updateSkinsImpl(device);

    // Assert
    assertEquals(sdkDeviceSkins, deviceSkins);
  }

  @Test
  public void copyTargetExists() throws IOException {
    // Arrange
    Path device = myFileSystem.getPath("pixel_4");
    Path layout = device.resolve("layout");
    Path source = myStudioSkins.resolve(layout);

    Files.createDirectories(myStudioSkins.resolve(device));
    Files.createFile(source);

    Path target = mySdkSkins.resolve(layout);

    Files.createDirectories(mySdkSkins.resolve(device));
    Files.createFile(target);

    // Act
    DeviceSkinUpdater.copy(source, target);

    // Assert
    // Files::copy throws a FileAlreadyExistsException without the Files.exists(target) guard
  }
}
