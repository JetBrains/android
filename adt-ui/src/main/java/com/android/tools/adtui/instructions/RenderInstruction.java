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
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * Base class for instructions used in updating state and rendering elements.
 *
 * To render, you can iterate through a list of instructions, draw an instruction using {@link #render(JComponent, Graphics2D, Rectangle)}
 * and then move the cursor to the next instruction using {@link #moveCursor(InstructionsRenderer, Point)}
 *
 * Also See {@link InstructionsRenderer#draw(JComponent, Graphics2D)} for a code sample.
 */
public abstract class RenderInstruction {
  @NotNull private static final Dimension EMPTY_SIZE = new Dimension();
  @NotNull protected static final Consumer<MouseEvent> EMPTY_CONSUMER = evt -> {
  };

  @NotNull private Consumer<MouseEvent> myMouseHandler = EMPTY_CONSUMER;

  @NotNull
  public Dimension getSize() {
    return EMPTY_SIZE;
  }

  public void setMouseHandler(@NotNull Consumer<MouseEvent> mouseHandler) {
    myMouseHandler = mouseHandler;
  }

  /**
   * Move {@code cursor} to the next point that should be used after this instruction has been rendered.
   */
  public void moveCursor(@NotNull InstructionsRenderer renderer, @NotNull Point cursor) {
    Dimension size = getSize();
    cursor.x += size.width;
  }

  public void render(@NotNull JComponent c, @NotNull Graphics2D g2d, @NotNull Rectangle bounds) {
  }

  /**
   * A helper method to retrieve the bounds that contain this instruction.
   */
  @NotNull
  public Rectangle getBounds(@NotNull InstructionsRenderer renderer, @NotNull Point cursor) {
    return new Rectangle(cursor.x, cursor.y, getSize().width, renderer.getRowHeight());
  }

  /**
   * @param evt the mouse event whose position is already converted into the instruction's coordinate space.
   */
  final void handleMouseEvent(@NotNull MouseEvent evt) {
    myMouseHandler.accept(evt);
  }
}
