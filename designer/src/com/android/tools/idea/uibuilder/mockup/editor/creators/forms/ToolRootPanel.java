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
package com.android.tools.idea.uibuilder.mockup.editor.creators.forms;

import com.intellij.ui.JBColor;

import javax.swing.*;
import java.awt.*;

/**
 * Base class for root component of the Tools popup. It draws rounded border.
 * The children need to be set to not opaque.
 */
public class ToolRootPanel extends JPanel {

  private static final int ARC_SIZE = 7;
  public static final JBColor BACKGROUND = new JBColor(JBColor.background(), JBColor.background());

  public ToolRootPanel() {
    setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
  }

  @Override
  protected void paintComponent(Graphics g) {
    Graphics2D g2d = (Graphics2D)g;
    g2d.setColor(BACKGROUND);
    g2d.fillRoundRect(0, 0, getWidth(), getHeight(), ARC_SIZE, ARC_SIZE);
    g2d.setColor(JBColor.border());
    g2d.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, ARC_SIZE, ARC_SIZE);
  }
}
