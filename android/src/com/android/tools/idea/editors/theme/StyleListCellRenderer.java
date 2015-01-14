/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.theme;

import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * A {@link ListCellRenderer} to render {@link ThemeEditorStyle} elements.
 */
public class StyleListCellRenderer extends JPanel implements ListCellRenderer {
  protected JLabel myStyleNameLabel = new JLabel();
  protected JLabel myReadOnlyLabel = new JLabel();

  public StyleListCellRenderer(JComponent list) {
    setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));

    myStyleNameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
    myStyleNameLabel.setFont(list.getFont());
    myReadOnlyLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
    myReadOnlyLabel.setText("R/O");
    myReadOnlyLabel.setHorizontalTextPosition(SwingConstants.CENTER);
    myReadOnlyLabel.setVerticalTextPosition(SwingConstants.CENTER);
    myReadOnlyLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.MINI).deriveFont(Font.BOLD));

    add(myStyleNameLabel);
    add(Box.createHorizontalGlue());
    add(myReadOnlyLabel);
  }

  @Override
  @Nullable
  public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    if (!(value instanceof ThemeEditorStyle)) {
      return null;
    }

    if (isSelected) {
      setBackground(list.getSelectionBackground());
      myStyleNameLabel.setForeground(list.getSelectionForeground());
      myReadOnlyLabel.setForeground(list.getSelectionForeground());
    } else {
      setBackground(list.getBackground());
      myStyleNameLabel.setForeground(list.getForeground());
      myReadOnlyLabel.setForeground(list.getForeground());
    }

    ThemeEditorStyle style = (ThemeEditorStyle)value;

    myStyleNameLabel.setText(style.getSimpleName());
    myReadOnlyLabel.setVisible(style.isReadOnly());

    return this;
  }
}
