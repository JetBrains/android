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

import com.android.tools.sherpa.drawing.ColorSet;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.ArrayList;

/**
 * Widget to support margin editing on the ui
 */
public class MarginWidget extends JPanel {
  String[] str = new String[]{"0", "8", "16", "24", "32"};
  boolean mInternal;
  @SuppressWarnings("UndesirableClassUsage")
  JComboBox<String> combo = new JComboBox<>(str);
  private static final String COMBO = "combo";
  private static final String TEXT = "text";
  public enum Show {
    IN_WIDGET,
    OUT_WIDGET,
    OUT_PANEL
  }

  ColorSet mColorSet ;
  ArrayList<ActionListener> mCallbacks = new ArrayList<>();

  JLabel label = new JLabel("0");
  CardLayout layout;
  JButton button;

  @Override
  public void setToolTipText(String text) {
    combo.setToolTipText(text);
  }

  public void showUI(Show show) {
    switch (show) {
      case IN_WIDGET:
        layout.show(this, COMBO );
        label.setText((String)combo.getSelectedItem());
        break;
      case OUT_WIDGET:
        if (!combo.isPopupVisible()) {
          layout.show(this, TEXT );
        }
        break;
      case OUT_PANEL:
        layout.show(this, TEXT );
        break;
    }
    label.setText((String)combo.getSelectedItem());
  }

  public MarginWidget(int alignment, ColorSet colorSet) {
    super(new CardLayout());
    mColorSet = colorSet;
    layout = (CardLayout)getLayout();
    combo.setEditable(true);
    setOpaque(false);
    setPreferredSize(new Dimension(42, 23));
    setBackground(null);
    label.setBackground(null);
    label.setOpaque(false);
    label.setHorizontalAlignment(alignment);
    combo.setBorder(new LineBorder(mColorSet.getInspectorStrokeColor()));


    label.setForeground(mColorSet.getInspectorStrokeColor());
    combo.setAlignmentX(LEFT_ALIGNMENT);
    combo.addActionListener(e -> {
      if (mInternal) {
        return;
      }
      label.setText((String)combo.getSelectedItem());
      for (ActionListener cb : mCallbacks) {
        cb.actionPerformed(e);
      }
    });

    add(label, TEXT);
    add(combo, COMBO);
    combo.setUI(ui);
    combo.setEditor(new BasicComboBoxEditor() {
      @Override
      public Component getEditorComponent() {
        return super.getEditorComponent();
      }
    });

    //noinspection GtkPreferredJComboBoxRenderer
    combo.setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        Component ret = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        ((JLabel)ret).setBorder(new LineBorder(mColorSet.getSubduedFrames(), 1));
        return ret;
      }
    });
  }

  BasicComboBoxUI ui = new BasicComboBoxUI() {

    @Override
    protected JButton createArrowButton() {
      button = new BasicArrowButton(SwingConstants.SOUTH);
      button.setBorder(new MatteBorder(0, 1, 0, 0, mColorSet.getInspectorStrokeColor()));
      return button;
    }
  };

  public void setMargin(int margin) {
    mInternal = true;
    String marginText = String.valueOf(margin);
    combo.setSelectedItem(marginText);
    label.setText(marginText);
    mInternal = false;
  }

  public int getMargin() {
    return Integer.parseInt(label.getText());
  }

  public void addActionListener(ActionListener actionListener) {
    mCallbacks.add(actionListener);
  }
}

