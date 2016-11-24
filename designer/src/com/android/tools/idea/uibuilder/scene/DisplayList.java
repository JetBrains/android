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

import java.awt.*;
import java.util.ArrayList;

import static com.android.tools.idea.uibuilder.model.Coordinates.*;
import static com.android.tools.idea.uibuilder.model.Coordinates.dpToPx;
import static com.android.tools.idea.uibuilder.model.Coordinates.getSwingDimension;

/**
 * DisplayList implementation for Scene
 */
public class DisplayList {
  private ArrayList<DrawCommand> myCommands = new ArrayList<DrawCommand>();

  /**
   * Paint interface for draw commands
   */
  interface DrawCommand {
    void paint(Graphics2D g, ScreenView screenView);
  }

  public void clear() {
    myCommands.clear();
  }

  public ArrayList<DrawCommand> getCommands() { return myCommands; }

  /////////////////////////////////////////////////////////////////////////////
  // Drawing Elements
  /////////////////////////////////////////////////////////////////////////////

  class Connection implements DrawCommand {

    Color color;
    int x1;
    int y1;
    int x2;
    int y2;

    public Connection(int x1, int y1, int x2, int y2, Color color) {
      this.color = color;
      this.x1 = x1;
      this.y1 = y1;
      this.x2 = x2;
      this.y2 = y2;
    }

    @Override
    public void paint(Graphics2D g, ScreenView screenView) {
      g.setColor(color);
      int sx1 = getSwingX(screenView, dpToPx(screenView, x1));
      int sy1 = getSwingY(screenView, dpToPx(screenView, y1));
      int sx2 = getSwingX(screenView, dpToPx(screenView, x2));
      int sy2 = getSwingY(screenView, dpToPx(screenView, y2));
      g.drawLine(sx1, sy1, sx2, sy2);
    }
  }

  class Rect implements DrawCommand {
    Color color;
    int l;
    int t;
    int r;
    int b;

    public Rect(int left, int top, int right, int bottom, Color c) {
      color = c;
      l = left;
      t = top;
      r = right;
      b = bottom;
    }

    @Override
    public void paint(Graphics2D g, ScreenView screenView) {
      g.setColor(color);
      int x = getSwingX(screenView, dpToPx(screenView, l));
      int y = getSwingY(screenView, dpToPx(screenView, t));
      int w = getSwingDimension(screenView, dpToPx(screenView, r - l));
      int h = getSwingDimension(screenView, dpToPx(screenView, b - t));
      g.drawRect(x, y, w, h);
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // Public methods to add elements to the display list
  /////////////////////////////////////////////////////////////////////////////

  public void addRect(int left, int top, int right, int bottom, Color color) {
    myCommands.add(new Rect(left, top, right, bottom, color));
  }
  public void addConnection(int x1, int y1, int x2, int y2, Color color) {
    myCommands.add(new Connection(x1, y1, x2, y2, color));
  }

  /////////////////////////////////////////////////////////////////////////////
  // Painting
  /////////////////////////////////////////////////////////////////////////////

  public void paint(Graphics2D g2, ScreenView screenView) {
    Graphics2D g = (Graphics2D) g2.create();
    int count = myCommands.size();
    for (int i = 0; i < count; i++) {
      DrawCommand command = myCommands.get(i);
      command.paint(g, screenView);
    }
    g.dispose();
  }
}
