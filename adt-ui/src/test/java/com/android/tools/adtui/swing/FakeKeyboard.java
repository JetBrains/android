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

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * A fake keyboard device that can be used for holding down keys in tests.
 *
 * Do not instantiate directly - use {@link FakeUi#keyboard} instead.
 */
public final class FakeKeyboard {
  public static final Key MENU_KEY = SystemInfo.isMac ? Key.META : Key.CTRL;

  private final List<Key> myPressedKeys = new ArrayList<>();
  @Nullable private Component myFocus;

  /**
   * Set (or clear) the component that will receive the key events. Note that if the focus is
   * {@code null}, you can still press/release keys but no events will be dispatched.
   */
  public void setFocus(@Nullable Component focus) {
    myFocus = focus;
  }

  public boolean isPressed(Key key) {
    return myPressedKeys.contains(key);
  }

  /**
   * Begin holding down the specified key. You may release it later using {@link #release(Key)}.
   *
   * Unfortunately, it seems like it may take some time for the key event to propagate to its
   * target component. Therefore, you may need to call {@link SwingUtilities#invokeLater(Runnable)}
   * after calling this method, to ensure the key is handled before you check a component's state.
   */
  public void press(Key key) {
    if (isPressed(key)) {
      throw new IllegalStateException(String.format("Can't press key %s as it's already pressed.", KeyEvent.getKeyText(key.code)));
    }

    // Dispatch BEFORE adding the key to our list of pressed keys. If it is a modifier key, we
    // don't want it to included in "toModifiersCode" logic called by "dispatchKeyEvent".
    dispatchKeyEvent(KeyEvent.KEY_PRESSED, key);
    myPressedKeys.add(key);
  }

  public void release(Key key) {
    if (!isPressed(key)) {
      throw new IllegalStateException(String.format("Can't release key %s as it's not pressed.", KeyEvent.getKeyText(key.code)));
    }

    myPressedKeys.remove(key);
    // Dispatch AFTER adding the key to our list of pressed keys. If it is a modifier key, we
    // don't want it to included in "toModifiersCode" logic called by "dispatchKeyEvent".
    dispatchKeyEvent(KeyEvent.KEY_RELEASED, key);
  }

  public int toModifiersCode() {
    int modifiers = 0;
    if (myPressedKeys.contains(Key.ALT)) {
      modifiers |= InputEvent.ALT_DOWN_MASK;
    }
    if (myPressedKeys.contains(Key.CTRL)) {
      modifiers |= InputEvent.CTRL_DOWN_MASK;
    }
    if (myPressedKeys.contains(Key.ESC) || myPressedKeys.contains(Key.SHIFT)) {
      modifiers |= InputEvent.SHIFT_DOWN_MASK;
    }
    if (myPressedKeys.contains(Key.META)) {
      modifiers |= InputEvent.META_DOWN_MASK;
    }
    return modifiers;
  }

  private void dispatchKeyEvent(int eventType, Key key) {
    if (myFocus == null) {
      return;
    }

    //noinspection MagicConstant toModifiersCode returns correct magic number type
    KeyEvent event = new KeyEvent(myFocus, eventType, System.nanoTime(), toModifiersCode(), key.code, (char)key.code);

    // If you use myFocus.dispatchEvent(), the event goes through a flow which gives other systems
    // a chance to handle it first. The following approach bypasses the event queue and sends the
    // event to listeners, directly.
    KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(myFocus, event);
  }

  public enum Key {
    ALT(KeyEvent.VK_ALT),
    CTRL(KeyEvent.VK_CONTROL),
    ENTER(KeyEvent.VK_ENTER),
    ESC(KeyEvent.VK_ESCAPE),
    LEFT(KeyEvent.VK_LEFT),
    META(KeyEvent.VK_META),
    RIGHT(KeyEvent.VK_RIGHT),
    SHIFT(KeyEvent.VK_SHIFT);
    // Add more keys here (alphabetically) as necessary

    final int code;

    Key(int code) {
      this.code = code;
    }
  }

}
