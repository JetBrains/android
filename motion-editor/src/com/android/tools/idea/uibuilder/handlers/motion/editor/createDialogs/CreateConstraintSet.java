/*
 * Copyright (C) 2024 The Android Open Source Project
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
import java.awt.GridBagConstraints;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSeparator;
import javax.swing.JTextField;

/**
 * This is the dialog that pops up when you create a constraint set
 */
public class CreateConstraintSet extends BaseCreatePanel {
  static String TITLE = "Create ConstraintSet";
  String MOTION_LAYOUT = "MotionLayout";
  JComboBox<String> comboBox = MEUI.makeComboBox(new String[]{MOTION_LAYOUT});
  protected JTextField mId;

  public CreateConstraintSet() {
    icon = MEIcons.CREATE_CONSTRAINTSET;
    comboBox.setEditable(true);
    GridBagConstraints gbc = new GridBagConstraints();
    int y = 0;
    grid(gbc, 0, y++, 1, 1);
    gbc.ipadx = MEUI.scale(40);
    gbc.weighty = 0;
    gbc.insets = MEUI.dialogTitleInsets();

    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(new JLabel("CREATE CONSTRAINTSET"), gbc);
    grid(gbc, 0, y++);
    gbc.weighty = 0;
    gbc.insets = MEUI.dialogSeparatorInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(new JSeparator(), gbc);

    grid(gbc, 0, y++);
    gbc.weighty = 0;
    gbc.insets = MEUI.dialogLabelInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(new JLabel("ID"), gbc);
    grid(gbc, 0, y++);
    gbc.insets = MEUI.dialogControlInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(mId = newTextField("Enter id", 15), gbc);
    grid(gbc, 0, y++);
    gbc.insets = MEUI.dialogLabelInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(new JLabel("Based On"), gbc);
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
    gbc.insets = MEUI.dialogBottomButtonInsets();
    gbc.anchor = GridBagConstraints.SOUTHEAST;
    grid(gbc, 0, y++, 2, 1);
    JButton ok = new JButton("Add");
    add(ok, gbc);
    ok.addActionListener(e -> create());
  }

  @Override
  protected boolean populateDialog() {
    MeModel model = mMotionEditor.getMeModel();
    MTag[] mtags = model.motionScene.getChildTags(MotionSceneAttrs.Tags.CONSTRAINTSET);
    comboBox.removeAllItems();
    comboBox.addItem(MOTION_LAYOUT);
    mId.setText(null);
    for (int i = 0; i < mtags.length; i++) {
      String id = mtags[i].getAttributeValue("id");
      comboBox.addItem(Utils.stripID(id));
    }
    return true;
  }

  /**
   * This creates the new ConstraintSet
   */
  @Override
  public MTag create() {
    String id = mId.getText().trim();
    if (id.isEmpty()) {
      showErrorDialog("ConstraintSet must have a valid id");
      return null;
    }
    // TODO error checking
    MeModel model = mMotionEditor.getMeModel();
    MTag.TagWriter writer = model.motionScene.getChildTagWriter(MotionSceneAttrs.Tags.CONSTRAINTSET);
    writer.setAttribute(MotionSceneAttrs.ANDROID, MotionSceneAttrs.ConstraintSet.ATTR_ID, addIdPrefix(id));
    if (comboBox.getSelectedIndex() != 0) {
      String derivesFrom = (String) comboBox.getSelectedItem();
      writer.setAttribute(MotionSceneAttrs.MOTION, MotionSceneAttrs.ConstraintSet.DERIVE_CONSTRAINTS_FROM, addIdPrefix(derivesFrom));
    }

    MTag ret = writer.commit("Create ConstraintSet");
    Track.createConstraintSet(mMotionEditor.myTrack);
    mMotionEditor.setMTag(model);
    super.create();
    return ret;
  }

  @Override
  public String getName() {
    return MotionSceneAttrs.Tags.CONSTRAINTSET;
  }
}
