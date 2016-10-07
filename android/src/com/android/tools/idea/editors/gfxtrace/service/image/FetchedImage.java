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
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.List;

public class FetchedImage implements MultiLevelImage {
  @NotNull private final Level[] myLevels;

  public static ListenableFuture<FetchedImage> load(final ServiceClient client, ListenableFuture<ImageInfoPath> imageInfo) {
    return Futures.transform(imageInfo, new AsyncFunction<ImageInfoPath, FetchedImage>() {
      @Override
      public ListenableFuture<FetchedImage> apply(ImageInfoPath imageInfoPath) throws Exception {
        return load(client, imageInfoPath);
      }
    });
  }

  public static ListenableFuture<FetchedImage> load(final ServiceClient client, final Path imagePath) {
    return Futures.transform(client.get(imagePath.as(Format.RGBA)), new Function<Object, FetchedImage>() {
      @Override
      public FetchedImage apply(Object object) {
        if (object instanceof ImageInfo) {
          return new FetchedImage(client, (ImageInfo)object);
        }
        if (object instanceof Texture2D) {
          return new FetchedImage(client, (Texture2D)object);
        }
        if (object instanceof Cubemap) {
          return new FetchedImage(client, (Cubemap)object);
        }
        throw new UnsupportedOperationException("Unexpected resource type " + object.toString());
      }
    });
  }

  public FetchedImage(ServiceClient client, ImageInfo imageInfo) {
    myLevels = new Level[] { new SingleFacedLevel(client, imageInfo) };
  }

  public FetchedImage(ServiceClient client, Texture2D texture) {
    ImageInfo[] infos = texture.getLevels();
    myLevels = new Level[infos.length];
    for (int i = 0; i < infos.length; i++) {
      myLevels[i] = new SingleFacedLevel(client, infos[i]);
    }
  }

  public FetchedImage(ServiceClient client, Cubemap cubemap) {
    CubemapLevel[] infos = cubemap.getLevels();
    myLevels = new Level[infos.length];
    for (int i = 0; i < infos.length; i++) {
      myLevels[i] = new SixFacedLevel(client, infos[i]);
    }
  }

  @Override
  public int getLevelCount() {
    return myLevels.length;
  }

  @Override
  public ListenableFuture<BufferedImage> getLevel(int index) {
    return (index < 0 || index >= myLevels.length) ?
           Futures.<BufferedImage>immediateFailedFuture(new IllegalArgumentException("Invalid image level")) : myLevels[index].get();
  }

  public static ListenableFuture<BufferedImage> loadLevel(ListenableFuture<FetchedImage> futureImage, final int level) {
    return Futures.transform(futureImage, new AsyncFunction<FetchedImage, BufferedImage>() {
      @Override
      public ListenableFuture<BufferedImage> apply(FetchedImage image) throws Exception {
        return image.getLevel(Math.min(level, image.getLevelCount()));
      }
    });
  }

  private abstract static class Level implements Function<BufferedImage, BufferedImage> {
    public static final Level EMPTY = new Level() {
      @Override
      public ListenableFuture<BufferedImage> get() {
        return Futures.immediateFuture(EMPTY_LEVEL);
      }

      @Override
      protected ListenableFuture<BufferedImage> doLoad() {
        return null;
      }
    };

    private BufferedImage image;

    public ListenableFuture<BufferedImage> get() {
      BufferedImage result;
      synchronized (this) {
        result = image;
      }
      return (result == null) ? Futures.transform(doLoad(), this) : Futures.immediateFuture(image);
    }

    @Override
    public BufferedImage apply(BufferedImage input) {
      synchronized (this) {
        image = input;
      }
      return input;
    }

    protected abstract ListenableFuture<BufferedImage> doLoad();

    protected static BufferedImage convertImage(@NotNull ImageInfo imageInfo, @NotNull byte[] data) {
      assert (imageInfo.getFormat() instanceof FmtRGBA);
      //noinspection UndesirableClassUsage
      BufferedImage image = new BufferedImage(imageInfo.getWidth(), imageInfo.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
      updateImageData(image, data, 0, 0, imageInfo.getWidth(), imageInfo.getHeight());
      return image;
    }

    protected static BufferedImage convertImage(@NotNull ImageInfo[] imageInfos, @NotNull byte[][] data) {
      assert (imageInfos.length == data.length && imageInfos.length == 6);
      // Typically these are all the same, but let's be safe.
      int width = Math.max(Math.max(
        Math.max(Math.max(Math.max(imageInfos[0].getWidth(), imageInfos[1].getWidth()), imageInfos[2].getWidth()),
                 imageInfos[3].getWidth()), imageInfos[4].getWidth()), imageInfos[5].getWidth());
      int height = Math.max(Math.max(
        Math.max(Math.max(Math.max(imageInfos[0].getHeight(), imageInfos[1].getHeight()), imageInfos[2].getHeight()),
                 imageInfos[3].getHeight()), imageInfos[4].getHeight()), imageInfos[5].getHeight());
      //noinspection UndesirableClassUsage
      BufferedImage image = new BufferedImage(4 * width, 3 * height, BufferedImage.TYPE_4BYTE_ABGR);

      // +----+----+----+----+
      // |    | -Y |    |    |
      // +----+----+----+----+
      // | -X | +Z | +X | -Z |
      // +----+----+----+----+
      // |    | +Y |    |    |
      // +----+----+----+----+
      updateImageData(image, data[0], 0 * width, 1 * height, imageInfos[0].getWidth(), imageInfos[0].getHeight()); // Negative X
      updateImageData(image, data[1], 2 * width, 1 * height, imageInfos[1].getWidth(), imageInfos[1].getHeight()); // Positive X
      updateImageData(image, data[2], 1 * width, 0 * height, imageInfos[2].getWidth(), imageInfos[2].getHeight()); // Negative Y
      updateImageData(image, data[3], 1 * width, 2 * height, imageInfos[3].getWidth(), imageInfos[3].getHeight()); // Positive Y
      updateImageData(image, data[4], 3 * width, 1 * height, imageInfos[4].getWidth(), imageInfos[4].getHeight()); // Negative Z
      updateImageData(image, data[5], 1 * width, 1 * height, imageInfos[5].getWidth(), imageInfos[5].getHeight()); // Positive Z
      return image;
    }

    private static void updateImageData(BufferedImage image, byte[] data, int x, int y, int w, int h) {
      assert (x + w <= image.getWidth() && y + h <= image.getHeight());
      byte[] destination = ((DataBufferByte)image.getRaster().getDataBuffer()).getData();
      int imageWidth = image.getWidth();
      // Convert between top-left and bottom-left formats.
      for (int row = 0, from = 0, to = ((y + h - 1) * imageWidth + x) * 4; row < h; row++, from += w * 4, to -= imageWidth * 4) {
        for (int col = 0, i = to, j = from; col < w; col++, i += 4, j += 4) {
          destination[i + 0] = data[j + 3];
          destination[i + 1] = data[j + 2];
          destination[i + 2] = data[j + 1];
          destination[i + 3] = data[j + 0];
        }
      }
    }
  }

  private static class SingleFacedLevel extends Level {
    private final ServiceClient client;
    private final ImageInfo imageInfo;

    public SingleFacedLevel(ServiceClient client, ImageInfo imageInfo) {
      this.client = client;
      this.imageInfo = imageInfo;
    }

    @Override
    protected ListenableFuture<BufferedImage> doLoad() {
      return Futures.transform(client.get(imageInfo.getData()), new Function<byte[], BufferedImage>() {
        @Override
        public BufferedImage apply(byte[] data) {
          return convertImage(imageInfo, data);
        }
      });
    }
  }

  private static class SixFacedLevel extends Level {
    private final ServiceClient client;
    private final ImageInfo[] imageInfos;

    public SixFacedLevel(ServiceClient client, CubemapLevel level) {
      this.client = client;
      this.imageInfos = new ImageInfo[] {
        level.getNegativeX(), level.getPositiveX(), level.getNegativeY(), level.getPositiveY(), level.getNegativeZ(), level.getPositiveZ()
      };
    }

    @Override
    protected ListenableFuture<BufferedImage> doLoad() {
      ListenableFuture<byte[]>[] futures = new ListenableFuture[imageInfos.length];
      for (int i = 0; i < imageInfos.length; i++) {
        futures[i] = client.get(imageInfos[i].getData());
      }
      return Futures.transform(Futures.allAsList(futures), new Function<List<byte[]>, BufferedImage>() {
        @Override
        public BufferedImage apply(List<byte[]> data) {
          return convertImage(imageInfos, data.toArray(new byte[data.size()][]));
        }
      });
    }
  }
}
