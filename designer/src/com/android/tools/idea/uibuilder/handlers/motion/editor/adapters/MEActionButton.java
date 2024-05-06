/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.editor.adapters;

import com.intellij.openapi.actionSystem.ActionButtonComponent;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.keymap.impl.IdeMouseEventDispatcher;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.plaf.basic.BasicButtonUI;

public class MEActionButton extends JButton {
  private boolean myMouseDown;
  private boolean myRollover;
  private boolean myPopupIsShowing;
  private JBDimension myMinimumButtonSize;

  public MEActionButton(Icon icon, Icon disable_icon, String tooltip) {
    addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent event) {
        repaint();
      }

      @Override
      public void focusLost(FocusEvent event) {
        repaint();
      }
    });
    setIcon(icon);
    setDisabledIcon(disable_icon);
    setContentAreaFilled(false);
    setBorderPainted(false);
    setToolTipText(tooltip);
    setBorder(JBUI.Borders.empty());
  }

  @Override
  public void updateUI() {
    setUI(new BasicButtonUI());
    myMinimumButtonSize = JBDimension.create(ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
  }

  @Override
  public Dimension getPreferredSize() {
    return myMinimumButtonSize;
  }

  @Override
  public void paintComponent(Graphics g) {
    if (!isEnabled()) {
      super.paintComponent(g);
      return;
    }
    if (!UIUtil.isUnderDarcula()) {
      paintBackground(g);
    }
    super.paintComponent(g);
  }

  @Override
  protected void processMouseEvent(MouseEvent event) {
    IdeMouseEventDispatcher.requestFocusInNonFocusedWindow(event);
    super.processMouseEvent(event);
    if (event.isConsumed()) {
      return;
    }
    switch (event.getID()) {
      case MouseEvent.MOUSE_PRESSED:
        myMouseDown = true;
        break;
      case MouseEvent.MOUSE_RELEASED:
        myMouseDown = false;
        break;
      case MouseEvent.MOUSE_ENTERED:
        myRollover = true;
        break;
      case MouseEvent.MOUSE_EXITED:
        myRollover = false;
        break;
      default:
        return;
    }
    repaint();
  }

  public void setPopupIsShowing(boolean isPopupShowing) {
    myPopupIsShowing = isPopupShowing;
    repaint();
  }

  private void paintBackground(Graphics g) {
    int state = computeState();
    if (state == ActionButtonComponent.NORMAL && !isBackgroundSet()) {
      return;
    }
    Rectangle rect = new Rectangle(getSize());
    JBInsets.removeFrom(rect, getInsets());
    ActionButtonLook.SYSTEM_LOOK.paintLookBackground(g, rect, getBackgroundColor(state));
  }

  private int computeState() {
    if (myPopupIsShowing) {
      return ActionButtonComponent.SELECTED;
    }
    else if (myMouseDown) {
      return ActionButtonComponent.PUSHED;
    }
    else if (myRollover && isEnabled()) {
      return ActionButtonComponent.POPPED;
    }
    else if (isFocusOwner()) {
      return ActionButtonComponent.SELECTED;
    }
    else {
      return ActionButtonComponent.NORMAL;
    }
  }

  private Color getBackgroundColor(int state) {
    switch (state) {
      case ActionButtonComponent.NORMAL:
        return getBackground();
      case ActionButtonComponent.PUSHED:
        return JBUI.CurrentTheme.ActionButton.pressedBackground();
      default:
        return JBUI.CurrentTheme.ActionButton.hoverBackground();
    }
  }
}
