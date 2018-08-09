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
import com.android.tools.idea.uibuilder.handlers.motion.timeline.MotionSceneModel;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

/**
 * This class provides the display and management of the MotionScene and what to edit in it.
 */
public class MotionSceneStatusPanel extends JPanel {

  private final MotionLayoutAttributePanel myBasePanel;
  private final JBLabel myLdAttribute = new JBLabel("MotionScene:", JLabel.LEFT);
  private final JBLabel myLdName = new JBLabel("foo", JLabel.LEFT);
  private final JBLabel myTransitionLabel = new JBLabel("Transition:", JLabel.LEFT);
  private final ComboBox myTransitionMenu = new ComboBox();
  ArrayList<MotionSceneModel.TransitionTag> myTransitions;

  static JBColor ourAttributeColor = new JBColor(0x0000ff, 0xafafaf);
  static JBColor ourValueColor = new JBColor(0x008000, 0x537f4e);
  private MotionSceneModel myModel;

  public MotionSceneStatusPanel(MotionLayoutAttributePanel basePanel) {
    setLayout(new GridBagLayout());
    myBasePanel = basePanel;
    setLayout(new GridBagLayout());
    setBackground(EditorUtils.ourMainBackground);
    setBorder(JBUI.Borders.empty());

    setAttributeLook(myLdAttribute);
    setValueLook(myLdName);
    myLdAttribute.setForeground(EditorUtils.ourNameColor);
    myLdAttribute.setAlignmentX(Component.LEFT_ALIGNMENT);
    myLdAttribute.setFont(myLdAttribute.getFont().deriveFont(Font.BOLD));
    myLdAttribute.setBorder(JBUI.Borders.empty(4));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 0;
    gbc.gridwidth = 2;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.anchor = GridBagConstraints.WEST;

    gbc.gridwidth = 2;
    add(myLdAttribute, gbc);

    gbc.gridx = 2;
    gbc.weightx = 1;
    gbc.anchor = GridBagConstraints.WEST;
    add(myLdName, gbc);
    gbc.gridy++;
    gbc.gridx = 0;
    gbc.weightx = 0;
    add(myTransitionLabel, gbc);
    gbc.gridx = 2;
    gbc.weightx = 0;
    add(myTransitionMenu, gbc);
    myTransitionMenu.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        // TODO this should be propagated to the rest of the system
        myTransitions.get(myTransitionMenu.getSelectedIndex());
      }
    });
  }

  private void setAttributeLook(JBLabel label) {
    label.setForeground(EditorUtils.ourNameColor);
    label.setAlignmentX(Component.LEFT_ALIGNMENT);
    label.setFont(myLdAttribute.getFont().deriveFont(Font.BOLD));
    label.setBorder(JBUI.Borders.empty(4));
  }

  private void setValueLook(JLabel label) {
    label.setForeground(EditorUtils.ourValueColor);
    label.setAlignmentX(Component.LEFT_ALIGNMENT);
    label.setFont(myLdAttribute.getFont().deriveFont(Font.BOLD));
    label.setBorder(JBUI.Borders.empty(4));
  }

  public void setModel(MotionSceneModel model) {
    myModel = model;
    String name = myModel.getName();
    name = name.substring(0, name.lastIndexOf(".xml"));
    myLdName.setText(name);
    myTransitionMenu.removeAllItems();

    myTransitions = model.getTransitions();

    for (MotionSceneModel.TransitionTag transitionTag : myTransitions) {
      MotionSceneModel.ConstraintSet csStart = transitionTag.getConstraintSetStart();
      MotionSceneModel.ConstraintSet csEnd = transitionTag.getConstraintSetEnd();

      String sid = (csStart == null) ? "[none]" : strip(csStart.getId());
      String eid = (csEnd == null) ? "" : strip(csEnd.getId());

      myTransitionMenu.addItem(sid + " - " + eid);
    }
  }

  private String strip(String s) {
    return s.substring(s.indexOf('/') + 1);
  }
}
