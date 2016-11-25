/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene;

import com.android.tools.idea.uibuilder.surface.ScreenView;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.android.tools.idea.uibuilder.model.Coordinates.*;

/**
 * Display a layout Scene
 */
public class Display {
  private long mTime;
  private DisplayList myDisplayList = new DisplayList();

  public void draw(@NotNull ScreenView screenView, @NotNull Graphics2D g, @NotNull Scene scene) {
    mTime = System.currentTimeMillis();
    myDisplayList.clear();
    boolean needsRepaint = scene.paint(myDisplayList, mTime);
    draw(screenView, g, myDisplayList);
    if (needsRepaint) {
      screenView.getSurface().repaint();
    }
  }

  public void draw(@NotNull ScreenView screenView, @NotNull Graphics2D g, @NotNull DisplayList list) {
    list.paint(g, screenView);
  }

  public void draw(@NotNull ScreenView screenView, @NotNull Graphics2D g, @NotNull SceneComponent component) {
    // TODO: use decorators
    g.setColor(Color.red);
    int x = getSwingX(screenView, dpToPx(screenView, component.getDrawX(mTime)));
    int y = getSwingY(screenView, dpToPx(screenView, component.getDrawY(mTime)));
    int w = getSwingDimension(screenView, dpToPx(screenView, component.getDrawWidth(mTime)));
    int h = getSwingDimension(screenView, dpToPx(screenView, component.getDrawHeight(mTime)));
    g.drawRect(x, y, w, h);
    int count = component.getChildCount();
    for (int i = 0; i < count; i++) {
      SceneComponent child = component.getChild(i);
      draw(screenView, g, child);
    }
  }
}
