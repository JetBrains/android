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
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MeModel;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Debug;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import java.awt.GridBagConstraints;
import java.util.Arrays;

/**
 * This is the dialog that pops up when you create a KeyAttribute
 */
public class CreateKeyAttribute extends BaseCreateKey {
  private static final String KEY_TAG = "KeyAttribute";
  static String TITLE = "Create ConstraintSet";
  String[] options = MotionSceneAttrs.KeyAttributeOptions;
  String[] optionsNameSpace = MotionSceneAttrs.KeyAttributeOptionsNameSpace;

  JComboBox<String> comboBox = MEUI.makeComboBox(options);
  private final JTextField mPosition;
  MTag mKeyFrameSet;
  private MTag mSelectedTransition;
  private final String POS_PROMPT = "0-100";

  public CreateKeyAttribute() {
    icon = MEIcons.CREATE_TRANSITION;

    GridBagConstraints gbc = new GridBagConstraints();
    int y = createTop(gbc, "CREATE KEY ATTRIBUTE");

    grid(gbc, 0, y++, 2, 1);
    gbc.weighty = 0;
    gbc.anchor = GridBagConstraints.CENTER;
    add(new JLabel("Position"), gbc);
    grid(gbc, 0, y++, 2, 1);
    gbc.anchor = GridBagConstraints.CENTER;
    add(mPosition = newTextField(POS_PROMPT, 15), gbc);

    grid(gbc, 0, y++, 2, 1);
    gbc.anchor = GridBagConstraints.CENTER;
    add(new JLabel("Attribute"), gbc);
    grid(gbc, 0, y++, 2, 1);
    gbc.anchor = GridBagConstraints.CENTER;
    add(comboBox, gbc);

    gbc.weighty = 1;
    grid(gbc, 0, y++, 2, 1);
    add(new JComponent() {
    }, gbc);
    gbc.weighty = 0;
    gbc.weightx = 1;
    gbc.anchor = GridBagConstraints.SOUTHEAST;
    grid(gbc, 0, y++, 2, 1);
    JButton ok = new JButton("Add");
    add(ok, gbc);
    ok.addActionListener(e -> create());
  }

  @Override
  protected boolean populateDialog() {
    MeModel model = mMotionEditor.getMeModel();
    populateTags(model.getLayoutViewNames());
    MotionEditorSelector.Type selectionType = model.getSelectedType();
    if (selectionType == null) return false;
    MTag[] selected = model.getSelected();
    switch (selectionType) {
      case KEY_FRAME:
      case KEY_FRAME_GROUP:
        mKeyFrameSet = selected[0].getParent();
        mMatchTag.setText(selected[0].getAttributeValue("motionTarget"));
        for (int i = 0; i < options.length; i++) {
          String str = selected[0].getAttributeValue(options[i]);
          System.out.println(str + " = " + options[i]);
          if (str != null) {
            comboBox.setSelectedIndex(i);
            break;
          }
        }
        break;
      case TRANSITION:
        MTag[] tag = selected[0].getChildTags("KeyFrameSet");
        if (tag != null && tag.length > 0) {
          mKeyFrameSet = tag[0];
        }
        mSelectedTransition = selected[0];
        break;
    }
    float pos = model.getCurrentProgress();
    if (!Float.isNaN(pos)) {
      mPosition.setText(Integer.toString((int) (pos * 100)));
    }
    if (DEBUG) {
      Debug.log("populateDialog " + selectionType + " " + Arrays.toString(model.getSelected()));
    }
    MTag[] mtags = model.motionScene.getChildTags("ConstraintSet");
    if (mtags.length < 2) {
      showPreconditionDialog("Transition must at least 2 ConstraintSets to create a Transition");
      return false;
    }

    return true;
  }

  @Override
  public MTag create() {
    if (DEBUG) {
      Debug.log("create");
    }
    String tag = mMatchTag.getText();
    MTag.TagWriter toCommit;
    MTag.TagWriter keyPosition;
    if (mKeyFrameSet == null) {
      mKeyFrameSet = toCommit = mSelectedTransition.getChildTagWriter(MotionSceneAttrs.Tags.KEY_FRAME_SET);
      keyPosition = mKeyFrameSet.getChildTagWriter(KEY_TAG);
    } else {
      toCommit = keyPosition = mKeyFrameSet.getChildTagWriter(KEY_TAG);
    }
    String pos = mPosition.getText();
    if (pos.trim().length() == 0 || pos.equals(POS_PROMPT)) {
      showErrorDialog("Must define the position of the view");
      return null;
    }

    keyPosition.setAttribute(MotionSceneAttrs.MOTION, MotionSceneAttrs.Key.MOTION_TARGET, tag);
    try {
      int posInt = Integer.parseInt(pos.trim());
      keyPosition.setAttribute(MotionSceneAttrs.MOTION, MotionSceneAttrs.Key.FRAME_POSITION, pos.trim());
    } catch (Exception ex) {
      showErrorDialog("was not able to parse \"" + pos.trim() + "\"");
      return null;
    }
    keyPosition.setAttribute(optionsNameSpace[comboBox.getSelectedIndex()], (String) comboBox.getSelectedItem(), "0");

    MTag ret = toCommit.commit("Create KeyAttribute");
    mMotionEditor.dataChanged();
    super.create();
    return ret;
  }

  @Override
  public String getName() {
    return "KeyAttribute";
  }

  public static void main(String[] arg) {
    JFrame f = new JFrame(TITLE);
    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    f.setContentPane(new CreateKeyAttribute());
    f.pack();
    f.validate();
    f.setVisible(true);
  }
}
