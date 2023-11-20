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

import static com.android.testutils.file.InMemoryFileSystems.createInMemoryFileSystemAndFolder;
import static com.android.testutils.truth.PathSubject.assertThat;
import static com.android.tools.idea.avdmanager.DeviceSkinUpdater.areAllFilesUpToDate;
import static com.google.common.truth.Truth.assertThat;

import com.android.testutils.TestUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@SuppressWarnings("RedundantThrows")
@RunWith(JUnit4.class)
public final class DeviceSkinUpdaterTest {
  private final Path myStudioSkins = TestUtils.resolveWorkspacePathUnchecked("tools/adt/idea/artwork/resources/device-art-resources");
  private final Path myHomeDir = createInMemoryFileSystemAndFolder("home/janedoe");
  private final Path mySdkSkins = myHomeDir.resolve("Android/Sdk/skins");

  @Test
  public void updateSkinDeviceToStringIsEmpty() {
    // Arrange
    String skinName = "";

    // Act
    Path deviceSkin = DeviceSkinUpdater.updateSkin(skinName, Collections.emptyList(), null, null);

    // Assert
    assertThat(deviceSkin).isEqualTo(Paths.get(skinName));
  }

  @Test
  public void updateSkinDeviceIsAbsolute() {
    // Arrange
    String skinName = myHomeDir.resolve("Android/Sdk/platforms/android-32/skins/HVGA").toString();

    // Act
    Path deviceSkin = DeviceSkinUpdater.updateSkin(skinName, Collections.emptyList(), null, null);

    // Assert
    assertThat(deviceSkin).isEqualTo(Paths.get(skinName));
  }

  @Test
  public void updateSkinDeviceEqualsNoSkin() {
    // Arrange
    var skinName = "_no_skin";

    // Act
    Path deviceSkin = DeviceSkinUpdater.updateSkin(skinName, Collections.emptyList(), null, null);

    // Assert
    assertThat(deviceSkin).isEqualTo(Paths.get(skinName));
  }

  @Test
  public void updateSkinImageSkinIsPresent() {
    // Arrange
    String skinName = "AndroidWearRound480x480";

    Path imageSkin = myHomeDir.resolve("Android/Sdk/system-images/android-28/android-wear/x86/skins/AndroidWearRound480x480");

    // Act
    Path deviceSkin = DeviceSkinUpdater.updateSkin(skinName, Collections.singletonList(imageSkin), null, null);

    // Assert
    assertThat(deviceSkin).isEqualTo(imageSkin);
  }

  @Test
  public void updateSkinStudioSkinIsNullAndSdkSkinIsNull() {
    // Arrange
    String skinName = "pixel_4";

    // Act
    Path deviceSkin = DeviceSkinUpdater.updateSkin(skinName, Collections.emptyList(), null, null);

    // Assert
    assertThat(deviceSkin).isEqualTo(Paths.get(skinName));
  }

  @Test
  public void updateSkinStudioSkinIsNull() {
    // Arrange
    String skinName = "pixel_4";

    // Act
    Path deviceSkin = DeviceSkinUpdater.updateSkin(skinName, Collections.emptyList(), null, mySdkSkins);

    // Assert
    assertThat(deviceSkin).isEqualTo(mySdkSkins.resolve(skinName));
  }

  @Test
  public void updateSkinSdkSkinIsNull() {
    // Arrange
    String skinName = "pixel_4";

    // Act
    Path deviceSkin = DeviceSkinUpdater.updateSkin(skinName, Collections.emptyList(), myStudioSkins, null);

    // Assert
    assertThat(deviceSkin).isEqualTo(myStudioSkins.resolve(skinName));
  }

  @Test
  public void updateSkinImpl() throws Exception {
    // Arrange
    String skinName = "pixel_fold";

    DeviceSkinUpdater updater = new DeviceSkinUpdater(myStudioSkins, mySdkSkins);

    // Act
    Path deviceSkin = updater.updateSkinImpl(skinName);

    // Assert
    assertThat(deviceSkin).isEqualTo(mySdkSkins.resolve(skinName));
    assertThat(areAllFilesUpToDate(deviceSkin, myStudioSkins.resolve(skinName))).isTrue();
    assertThat(deviceSkin.resolve("default/layout")).exists();
    assertThat(deviceSkin.resolve("closed/layout")).exists();
  }

  @Test
  public void updateSkinImplFilesListThrowsNoSuchFileException() {
    // Arrange
    String skinName = "pixel_4";

    DeviceSkinUpdater updater = new DeviceSkinUpdater(myStudioSkins, mySdkSkins);

    // Act
    Path deviceSkin = updater.updateSkinImpl(skinName);

    // Assert
    assertThat(deviceSkin).isEqualTo(mySdkSkins.resolve(skinName));
    assertThat(areAllFilesUpToDate(deviceSkin, myStudioSkins.resolve(skinName))).isTrue();
    assertThat(deviceSkin.resolve("layout")).exists();
  }

  @Test
  public void updateSkinImplDeviceEqualsWearSmallRound() {
    // Arrange
    String skinName = "WearSmallRound";

    DeviceSkinUpdater updater = new DeviceSkinUpdater(myStudioSkins, mySdkSkins);

    // Act
    Path deviceSkin = updater.updateSkinImpl(skinName);

    // Assert
    assertThat(deviceSkin).isEqualTo(mySdkSkins.resolve(skinName));
    assertThat(areAllFilesUpToDate(deviceSkin, myStudioSkins.resolve("wearos_small_round"))).isTrue();
    assertThat(deviceSkin.resolve("layout")).exists();
  }

  @Test
  public void updateSkinImplDeviceEqualsWearLargeRound() {
    // Arrange
    String skinName = "WearLargeRound";

    DeviceSkinUpdater updater = new DeviceSkinUpdater(myStudioSkins, mySdkSkins);

    // Act
    Path deviceSkin = updater.updateSkinImpl(skinName);

    // Assert
    assertThat(deviceSkin).isEqualTo(mySdkSkins.resolve(skinName));
    assertThat(areAllFilesUpToDate(deviceSkin, myStudioSkins.resolve("wearos_large_round"))).isTrue();
    assertThat(deviceSkin.resolve("layout")).exists();
  }

  @Test
  public void updateSkinImplDeviceEqualsAndroidWearSquare() {
    // Arrange
    String skinName = "WearSquare";

    DeviceSkinUpdater updater = new DeviceSkinUpdater(myStudioSkins, mySdkSkins);

    // Act
    Path deviceSkin = updater.updateSkinImpl(skinName);

    // Assert
    assertThat(deviceSkin).isEqualTo(mySdkSkins.resolve(skinName));
    assertThat(areAllFilesUpToDate(deviceSkin, myStudioSkins.resolve("wearos_square"))).isTrue();
    assertThat(deviceSkin.resolve("layout")).exists();
  }

  @Test
  public void updateSkinImplSdkLayoutDoesntExist() throws Exception {
    // Arrange
    String skinName = "pixel_4";
    Path sdkDeviceSkin = mySdkSkins.resolve(skinName);
    Files.createDirectories(sdkDeviceSkin);

    DeviceSkinUpdater updater = new DeviceSkinUpdater(myStudioSkins, mySdkSkins);

    // Act
    Path deviceSkin = updater.updateSkinImpl(skinName);

    // Assert
    assertThat(deviceSkin).isEqualTo(mySdkSkins.resolve(skinName));
  }

  @Test
  public void updateSkinImplStudioLayoutLastModifiedTimeIsBeforeSdkLayoutLastModifiedTime() throws Exception {
    // Arrange
    String skinName = "pixel_4";
    Path sdkDeviceSkin = mySdkSkins.resolve(skinName);
    Files.createDirectories(sdkDeviceSkin);

    Path studioSkin = myHomeDir.resolve("android-studio/plugins/android/resources/device-art-resources");
    Path studioDeviceSkin = studioSkin.resolve(skinName);
    Files.createDirectories(studioDeviceSkin);

    Path studioLayout = studioDeviceSkin.resolve("layout");
    Files.createFile(studioLayout);
    Files.setLastModifiedTime(studioLayout, FileTime.from(Instant.parse("2020-08-26T20:39:22.922Z")));

    Path sdkLayout = sdkDeviceSkin.resolve("layout");
    Files.createFile(sdkLayout);
    Files.setLastModifiedTime(sdkLayout, FileTime.from(Instant.parse("2020-08-26T20:39:22.923Z")));

    DeviceSkinUpdater updater = new DeviceSkinUpdater(studioSkin, mySdkSkins);

    // Act
    Path deviceSkin = updater.updateSkinImpl(skinName);

    // Assert
    assertThat(deviceSkin).isEqualTo(sdkDeviceSkin);
  }

  @Test
  public void updateSkinImplStudioLayoutLastModifiedTimeIsAfterSdkLayoutLastModifiedTime() throws Exception {
    // Arrange
    String skinName = "pixel_4";
    Path sdkDeviceSkin = mySdkSkins.resolve(skinName);
    Files.createDirectories(sdkDeviceSkin);

    Path studioSkin = myHomeDir.resolve("android-studio/plugins/android/resources/device-art-resources");
    Path studioDeviceSkin = studioSkin.resolve(skinName);
    Files.createDirectories(studioDeviceSkin);

    Path studioLayout = studioDeviceSkin.resolve("layout");
    Files.createFile(studioLayout);
    Files.setLastModifiedTime(studioLayout, FileTime.from(Instant.parse("2020-08-26T20:39:22.924Z")));

    Path sdkLayout = sdkDeviceSkin.resolve("layout");
    Files.createFile(sdkLayout);
    Files.setLastModifiedTime(sdkLayout, FileTime.from(Instant.parse("2020-08-26T20:39:22.923Z")));

    DeviceSkinUpdater updater = new DeviceSkinUpdater(studioSkin, mySdkSkins);

    // Act
    Path deviceSkin = updater.updateSkinImpl(skinName);

    // Assert
    assertThat(deviceSkin).isEqualTo(sdkDeviceSkin);
  }
}
