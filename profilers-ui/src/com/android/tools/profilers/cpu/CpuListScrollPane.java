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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.common.AdtUiUtils;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

/**
 * Special {@link JScrollPane} used in CPU profiler panels that have a {@link JList} as the main viewport.
 * It needs to make sure the vertical scrollbar overlaps the viewport instead of shifting it to the left.
 */
class CpuListScrollPane extends JBScrollPane {

  CpuListScrollPane(@NotNull JList viewportView, @NotNull JComponent dispatchComponent) {
    super();
    getVerticalScrollBar().setOpaque(false);
    setBorder(JBUI.Borders.empty());
    setViewportView(viewportView);
    addMouseWheelListener(new CpuMouseWheelListener(dispatchComponent));
  }

  @Override
  protected JViewport createViewport() {
    if (SystemInfoRt.isMac) {
      return super.createViewport();
    }
    // Overrides it because, when not on mac, JBViewport adds the width of the scrollbar to the right inset of the border,
    // which would consequently misplace the threads state chart.
    return new JViewport();
  }

  /**
   * Class to help dispatch mouse events that would otherwise be consumed by the {@link JScrollPane}.
   * Refer to implementation in {@link javax.swing.plaf.basic.BasicScrollPaneUI.Handler#mouseWheelMoved}
   * Note: We cannot override the {@link JScrollPane#processMouseEvent} method as dispatching an event
   * to the view will result in a loop since our controls do not consume events.
   */
  private static class CpuMouseWheelListener implements MouseWheelListener {
    @NotNull
    private final JComponent myDispatchComponent;

    public CpuMouseWheelListener(@NotNull JComponent dispatchComponent) {
      myDispatchComponent = dispatchComponent;
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
      // If we have the modifier keys down then pass the event on to the parent control. Otherwise
      // the JScrollPane will consume the event.
      boolean isMenuKeyDown = AdtUiUtils.isActionKeyDown(e);
      // The shift key modifier is used when making the determination if we are panning vs scrolling vertically when the mouse
      // wheel is triggered.
      boolean isShiftKeyDown = e.isShiftDown();
      if (isMenuKeyDown || isShiftKeyDown) {
        myDispatchComponent.dispatchEvent(e);
      }
    }
  }
}
