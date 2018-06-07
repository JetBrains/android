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
import com.android.tools.adtui.ImageComponent;
import com.android.tools.adtui.util.GraphicsUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.android.tools.idea.npw.assetstudio.wizard.CheckeredBackgroundPanel.DEFAULT_EVEN_CELL_COLOR;
import static com.android.tools.idea.npw.assetstudio.wizard.CheckeredBackgroundPanel.DEFAULT_ODD_CELL_COLOR;

/**
 * A Swing component that displays an image centered in the middle.
 */
public class VectorImageComponent extends ImageComponent {
  private Rectangle myRectangle = new Rectangle();
  private static final int CELL_SIZE = 8;

  @Override
  protected void paintChildren(@NotNull Graphics g) {
    // Draw the chess board background.
    myRectangle.setBounds(0, 0, getWidth(), getHeight());
    GraphicsUtil.paintCheckeredBackground(g, DEFAULT_ODD_CELL_COLOR, DEFAULT_EVEN_CELL_COLOR, myRectangle, CELL_SIZE);

    if (myImage != null) {
      // Draw the image in the center.
      AssetUtil.drawCenterInside((Graphics2D) g, myImage, myRectangle);
    }
  }
}
