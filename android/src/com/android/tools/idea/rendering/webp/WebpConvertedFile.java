/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.rendering.webp;

import com.android.tools.adtui.ImageUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import java.awt.image.BufferedImage;
import java.io.*;

import static com.android.SdkConstants.*;
import static com.android.utils.SdkUtils.endsWithIgnoreCase;

/** Represents a file to be converted to WEBP by the {@link ConvertToWebpAction} */
class WebpConvertedFile {
  public final VirtualFile sourceFile;
  public final long sourceFileSize;
  public byte[] encoded;
  public long saved;

  public WebpConvertedFile(@NotNull VirtualFile sourceFile, long sourceFileSize) {
    this.sourceFile = sourceFile;
    this.sourceFileSize = sourceFileSize;
  }

  public void apply(@Nullable Object requestor) throws IOException {
    VirtualFile folder = sourceFile.getParent();
    VirtualFile output = folder.createChildData(requestor, sourceFile.getNameWithoutExtension() + DOT_WEBP);
    try (OutputStream fos = new BufferedOutputStream(output.getOutputStream(requestor))) {
      fos.write(encoded);
    }
    sourceFile.delete(requestor);
  }

  public boolean convert(@NotNull WebpConversionSettings settings) {
    try {
      InputStream stream = new BufferedInputStream(sourceFile.getInputStream());
      BufferedImage image = ImageIO.read(stream);
      stream.close();

      return convert(image, settings);
    }
    catch (IOException e) {
      Logger.getInstance(WebpConvertedFile.class).error("Can't convert " + sourceFile.getPath(), e);
      return false;
    }
  }

  public boolean convert(@NotNull BufferedImage image, @NotNull WebpConversionSettings settings) {
    try {
      // See if we find an alpha channel in this image and if so, return null
      if (settings.skipTransparentImages) {
        String name = sourceFile.getName();
        if (name.endsWith(DOT_PNG) || name.endsWith(DOT_GIF)) {
          if (ImageUtils.isNonOpaque(image)) {
            return false;
          }
        }
      }

      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream((int)sourceFileSize);

      ImageTypeSpecifier type = ImageTypeSpecifier.createFromRenderedImage(image);
      if (!WebpImageWriterSpi.canWriteImage(type)) {
        return false;
      }

      WebpImageWriterSpi.writeImage(image, byteArrayOutputStream, settings.lossless, settings.quality);
      encoded = byteArrayOutputStream.toByteArray();
      saved = sourceFileSize - encoded.length;
      return true;
    } catch (IOException e) {
      Logger.getInstance(WebpConvertedFile.class).error("Can't convert " + sourceFile.getPath(), e);
      return false;
    }
  }

  @Nullable
  public static WebpConvertedFile create(@NotNull VirtualFile pngFile, @NotNull WebpConversionSettings settings) {
    try {
      InputStream stream = new BufferedInputStream(pngFile.getInputStream());
      long size = pngFile.getLength();
      BufferedImage image = ImageIO.read(stream);
      stream.close();

      if (image == null) {
        Logger.getInstance(WebpConvertedFile.class).warn("Can't read image: " + pngFile.getPath());
        return null;
      }

      String fileName = pngFile.getName();
      if (settings.skipNinePatches && endsWithIgnoreCase(fileName, DOT_9PNG)) {
        return null;
      }

      // See if we find an alpha channel in this image and if so, return null
      if (settings.skipTransparentImages && (endsWithIgnoreCase(fileName, DOT_PNG) || endsWithIgnoreCase(fileName, DOT_GIF))) {
        if (ImageUtils.isNonOpaque(image)) {
          return null;
        }
      }
      return new WebpConvertedFile(pngFile, size);
    } catch (IOException e) {
      Logger.getInstance(WebpConvertedFile.class).error("Can't convert " + pngFile.getPath(), e);
    }
    return null;
  }

  @Nullable
  public BufferedImage getSourceImage() throws IOException {
    InputStream pngStream = sourceFile.getInputStream();
    return ImageIO.read(pngStream);
  }

  @Nullable
  public BufferedImage getEncodedImage() throws IOException {
    if (encoded != null) {
      InputStream webpStream = new ByteArrayInputStream(encoded);
      return ImageIO.read(webpStream);
    } else {
      return null;
    }
  }
}