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
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;

/**
 * Widget to support margin editing on the ui
 */
public class MarginWidget extends JPanel {
  private String[] str = new String[]{"0", "8", "16", "24", "32"};
  private boolean mInternal;
  @SuppressWarnings("UndesirableClassUsage")
  private JComboBox<String> combo = new JComboBox<>(str);
  private static final String COMBO = "combo";
  private static final String TEXT = "text";

  public enum Show {
    IN_WIDGET,
    OUT_WIDGET,
    OUT_PANEL
  }

  private ColorSet mColorSet;
  private ArrayList<ActionListener> mCallbacks = new ArrayList<>();

  private JLabel label = new JLabel("0");
  private CardLayout layout;

  @Override
  public void setToolTipText(String text) {
    combo.setToolTipText(text);
  }

  public void showUI(Show show) {
    if (combo.getEditor().getEditorComponent().hasFocus()) {
      return;
    }
    switch (show) {
      case IN_WIDGET:
        layout.show(this, COMBO);
        label.setText((String)combo.getSelectedItem());
        break;
      case OUT_WIDGET:
        if (!combo.isPopupVisible()) {
          layout.show(this, TEXT);
        }
        break;
      case OUT_PANEL:
        layout.show(this, TEXT);
        break;
    }
    label.setText((String)combo.getSelectedItem());
  }

  public MarginWidget(@NotNull ColorSet colorSet, int alignment, @NotNull String name) {
    super(new CardLayout());
    mColorSet = colorSet;
    layout = (CardLayout)getLayout();

    initLabel(alignment);
    initComboBox(name);

    setBackground(null);
    setName(name);
    setOpaque(false);
    setPreferredSize(new Dimension(42, 23));

    add(label, TEXT);
    add(combo, COMBO);
  }

  private void initLabel(int alignment) {
    label.setBackground(null);
    label.setForeground(mColorSet.getInspectorStrokeColor());
    label.setHorizontalAlignment(alignment);
    label.setOpaque(false);
  }

  private void initComboBox(@NotNull String name) {
    combo.setAlignmentX(LEFT_ALIGNMENT);
    combo.setBorder(new LineBorder(mColorSet.getInspectorStrokeColor()));
    combo.setEditable(true);

    // TODO I don't think we need this
    combo.setEditor(new BasicComboBoxEditor() {
      @NotNull
      @Override
      public Component getEditorComponent() {
        return super.getEditorComponent();
      }
    });

    combo.setName(name + "ComboBox");

    // noinspection GtkPreferredJComboBoxRenderer
    combo.setRenderer(new DefaultListCellRenderer() {
      @NotNull
      @Override
      public Component getListCellRendererComponent(@NotNull JList list,
                                                    @NotNull Object value,
                                                    int index,
                                                    boolean selected,
                                                    boolean focused) {
        Component component = super.getListCellRendererComponent(list, value, index, selected, focused);
        ((JComponent)component).setBorder(new LineBorder(mColorSet.getSubduedFrames(), 1));

        return component;
      }
    });

    combo.setUI(ui);

    combo.getEditor().getEditorComponent().addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(@NotNull FocusEvent event) {
        showUI(Show.OUT_PANEL);
      }
    });

    combo.addActionListener(event -> {
      if (mInternal) {
        return;
      }

      label.setText((String)combo.getSelectedItem());
      mCallbacks.forEach(listener -> listener.actionPerformed(event));
    });
  }

  private BasicComboBoxUI ui = new BasicComboBoxUI() {

    @Override
    protected JButton createArrowButton() {
      Color background = mColorSet.getInspectorBackgroundColor();
      Color shadow = mColorSet.getInspectorStrokeColor();
      Color darkShadow = mColorSet.getInspectorStrokeColor();
      Color highlight = mColorSet.getSubduedFrames();
      JButton button = new BasicArrowButton(SwingConstants.SOUTH, background, shadow, darkShadow, highlight);
      button.setForeground(JBColor.RED);
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
    try {
      return Integer.parseInt(label.getText());
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }

  public void addActionListener(ActionListener actionListener) {
    mCallbacks.add(actionListener);
  }
}