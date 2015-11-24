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

import com.android.assetstudiolib.*;
import com.android.ide.common.util.AssetUtil;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.rendering.ImageUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Maps;
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
  private static Map<AndroidIconType, GraphicGenerator> ourGraphicGenerators = Maps.newHashMap();

  static {
    ourGraphicGenerators.put(AndroidIconType.ACTIONBAR, new ActionBarIconGenerator());
    ourGraphicGenerators.put(AndroidIconType.LAUNCHER, new LauncherIconGenerator());
    ourGraphicGenerators.put(AndroidIconType.NOTIFICATION, new NotificationIconGenerator());
  }

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

  public static Map<String, Map<String, BufferedImage>> newAssetMap() {
    return Maps.newHashMap();
  }

  @NotNull
  public static BufferedImage trim(@NotNull BufferedImage image) {
    BufferedImage cropped = ImageUtils.cropBlank(image, null, TYPE_INT_ARGB);
    return cropped != null ? cropped : image;
  }

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

    int side = Math.max(image.getWidth(), image.getHeight());
    int padding = (side * paddingPercent / 100) / 2;

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

  public void generateNotificationIconsIntoMap(@NotNull BufferedImage sourceImage,
                                               @NotNull Map<String, Map<String, BufferedImage>> assetMap,
                                               @NotNull String name) {
    GraphicGenerator notificationIconGenerator = ourGraphicGenerators.get(AndroidIconType.NOTIFICATION);

    // TODO: Pass in minSdk value into options and generate only what's needed?
    NotificationIconGenerator.NotificationOptions options = new NotificationIconGenerator.NotificationOptions();
    options.sourceImage = sourceImage;
    notificationIconGenerator.generate(null, assetMap, this, options, name);

    options.version = NotificationIconGenerator.Version.V11;
    notificationIconGenerator.generate(null, assetMap, this, options, name);
  }

  /**
   * Outputs final-rendered images to disk, into the target directory (usually the parent of the res/ folder).
   */
  public void generateIconsIntoPath(@NotNull File rootDir,
                                    @NotNull AndroidIconType iconType,
                                    @NotNull BaseAsset sourceAsset,
                                    @NotNull String name) {
    final Map<String, Map<String, BufferedImage>> assetMap = newAssetMap();
    switch (iconType) {
      case NOTIFICATION:
        generateNotificationIconsIntoMap(sourceAsset.toImage(), assetMap, name);
        break;
      case LAUNCHER:
      case ACTIONBAR:
      default:
        throw new UnsupportedOperationException();
    }

    for (Map<String, BufferedImage> density : assetMap.values()) {
      for (Map.Entry<String, BufferedImage> image : density.entrySet()) {
        File file = new File(rootDir, image.getKey());
        try {
          VirtualFile directory = VfsUtil.createDirectories(file.getParentFile().getAbsolutePath());
          VirtualFile imageFile = directory.findChild(file.getName());
          if (imageFile == null || !imageFile.exists()) {
            imageFile = directory.createChildData(this, file.getName());
          }
          OutputStream outputStream = imageFile.getOutputStream(this);
          try {
            ImageIO.write(image.getValue(), "PNG", outputStream);
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
}
