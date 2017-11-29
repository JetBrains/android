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
package com.android.tools.adtui.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText;
import com.intellij.ui.TextAccessor;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import java.awt.*;

import static com.intellij.openapi.actionSystem.ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE;

/**
 * UI component for {@link DropDownAction}.
 *
 * It displays an ActionButton with a down arrow on the right of the text or the icon
 * if there is no text.
 */
public class DropDownActionButton extends ActionButtonWithText implements TextAccessor {

  private static final Icon DROP_DOWN_ICON = AllIcons.General.Combo3;
  private static final int ICON_TEXT_SPACE = JBUI.scale(4);
  private static final JBInsets INSETS = JBUI.insets(0, 4, 0, 2);

  /**
   * The icon has some padding, this constant is used to compensate the padding
   * so the whole button looks as expected
   */
  private static final int DROP_DOWN_ICON_SIZE_OFFSET = JBUI.scale(-8);

  private boolean myIsSelected = false;

  public DropDownActionButton(@NotNull AnAction action,
                              @NotNull Presentation presentation,
                              @NotNull String place) {
    super(action, presentation, place, DEFAULT_MINIMUM_BUTTON_SIZE);
  }

  @Override
  public int getPopState() {
    return myIsSelected ? POPPED : super.getPopState();
  }

  @Override
  public void paintComponent(Graphics g) {
    super.paintComponent(g);
    Insets insets = getInsets();
    DROP_DOWN_ICON.paintIcon(this, g, getWidth() - DROP_DOWN_ICON.getIconWidth() - insets.right,
                             (getHeight() - DROP_DOWN_ICON.getIconHeight() - insets.bottom - insets.top) / 2);
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    return new Dimension(size.width + DROP_DOWN_ICON.getIconWidth() + DROP_DOWN_ICON_SIZE_OFFSET, size.height);
  }

  @Override
  protected int iconTextSpace() {
    return ICON_TEXT_SPACE;
  }

  @Override
  public Insets getInsets() {
    Insets insets = super.getInsets();
    insets.left += INSETS.left;
    insets.right += INSETS.right;
    return insets;
  }

  public void setSelected(boolean selected) {
    myIsSelected = selected;
    repaint();
  }

  @Override
  protected int horizontalTextAlignment() {
    return SwingConstants.LEFT;
  }

  @Override
  public void setText(String text) {
    myPresentation.setText(text);
  }

  @Override
  public String getText() {
    return myPresentation.getText();
  }
}
