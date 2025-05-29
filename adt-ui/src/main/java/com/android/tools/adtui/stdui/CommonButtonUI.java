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
package com.android.tools.adtui.stdui;

import com.intellij.util.ui.JBUI;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicButtonUI;

class CommonButtonUI extends BasicButtonUI {

  private final MouseAdapter myAdapter;
  private boolean myHover;

  public CommonButtonUI() {
    myAdapter = new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        myHover = true;
        e.getComponent().repaint();
      }

      @Override
      public void mouseExited(MouseEvent e) {
        myHover = false;
        e.getComponent().repaint();
      }
    };
  }

  @Override
  protected void installDefaults(AbstractButton b) {
    super.installDefaults(b);
    b.setOpaque(false);
    Border border = b.getBorder();
    if (border == null || border instanceof UIResource) {
      // TODO: This is only for 16x16 icon buttons
      b.setBorder(BorderFactory.createEmptyBorder(JBUI.scale(4), JBUI.scale(4), JBUI.scale(4), JBUI.scale(4)));
    }
  }

  @Override
  protected void installListeners(AbstractButton b) {
    super.installListeners(b);
    b.addMouseListener(myAdapter);
  }

  @Override
  protected void uninstallListeners(AbstractButton b) {
    super.uninstallListeners(b);
    b.removeMouseListener(myAdapter);
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    AbstractButton b = (AbstractButton)c;
    // TODO: Create a unique style for showing focus on buttons, for now use the hover state visuals.
    if ((myHover && b.isEnabled()) || b.isSelected() || b.isFocusOwner()) {
      GraphicsUtilKt.paintBackground(g, c);
    }
    super.paint(g, c);
  }
}
