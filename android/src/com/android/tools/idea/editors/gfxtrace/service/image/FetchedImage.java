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
import com.android.tools.idea.editors.gfxtrace.service.gfxapi.CubemapLevel;
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
import java.util.List;

public class FetchedImage implements MultiLevelImage {
  @NotNull private static final Logger LOG = Logger.getInstance(FetchedImage.class);

  @NotNull private final Dimension dimensions;
  @NotNull private final BufferedImage image;

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
          // TODO: Display mip-level, etc.
          CubemapLevel level = ((Cubemap)object).getLevels()[0];
          return load(client, level.getNegativeX(), level.getPositiveX(), level.getNegativeY(), level.getPositiveY(), level.getNegativeZ(),
                      level.getPositiveZ());
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

  private static ListenableFuture<FetchedImage> load(ServiceClient client, final ImageInfo... imageInfos) {
    ListenableFuture<byte[]>[] futures = new ListenableFuture[imageInfos.length];
    for (int i = 0; i < imageInfos.length; i++) {
      futures[i] = client.get(imageInfos[i].getData());
    }
    return Futures.transform(Futures.allAsList(futures), new Function<List<byte[]>, FetchedImage>() {
      @Override
      public FetchedImage apply(List<byte[]> data) {
        return new FetchedImage(imageInfos, data.toArray(new byte[data.size()][]));
      }
    });
  }

  public FetchedImage(@NotNull ImageInfo imageInfo, @NotNull byte[] data) {
    assert (imageInfo.getFormat() instanceof FmtRGBA);
    dimensions = new Dimension(imageInfo.getWidth(), imageInfo.getHeight());
    //noinspection UndesirableClassUsage
    image = new BufferedImage(dimensions.width, dimensions.height, BufferedImage.TYPE_4BYTE_ABGR);

    updateImageData(data, 0, 0, imageInfo.getWidth(), imageInfo.getHeight());
  }

  public FetchedImage(@NotNull ImageInfo[] imageInfos, @NotNull byte[][] data) {
    assert (imageInfos.length == data.length && imageInfos.length == 6);
    // Typically these are all the same, but let's be safe.
    int width = Math.max(Math.max(
      Math.max(Math.max(Math.max(imageInfos[0].getWidth(), imageInfos[1].getWidth()), imageInfos[2].getWidth()), imageInfos[3].getWidth()),
      imageInfos[4].getWidth()), imageInfos[5].getWidth());
    int height = Math.max(Math.max(
      Math.max(Math.max(Math.max(imageInfos[0].getHeight(), imageInfos[1].getHeight()), imageInfos[2].getHeight()),
      imageInfos[3].getHeight()), imageInfos[4].getHeight()), imageInfos[5].getHeight());
    dimensions = new Dimension(4 * width, 3 * height);
    //noinspection UndesirableClassUsage
    image = new BufferedImage(dimensions.width, dimensions.height, BufferedImage.TYPE_4BYTE_ABGR);

    // +----+----+----+----+
    // |    | -Y |    |    |
    // +----+----+----+----+
    // | -X | +Z | +X | -Z |
    // +----+----+----+----+
    // |    | +Y |    |    |
    // +----+----+----+----+
    updateImageData(data[0], 0 * width, 1 * height, imageInfos[0].getWidth(), imageInfos[0].getHeight()); // Negative X
    updateImageData(data[1], 2 * width, 1 * height, imageInfos[1].getWidth(), imageInfos[1].getHeight()); // Positive X
    updateImageData(data[2], 1 * width, 0 * height, imageInfos[2].getWidth(), imageInfos[2].getHeight()); // Negative Y
    updateImageData(data[3], 1 * width, 2 * height, imageInfos[3].getWidth(), imageInfos[3].getHeight()); // Positive Y
    updateImageData(data[4], 3 * width, 1 * height, imageInfos[4].getWidth(), imageInfos[4].getHeight()); // Negative Z
    updateImageData(data[5], 1 * width, 1 * height, imageInfos[5].getWidth(), imageInfos[5].getHeight()); // Positive Z
  }

  private void updateImageData(byte[] data, int x, int y, int w, int h) {
    assert (x + w <= dimensions.width && y + h <= dimensions.height);
    byte[] destination = ((DataBufferByte)image.getRaster().getDataBuffer()).getData();
    // Convert between top-left and bottom-left formats.
    for (int row = 0, from = 0, to = ((y + h - 1) * dimensions.width + x) * 4; row < h; row++, from += w * 4, to -= dimensions.width * 4) {
      for (int col = 0, i = to, j = from; col < w; col++, i += 4, j += 4) {
        destination[i + 0] = data[j + 3];
        destination[i + 1] = data[j + 2];
        destination[i + 2] = data[j + 1];
        destination[i + 3] = data[j + 0];
      }
    }
  }

  @Override
  public int getLevelCount() {
    return 1;
  }

  @Override
  public ListenableFuture<BufferedImage> getLevel(int index) {
    return (index == 0) ? Futures.immediateFuture(image) :
           Futures.<BufferedImage>immediateFailedFuture(new IllegalArgumentException("Invalid image level"));
  }

  public static ListenableFuture<BufferedImage> loadLevel(ListenableFuture<FetchedImage> futureImage, final int level) {
    return Futures.transform(futureImage, new AsyncFunction<FetchedImage, BufferedImage>() {
      @Override
      public ListenableFuture<BufferedImage> apply(FetchedImage image) throws Exception {
        return image.getLevel(Math.min(level, image.getLevelCount()));
      }
    });
  }
}
