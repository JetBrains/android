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
package com.android.tools.idea.npw.template;

import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Guava {@link CacheLoader} which can convert a file path to an icon. This is used to help us
 * load standard 256x256 icons out of template files.
 */
final class IconLoader extends CacheLoader<File, Optional<Icon>> {

  public static LoadingCache<File, Optional<Icon>> createLoadingCache() {
    return CacheBuilder.newBuilder().build(new IconLoader());
  }

  private static Logger getLog() {
    return Logger.getInstance(IconLoader.class);
  }

  @Nullable
  @Override
  public Optional<Icon> load(@NotNull File iconPath) {
    try {
      if (iconPath.isFile()) {
        BufferedImage image = ImageIO.read(iconPath);
        if (image != null) {
          return Optional.of(new ImageIcon(image.getScaledInstance(256, 256, Image.SCALE_SMOOTH)));
        }
        else {
          getLog().warn("File " + iconPath.getAbsolutePath() + " exists but is not a valid image");
        }
      }
      else {
        getLog().warn("Image file " + iconPath.getAbsolutePath() + " was not found");
      }
    }
    catch (IOException e) {
      getLog().warn(e);
    }
    return Optional.absent();
  }
}
