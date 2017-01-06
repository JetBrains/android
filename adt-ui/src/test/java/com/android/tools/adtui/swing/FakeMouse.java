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
package com.android.tools.adtui.swing;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

/**
 * A fake mouse device that can be used for clicking on / scrolling programmatically in tests.
 *
 * Do not instantiate directly - use {@link FakeInputDevices#mouse} instead.
 */
public final class FakeMouse {

  private final FakeKeyboard myKeyboard;
  @Nullable Cursor myCursor;

  /**
   * Created by {@link FakeInputDevices}.
   */
  FakeMouse(FakeKeyboard keyboard) {
    myKeyboard = keyboard;
  }

  /**
   * Reset this mouse's state.
   *
   * Called by {@link FakeInputDevices} so a user does not have to explicitly call it.
   */
  public void reset() {
    myCursor = null;
  }

  /**
   * Begin holding down a mouse button. Can be dragged with {@link #dragTo(int, int)} and
   * eventually should be released by {@link #release()}
   */
  public void press(JComponent target, int x, int y, Button button) {
    if (myCursor != null) {
      throw new IllegalStateException("Mouse already pressed. Call release before pressing again.");
    }
    dispatchMouseEvent(MouseEvent.MOUSE_PRESSED, target, x, y, button);
    myCursor = new Cursor(target, button, x, y);
  }

  public void dragTo(int x, int y) {
    if (myCursor == null) {
      throw new IllegalStateException("Mouse not pressed. Call press before dragging.");
    }

    dispatchMouseEvent(MouseEvent.MOUSE_DRAGGED, myCursor.target, x, y, myCursor.button);
    myCursor = new Cursor(myCursor, x, y);
  }

  /**
   * Like {@link #dragTo(int, int)} but with relative values.
   */
  public void dragDelta(int xDelta, int yDelta) {
    if (myCursor == null) {
      throw new IllegalStateException("Mouse not pressed. Call press before dragging.");
    }

    dragTo(myCursor.x + xDelta, myCursor.y + yDelta);
  }

  public void release() {
    if (myCursor == null) {
      throw new IllegalStateException("Mouse not pressed. Call press before releasing.");
    }
    dispatchMouseEvent(MouseEvent.MOUSE_RELEASED, myCursor.target, myCursor.x, myCursor.y, myCursor.button);
    myCursor = null;
  }

  /**
   * Convenience method which calls {@link #press(JComponent, int, int, Button)} and
   * {@link #release()} in turn.
   */
  public void click(JComponent target, int x, int y, Button button) {
    if (myCursor != null) {
      throw new IllegalStateException("Mouse already pressed. Call release before clicking.");
    }

    press(target, x, y, button);
    release();
  }

  /**
   * Convenience method which calls {@link #press(JComponent, int, int, Button)}, {@link #dragTo(int, int)},
   * and {@link #release()} in turn.
   */
  public void drag(JComponent target, int xStart, int yStart, int xDelta, int yDelta, Button button) {
    if (myCursor != null) {
      throw new IllegalStateException("Mouse already pressed. Call release before dragging.");
    }

    int xTo = xStart + xDelta;
    int yTo = yStart + yDelta;
    press(target, xStart, yStart, button);
    dragTo(xTo, yTo);
    release();
  }

  public void press(JComponent target, int x, int y) {
    press(target, x, y, Button.LEFT);
  }

  public void click(JComponent target, int x, int y) {
    click(target, x, y, Button.LEFT);
  }

  public void drag(JComponent target, int xStart, int yStart, int xDelta, int yDelta) {
    drag(target, xStart, yStart, xDelta, yDelta, Button.LEFT);
  }

  /**
   * Scroll the mouse unit {@code rotation} clicks. Negative values mean scroll up / away, and
   * positive values mean scroll down / towards.
   */
  public void wheel(JComponent target, int x, int y, int rotation) {
    dispatchMouseWheelEvent(target, x, y, rotation);
  }

  private void dispatchMouseEvent(int eventType, JComponent target, int x, int y, Button button) {
    //noinspection MagicConstant (modifier code is valid, from FakeKeyboard class)
    MouseEvent event = new MouseEvent(target, eventType, System.nanoTime(), myKeyboard.toModifiersCode(), x, y, 1, false, button.code);
    target.dispatchEvent(event);
  }

  private void dispatchMouseWheelEvent(JComponent target, int x, int y, int rotation) {
    //noinspection MagicConstant (modifier code is valid, from FakeKeyboard class)
    MouseWheelEvent event =
      new MouseWheelEvent(target, MouseEvent.MOUSE_WHEEL, System.nanoTime(), myKeyboard.toModifiersCode(), x, y, 0, false,
                          MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, rotation);
    target.dispatchEvent(event);
  }

  public enum Button {
    LEFT(MouseEvent.BUTTON1),
    RIGHT(MouseEvent.BUTTON3);

    final int code;

    Button(int code) {
      this.code = code;
    }
  }

  private static final class Cursor {
    final JComponent target;
    final Button button;
    final int x;
    final int y;

    public Cursor(JComponent target, Button button, int x, int y) {
      this.target = target;
      this.button = button;
      this.x = x;
      this.y = y;
    }

    public Cursor(Cursor prev, int x, int y) {
      this.target = prev.target;
      this.button = prev.button;
      this.x = x;
      this.y = y;
    }
  }
}
