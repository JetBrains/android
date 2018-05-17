/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.emulator;

import static com.android.tools.idea.util.StudioPathManager.isRunningFromSources;

import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.idea.protobuf.UnsafeByteOperations;
import com.android.tools.idea.util.StudioPathManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.system.CpuArch;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

public class ImageConverter {
  private static Field bytesField;
  private static Field offsetField;

  static {
    try {
      loadNativeLibrary();
      initNative();
      initByteStringFields();
    }
    catch (Throwable e) {
      logger().error("Native image converter library is not available", e);
    }
  }

  /**
   * Converts pixel values in RGB888 format to the 32-bit integers in the 0xAARRGGBB format.
   *
   * @param imageBytes the pixel values to convert
   * @param pixels the converted pixel values
   */
  public static void unpackRgb888(@NotNull ByteString imageBytes, int[] pixels) {
    int length = imageBytes.size();
    if (length == 0) {
      return;
    }
    if (bytesField != null) {
      try {
        byte[] bytes = (byte[])bytesField.get(imageBytes);
        int offset = offsetField.getInt(imageBytes);
        unpackRgb888(bytes, offset, length, pixels);
        return;
      }
      catch (IllegalAccessException e) {
        logger().error("Unable to use reflection, will use slow path", e);
        bytesField = null;
        offsetField = null;
      }
    }
    unpackRgb888Slow(imageBytes, pixels);
  }

  /**
   * Converts pixel values in RGB888 format to the 32-bit integers in the 0xAARRGGBB format without
   * using native code.
   *
   * @param imageBytes the pixel values to convert
   * @param pixels the converted pixel values
   */
  public static void unpackRgb888Slow(@NotNull ByteString imageBytes, int[] pixels) {
    int length = imageBytes.size();
    if (length % 3 != 0) {
      throw new IllegalArgumentException("Number of bytes (" + length + ") is not a multiple of 3");
    }
    int i = 0;
    int j = 0;
    while (i < length) {
      int red = imageBytes.byteAt(i++) & 0xFF;
      int green = imageBytes.byteAt(i++) & 0xFF;
      int blue = imageBytes.byteAt(i++) & 0xFF;
      pixels[j++] = 0xFF000000 | (red << 16) | (green << 8) | blue;
    }
  }

  @VisibleForTesting
  synchronized static void loadNativeLibrary() {
    Path libFile = getLibLocation();
    System.load(libFile.toString());
  }

  private static @NotNull Path getLibLocation() {
    String libName = getLibName();
    Path homePath = Paths.get(PathManager.getHomePath());
    // Installed Studio.
    Path libFile = homePath.resolve("plugins/android/resources/native").resolve(libName);
    if (Files.exists(libFile)) {
      return libFile;
    }

    if (isRunningFromSources()) {
      // Dev environment.
      libFile = StudioPathManager.resolvePathFromSourcesRoot("tools/adt/idea/streaming/native")
          .resolve(getPlatformName()).resolve(libName);
      if (Files.exists(libFile)) {
        return libFile;
      }
      throw new UnsatisfiedLinkError("Unable to find " + libFile);
    }
    else {
      throw new UnsatisfiedLinkError("Unable to find " + libName + ". Possibly corrupted Studio installation");
    }
  }

  private static @NotNull String getLibName() {
    return System.mapLibraryName("image_converter");
  }

  private static @NotNull String getPlatformName() {
    if (SystemInfo.isWindows) {
      return "win";
    }
    if (SystemInfo.isMac) {
      return CpuArch.isArm64() ? "mac_arm" : "mac";
    }
    if (SystemInfo.isLinux) {
      return "linux";
    }
    return "";
  }

  private static void initByteStringFields() {
    Class<? extends ByteString> byteStringClass = UnsafeByteOperations.unsafeWrap(new byte[4], 1, 2).getClass();
    try {
      bytesField = byteStringClass.getSuperclass().getDeclaredField("bytes");
      bytesField.setAccessible(true);
      offsetField = byteStringClass.getDeclaredField("bytesOffset");
      offsetField.setAccessible(true);
    }
    catch (ReflectiveOperationException | RuntimeException e) {
      bytesField = null;
      offsetField = null;
      throw new RuntimeException("Unable to access fields of " + byteStringClass.getName(), e);
    }
  }

  @NotNull
  private static Logger logger() {
    return Logger.getInstance(ImageConverter.class);
  }

  private static native void initNative();

  /**
   * Converts pixel values in RGB888 format to the 32-bit integers in the 0xAARRGGBB format.
   *
   * @param imageBytes the array containing pixel values to convert
   * @param offset the offset of the first image byte
   * @param length the number of image bytes; has to be a multiple of 3
   * @param pixels the converted pixel values
   */
  private static native void unpackRgb888(byte[] imageBytes, int offset, int length, int[] pixels);

  // Do not instantiate. All methods are static.
  private ImageConverter() {}
}
