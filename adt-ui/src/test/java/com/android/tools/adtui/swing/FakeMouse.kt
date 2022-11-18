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

import java.awt.Component;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A fake mouse device that can be used for clicking on / scrolling programmatically in tests.
 *
 * Do not instantiate directly - use {@link FakeUi#mouse} instead.
 */
public final class FakeMouse {

  private final FakeKeyboard myKeyboard;
  @NotNull
  private final FakeUi myUi;
  @Nullable Cursor myCursor;
  @Nullable Component myFocus;

  /**
   * Created by {@link FakeUi}.
   */
  FakeMouse(@NotNull FakeUi ui, FakeKeyboard keyboard) {
    myUi = ui;
    myKeyboard = keyboard;
  }

  /**
   * Returns the component underneath the mouse cursor, the one which will receive any dispatched
   * mouse events.
   *
   * Note: This value is potentially updated after every call to {@link #moveTo(int, int)}.
   */
  @Nullable public Component getFocus() {
    return myFocus;
  }

  /**
   * Begin holding down a mouse button. Can be dragged with {@link #dragTo(int, int)} and
   * eventually should be released by {@link #release()}
   */
  public void press(int x, int y, Button button) {
    press(x, y, button, 1);
  }

  private void press(int x, int y, Button button, int clickCount) {
    if (myCursor != null) {
      throw new IllegalStateException("Mouse already pressed. Call release before pressing again.");
    }
    dispatchMouseEvent(MouseEvent.MOUSE_PRESSED, x, y, button, clickCount, button == Button.RIGHT);
    myCursor = new Cursor(button, x, y);
  }

  public void dragTo(@NotNull Point point) {
    dragTo(point.x, point.y);
  }

  /**
   * Simulates dragging mouse pointer from the current position (determined by {@link #myCursor})
   * to the given point. In addition to a MOUSE_DRAGGED event, may generate MOUSE_ENTERED
   * and/or MOUSE_EXITED events, if the mouse pointer crosses component boundaries.
   */
  public void dragTo(int x, int y) {
    if (myCursor == null) {
      throw new IllegalStateException("Mouse not pressed. Call press before dragging.");
    }

    FakeUi.RelativePoint point = myUi.targetMouseEvent(x, y);
    Component target = point == null ? null : point.component;
    if (target != myFocus) {
      if (myFocus != null) {
        Point relative = myUi.toRelative(myFocus, x, y);
        FakeUi.RelativePoint relativePoint = new FakeUi.RelativePoint(myFocus, relative.x, relative.y);
        dispatchMouseEvent(relativePoint, MouseEvent.MOUSE_EXITED, myCursor.button.mask, 0, 1, false);
      }
      if (target != null) {
        dispatchMouseEvent(point, MouseEvent.MOUSE_ENTERED, myCursor.button.mask, 0, 1, false);
      }
    }
    if (target != null) {
      dispatchMouseEvent(MouseEvent.MOUSE_DRAGGED, x, y, myCursor.button, 1, false);
      myCursor = new Cursor(myCursor, x, y);
    }
    myFocus = target;
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

  public void moveTo(@NotNull Point point) {
    moveTo(point.x, point.y);
  }

  /**
   * Simulates moving mouse pointer from the current position (determined by {@link #myCursor})
   * to the given point. In addition to a MOUSE_DRAGGED event, may generate MOUSE_ENTERED
   * and/or MOUSE_EXITED events, if the mouse pointer crosses component boundaries.
   */
  public void moveTo(int x, int y) {
    FakeUi.RelativePoint point = myUi.targetMouseEvent(x, y);
    Component target = point == null ? null : point.component;
    if (target != myFocus) {
      if (myFocus != null) {
        Point converted = myUi.toRelative(myFocus, x, y);
        dispatchMouseEvent(new FakeUi.RelativePoint(myFocus, converted.x, converted.y), MouseEvent.MOUSE_EXITED, 0, 0, 1, false);
      }
      if (target != null) {
        dispatchMouseEvent(point, MouseEvent.MOUSE_ENTERED, 0, 0, 1, false);
      }
    }
    if (target != null) {
      dispatchMouseEvent(point, MouseEvent.MOUSE_MOVED, 0, 0, 1, false);
    }
    myFocus = target;
  }

  public void release() {
    if (myCursor == null) {
      throw new IllegalStateException("Mouse not pressed. Call press before releasing.");
    }
    dispatchMouseEvent(MouseEvent.MOUSE_RELEASED, myCursor.x, myCursor.y, myCursor.button, 1, false);
    myCursor = null;
  }

  /**
   * Convenience method which calls {@link #press(int, int, Button)} and
   * {@link #release()} in turn and ensures that a clicked event is fired.
   *
   * Unfortunately, it seems like it may take some time for the click event to propagate to its
   * target component. Therefore, you may need to call {@link SwingUtilities#invokeLater(Runnable)}
   * or {@link SwingUtilities#invokeAndWait(Runnable)} after calling this method, to ensure the
   * event is handled before you check a component's state.
   */
  public void click(int x, int y, Button button) {
    click(x, y, button, 1);
  }

  private void click(int x, int y, Button button, int clickCount) {
    if (myCursor != null) {
      throw new IllegalStateException("Mouse already pressed. Call release before clicking.");
    }
    moveTo(x, y);
    press(x, y, button, clickCount);
    Cursor cursor = myCursor;
    release();
    // PRESSED + RELEASED should additionally fire a CLICKED event
    dispatchMouseEvent(MouseEvent.MOUSE_CLICKED, cursor.x, cursor.y, cursor.button, clickCount, false);
  }

  /**
   * Convenience method which calls {@link #click(int, int)} twice in quick succession.
   *
   * Unfortunately, it seems like it may take some time for the click event to propagate to its
   * target component. Therefore, you may need to call {@link SwingUtilities#invokeLater(Runnable)}
   * or {@link SwingUtilities#invokeAndWait(Runnable)} after calling this method, to ensure the
   * event is handled before you check a component's state.
   */
  public void doubleClick(int x, int y, Button button) {
    if (myCursor != null) {
      throw new IllegalStateException("Mouse already pressed. Call release before double-clicking.");
    }
    click(x, y, button, 1);
    click(x, y, button, 2);
  }

  /**
   * Convenience method which calls {@link #press(int, int, Button)}, {@link #dragTo(int, int)},
   * and {@link #release()} in turn.
   */
  public void drag(int xStart, int yStart, int xDelta, int yDelta, Button button) {
    if (myCursor != null) {
      throw new IllegalStateException("Mouse already pressed. Call release before dragging.");
    }

    int xTo = xStart + xDelta;
    int yTo = yStart + yDelta;
    press(xStart, yStart, button);
    dragTo(xTo, yTo);
    release();
  }

  public void press(@NotNull Point point) {
    press(point.x, point.y, Button.LEFT);
  }

  public void press(int x, int y) {
    press(x, y, Button.LEFT);
  }

  public void click(int x, int y) {
    click(x, y, Button.LEFT);
  }

  public void rightClick(int x, int y) {
    click(x, y, Button.RIGHT, 1);
  }

  public void doubleClick(int x, int y) {
    doubleClick(x, y, Button.LEFT);
  }

  public void drag(int xStart, int yStart, int xDelta, int yDelta) {
    drag(xStart, yStart, xDelta, yDelta, Button.LEFT);
  }

  /**
   * Scroll the mouse unit {@code rotation} clicks. Negative values mean scroll up / away, and
   * positive values mean scroll down / towards.
   */
  public void wheel(int x, int y, int rotation) {
    dispatchMouseWheelEvent(x, y, rotation);
  }

  private void dispatchMouseEvent(int eventType, int x, int y, Button button, int clickCount, boolean popupTrigger) {
    FakeUi.RelativePoint point = myUi.targetMouseEvent(x, y);
    if (point != null) {
      // Rare, but can happen if, say, a release mouse event closes a component, and then we try to
      // fire a followup clicked event on it.
      dispatchMouseEvent(point, eventType, button.mask, button.code, clickCount, popupTrigger);
    }
  }

  /**
   * Helper method to dispatch a mouse event.
   *
   * @param point Point that supplies the component and coordinates to trigger mouse event.
   * @param eventType Type of mouse event to trigger.
   * @param modifiers Mouse modifier codes.
   * @param button Which mouse button to pass to the event.
   * @param clickCount The number of consecutive clicks passed in the event. This is not how many
   *     times to issue the event but how many times a click has occurred.
   * @param popupTrigger should this event trigger a popup menu
   */
  private void dispatchMouseEvent(
    @NotNull FakeUi.RelativePoint point,
    int eventType,
    int modifiers,
    int button,
    int clickCount,
    boolean popupTrigger
  ) {
    //noinspection MagicConstant (modifier code is valid, from FakeKeyboard class)
    MouseEvent event = new MouseEvent(point.component, eventType, TimeUnit.NANOSECONDS.toMillis(System.nanoTime()),
                                      myKeyboard.toModifiersCode() | modifiers,
                                      point.x, point.y, clickCount, popupTrigger, button);
    point.component.dispatchEvent(event);
  }

  private void dispatchMouseWheelEvent(int x, int y, int rotation) {
    FakeUi.RelativePoint point = myUi.targetMouseEvent(x, y);
    if (point == null) {
      return;
    }
    //noinspection MagicConstant (modifier code is valid, from FakeKeyboard class)
    MouseWheelEvent event = new MouseWheelEvent(point.component, MouseEvent.MOUSE_WHEEL, TimeUnit.NANOSECONDS.toMillis(System.nanoTime()),
                                                myKeyboard.toModifiersCode(), point.x, point.y, 0, false,
                                                MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, rotation);
    point.component.dispatchEvent(event);
  }

  public enum Button {
    LEFT(MouseEvent.BUTTON1, InputEvent.BUTTON1_DOWN_MASK),
    CTRL_LEFT(MouseEvent.BUTTON1, InputEvent.BUTTON1_DOWN_MASK | InputEvent.CTRL_DOWN_MASK),
    RIGHT(MouseEvent.BUTTON3, InputEvent.BUTTON3_DOWN_MASK),
    MIDDLE(MouseEvent.BUTTON2, InputEvent.BUTTON2_DOWN_MASK);

    final int code;
    final int mask;

    Button(int code, int mask) {
      this.code = code;
      this.mask = mask;
    }
  }

  private static final class Cursor {
    final Button button;
    final int x;
    final int y;

    public Cursor(@NotNull Button button, int x, int y) {
      this.button = button;
      this.x = x;
      this.y = y;
    }

    public Cursor(@NotNull Cursor prev, int x, int y) {
      this.button = prev.button;
      this.x = x;
      this.y = y;
    }
  }
}
