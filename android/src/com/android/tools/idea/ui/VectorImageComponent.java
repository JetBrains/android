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
package com.android.tools.idea.ui;

import com.android.ide.common.util.AssetUtil;
import com.android.tools.swing.util.GraphicsUtil;
import com.intellij.ui.Gray;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * VectorImageComponent is a Swing component that displays an image.
 * Particularly added a 3D boundary and center the image in the middle.
 */
public class VectorImageComponent extends ImageComponent {
  private Rectangle myRectangle = new Rectangle();
  private static final int CELL_SIZE = 8;

  @Override
  protected void paintChildren(@NotNull Graphics g) {
    Graphics2D g2d = (Graphics2D) g;

    // Draw the chess board background all the time.
    myRectangle.setBounds(0, 0, getWidth(), getHeight());
    GraphicsUtil.paintCheckeredBackground(g, Gray.xAA, Gray.xEE, myRectangle, CELL_SIZE);

    // Then draw the icon to the center.
    if (myImage == null) return;

    g.draw3DRect(0, 0, getWidth() - 1, getHeight() - 1, false);

    Rectangle rect = new Rectangle(0, 0, getWidth(), getHeight());
    AssetUtil.drawCenterInside(g2d, myImage, rect);
  }
}
