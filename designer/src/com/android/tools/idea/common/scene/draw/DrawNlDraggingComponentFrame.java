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
package com.android.tools.idea.common.scene.draw;

import com.android.sdklib.AndroidDpCoordinate;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import org.jetbrains.annotations.NotNull;

/**
 * Draw the frame of a SceneComponent
 */
public class DrawNlDraggingComponentFrame extends DrawNlComponentFrame {

  public DrawNlDraggingComponentFrame(String s) {
    super(s);
  }

  public DrawNlDraggingComponentFrame(@AndroidDpCoordinate int x,
                                      @AndroidDpCoordinate int y,
                                      @AndroidDpCoordinate int width,
                                      @AndroidDpCoordinate int height,
                                      @NotNull SceneComponent.DrawState mode,
                                      int layoutWidth,
                                      int layoutHeight) {
    super(x, y, width, height, mode, layoutWidth, layoutHeight);
  }

  @Override
  public void paint(Graphics2D g, SceneContext sceneContext) {
    // This code is used to paint a component while it is being dragged.
    // Allow the component to reach outside of its clipping region.
    Shape previousClip = g.getClip();
    g.setClip(null);
    super.paint(g, sceneContext);
    g.setClip(previousClip);
  }

  public static void add(DisplayList list,
                         SceneContext sceneContext,
                         @AndroidDpCoordinate Rectangle rect,
                         @NotNull SceneComponent.DrawState mode,
                         int layout_width,
                         int layout_height) {
    int l = sceneContext.getSwingXDip(rect.x);
    int t = sceneContext.getSwingYDip(rect.y);
    int w = sceneContext.getSwingDimensionDip(rect.width);
    int h = sceneContext.getSwingDimensionDip(rect.height);
    list.add(new DrawNlDraggingComponentFrame(l, t, w, h, mode, layout_width, layout_height));
  }
}
