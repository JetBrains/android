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
package com.android.tools.adtui.event;

import java.awt.Component;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Delegates a target scroll pane's vertical wheel events to its parent scroll pane. This is useful
 * for when you have a component that scrolls but needs to be installed within another scroll
 * pane. Once installed, the vertical scroll of the target scroll pane will essentially be
 * disabled, allowing the parent scroll pane to handle the scrolling instead.
 *
 * This prevents a user's wheel events from being captured by the nested scroll pane, which is
 * desirable because nested scroll panes are often a UX anti-pattern.
 *
 * Use {@link #installOn(JScrollPane)} to instantiate and configure this class.
 */
public final class NestedScrollPaneMouseWheelListener implements MouseWheelListener {
  @NotNull private final JScrollPane myScrollPane;
  @Nullable private JScrollPane myParentScrollPane;

  /**
   * Install this class onto a target scroll pane. Note that, by doing so, it will automatically
   * set the scroll pane to have a "never scroll" vertical scroll policy.
   */
  public static void installOn(@NotNull JScrollPane scrollPane) {
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
    scrollPane.addMouseWheelListener(new NestedScrollPaneMouseWheelListener(scrollPane));
  }

  private NestedScrollPaneMouseWheelListener(@NotNull JScrollPane scrollPane) {
    myScrollPane = scrollPane;
  }

  @Override
  public void mouseWheelMoved(MouseWheelEvent e) {
    setParentScrollPane();
    if (myParentScrollPane == null) {
      myScrollPane.removeMouseWheelListener(this);
      return;
    }

    myParentScrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(myScrollPane, e, myParentScrollPane));
  }

  private void setParentScrollPane() {
    if (myParentScrollPane == null) {
      Component parent = myScrollPane.getParent();
      while (parent != null && !(parent instanceof JScrollPane)) {
        parent = parent.getParent();
      }
      myParentScrollPane = (JScrollPane) parent;
    }
  }
}
