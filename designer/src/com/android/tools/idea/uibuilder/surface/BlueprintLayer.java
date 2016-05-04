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
package com.android.tools.idea.uibuilder.surface;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Rectangle2D;

import static com.android.tools.idea.uibuilder.graphics.NlConstants.*;

public class BlueprintLayer extends Layer {
  private final ScreenView myScreenView;
  private Dimension myScreenViewSize = new Dimension();
  private Rectangle mySizeRectangle = new Rectangle();

  public BlueprintLayer(@NotNull ScreenView screenView) {
    myScreenView = screenView;
  }

  /**
   * Base paint method. Draw the blueprint background.
   * TODO: We might want to simplify the stack of layers and not keep this one.
   *
   * @param gc The Graphics object to draw into
   */
  @Override
  public void paint(@NotNull Graphics2D gc) {
    myScreenView.getSize(myScreenViewSize);

    mySizeRectangle.setBounds(myScreenView.getX(), myScreenView.getY(), myScreenViewSize.width, myScreenViewSize.height);
    Rectangle2D.intersect(mySizeRectangle, gc.getClipBounds(), mySizeRectangle);
    if (mySizeRectangle.isEmpty()) {
      return;
    }

    // Draw the background
    Graphics2D g = (Graphics2D) gc.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(BLUEPRINT_BG_COLOR);
    g.fillRect(mySizeRectangle.x, mySizeRectangle.y, mySizeRectangle.width, mySizeRectangle.height);
    g.dispose();
  }

}
