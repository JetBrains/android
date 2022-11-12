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

import static com.intellij.ui.tabs.TabsUtil.getTabsHeight;
import static com.intellij.util.ui.UIUtil.getLabelFont;

import com.intellij.ui.Gray;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.SideBorder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil.FontSize;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class Header extends JPanel {
  @NotNull private final JLabel myTitleLabel;

  public Header(@NotNull String title) {
    super(new FlowLayout(FlowLayout.LEADING,0,0));
    myTitleLabel = new JLabel(title);
    myTitleLabel.setBorder(JBUI.Borders.empty(2, 5, 2, 5));
    myTitleLabel.setFont(getLabelFont(FontSize.SMALL));
    add(myTitleLabel);

    setBackground(new JBColor(Gray._210, Gray._75)); // Taken from WelcomeScreenColors#CAPTION_BACKGROUND
    setForeground(new JBColor(JBColor.BLACK, Gray._197)); // Taken from WelcomeScreenColors#CAPTION_FOREGROUND
    setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
  }

  public void setIcon(@NotNull Icon icon) {
    myTitleLabel.setIcon(icon);
  }

  public void addNextComponent(JComponent component){
    add(component);
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

  @Override
  public Dimension getMaximumSize() {
    Dimension size = super.getMaximumSize();
    return new Dimension(size.width, getTabsHeight());
  }
}
