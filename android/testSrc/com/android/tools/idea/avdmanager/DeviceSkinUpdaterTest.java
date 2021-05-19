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

import com.android.tools.idea.avdmanager.DeviceSkinUpdater.Converter;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class DeviceSkinUpdaterTest {
  private final FileSystem myFileSystem = Jimfs.newFileSystem(Configuration.unix());

  private final Path myStudioSkins =
    myFileSystem.getPath("/home/juancnuno/Development/studio-master-dev/tools/idea/../adt/idea/artwork/resources/device-art-resources");

  private final Path mySdkSkins = myFileSystem.getPath("/home/juancnuno/Android/Sdk/skins");

  @Test
  public void updateSkinsStudioSkinsIsNullAndSdkSkinsIsNull() {
    // Arrange
    Path device = myFileSystem.getPath("pixel_4");

    // Act
    Object deviceSkins = DeviceSkinUpdater.updateSkins(device, null, null, true);

    // Assert
    assertEquals(device, deviceSkins);
  }

  @Test
  public void updateSkinsStudioSkinsIsNull() {
    // Arrange
    Path device = myFileSystem.getPath("pixel_4");

    // Act
    Object deviceSkins = DeviceSkinUpdater.updateSkins(device, null, mySdkSkins, true);

    // Assert
    assertEquals(mySdkSkins.resolve(device), deviceSkins);
  }

  @Test
  public void updateSkinsSdkSkinsIsNull() {
    // Arrange
    Path device = myFileSystem.getPath("pixel_4");

    // Act
    Object deviceSkins = DeviceSkinUpdater.updateSkins(device, myStudioSkins, null, true);

    // Assert
    assertEquals(myStudioSkins.resolve(device), deviceSkins);
  }

  @Test
  public void updateSkinsImplSdkDeviceSkinsAreUpToDate() throws IOException {
    // Arrange
    Path device = myFileSystem.getPath("pixel_4");
    Path sdkDeviceSkins = mySdkSkins.resolve(device);
    Files.createDirectories(sdkDeviceSkins);

    DeviceSkinUpdater updater = new DeviceSkinUpdater(myStudioSkins, mySdkSkins, true, myFileSystem, Mockito.mock(Converter.class));

    // Act
    Object deviceSkins = updater.updateSkinsImpl(device);

    // Assert
    assertEquals(sdkDeviceSkins, deviceSkins);
  }

  @Test
  public void updateSkinsImplEmulatorSupportsWebP() throws IOException {
    // Arrange
    Path device = myFileSystem.getPath("pixel_4");
    Files.createDirectories(myStudioSkins.resolve(device));

    DeviceSkinUpdater updater = new DeviceSkinUpdater(myStudioSkins, mySdkSkins, true, myFileSystem, Mockito.mock(Converter.class));

    // Act
    Object deviceSkins = updater.updateSkinsImpl(device);

    // Assert
    assertEquals(mySdkSkins.resolve(device), deviceSkins);
  }

  @Test
  public void updateSkinsImplEmulatorDoesntSupportWebP() throws IOException {
    // Arrange
    Path device = myFileSystem.getPath("pixel_4");
    Files.createDirectories(myStudioSkins.resolve(device));

    DeviceSkinUpdater updater = new DeviceSkinUpdater(myStudioSkins, mySdkSkins, false, myFileSystem, Mockito.mock(Converter.class));

    // Act
    Object deviceSkins = updater.updateSkinsImpl(device);

    // Assert
    assertEquals(mySdkSkins.resolve(device), deviceSkins);
  }

  @Test
  public void updateSkinsImplFilesListThrowsNoSuchFileException() {
    // Arrange
    Path device = myFileSystem.getPath("pixel_4");

    DeviceSkinUpdater updater = new DeviceSkinUpdater(myStudioSkins, mySdkSkins, true, myFileSystem, Mockito.mock(Converter.class));

    // Act
    Object deviceSkins = updater.updateSkinsImpl(device);

    // Assert
    assertEquals(myStudioSkins.resolve(device), deviceSkins);
  }

  @Test
  public void updateSkinsImplDeviceEqualsAndroidWearRound() {
    // Arrange
    Path device = myFileSystem.getPath("AndroidWearRound");

    DeviceSkinUpdater updater = new DeviceSkinUpdater(myStudioSkins, mySdkSkins, true, myFileSystem, Mockito.mock(Converter.class));

    // Act
    Object deviceSkins = updater.updateSkinsImpl(device);

    // Assert
    assertEquals(myStudioSkins.resolve("wear_round"), deviceSkins);
  }

  @Test
  public void updateSkinsImplDeviceEqualsAndroidWearSquare() {
    // Arrange
    Path device = myFileSystem.getPath("AndroidWearSquare");

    DeviceSkinUpdater updater = new DeviceSkinUpdater(myStudioSkins, mySdkSkins, true, myFileSystem, Mockito.mock(Converter.class));

    // Act
    Object deviceSkins = updater.updateSkinsImpl(device);

    // Assert
    assertEquals(myStudioSkins.resolve("wear_square"), deviceSkins);
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

    DeviceSkinUpdater updater = new DeviceSkinUpdater(myStudioSkins, mySdkSkins, true, myFileSystem, Mockito.mock(Converter.class));

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

    DeviceSkinUpdater updater = new DeviceSkinUpdater(myStudioSkins, mySdkSkins, true, myFileSystem, Mockito.mock(Converter.class));

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

    DeviceSkinUpdater updater = new DeviceSkinUpdater(myStudioSkins, mySdkSkins, true, myFileSystem, Mockito.mock(Converter.class));

    // Act
    Object deviceSkins = updater.updateSkinsImpl(device);

    // Assert
    assertEquals(sdkDeviceSkins, deviceSkins);
  }

  @Test
  public void updateSkinsImplConvertAndCopyStudioDeviceSkins() throws IOException {
    // Arrange
    Path device = myFileSystem.getPath("pixel_4");
    Path studioDeviceSkins = myStudioSkins.resolve(device);
    Files.createDirectories(studioDeviceSkins);
    Files.createFile(studioDeviceSkins.resolve("back.webp"));

    String studioLayoutContents = "parts {\n" +
                                  "  device {\n" +
                                  "    display {\n" +
                                  "      width 1080\n" +
                                  "      height 2280\n" +
                                  "      x 0\n" +
                                  "      y 0\n" +
                                  "    }\n" +
                                  "  }\n" +
                                  "  portrait {\n" +
                                  "    background {\n" +
                                  "      image back.webp\n" +
                                  "    }\n" +
                                  "    foreground {\n" +
                                  "      mask mask.webp\n" +
                                  "      cutout emu01\n" +
                                  "    }\n" +
                                  "  }\n" +
                                  "}\n" +
                                  "layouts {\n" +
                                  "  portrait {\n" +
                                  "    width 1178\n" +
                                  "    height 2498\n" +
                                  "    event EV_SW:0:1\n" +
                                  "    part1 {\n" +
                                  "      name portrait\n" +
                                  "      x 0\n" +
                                  "      y 0\n" +
                                  "    }\n" +
                                  "    part2 {\n" +
                                  "      name device\n" +
                                  "      x 46\n" +
                                  "      y 146\n" +
                                  "    }\n" +
                                  "  }\n" +
                                  "}\n";

    Files.write(studioDeviceSkins.resolve("layout"), studioLayoutContents.getBytes(StandardCharsets.UTF_8));
    Files.createFile(studioDeviceSkins.resolve("mask.webp"));

    DeviceSkinUpdater updater = new DeviceSkinUpdater(myStudioSkins, mySdkSkins, false, myFileSystem, new Converter() {
      @Override
      boolean convert(@NotNull Path webPImage, @NotNull Path pngImage) throws IOException {
        Files.createFile(pngImage);
        return true;
      }
    });

    // Act
    Object deviceSkins = updater.updateSkinsImpl(device);

    // Assert
    Path sdkDeviceSkins = mySdkSkins.resolve(device);
    assertEquals(sdkDeviceSkins, deviceSkins);

    Path sdkLayout = sdkDeviceSkins.resolve("layout");
    Object paths = Arrays.asList(sdkDeviceSkins.resolve("back.png"), sdkLayout, sdkDeviceSkins.resolve("mask.png"));

    assertEquals(paths, DeviceSkinUpdater.list(sdkDeviceSkins));

    String sdkLayoutContents = "parts {\n" +
                               "  device {\n" +
                               "    display {\n" +
                               "      width 1080\n" +
                               "      height 2280\n" +
                               "      x 0\n" +
                               "      y 0\n" +
                               "    }\n" +
                               "  }\n" +
                               "  portrait {\n" +
                               "    background {\n" +
                               "      image back.png\n" +
                               "    }\n" +
                               "    foreground {\n" +
                               "      mask mask.png\n" +
                               "      cutout emu01\n" +
                               "    }\n" +
                               "  }\n" +
                               "}\n" +
                               "layouts {\n" +
                               "  portrait {\n" +
                               "    width 1178\n" +
                               "    height 2498\n" +
                               "    event EV_SW:0:1\n" +
                               "    part1 {\n" +
                               "      name portrait\n" +
                               "      x 0\n" +
                               "      y 0\n" +
                               "    }\n" +
                               "    part2 {\n" +
                               "      name device\n" +
                               "      x 46\n" +
                               "      y 146\n" +
                               "    }\n" +
                               "  }\n" +
                               "}\n";

    assertEquals(sdkLayoutContents, new String(Files.readAllBytes(sdkLayout), StandardCharsets.UTF_8));
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
