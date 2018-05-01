/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.adtui.ui;

import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.UIUtil;
import icons.StudioIcons;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public final class AdtUiCursors {
  private static Cursor makeCursor(String name, Icon icon) {
    if (GraphicsEnvironment.isHeadless()) {
      return Cursor.getDefaultCursor();
    }
    // Icons are loaded at 2x for retina displays. For cursors we don't want to use this double sized icon so we scale it down.
    float scaleFactor = UIUtil.isRetina() ? 0.5f : 1.0f;
    Icon scaledIcon = ((IconLoader.CachedImageIcon)icon).scale(scaleFactor);
    BufferedImage image = UIUtil.createImage(scaledIcon.getIconWidth(), scaledIcon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
    scaledIcon.paintIcon(new JPanel(), image.getGraphics(), 0, 0);
    // We offset the icon center from the upper left to the center for a more natural placement with existing cursors.
    return Toolkit.getDefaultToolkit().createCustomCursor(image, new Point(image.getWidth() / 2, image.getHeight() / 2), name);
  }

  public static final Cursor GRAB = makeCursor("GRAB", StudioIcons.Cursors.GRAB);
  public static final Cursor GRABBING = makeCursor("GRABBING", StudioIcons.Cursors.GRABBING);
}
