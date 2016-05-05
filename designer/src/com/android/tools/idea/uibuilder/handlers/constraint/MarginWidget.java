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
package com.android.tools.idea.uibuilder.handlers.constraint;

import com.android.tools.sherpa.drawing.BlueprintColorSet;
import com.android.tools.sherpa.drawing.ColorSet;
import com.intellij.openapi.ui.ComboBox;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

/**
 * Widget to support margin editing on the ui
 */
public class MarginWidget extends JPanel {
  String[] str = new String[]{"0", "8", "16", "24", "32"};
  @SuppressWarnings("UndesirableClassUsage")
  JComboBox<String> combo = new JComboBox<>(str);
  ColorSet mColorSet = new BlueprintColorSet();
  Color TEXT_COLOR = mColorSet.getSelectedConstraints();
  Color BACKGROUND_COLOR = mColorSet.getBackground();
  ArrayList<ActionListener> mCallbacks = new ArrayList<>();

  JLabel label = new JLabel("0");
  CardLayout layout;
  JButton button;

  public void showUI(boolean show) {
    label.setText((String)combo.getSelectedItem());
    layout.show(this, (show) ? "full" : "simple");
  }

  public MarginWidget(int alignment) {
    super(new CardLayout());
    layout = (CardLayout)getLayout();
    combo.setEditable(true);
    setOpaque(false);
    setPreferredSize(new Dimension(42, 23));
    setBackground(null);
    label.setBackground(null);
    label.setOpaque(false);
    label.setHorizontalAlignment(alignment);
    label.setForeground(TEXT_COLOR);
    combo.getEditor().getEditorComponent().setForeground(TEXT_COLOR);
    combo.getEditor().getEditorComponent().setBackground(BACKGROUND_COLOR);
    combo.setBackground(BACKGROUND_COLOR);
    combo.setForeground(TEXT_COLOR);
    combo.addActionListener(e -> {
      label.setText((String)combo.getSelectedItem());
      for (ActionListener cb : mCallbacks) {
        cb.actionPerformed(e);
      }
    });

    add(label, "simple");
    add(combo, "full");
    combo.setUI(ui);
    combo.setEditor(new BasicComboBoxEditor() {
      @Override
      public Component getEditorComponent() {
        Component ret = super.getEditorComponent();
        ret.setBackground(BACKGROUND_COLOR);
        ret.setForeground(TEXT_COLOR);
        return ret;
      }
    });

    //noinspection GtkPreferredJComboBoxRenderer
    combo.setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        Component ret = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        ret.setBackground((isSelected) ? TEXT_COLOR : BACKGROUND_COLOR);
        ret.setForeground((isSelected) ? BACKGROUND_COLOR : TEXT_COLOR);
        ((JLabel)ret).setBorder(new LineBorder(mColorSet.getSubduedFrames(), 1));
        return ret;
      }
    });
  }

  BasicComboBoxUI ui = new BasicComboBoxUI() {

    @Override
    protected JButton createArrowButton() {
      button = new BasicArrowButton(SwingConstants.SOUTH);
      button.setBackground(TEXT_COLOR);
      button.setBorder(new EmptyBorder(0, 0, 0, 0));
      return button;
    }
  };

  public void setMargin(int margin) {
    String marginText = String.valueOf(margin);
    combo.setSelectedItem(marginText);
    label.setText(marginText);
  }

  public int getMargin() {
    return Integer.parseInt(label.getText());
  }

  public void addActionListener(ActionListener actionListener) {
    mCallbacks.add(actionListener);
  }
}

