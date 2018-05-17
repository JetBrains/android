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

import com.android.SdkConstants;
import com.android.annotations.concurrency.Slow;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.adtui.device.DeviceArtDescriptor;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.utils.FileUtils;
import com.android.utils.PathUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.MoreFiles;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import java.awt.image.RenderedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DeviceSkinUpdater {
  @VisibleForTesting
  static class Converter {
    @VisibleForTesting
    boolean convert(@NotNull Path webPImage, @NotNull Path pngImage) throws IOException {
      RenderedImage image;

      try (InputStream in = new BufferedInputStream(Files.newInputStream(webPImage))) {
        image = ImageIO.read(in);
      }

      if (image == null) {
        return false;
      }

      try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(pngImage))) {
        ImageIO.write(image, "PNG", out);
        return true;
      }
    }
  }

  private final @NotNull Path myStudioSkins;
  private final @NotNull Path mySdkSkins;
  private final boolean myEmulatorSupportsWebP;

  private final @NotNull Converter myConverter;

  @VisibleForTesting
  DeviceSkinUpdater(@NotNull Path studioSkins,
                    @NotNull Path sdkSkins,
                    boolean emulatorSupportsWebP,
                    @NotNull Converter converter) {
    myStudioSkins = studioSkins;
    mySdkSkins = sdkSkins;
    myEmulatorSupportsWebP = emulatorSupportsWebP;

    myConverter = converter;
  }

  /**
   * Usually returns the SDK skins path for the device (${HOME}/Android/Sdk/skins/pixel_4). This method also copies device skins from Studio
   * to the SDK if the SDK ones are out of date and converts WebP skin images to PNG if the emulator doesn't support WebP images.
   *
   * @return the SDK skins path for the device. Returns device as is if it's empty, absolute, equal to _no_skin, or both the Studio skins
   * path and SDK are not found. Returns the SDK skins path for the device if the Studio skins path is not found. Returns the Studio skins
   * path for the device (${HOME}/android-studio/plugins/android/resources/device-art-resources/pixel_4) if the SDK is not found or an IOException
   * is thrown.
   * @see DeviceSkinUpdaterService
   */
  @Slow
  static @NotNull Path updateSkins(@NotNull Path device) {
    return updateSkins(device, null);
  }

  @Slow
  static @NotNull Path updateSkins(@NotNull Path device, @Nullable SystemImageDescription image) {
    Collection<Path> imageSkins = image == null ? Collections.emptyList() : Arrays.asList(image.getSkins());

    File studioSkins = DeviceArtDescriptor.getBundledDescriptorsFolder();
    AndroidSdkHandler sdk = AndroidSdks.getInstance().tryToChooseSdkHandler();

    return updateSkins(device,
                       imageSkins,
                       studioSkins == null ? null : device.resolve(studioSkins.getPath()),
                       sdk.getLocation() == null ? null : sdk.getLocation().resolve("skins"),
                       AvdWizardUtils.emulatorSupportsWebp(sdk));
  }

  @VisibleForTesting
  static @NotNull Path updateSkins(@NotNull Path device,
                                   @NotNull Collection<Path> imageSkins,
                                   @Nullable Path studioSkins,
                                   @Nullable Path sdkSkins,
                                   boolean emulatorSupportsWebP) {
    if (device.toString().isEmpty() || device.isAbsolute() || device.equals(device.getFileSystem().getPath(SkinUtils.NO_SKIN))) {
      return device;
    }

    Optional<Path> optionalImageSkin = imageSkins.stream()
      .filter(skin -> skin.endsWith(device))
      .findFirst();

    if (optionalImageSkin.isPresent()) {
      return optionalImageSkin.get();
    }

    if (studioSkins == null && sdkSkins == null) {
      return device;
    }

    if (studioSkins == null) {
      return sdkSkins.resolve(device);
    }

    if (sdkSkins == null) {
      return studioSkins.resolve(device);
    }

    return new DeviceSkinUpdater(studioSkins, sdkSkins, emulatorSupportsWebP, new Converter()).updateSkinsImpl(device);
  }

  @VisibleForTesting
  @NotNull Path updateSkinsImpl(@NotNull Path device) {
    assert !device.toString().isEmpty() && !device.isAbsolute() &&
           !device.equals(device.getFileSystem().getPath(SkinUtils.NO_SKIN)) : device;

    Path sdkDeviceSkins = mySdkSkins.resolve(device);
    Path studioDeviceSkins = getStudioDeviceSkins(device);

    try {
      if (areSdkDeviceSkinsUpToDate(sdkDeviceSkins, studioDeviceSkins)) {
        return sdkDeviceSkins;
      }

      Files.createDirectories(sdkDeviceSkins);

      if (myEmulatorSupportsWebP) {
        copyStudioDeviceSkins(studioDeviceSkins, sdkDeviceSkins);
      }
      else {
        convertAndCopyStudioDeviceSkins(studioDeviceSkins, sdkDeviceSkins);
      }

      return sdkDeviceSkins;
    }
    catch (IOException exception) {
      Logger.getInstance(DeviceSkinUpdater.class).warn(exception);
      return studioDeviceSkins;
    }
  }

  private @NotNull Path getStudioDeviceSkins(@NotNull Path device) {
    if (device.equals(device.getFileSystem().getPath("WearLargeRound"))) {
      return myStudioSkins.resolve("wearos_large_round");
    }

    if (device.equals(device.getFileSystem().getPath("WearSmallRound"))) {
      return myStudioSkins.resolve("wearos_small_round");
    }

    if (device.equals(device.getFileSystem().getPath("WearSquare"))) {
      return myStudioSkins.resolve("wearos_square");
    }

    if (device.equals(device.getFileSystem().getPath("WearRect"))) {
      return myStudioSkins.resolve("wearos_rect");
    }

    return myStudioSkins.resolve(device);
  }

  private static boolean areSdkDeviceSkinsUpToDate(@NotNull Path sdkDeviceSkins, @NotNull Path studioDeviceSkins) throws IOException {
    if (Files.notExists(sdkDeviceSkins)) {
      return false;
    }

    Path studioLayout = studioDeviceSkins.resolve(SdkConstants.FN_SKIN_LAYOUT);

    if (Files.notExists(studioLayout)) {
      return true;
    }

    Path sdkLayout = sdkDeviceSkins.resolve(SdkConstants.FN_SKIN_LAYOUT);

    if (Files.notExists(sdkLayout)) {
      return false;
    }

    if (Files.getLastModifiedTime(studioLayout).compareTo(Files.getLastModifiedTime(sdkLayout)) < 0) {
      return true;
    }

    PathUtils.deleteRecursivelyIfExists(sdkDeviceSkins);
    return false;
  }

  private static void copyStudioDeviceSkins(@NotNull Path studioDeviceSkins, @NotNull Path sdkDeviceSkins) throws IOException {
    for (Path path : list(studioDeviceSkins)) {
      copy(path, sdkDeviceSkins.resolve(path.getFileName()));
    }
  }

  private void convertAndCopyStudioDeviceSkins(@NotNull Path studioDeviceSkins, @NotNull Path sdkDeviceSkins) throws IOException {
    Collection<Path> paths = list(studioDeviceSkins);
    Path layout = null;

    int size = paths.size();
    List<String> namesToReplace = new ArrayList<>(size);
    List<String> namesToReplaceThemWith = new ArrayList<>(size);

    for (Path path : paths) {
      String name = path.getFileName().toString();

      if (name.equals(SdkConstants.FN_SKIN_LAYOUT)) {
        layout = path;
        continue;
      }

      if (name.endsWith(SdkConstants.DOT_WEBP)) {
        @SuppressWarnings("UnstableApiUsage")
        Path pngImage = sdkDeviceSkins.resolve(MoreFiles.getNameWithoutExtension(path.getFileName()) + SdkConstants.DOT_PNG);

        if (myConverter.convert(path, pngImage)) {
          namesToReplace.add(name);
          namesToReplaceThemWith.add(pngImage.getFileName().toString());

          continue;
        }
      }

      copy(path, sdkDeviceSkins.resolve(name));
    }

    if (layout == null) {
      return;
    }

    replaceAll(layout, namesToReplace, namesToReplaceThemWith, sdkDeviceSkins.resolve(layout.getFileName()));
  }

  private static void replaceAll(@NotNull Path source,
                                 @NotNull List<String> stringsToReplace,
                                 @NotNull List<String> stringsToReplaceThemWith,
                                 @NotNull Path target) throws IOException {
    String sourceString = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
    String targetString = StringUtil.replace(sourceString, stringsToReplace, stringsToReplaceThemWith);

    Files.write(target, targetString.getBytes(StandardCharsets.UTF_8));
  }

  @VisibleForTesting
  static @NotNull Collection<Path> list(@NotNull Path directory) throws IOException {
    try (Stream<Path> stream = Files.list(directory)) {
      return stream
        .sorted()
        .collect(Collectors.toList());
    }
  }

  @VisibleForTesting
  static void copy(@NotNull Path source, @NotNull Path target) throws IOException {
    if (!Files.isRegularFile(source)) {
      return;
    }

    if (Files.exists(target)) {
      return;
    }

    FileUtils.copyFile(source, target);
  }
}
