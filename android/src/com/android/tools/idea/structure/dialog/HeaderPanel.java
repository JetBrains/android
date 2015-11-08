/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.structure.dialog;

import com.intellij.ui.Gray;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.SideBorder;
import com.intellij.util.ui.UIUtil.FontSize;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.intellij.ui.tabs.TabsUtil.getTabsHeight;
import static com.intellij.util.ui.UIUtil.getLabelFont;

public class HeaderPanel extends JPanel {
  public HeaderPanel(@NotNull String title) {
    super(new BorderLayout());
    JLabel titleLabel = new JLabel(title);
    titleLabel.setBorder(IdeBorderFactory.createEmptyBorder(2, 5, 2, 10));
    titleLabel.setFont(getLabelFont(FontSize.SMALL));
    add(titleLabel, BorderLayout.CENTER);

    setBackground(new JBColor(Gray._210, Gray._75)); // Taken from WelcomeScreenColors#CAPTION_BACKGROUND
    setForeground(new JBColor(JBColor.BLACK, Gray._197)); // Taken from WelcomeScreenColors#CAPTION_FOREGROUND
    setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    return new Dimension(size.width, getTabsHeight());
  }

  @Override
  public Dimension getMinimumSize() {
    Dimension size = super.getMinimumSize();
    return new Dimension(size.width, getTabsHeight());
  }
}
