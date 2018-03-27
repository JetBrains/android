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

import com.android.tools.adtui.common.StudioColorsKt;
import com.android.tools.adtui.model.stdui.DefaultCommonComboBoxModel;
import com.android.tools.adtui.stdui.CommonComboBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseWheelEvent;
import java.util.Arrays;

/**
 * Widget to support margin editing on the ui
 */
public class MarginWidget extends CommonComboBox<String, DefaultCommonComboBoxModel<String>> {
  private static final String[] str = new String[]{"0", "8", "16", "24", "32"};

  public enum Show {
    IN_WIDGET,
    OUT_WIDGET,
    OUT_PANEL
  }

  public MarginWidget(@NotNull String name) {
    super(new DefaultCommonComboBoxModel<>(str[0], Arrays.asList(str)));
    getModel().setEditable(true);
    setBackground(StudioColorsKt.getSecondaryPanelBackground());
    JTextField textField = (JTextField)getEditor().getEditorComponent();
    textField.setFont(textField.getFont().deriveFont(12f));
    initComboBox(name);
    setName(name);
  }

  private void initComboBox(@NotNull String name) {
    setAlignmentX(RIGHT_ALIGNMENT);
    setEditable(true);
    setName(name + "ComboBox");

    addMouseWheelListener(new MouseAdapter() {
      @Override
      public void mouseWheelMoved(MouseWheelEvent e) {
        int newMargin = getMargin() - e.getWheelRotation();
        if (newMargin >= 0) {
          setMargin(newMargin);
        }
      }
    });
  }

  public void setMargin(int margin) {
    String marginText = String.valueOf(margin);
    if(getSelectedItem().equals(marginText)) return;
    setSelectedItem(marginText);
  }

  public int getMargin() {
    try {
      String item = (String)getSelectedItem();
      return item != null ? Integer.parseInt(item) : 0;
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }

  @Override
  public void setSelectedItem(Object anObject) {
    if(anObject != null && anObject.equals(getSelectedItem())) {
      return;
    }
    super.setSelectedItem(anObject);
    if(hasFocus()) requestFocusInWindow();
  }

  @Override
  public void addActionListener(ActionListener actionListener) {
    super.addActionListener(actionListener);
  }
}