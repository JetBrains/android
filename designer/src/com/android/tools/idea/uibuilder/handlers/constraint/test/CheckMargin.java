/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.constraint.test;

import com.android.tools.idea.uibuilder.handlers.constraint.MarginWidget;
import com.android.tools.sherpa.drawing.BlueprintColorSet;
import com.android.tools.sherpa.drawing.ColorSet;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

/**
 * Test for margin widget
 */
public class CheckMargin extends JPanel {
  ColorSet mColorSet = new BlueprintColorSet();
  MarginWidget top = new MarginWidget(JLabel.LEFT, mColorSet);
  MarginWidget left = new MarginWidget(JLabel.LEFT, mColorSet);
  MarginWidget right = new MarginWidget(JLabel.LEFT, mColorSet);
  MarginWidget bottom = new MarginWidget(JLabel.LEFT, mColorSet);

  public CheckMargin() {
    super(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.weightx = 1;
    gbc.weighty = 1;
    gbc.gridx = 1;
    gbc.gridy = 0;
    add(top, gbc);
    gbc.gridx = 0;
    gbc.gridy = 1;
    add(left, gbc);
    gbc.gridx = 2;
    gbc.gridy = 1;
    add(right, gbc);
    gbc.gridx = 1;
    gbc.gridy = 02;
    add(bottom, gbc);
    setBackground(mColorSet.getBackground());

    addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        top.showUI(top.getBounds().contains(e.getPoint()) ? MarginWidget.Show.IN_WIDGET : MarginWidget.Show.OUT_WIDGET);
        left.showUI(left.getBounds().contains(e.getPoint()) ? MarginWidget.Show.IN_WIDGET : MarginWidget.Show.OUT_WIDGET);
        right.showUI(right.getBounds().contains(e.getPoint()) ? MarginWidget.Show.IN_WIDGET : MarginWidget.Show.OUT_WIDGET);
        bottom.showUI(bottom.getBounds().contains(e.getPoint()) ? MarginWidget.Show.IN_WIDGET : MarginWidget.Show.OUT_WIDGET);
      }
    });
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseExited(MouseEvent e) {
        if (getBounds().contains(e.getPoint())){
          return;
        }
        top.showUI(MarginWidget.Show.OUT_PANEL);
        left.showUI(MarginWidget.Show.OUT_PANEL);
        right.showUI(MarginWidget.Show.OUT_PANEL);
        bottom.showUI(MarginWidget.Show.OUT_PANEL);
      }
    });
  }

  @Override
  protected void paintComponent(Graphics g) {
    int width = getWidth();
    int height = getHeight();
    Graphics2D g2d = (Graphics2D)g;
    g.setColor(getBackground());
    g.fillRect(0, 0, width, height);
  }

  public static void main(String[] args) {
    JFrame f = new JFrame("CustomTextInput");
    f.setBounds(new Rectangle(800, 800));
    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    CheckMargin p = new CheckMargin();
    f.setContentPane(p);
    f.validate();
    f.setVisible(true);
  }
}
