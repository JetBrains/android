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
import com.android.tools.adtui.device.DeviceArtDescriptor;
import com.android.tools.idea.sdk.AndroidSdks;
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
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.jetbrains.android.sdk.AndroidSdkData;
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

  private final @NotNull FileSystem myFileSystem;
  private final @NotNull Converter myConverter;

  @VisibleForTesting
  DeviceSkinUpdater(@NotNull Path studioSkins,
                    @NotNull Path sdkSkins,
                    boolean emulatorSupportsWebP,
                    @NotNull FileSystem fileSystem,
                    @NotNull Converter converter) {
    myStudioSkins = studioSkins;
    mySdkSkins = sdkSkins;
    myEmulatorSupportsWebP = emulatorSupportsWebP;

    myFileSystem = fileSystem;
    myConverter = converter;
  }

  /**
   * Call this through {@link DeviceSkinUpdaterService#updateSkins}
   */
  @Slow
  static @NotNull Path updateSkins(@NotNull Path device) {
    File studioSkins = DeviceArtDescriptor.getBundledDescriptorsFolder();
    AndroidSdkData sdk = AndroidSdks.getInstance().tryToChooseAndroidSdk();

    return updateSkins(device,
                       studioSkins == null ? null : studioSkins.toPath(),
                       sdk == null ? null : sdk.getLocation().toPath(),
                       sdk != null && AvdWizardUtils.emulatorSupportsWebp(sdk.getSdkHandler()));
  }

  @VisibleForTesting
  static @NotNull Path updateSkins(@NotNull Path device,
                                   @Nullable Path studioSkins,
                                   @Nullable Path sdkSkins,
                                   boolean emulatorSupportsWebP) {
    if (studioSkins == null && sdkSkins == null) {
      return device;
    }

    if (studioSkins == null) {
      return sdkSkins.resolve(device);
    }

    if (sdkSkins == null) {
      return studioSkins.resolve(device);
    }

    DeviceSkinUpdater updater = new DeviceSkinUpdater(studioSkins,
                                                      sdkSkins,
                                                      emulatorSupportsWebP,
                                                      FileSystems.getDefault(),
                                                      new Converter());

    return updater.updateSkinsImpl(device);
  }

  @VisibleForTesting
  @NotNull Path updateSkinsImpl(@NotNull Path device) {
    assert !device.toString().isEmpty() && !device.isAbsolute() && !device.equals(myFileSystem.getPath("_no_skin")) : device;

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
    if (device.equals(myFileSystem.getPath("AndroidWearRound"))) {
      return myStudioSkins.resolve("wear_round");
    }

    if (device.equals(myFileSystem.getPath("AndroidWearSquare"))) {
      return myStudioSkins.resolve("wear_square");
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
                                 @NotNull List<@NotNull String> stringsToReplace,
                                 @NotNull List<@NotNull String> stringsToReplaceThemWith,
                                 @NotNull Path target) throws IOException {
    String sourceString = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
    String targetString = StringUtil.replace(sourceString, stringsToReplace, stringsToReplaceThemWith);

    Files.write(target, targetString.getBytes(StandardCharsets.UTF_8));
  }

  @VisibleForTesting
  static @NotNull Collection<@NotNull Path> list(@NotNull Path directory) throws IOException {
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

    Files.copy(source, target);
  }
}
