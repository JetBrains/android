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

import static com.android.SdkConstants.DOT_9PNG;
import static com.android.SdkConstants.DOT_GIF;
import static com.android.SdkConstants.DOT_PNG;
import static com.android.SdkConstants.DOT_WEBP;
import static com.android.utils.SdkUtils.endsWithIgnoreCase;

import com.android.tools.adtui.ImageUtils;
import com.android.tools.adtui.webp.WebpImageWriterSpi;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.stream.ImageInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Represents a file to be converted to WEBP by the {@link ConvertToWebpAction} */
public class WebpConvertedFile {
  public final VirtualFile sourceFile;
  public final long sourceFileSize;
  public final boolean isMultiFrame;
  public final int frameCount;
  public byte[] encoded;
  public long saved;

  public WebpConvertedFile(@NotNull VirtualFile sourceFile, long sourceFileSize) {
    this(sourceFile, sourceFileSize, 1);
  }

  public WebpConvertedFile(@NotNull VirtualFile sourceFile, long sourceFileSize, boolean isMultiFrame) {
    this(sourceFile, sourceFileSize, isMultiFrame ? 2 : 1);
  }

  public WebpConvertedFile(@NotNull VirtualFile sourceFile, long sourceFileSize, int frameCount) {
    this.sourceFile = sourceFile;
    this.sourceFileSize = sourceFileSize;
    this.frameCount = frameCount;
    this.isMultiFrame = frameCount > 1;
  }

  public void apply(@Nullable Object requestor) throws IOException {
    VirtualFile folder = sourceFile.getParent();
    VirtualFile output = folder.createChildData(requestor, sourceFile.getNameWithoutExtension() + DOT_WEBP);
    try (OutputStream stream = new BufferedOutputStream(output.getOutputStream(requestor))) {
      stream.write(encoded);
    }
    sourceFile.delete(requestor);
  }

  public boolean convert(@NotNull WebpConversionSettings settings) {
    String name = sourceFile.getName();
    if (endsWithIgnoreCase(name, DOT_GIF)) {
      try {
        ImageReader reader = ImageIO.getImageReadersBySuffix("GIF").next();
        try (ImageInputStream iis = ImageIO.createImageInputStream(sourceFile.getInputStream())) {
          reader.setInput(iis);
          int numImages = reader.getNumImages(true);
          if (numImages > 1) {
            if (settings.skipTransparentImages) {
              for (int i = 0; i < numImages; i++) {
                BufferedImage frame = reader.read(i);
                if (frame != null && ImageUtils.isNonOpaque(frame)) {
                  return false;
                }
              }
            }
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream((int)sourceFileSize);
            WebpEncoding.writeAnimatedGif(reader, numImages, byteArrayOutputStream, settings);
            encoded = byteArrayOutputStream.toByteArray();
            saved = sourceFileSize - encoded.length;
            return true;
          }
        }
        finally {
          reader.dispose();
        }
      } catch (IOException e) {
        Logger.getInstance(WebpConvertedFile.class).error("Can't convert " + sourceFile.getPath(), e);
        return false;
      }
    }

    try {
      BufferedImage image;
      try (InputStream stream = new BufferedInputStream(sourceFile.getInputStream())) {
        image = ImageIO.read(stream);
      }

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
      long size = pngFile.getLength();
      String fileName = pngFile.getName();

      if (settings.skipNinePatches && endsWithIgnoreCase(fileName, DOT_9PNG)) {
        return null;
      }

      if (endsWithIgnoreCase(fileName, DOT_GIF)) {
        ImageReader reader = ImageIO.getImageReadersBySuffix("GIF").next();
        try (ImageInputStream iis = ImageIO.createImageInputStream(pngFile.getInputStream())) {
          reader.setInput(iis);
          int numImages = reader.getNumImages(true);
          if (numImages <= 0) {
            return null;
          }
          if (settings.skipTransparentImages) {
            for (int i = 0; i < numImages; i++) {
              BufferedImage frame = reader.read(i);
              if (frame != null && ImageUtils.isNonOpaque(frame)) {
                return null;
              }
            }
          }
          return new WebpConvertedFile(pngFile, size, numImages);
        }
        finally {
          reader.dispose();
        }
      }

      InputStream stream = new BufferedInputStream(pngFile.getInputStream());
      BufferedImage image = ImageIO.read(stream);
      stream.close();

      if (image == null) {
        Logger.getInstance(WebpConvertedFile.class).warn("Can't read image: " + pngFile.getPath());
        return null;
      }

      // See if we find an alpha channel in this image and if so, return null
      if (settings.skipTransparentImages && endsWithIgnoreCase(fileName, DOT_PNG)) {
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
      return WebpEncoding.decodeWebp(encoded);
    } else {
      return null;
    }
  }
}