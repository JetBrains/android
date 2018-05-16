/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.attributeEditor;

import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.TimeLineIcons;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.Comparator;

/**
 * Collection of utilities used by the Panels
 */
public class EditorUtils {
  static JBColor ourNameColor = new JBColor(0x0000ff, 0xafafaf);
  static JBColor ourEasingGraphColor = new JBColor(0x0000ff, 0xafafaf);
  static JBColor ourEasingControlsColor  = new JBColor(0xE2739A, 0xE27BA4);
  static JBColor ourTagColor = new JBColor(0x000080, 0xe5b764);
  static JBColor ourValueColor = new JBColor(0x008000, 0x537f4e);
  static JBColor ourSecondaryPanelBackground = new JBColor(0xfcfcfc, 0x313435);
  static JBColor ourMainBackground = ourSecondaryPanelBackground;

  static class AddRemovePanel extends JPanel {
    JButton myAddButton = makeButton(TimeLineIcons.ADD_KEYFRAME);
    JButton myRemoveButton = makeButton(TimeLineIcons.REMOVE_KEYFRAME);

    AddRemovePanel() {
      setBackground(ourMainBackground);
      myRemoveButton.setEnabled(false);
      add(myAddButton);
      add(myRemoveButton);
    }
  }

  static JButton makeButton(Icon icon) {
    JButton button = new CommonButton(icon);

    return button;
  }

  //============================AttributesNamesHolder==================================//

  static class AttributesNamesHolder {
    String name;

    AttributesNamesHolder(String n) {
      name = n;
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }

    @Override
    public String toString() {
      return name;
    }

    @Override
    public boolean equals(Object obj) {
      return name.equals(obj);
    }
  }

  //============================AttributesNamesCellRenderer==================================//
  static class AttributesNamesCellRenderer extends DefaultTableCellRenderer {

    @Override
    public void setValue(Object value) {
      setText(value.toString());
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus, int row, int col) {

      Component c = super.getTableCellRendererComponent(table, value,
                                                        isSelected, hasFocus, row, col);
      if (!isSelected) {
        setForeground(ourNameColor);
      }
      return c;
    }
  }

  //============================AttributesValueCellRenderer==================================//
  static class AttributesValueCellRenderer extends DefaultTableCellRenderer {

    @Override
    public void setValue(Object value) {
      setText(value.toString());
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus, int row, int col) {

      Component c = super.getTableCellRendererComponent(table, value,
                                                        isSelected, hasFocus, row, col);
      if (!isSelected) {
        setForeground(ourValueColor);
      }
      return c;
    }
  }
  //==============================================================//
   static Comparator compareAttributes = new Comparator<String>() {
    public static final  String ourframePosition = "framePosition";
    public static final  String ourTarget= "target";
    @Override
    public int compare(String s1, String s2) {

      if (s1.equals(ourTarget)) {
        return -1;
      }
      if (s2.equals(ourTarget)) {
        return 1;
      }
      if (s1.equals(ourframePosition)) {
        return -1;
      }
      if (s2.equals(ourframePosition)) {
        return 1;
      }
      return s1.compareTo(s2);
    }
  };
}
