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

import com.android.annotations.concurrency.Slow;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.adtui.device.DeviceArtDescriptor;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.utils.FileUtils;
import com.android.utils.PathUtils;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

public final class DeviceSkinUpdater {
  private final @NotNull Path studioSkins;
  private final @NotNull Path sdkLocation;

  private DeviceSkinUpdater(@NotNull Path studioSkins, @NotNull Path sdkLocation) {
    this.studioSkins = studioSkins;
    this.sdkLocation = sdkLocation;
  }

  /**
   * Usually returns the SDK skins path for the device (${HOME}/Android/Sdk/skins/pixel_4). This method also copies device skins from Studio
   * to the SDK if the SDK ones are out of date and converts WebP skin images to PNG if the emulator doesn't support WebP images.
   *
   * @return the SDK skins path for the device. Returns device as is if it's empty, absolute, equal to _no_skin, or both the Studio skins
   * path and SDK are not found. Returns the SDK skins path for the device if the Studio skins path is not found. Returns the Studio skins
   * path for the device (${HOME}/android-studio/plugins/android/resources/device-art-resources/pixel_4) if the SDK is not found or
   * an IOException is thrown.
   * @see DeviceSkinUpdaterService
   */
  @Slow
  public static @NotNull Path updateSkin(@NotNull Path skin) {
    return updateSkin(skin, null);
  }

  @Slow
  static @NotNull Path updateSkin(@NotNull Path skin, @Nullable SystemImageDescription image) {
    if (skin.isAbsolute()) {
      return skin;
    }
    String skinName = skin.toString();
    if (skinName.isEmpty() || skinName.equals("_no_skin")) {
      return skin;
    }

    Collection<Path> imageSkins = image == null ? Collections.emptyList() : Arrays.asList(image.getSkins());

    File studioSkins = DeviceArtDescriptor.getBundledDescriptorsFolder();
    AndroidSdkHandler sdk = AndroidSdks.getInstance().tryToChooseSdkHandler();

    return updateSkin(skin, imageSkins, studioSkins == null ? null : studioSkins.toPath(), sdk.getLocation());
  }

  @VisibleForTesting
  static @NotNull Path updateSkin(@NotNull Path skin,
                                  @NotNull Collection<Path> imageSkins,
                                  @Nullable Path studioSkins,
                                  @Nullable Path sdkLocation) {
    for (Path imageSkin : imageSkins) {
      if (imageSkin.endsWith(skin)) {
        return imageSkin;
      }
    }

    if (studioSkins == null && sdkLocation == null) {
      return skin;
    }

    if (studioSkins == null) {
      return sdkLocation.resolve(skin);
    }

    if (sdkLocation == null) {
      return studioSkins.resolve(getStudioSkinName(skin.getFileName().toString()));
    }

    return new DeviceSkinUpdater(studioSkins, sdkLocation).updateSkinImpl(skin);
  }

  private @NotNull Path updateSkinImpl(@NotNull Path skin) {
    assert !skin.toString().isEmpty() && !skin.toString().equals("_no_skin");

    // For historical reasons relative skin paths are resolved relative to SDK itself, not its "skins" directory.
    Path sdkDeviceSkin = sdkLocation.resolve(skin);
    Path studioDeviceSkin = getStudioDeviceSkin(skin.getFileName().toString());

    try {
      if (areAllFilesUpToDate(sdkDeviceSkin, studioDeviceSkin)) {
        return sdkDeviceSkin;
      }

      PathUtils.deleteRecursivelyIfExists(sdkDeviceSkin);
      FileUtils.copyDirectory(studioDeviceSkin, sdkDeviceSkin, false);
      return sdkDeviceSkin;
    }
    catch (IOException exception) {
      Logger.getInstance(DeviceSkinUpdater.class).warn(exception);
      return studioDeviceSkin;
    }
  }

  private @NotNull Path getStudioDeviceSkin(@NotNull String skinName) {
    return studioSkins.resolve(getStudioSkinName(skinName));
  }

  private static @NotNull String getStudioSkinName(@NotNull String skinName) {
    return switch (skinName) {
      case "WearLargeRound" -> "wearos_large_round";
      case "WearSmallRound" -> "wearos_small_round";
      case "WearSquare" -> "wearos_square";
      case "WearRect" -> "wearos_rect";
      default -> skinName;
    };
  }

  /**
   * Checks if all files under the {@code sourceDir} directory have their piers under
   * the {@code targetDir} directory with timestamps not older than the corresponding source file.
   */
  @VisibleForTesting
  static boolean areAllFilesUpToDate(@NotNull Path targetDir, @NotNull Path sourceDir) {
    class UpToDateChecker extends SimpleFileVisitor<Path> {
      boolean targetOlder;

      @Override
      public FileVisitResult visitFile(Path sourceFile, BasicFileAttributes attrs) throws IOException {
        if (!sourceFile.getFileName().toString().startsWith(".")) {
          Path targetFile = targetDir.resolve(sourceDir.relativize(sourceFile).toString());
          // Convert to milliseconds to compensate for different timestamp precision on different file systems.
          if (getLastModifiedTimeMillis(targetFile) < getLastModifiedTimeMillis(sourceFile)) {
            targetOlder = true;
            return FileVisitResult.TERMINATE;
          }
        }
        return FileVisitResult.CONTINUE;
      }

      private static long getLastModifiedTimeMillis(@NotNull Path file) throws IOException {
        return Files.getLastModifiedTime(file).toMillis();
      }
    }

    UpToDateChecker checker = new UpToDateChecker();
    try {
      Files.walkFileTree(sourceDir, checker);
    }
    catch (IOException e) {
      return false;
    }

    return !checker.targetOlder;
  }
}
