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

package com.android.tools.idea.npw.assetstudio;

import com.android.assetstudiolib.GraphicGenerator;
import com.android.assetstudiolib.GraphicGeneratorContext;
import com.android.ide.common.util.AssetUtil;
import com.android.tools.idea.npw.assetstudio.icon.CategoryIconMap;
import com.android.tools.idea.rendering.ImageUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;

/**
 * To learn more about Android Asset Studio, visit the
 * <a href="https://romannurik.github.io/AndroidAssetStudio/">project page</a> and
 * <a href="http://tools.android.com/recent/assetstudiointegration">read here</a>. In short, it's a
 * web application that can generates a set of images in a format consumable by Android, ported
 * into Android Studio for convenience.
 */
public final class AssetStudioAssetGenerator implements GraphicGeneratorContext {

  private static Cache<String, BufferedImage> ourImageCache = CacheBuilder.newBuilder().build();

  private static Logger getLog() {
    return Logger.getInstance(AssetStudioAssetGenerator.class);
  }

  /**
   * Create a tiny dummy image, so that we can always return a {@link NotNull} result if an image
   * we were looking for isn't found.
   */
  @NotNull
  public static BufferedImage createDummyImage() {
    // IntelliJ wants us to use UiUtil.createImage, for retina desktop screens, but we
    // intentionally avoid that here, because we just want to make a small notnull image
    //noinspection UndesirableClassUsage
    return new BufferedImage(1, 1, TYPE_INT_ARGB);
  }

  @NotNull
  private static BufferedImage getStencilImage(@NotNull String path) throws IOException {
    BufferedImage image = GraphicGenerator.getStencilImage(path);
    if (image == null) {
      image = createDummyImage();
    }

    return image;
  }

  @NotNull
  public static Map<String, Map<String, BufferedImage>> newAssetMap() {
    return Maps.newHashMap();
  }

  /**
   * Remove any surrounding padding from the image.
   */
  @NotNull
  public static BufferedImage trim(@NotNull BufferedImage image) {
    BufferedImage cropped = ImageUtils.cropBlank(image, null, TYPE_INT_ARGB);
    return cropped != null ? cropped : image;
  }

  /**
   * Pad the image with extra space. The padding percent works by taking the largest side of the
   * current image, multiplying that with the percent value, and adding that portion to each side
   * of the image.
   *
   * So for example, an image that's 100x100, with 50% padding percent, ends up resized to
   * (50+100+50)x(50+100+50), or 200x200. The 100x100 portion is then centered, taking up what
   * looks like 50% of the final image. The same 100x100 image, with 100% padding, ends up at
   * 300x300, looking in the final image like it takes up ~33% of the space.
   *
   * Padding can also be negative, which eats into the space of the original asset, causing a zoom
   * in effect.
   */
  @NotNull
  public static BufferedImage pad(@NotNull BufferedImage image, int paddingPercent) {
    if (image.getWidth() <= 1 || image.getHeight() <= 1) {
      // If we're handling a dummy image, just abort now before AssetUtil.paddedImage throws an
      // exception.
      return image;
    }

    if (paddingPercent > 100) {
      paddingPercent = 100;
    }

    int largerSide = Math.max(image.getWidth(), image.getHeight());
    int smallerSide = Math.min(image.getWidth(), image.getHeight());
    int padding = (largerSide * paddingPercent / 100);

    // Don't let padding get so negative that it would totally wipe out one of the dimensions. And
    // since padding is added to all sides, negative padding should be at most half of the smallest
    // side. (e.g if the smaller side is 100px, min padding is -49px)
    padding = Math.max(-(smallerSide / 2 - 1), padding);

    return AssetUtil.paddedImage(image, padding);
  }

  @Override
  public BufferedImage loadImageResource(@NotNull final String path) {
    try {
      return ourImageCache.get(path, new Callable<BufferedImage>() {
        @Override
        public BufferedImage call() throws Exception {
          return getStencilImage(path);
        }
      });
    }
    catch (ExecutionException e) {
      getLog().error(e);
      return null;
    }
  }

  /**
   * Render images to disk. You likely want to use {@link CategoryIconMap#toFileMap(File)} to
   * generate the input for this method.
   *
   * This method must be called from within a WriteAction.
   */
  public void generateIconsIntoPath(@NotNull Map<File, BufferedImage> pathIconMap) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    for (Map.Entry<File, BufferedImage> fileImageEntry : pathIconMap.entrySet()) {
      File file = fileImageEntry.getKey();
      BufferedImage image = fileImageEntry.getValue();
      try {
        VirtualFile directory = VfsUtil.createDirectories(file.getParentFile().getAbsolutePath());
        VirtualFile imageFile = directory.findChild(file.getName());
        if (imageFile == null || !imageFile.exists()) {
          imageFile = directory.createChildData(this, file.getName());
        }
        OutputStream outputStream = imageFile.getOutputStream(this);
        try {
          ImageIO.write(image, "PNG", outputStream);
        }
        finally {
          outputStream.close();
        }
      }
      catch (IOException e) {
        getLog().error(e);
      }
    }
  }
}
