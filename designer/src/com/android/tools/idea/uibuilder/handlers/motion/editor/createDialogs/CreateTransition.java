/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs;

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEIcons;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEUI;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Track;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MeModel;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.Utils;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Debug;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import java.awt.GridBagConstraints;
import java.awt.Insets;

/**
 * This is the dialog that pops up when you create a Transition
 */
public class CreateTransition extends BaseCreatePanel {
  static String TITLE = "Create ConstraintSet";
  JComboBox<String> mStartId = MEUI.makeComboBox(new String[]{});
  JComboBox<String> mEndId = MEUI.makeComboBox(new String[]{});
  private final String DURATION_PROMPT = "Duration in ms";
  private final String ENTER_TID = "Enter Transition's id";
  String[] options = {"Do Nothing",
    "Jump to Start",
    "Jump to End",
    "Animate to Start",
    "Animate to End"
  };

  static final String[] ourValues = {
    "none",
    "jumpToStart",
    "jumpToEnd",
    "animateToStart",
    "animateToEnd",
  };
  JComboBox<String> comboBox = MEUI.makeComboBox(options);

  public CreateTransition() {
    icon = MEIcons.CREATE_TRANSITION;

    GridBagConstraints gbc = new GridBagConstraints();
    int y = 0;
    grid(gbc, 0, y++, 1, 1);
    gbc.weighty = 0;
    gbc.ipadx = MEUI.scale(60);
    gbc.insets = MEUI.dialogLabelInsets();

    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(new JLabel("CREATE TRANSITION"), gbc);
    grid(gbc, 0, y++);
    gbc.weighty = 0;
    gbc.insets = MEUI.dialogSeparatorInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(new JSeparator(), gbc);

    grid(gbc, 0, y++);
    gbc.weighty = 0;
    gbc.insets = MEUI.dialogLabelInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(new JLabel("Start"), gbc);
    grid(gbc, 0, y++);
    gbc.insets = MEUI.dialogControlInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(mStartId, gbc);

    grid(gbc, 0, y++);
    gbc.weighty = 0;
    gbc.insets = MEUI.dialogLabelInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(new JLabel("End"), gbc);
    grid(gbc, 0, y++);
    gbc.insets = MEUI.dialogControlInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(mEndId, gbc);


    grid(gbc, 0, y++);
    gbc.insets = MEUI.dialogLabelInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(new JLabel("Automatically"), gbc);
    grid(gbc, 0, y++);
    gbc.insets = MEUI.dialogControlInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(comboBox, gbc);
    gbc.weighty = 1;
    grid(gbc, 0, y++, 2, 1);
    add(new JComponent() {
    }, gbc);
    gbc.weighty = 0;
    gbc.weightx = 1;
    gbc.insets = MEUI.insets(8, 12, 12, 12);
    gbc.anchor = GridBagConstraints.SOUTHEAST;
    grid(gbc, 0, y++, 2, 1);
    JButton ok = new JButton("Add");
    add(ok, gbc);
    ok.addActionListener(e -> create());
  }

  @Override
  protected boolean populateDialog() {
    MeModel model = mMotionEditor.getMeModel();
    MTag[] mtags = model.motionScene.getChildTags("ConstraintSet");
    if (mtags.length < 2) {
      showPreconditionDialog("Transition must at least 2 ConstraintSets to create a Transition");
      return false;
    }

    mStartId.removeAllItems();
    mEndId.removeAllItems();
    for (int i = 0; i < mtags.length; i++) {
      String id = mtags[i].getAttributeValue("id");
      mStartId.addItem(Utils.stripID(id));
      mEndId.addItem(Utils.stripID(id));
    }
    mStartId.setSelectedIndex(0);
    mEndId.setSelectedIndex(1);
    return true;
  }

  /**
   * This creates the transition
   */
  @Override
  public MTag create() {
    if (DEBUG) {
      Debug.log(" IN CREATE TRANSITION");
    }
    String sid = (String) mStartId.getSelectedItem();
    String eid = (String) mEndId.getSelectedItem();

    if (sid.length() == 0 && eid.length() == 0) {
      showErrorDialog("Transition must have a start and end id");
      return null;
    }
    if (sid.length() == 0) {
      showErrorDialog("Transition must have a start id");
      return null;
    }
    if (eid.length() == 0) {
      showErrorDialog("Transition must have an end id");
      return null;
    }

    // TODO error checking
    MeModel model = mMotionEditor.getMeModel();
    MTag.TagWriter writer = model.motionScene.getChildTagWriter(MotionSceneAttrs.Tags.TRANSITION);
    writer.setAttribute(MotionSceneAttrs.MOTION, MotionSceneAttrs.Transition.ATTR_CONSTRAINTSET_START, addIdPrefix(sid));
    writer.setAttribute(MotionSceneAttrs.MOTION, MotionSceneAttrs.Transition.ATTR_CONSTRAINTSET_END, addIdPrefix(eid));

    int index = comboBox.getSelectedIndex();
    if (index > 0) {
      writer.setAttribute(MotionSceneAttrs.MOTION, MotionSceneAttrs.Transition.ATTR_AUTO_TRANSITION, ourValues[index]);
    }
    MTag ret = writer.commit("Create Transition");
    Track.createTransition(mMotionEditor.myTrack);
    mMotionEditor.setMTag(model);
    super.create();
    return ret;
  }

  @Override
  public String getName() {
    return MotionSceneAttrs.Tags.TRANSITION;
  }

}
