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
package com.android.tools.idea.editors.gfxtrace.service.image;

import com.android.tools.idea.editors.gfxtrace.service.ServiceClient;
import com.android.tools.idea.editors.gfxtrace.service.gfxapi.Cubemap;
import com.android.tools.idea.editors.gfxtrace.service.gfxapi.Texture2D;
import com.android.tools.idea.editors.gfxtrace.service.path.ImageInfoPath;
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.google.common.base.Function;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;

public class FetchedImage {
  @NotNull private static final Logger LOG = Logger.getInstance(FetchedImage.class);

  @NotNull private final ImageInfo myImageInfo;
  @NotNull public final Dimension dimensions;
  @NotNull public final BufferedImage image;

  public static ListenableFuture<FetchedImage> load(final ServiceClient client, ListenableFuture<ImageInfoPath> imageInfo) {
    return Futures.transform(imageInfo, new AsyncFunction<ImageInfoPath, FetchedImage>() {
      @Override
      public ListenableFuture<FetchedImage> apply(ImageInfoPath imageInfoPath) throws Exception {
        return load(client, imageInfoPath);
      }
    });
  }

  public static ListenableFuture<FetchedImage> load(final ServiceClient client, final Path imagePath) {
    return Futures.transform(client.get(imagePath.as(Format.RGBA)), new AsyncFunction<Object, FetchedImage>() {
      @Override
      public ListenableFuture<FetchedImage> apply(Object object) throws Exception {
        if (object instanceof ImageInfo) {
          return load(client, (ImageInfo)object);
        }
        if (object instanceof Texture2D) {
          // TODO: Display mip-level selection, etc.
          return load(client, ((Texture2D)object).getLevels()[0]);
        }
        if (object instanceof Cubemap) {
          // TODO: Display mip-level and face selection, etc.
          return load(client, ((Cubemap)object).getLevels()[0].getNegativeX());
        }
        throw new UnsupportedOperationException("Unexpected resource type " + object.toString());
      }
    });
  }

  private static ListenableFuture<FetchedImage> load(ServiceClient client, final ImageInfo imageInfo) {
    return Futures.transform(client.get(imageInfo.getData()), new Function<byte[], FetchedImage>() {
      @Override
      public FetchedImage apply(byte[] data) {
        return new FetchedImage(imageInfo, data);
      }
    });
  }

  public FetchedImage(@NotNull ImageInfo imageInfo, @NotNull byte[] data) {
    myImageInfo = imageInfo;
    dimensions = new Dimension(myImageInfo.getWidth(), myImageInfo.getHeight());
    //noinspection UndesirableClassUsage
    image = new BufferedImage(dimensions.width, dimensions.height, BufferedImage.TYPE_4BYTE_ABGR);
    WritableRaster raster = image.getRaster();
    DataBufferByte dataBuffer = (DataBufferByte)raster.getDataBuffer();
    assert (myImageInfo.getFormat() instanceof FmtRGBA);
    final int stride = dimensions.width * 4;
    int length = stride * dimensions.height;
    byte[] destination = dataBuffer.getData();
    assert (destination.length >= length);
    // Convert between top-left and bottom-left formats.
    for (int y = 0; y < dimensions.height; ++y) {
      int yOffsetSource = stride * y;
      int yOffsetDestination = length - stride - yOffsetSource;
      for (int x = 0; x < stride; x += 4) {
        int destinationOffset = yOffsetDestination + x;
        int sourceOffset = yOffsetSource + x;
        destination[destinationOffset + 0] = data[sourceOffset + 3];
        destination[destinationOffset + 1] = data[sourceOffset + 2];
        destination[destinationOffset + 2] = data[sourceOffset + 1];
        destination[destinationOffset + 3] = data[sourceOffset + 0];
      }
    }
  }
}
