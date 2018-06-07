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
package com.android.tools.adtui.instructions;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper object for calculating size and rendering a list of {@link RenderInstruction}.
 */
public final class InstructionsRenderer {

  public enum HorizontalAlignment {
    CENTER(SwingConstants.CENTER),
    LEFT(SwingConstants.LEFT),
    RIGHT(SwingConstants.RIGHT);

    private int myAlignment;

    HorizontalAlignment(int alignment) {
      myAlignment = alignment;
    }

    int getAlignment() {
      return myAlignment;
    }
  }

  private int myRowHeight = 0;
  private HorizontalAlignment myAlignment;
  @NotNull private final List<RenderInstruction> myInstructions;
  @NotNull private final Dimension mySize;
  @NotNull private final Map<Integer, Integer> myPerRowWidthMap = new HashMap<>();

  public InstructionsRenderer(@NotNull List<RenderInstruction> instructions, @NotNull HorizontalAlignment alignment) {
    myInstructions = instructions;
    myAlignment = alignment;

    // Pre-determine the row height. This is assumed to be equal across all rows.
    for (RenderInstruction instruction : instructions) {
      myRowHeight = Math.max(instruction.getSize().height, myRowHeight);
    }

    // Pre-determine the total render size and width of each row across all instructions.
    int width = 0;
    int height = 0;
    Point cursor = new Point();
    for (RenderInstruction instruction : instructions) {
      instruction.moveCursor(this, cursor);
      width = Math.max(cursor.x, width);
      height = Math.max(cursor.y + myRowHeight, height);

      if (!myPerRowWidthMap.containsKey(cursor.y) || myPerRowWidthMap.get(cursor.y) < cursor.x) {
        myPerRowWidthMap.put(cursor.y, cursor.x);
      }
    }
    mySize = new Dimension(width, height);
  }

  public int getRowHeight() {
    return myRowHeight;
  }

  @NotNull
  public Dimension getRenderSize() {
    return mySize;
  }

  @NotNull
  List<RenderInstruction> getInstructions() {
    return myInstructions;
  }

  /**
   * Retrieve the starting x position for a particular row as specified by its y value.
   */
  public int getStartX(int y) {
    if (!myPerRowWidthMap.containsKey(y)) {
      // If a row's width has not been calculated, simply use the leftmost point.
      return 0;
    }

    int rowWidth = myPerRowWidthMap.get(y);
    switch (myAlignment) {
      case CENTER:
        return (mySize.width - rowWidth) / 2;
      case RIGHT:
        return mySize.width - rowWidth;
      case LEFT:
      default:
        return 0;
    }
  }

  public void draw(@NotNull JComponent component, @NotNull Graphics2D g2d) {
    Point cursor = new Point(getStartX(0), 0);
    for (RenderInstruction instruction : myInstructions) {
      Rectangle bounds = instruction.getBounds(this, cursor);
      instruction.render(component, g2d, bounds);
      instruction.moveCursor(this, cursor);
    }
  }
}
