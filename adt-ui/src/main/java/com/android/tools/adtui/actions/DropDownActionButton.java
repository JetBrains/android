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
import com.intellij.openapi.actionSystem.AnActionHolder;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;

import static com.intellij.openapi.actionSystem.ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE;

/**
 * TODO Write documention
 */
public class DropDownActionButton extends ActionButtonWithText {

  private static final int DROP_DOWN_ICON_MARGIN_RIGHT = JBUI.scale(2);
  private static final Icon DROP_DOWN_ICON = AllIcons.General.Combo3;
  private static final int ICON_TEXT_SPACE = JBUI.scale(4);

  private boolean myIsSelected = false;

  public DropDownActionButton(@NotNull AnAction action,
                              @NotNull Presentation presentation,
                              @NotNull String place) {
    super(action, presentation, place, DEFAULT_MINIMUM_BUTTON_SIZE);
    presentation.setEnabled(true);
    setHorizontalTextAlignment(SwingConstants.CENTER);
    setBorder(new CompoundBorder(
      getBorder(),
      BorderFactory.createEmptyBorder(0, 0, 0, DROP_DOWN_ICON.getIconWidth() - DROP_DOWN_ICON_MARGIN_RIGHT)));
  }

  @Override
  public int getPopState() {
    return myIsSelected ? POPPED : super.getPopState();
  }

  @Override
  public void paintComponent(Graphics g) {
    super.paintComponent(g);
    Insets insets = getInsets();
    DROP_DOWN_ICON.paintIcon(this, g, getWidth() - DROP_DOWN_ICON.getIconWidth() - DROP_DOWN_ICON_MARGIN_RIGHT,
                             (getHeight() - DROP_DOWN_ICON.getIconHeight() - insets.bottom - insets.top) / 2);
  }

  @Override
  protected int iconTextSpace() {
    return ICON_TEXT_SPACE;
  }

  @Override
  public Dimension getPreferredSize() {
    return super.getPreferredSize();
  }

  public void setSelected(boolean selected) {
    myIsSelected = selected;
  }

}
