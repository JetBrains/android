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

import static com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.Key_framePosition;
import static com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.Key_frameTarget;

import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.idea.uibuilder.handlers.motion.AttrName;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.TimeLineIcons;
import com.intellij.ui.JBColor;
import java.awt.Component;
import java.util.Comparator;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

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
    AttrName name;

    AttributesNamesHolder(AttrName n) {
      name = n;
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }

    @Override
    public String toString() {
      return name.toString();
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

  static Comparator compareAttributes = new Comparator<AttrName>() {
    // Adjust the sort order to sort "target" and "framePosition" first
    private final AttrName ourFramePosition = AttrName.motionAttr(Key_framePosition);
    private final AttrName ourTarget = AttrName.motionAttr(Key_frameTarget);

    @Override
    public int compare(AttrName s1, AttrName s2) {
      if (s1.equals(ourTarget)) {
        return -1;
      }
      if (s2.equals(ourTarget)) {
        return 1;
      }
      if (s1.equals(ourFramePosition)) {
        return -1;
      }
      if (s2.equals(ourFramePosition)) {
        return 1;
      }
      return s1.compareTo(s2);
    }
  };
}
