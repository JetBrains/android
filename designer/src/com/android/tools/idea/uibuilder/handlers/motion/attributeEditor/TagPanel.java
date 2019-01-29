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

import com.android.tools.idea.uibuilder.handlers.motion.MotionLayoutAttributePanel;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;

/**
 * Base class for all tag panels.
 */
public class TagPanel extends JPanel {
  MotionLayoutAttributePanel myBasePanel;
  protected JLabel myTitle = new JLabel("", JLabel.LEFT);
  EditorUtils.AddRemovePanel myAddRemovePanel = new EditorUtils.AddRemovePanel();
  JTable myTable;
  protected JButton myRemoveTagButton;

  TagPanel(MotionLayoutAttributePanel basePanel) {
    myBasePanel = basePanel;
    setLayout(new GridBagLayout());
    setBackground(EditorUtils.ourMainBackground);
    setBorder(JBUI.Borders.empty());
    myTitle.setForeground(EditorUtils.ourTagColor);
    myTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
    myTitle.setFont(myTitle.getFont().deriveFont(Font.BOLD));
    myTitle.setBorder(JBUI.Borders.empty(4));
  }

  protected void deleteAttr(int selection) {
  }

  protected void deleteTag() {
  }

  protected void setup() {
    if (myRemoveTagButton != null) {
      myRemoveTagButton.addActionListener(e -> deleteTag());
    }
    myAddRemovePanel.myRemoveButton.addActionListener((e)-> deleteAttr(myTable.getSelectedRow()));
    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }
         myAddRemovePanel.myRemoveButton.setEnabled((myTable.getSelectedColumn() != -1));
      }
    });
  }
}
