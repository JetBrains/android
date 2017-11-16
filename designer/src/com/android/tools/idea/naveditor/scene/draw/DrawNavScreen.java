/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.scene.draw;

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DrawCommand;
import com.android.tools.idea.rendering.ImagePool;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.android.tools.idea.naveditor.scene.NavDrawHelperKt.DRAW_NAV_SCREEN_LEVEL;

/**
 * {@link DrawCommand} that draws a screen in the navigation editor.
 */
public class DrawNavScreen extends NavBaseDrawCommand {
  @SwingCoordinate private final int myX;
  @SwingCoordinate private final int myY;
  @SwingCoordinate private final int myWidth;
  @SwingCoordinate private final int myHeight;
  @NotNull private final ImagePool.Image myImage;

  public DrawNavScreen(@SwingCoordinate int x, @SwingCoordinate int y, @SwingCoordinate int width, @SwingCoordinate int height,
                       @NotNull ImagePool.Image image) {
    myX = x;
    myY = y;
    myWidth = width;
    myHeight = height;
    myImage = image;
  }

  @Override
  public int getLevel() {
    return DRAW_NAV_SCREEN_LEVEL;
  }

  @Override
  protected void onPaint(@NotNull Graphics2D g, @NotNull SceneContext sceneContext) {
    setRenderingHints(g);
    g.clipRect(myX, myY, myWidth, myHeight);
    // TODO: better scaling (similar to ScreenViewLayer)
    myImage.drawImageTo(g, myX, myY, myWidth, myHeight);
  }

  @Override
  @NotNull
  protected Object[] getProperties() {
    return new Object[]{myX, myY, myWidth, myHeight};
  }
}
