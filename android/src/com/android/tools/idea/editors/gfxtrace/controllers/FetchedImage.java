/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace.controllers;

import com.android.tools.idea.editors.gfxtrace.LoadingCallback;
import com.android.tools.idea.editors.gfxtrace.service.ServiceClient;
import com.android.tools.idea.editors.gfxtrace.service.image.FmtFloat32;
import com.android.tools.idea.editors.gfxtrace.service.image.FmtRGBA;
import com.android.tools.idea.editors.gfxtrace.service.image.ImageInfo;
import com.android.tools.idea.editors.gfxtrace.service.path.AsPath;
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class FetchedImage {
  @NotNull private static final Logger LOG = Logger.getInstance(FetchedImage.class);
  @NotNull private final ImageInfo myImageInfo;
  @NotNull private final byte[] myData;
  @NotNull private final DepthConversionMode myDepthConversionMode = DepthConversionMode.GO_CLIENT;

  public static ListenableFuture<FetchedImage> load(final ServiceClient client, final Path imagePath) {
    final SettableFuture<FetchedImage> result = SettableFuture.create();
    Futures.addCallback(client.get(imagePath), new LoadingCallback<Object>(LOG) {
      @Override
      public void onSuccess(@Nullable final Object object) {
        assert (object instanceof ImageInfo);
        final ImageInfo imageInfo = (ImageInfo)object;
        if (imageInfo.getFormat() instanceof FmtRGBA) {
          doLoad(client, imageInfo, result);
        }
        else {
          final AsPath asPath = new AsPath().setObject(imagePath).setType(new FmtRGBA());
          Futures.addCallback(client.get(asPath), new LoadingCallback<Object>(LOG) {
            @Override
            public void onSuccess(@Nullable final Object object) {
              assert (object instanceof ImageInfo);
              final ImageInfo imageInfo = (ImageInfo)object;
              doLoad(client, imageInfo, result);
            }
          });
        }
      }
    });
    return result;
  }

  private static void doLoad(final ServiceClient client, final ImageInfo imageInfo, final SettableFuture<FetchedImage> result) {
    Futures.addCallback(client.get(imageInfo.getData()), new LoadingCallback<byte[]>(LOG) {
      @Override
      public void onSuccess(@Nullable final byte[] data) {
        result.set(new FetchedImage(imageInfo, data));
      }
    });
  }

  public FetchedImage(@NotNull ImageInfo imageInfo, @NotNull byte[] data) {
    myImageInfo = imageInfo;
    myData = data;
  }

  @NotNull
  public ImageIcon createImageIcon() {
    Dimension imageDimensions = getImageDimensions();
    //noinspection UndesirableClassUsage
    BufferedImage image = new BufferedImage(imageDimensions.width, imageDimensions.height, BufferedImage.TYPE_4BYTE_ABGR_PRE);
    WritableRaster raster = image.getRaster();
    DataBufferByte dataBuffer = (DataBufferByte)raster.getDataBuffer();
    setImageBytes(dataBuffer.getData());
    return new ImageIcon(image);
  }

  @NotNull
  private Dimension getImageDimensions() {
    return new Dimension((int)myImageInfo.getWidth(), (int)myImageInfo.getHeight());
  }

  private void setImageBytes(@NotNull byte[] destination) {
    // TODO: move these into the image format classes
    if (myImageInfo.getFormat() instanceof FmtRGBA) {
      Dimension dimension = getImageDimensions();
      final int stride = dimension.width * 4;

      int length = stride * dimension.height;
      assert (destination.length >= length);

      // Covert between top-left and bottom-left formats.
      byte[] data = myData;
      for (int y = 0; y < dimension.height; ++y) {
        int yOffsetSource = stride * y;
        int yOffsetDestination = length - stride - yOffsetSource;
        for (int x = 0; x < stride; x += 4) {
          int destinationOffset = yOffsetDestination + x;
          int sourceOffset = yOffsetSource + x;
          destination[destinationOffset] = (byte)0xff;
          destination[destinationOffset + 1] = data[sourceOffset + 2];
          destination[destinationOffset + 2] = data[sourceOffset + 1];
          destination[destinationOffset + 3] = data[sourceOffset];
        }
      }
    }
    else if (myImageInfo.getFormat() instanceof FmtFloat32) {
      Dimension dimension = getImageDimensions();
      final int stride = dimension.width * 4;

      int length = stride * dimension.height;
      assert (destination.length >= length);

      // Covert between top-left and bottom-left formats.
      byte[] data = myData;
      byte[] floatBuffer = new byte[4];
      ByteBuffer intBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
      for (int y = 0; y < dimension.height; ++y) {
        int yOffsetSource = stride * y;
        int yOffsetDestination = length - stride - yOffsetSource;
        for (int x = 0; x < stride; x += 4) {
          int destinationOffset = yOffsetDestination + x;
          int sourceOffset = yOffsetSource + x;

          floatBuffer[0] = data[sourceOffset];
          floatBuffer[1] = data[sourceOffset + 1];
          floatBuffer[2] = data[sourceOffset + 2];
          floatBuffer[3] = data[sourceOffset + 3];

          float depth = ByteBuffer.wrap(floatBuffer).order(ByteOrder.LITTLE_ENDIAN).getFloat();
          assert (depth <= 1.0f);

          byte red;
          byte green;
          byte blue;

          switch (myDepthConversionMode) {
            case GO_CLIENT: {
              float semiLinearDepth = 0.01f / (1.0f - depth);
              double realRed = Math.cos(semiLinearDepth + Math.PI * 2.0f) * 127.0 + 128.0;
              double realGreen = Math.cos(semiLinearDepth + Math.PI * 2.0f * 0.33333f) * 127.0 + 128.0;
              double realBlue = Math.cos(semiLinearDepth + Math.PI * 2.0f * 0.66667f) * 127.0 + 128.0;
              red = (byte)realRed;
              blue = (byte)realBlue;
              green = (byte)realGreen;
              break;
            }

            case TRIGONOMETRIC: {
              float semiLinearDepth = (1.0f / (1.0009765625f - depth)) / (1.0f / 0.0009765625f + 1.0f / 1.0009765625f);
              double realRed = (1.0 - Math.cos(semiLinearDepth * Math.PI)) / 2.0;
              double realGreen = (1.0 - Math.cos(semiLinearDepth * Math.PI * 10.0)) / 2.0;
              double realBlue = (1.0 - Math.cos(semiLinearDepth * Math.PI * 100.0)) / 2.0;
              red = (byte)(realRed * 255.0);
              green = (byte)(realGreen * 255.0);
              blue = (byte)(realBlue * 255.0);
              break;
            }

            case DEFAULT:
            default: {
              int intDepth = (int)(depth * (float)(0x1000000 - 1));
              intBuffer.clear();
              byte[] colorArray = intBuffer.putInt(intDepth).array();

              destination[destinationOffset] = (byte)0xff;
              red = colorArray[2];
              green = colorArray[1];
              blue = colorArray[0];
              break;
            }
          }

          destination[destinationOffset] = (byte)0xff;
          destination[destinationOffset + 1] = blue;
          destination[destinationOffset + 2] = green;
          destination[destinationOffset + 3] = red;
        }
      }
    }
    else {
      throw new RuntimeException("Unsupported image format to decode.");
    }
  }

  /*
   * Client side floating depth to color conversion modes.
   * DEFAULT - Converts float z-depth to linear integer depth, then casts to color.
   * TRIGONOMETRIC - Converts float z-depth to linear integer depth, then calculates harmonic cosine values for each color component.
   * GO_CLIENT - Uses the Go client's mode of displaying depth.
   */
  enum DepthConversionMode {
    DEFAULT,
    TRIGONOMETRIC,
    GO_CLIENT
  }
}
