/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.adtui.stdui;

import com.android.tools.adtui.TooltipComponent;
import java.awt.Component;
import java.awt.Rectangle;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import org.jetbrains.annotations.NotNull;

/**
 * This class is meant to be used as a Swing wrapper for the contents of a tool window.
 * This class is also needed by {@link TooltipComponent}, so that it can add itself to this container.
 * By doing so, {@link TooltipComponent} will have the same bounds as {@link TooltipLayeredPane}.
 */
public class TooltipLayeredPane extends JLayeredPane {

  /**
   *
   * @param content The one and only non-tooltip {@link JComponent} this layered pane shall ever have.
   */
  public TooltipLayeredPane(@NotNull JComponent content) {
    add(content, JLayeredPane.DEFAULT_LAYER);
  }

  @Override
  public void setBounds(int x, int y, int width, int height) {
    Rectangle oldBounds = getBounds();

    super.setBounds(x, y, width, height);

    if (x == oldBounds.x && y == oldBounds.y && width == oldBounds.width && height == oldBounds.height) {
      return;
    }

    for (int i = 0; i < getComponentCount(); i++) {
      Component component = getComponent(i);
      component.setBounds(0, 0, width, height);
    }
    revalidate();
    repaint();
  }
}
