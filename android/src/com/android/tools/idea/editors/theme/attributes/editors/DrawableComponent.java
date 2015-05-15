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
package com.android.tools.idea.editors.theme.attributes.editors;

import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.swing.util.GraphicsUtil;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Icon;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class is used to draw drawable resources in theme editor cell.
 * Used in {@link DrawableEditor} and {@link DrawableRenderer}.
 */
public class DrawableComponent extends ResourceComponent {

  private final List<BufferedImage> myImages = new ArrayList<BufferedImage>();
  private ImageIcon myIcon = new ImageIcon();

  @Override
  int getIconCount() {
    return myImages.size();
  }

  @Override
  Icon getIconAt(int i) {
    myIcon.setImage(myImages.get(i));
    return myIcon;
  }

  @Override
  void setIconHeight(int height) {
    myIcon.setHeight(height);
  }

  /**
   * Populate text fields shown in a cell from EditedStyleItem value
   */
  public void configure(final @NotNull EditedStyleItem item, final @Nullable RenderTask renderTask) {
    configure(ThemeEditorUtils.getDisplayHtml(item), "drawable", item.getValue());

    myImages.clear();
    if (renderTask != null) {
      myImages.addAll(renderTask.renderDrawableAllStates(item.getItemResourceValue()));
    }
  }

  static class ImageIcon extends javax.swing.ImageIcon {

    private int myHeight;

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Image image = getImage();
      GraphicsUtil.paintCheckeredBackground(g, new Rectangle(x, y, getIconWidth(), getIconHeight()));
      g.drawImage(image, x, y, getIconWidth(), getIconHeight(), c);
    }

    public void setHeight(int height) {
      myHeight = height;
    }

    @Override
    public int getIconHeight() {
      return myHeight;
    }

    @Override
    public int getIconWidth() {
      return myHeight * super.getIconWidth() / super.getIconHeight();
    }
  }
}
