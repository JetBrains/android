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
package com.android.tools.adtui.swing.laf;

import com.android.tools.adtui.swing.FakeKeyboard;
import com.android.tools.adtui.swing.FakeMouse;

import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.basic.BasicListUI;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/**
 * A stubbed {@link BasicListUI} for use in headless unit tests, where some functionality is
 * removed to avoid making calls that would otherwise throw a {@link HeadlessException}. This will
 * allow you to interact with {@link List} components using {@link FakeMouse} and
 * {@link FakeKeyboard}.
 *
 * To use, you must remember to call {@code someList.setUI(new HeadlessListUI()} before calling
 * methods like {@link FakeMouse#click(int, int)} or {@link FakeKeyboard#press(FakeKeyboard.Key)}.
 *
 * NOTE: Changing the UI of a component can subtly change its behavior! This class may need to be
 * updated in the future to add more functionality, so it more closely matches its parent class.
 */
public class HeadlessListUI extends BasicListUI {
  private final MouseListener myMouseListener = new HeadlessMouseListener();

  @Override
  protected void installListeners() {
    list.addMouseListener(myMouseListener);
  }

  @Override
  protected void uninstallListeners() {
    list.removeMouseListener(myMouseListener);
  }

  /**
   * A minimal mouse listener, which only does a subset of what {@link BasicListUI}'s mouse handler
   * does. This allows it to avoid calling
   * {@link BasicGraphicsUtils#isMenuShortcutKeyDown(InputEvent)}, which fails in headless mode.
   */
  private final class HeadlessMouseListener extends MouseAdapter {
    @Override
    public void mousePressed(MouseEvent e) {
      list.requestFocus();
      int row = list.locationToIndex(e.getPoint());
      if (row < 0) {
        list.clearSelection();
      }
      else {
        list.setSelectionInterval(row, row);
      }
    }
  }
}
