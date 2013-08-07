/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;

public class GutterIconCache {
  private static final Logger LOG = Logger.getInstance("#" + GutterIconCache.class.getName());
  private static final int MAX_WIDTH = 16;
  private static final int MAX_HEIGHT = 16;
  private static final Icon NONE = AndroidIcons.Android; // placeholder

  private static final GutterIconCache ourInstance = new GutterIconCache();
  // TODO: Timestamps?
  private Map<String,Icon> myThumbnailCache = Maps.newHashMap();

  @NotNull
  public static GutterIconCache getInstance() {
    return ourInstance;
  }

  @Nullable
  public Icon getIcon(@NotNull String path) {
    Icon myIcon = myThumbnailCache.get(path);
    if (myIcon == null) {
      try {
        BufferedImage image = ImageIO.read(new File(path));
        if (image != null) {
          if (image.getWidth() > MAX_WIDTH || image.getHeight() > MAX_HEIGHT) {
            double scale = Math.min(MAX_WIDTH / (double)image.getWidth(), MAX_HEIGHT / (double)image.getHeight());
            if (image.getType() == BufferedImage.TYPE_BYTE_INDEXED) {
              // Indexed images look terrible if they are scaled directly; instead, paint into an ARGB blank image
              BufferedImage bg = UIUtil.createImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
              Graphics g = bg.getGraphics();
              g.setColor(new Color(255, 255, 255, 0));
              g.fillRect(0, 0, bg.getWidth(), bg.getHeight());
              UIUtil.drawImage(g, image, 0, 0, null);
              g.dispose();
              image = bg;
            }
            image = ImageUtils.scale(image, scale, scale);
          }
          myIcon = new ImageIcon(image);
        }
      }
      catch (IOException e) {
        LOG.error(String.format("Could not read icon image %1$s", path), e);
      }

      if (myIcon == null) {
        myIcon = NONE;
      }

      myThumbnailCache.put(path, myIcon);
    }

    return myIcon != NONE ? myIcon : null;
  }
}
