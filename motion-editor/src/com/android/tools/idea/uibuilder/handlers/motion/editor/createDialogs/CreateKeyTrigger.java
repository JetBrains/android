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
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Debug;
import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSeparator;
import javax.swing.JTextField;

/**
 * This is the dialog that pops up when you create a KeyTrigger
 */
public class CreateKeyTrigger extends BaseCreateKey {
  static String TITLE = "Create ConstraintSet";
  private static final String KEY_TAG = MotionSceneAttrs.Tags.KEY_TRIGGER;
  private String[] options = {
    "Position",
    "Collision"
  };
  JComboBox<String> comboBox = MEUI.makeComboBox(options);
  MTag mKeyFrameSet;
  private String mPosPrompt = "0-100";
  private MTag mSelectedTransition;
  private final JTextField mOnCross;
  PromptedTextField mMainParameter;

  public CreateKeyTrigger() {
    icon = MEIcons.CREATE_TRANSITION;

    GridBagConstraints gbc = new GridBagConstraints();
    int y = createTop(gbc, "CREATE KEY TRIGGER");

    grid(gbc, 0, y++, 2, 1);
    gbc.weighty = 0;
    gbc.insets = MEUI.dialogSeparatorInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(new JSeparator(), gbc);

    grid(gbc, 0, y++, 2, 1);
    gbc.insets = MEUI.dialogLabelInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(new JLabel("Type"), gbc);
    grid(gbc, 0, y++, 2, 1);
    gbc.anchor = GridBagConstraints.CENTER;
    gbc.insets = MEUI.dialogControlInsets();
    add(comboBox, gbc);

    JLabel title;
    grid(gbc, 0, y++, 2, 1);
    gbc.weighty = 0;
    gbc.insets = MEUI.dialogLabelInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(title = new JLabel("Position"), gbc);
    grid(gbc, 0, y++, 2, 1);
    gbc.insets = MEUI.dialogControlInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(mMainParameter = newTextField("0-100", 15), gbc);

    comboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (comboBox.getSelectedIndex() == 0) {
          title.setText("Position");
          mMainParameter.setPromptText(mPosPrompt = "0-100");
        } else {
          title.setText("Collide width");
          mMainParameter.setPromptText(mPosPrompt = "id of view");
        }
      }
    });
    grid(gbc, 0, y++,2, 1);
    gbc.weighty = 0;
    gbc.insets = MEUI.dialogLabelInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(new JLabel("Collide width"), gbc);
    grid(gbc, 0, y++, 2, 1);
    gbc.insets = MEUI.dialogControlInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(newTextField("triggerId", 15), gbc);

    grid(gbc, 0, y++, 2, 1);
    gbc.weighty = 0;
    gbc.insets = MEUI.dialogLabelInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(new JLabel("onCross"), gbc);
    grid(gbc, 0, y++, 2, 1);
    gbc.insets = MEUI.dialogControlInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(mOnCross = newTextField("method", 15), gbc);

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
    populateTags(model.getLayoutViewNames());
    MotionEditorSelector.Type selectionType = model.getSelectedType();
    if (selectionType == null) return false;
    MTag[] selected = model.getSelected();
    switch (selectionType) {
      case KEY_FRAME:
      case KEY_FRAME_GROUP:
        mKeyFrameSet = selected[0].getParent();
        mMatchTag.setText(selected[0].getAttributeValue(MotionSceneAttrs.Key.MOTION_TARGET));

        break;
      case TRANSITION:
        MTag[] tag = selected[0].getChildTags(MotionSceneAttrs.Tags.KEY_FRAME_SET);
        if (tag != null && tag.length > 0) {
          mKeyFrameSet = tag[0];
        }
        mSelectedTransition = selected[0];
        break;
    }
    float pos = model.getCurrentProgress();
    if (!Float.isNaN(pos)) {
      mMainParameter.setText(Integer.toString((int) (pos * 100)));
    }
    if (DEBUG) {
      Debug.log("populateDialog " + selectionType + " " + Arrays.toString(model.getSelected()));
    }
    return true;
  }

  @Override
  public MTag create() {
    if (DEBUG) {
      Debug.log("create");
    }
    MTag.TagWriter toCommit;
    MTag.TagWriter keyPosition;
    if (mKeyFrameSet == null) {
      mKeyFrameSet = toCommit = mSelectedTransition.getChildTagWriter(MotionSceneAttrs.Tags.KEY_FRAME_SET);
      keyPosition = mKeyFrameSet.getChildTagWriter(KEY_TAG);
    } else {
      toCommit = keyPosition = mKeyFrameSet.getChildTagWriter(KEY_TAG);
    }
    keyPosition.setAttribute(MotionSceneAttrs.MOTION, MotionSceneAttrs.Key.MOTION_TARGET, getMotionTarget());

    if (comboBox.getSelectedIndex() == 0) {
      String pos = mMainParameter.getText();
      if (pos.trim().length() == 0 || pos.equals(mPosPrompt)) {
        showErrorDialog("Must define the position of the view");
        return null;
      }

      try {
        int posInt = Integer.parseInt(pos.trim());
        keyPosition.setAttribute(MotionSceneAttrs.MOTION, MotionSceneAttrs.Key.FRAME_POSITION, pos.trim());
      } catch (Exception ex) {
        showErrorDialog("was not able to parse \"" + pos.trim() + "\"");
        return null;
      }
    } else {
      String pos = mMainParameter.getText();
      if (pos.trim().length() == 0 || pos.equals(mPosPrompt)) {
        showErrorDialog("Must define the id of the colliding view");
        return null;
      }

      keyPosition.setAttribute(MotionSceneAttrs.MOTION, MotionSceneAttrs.KeyTrigger.MOTION_TRIGGER_ON_COLLISION, pos);
    }
    keyPosition.setAttribute(MotionSceneAttrs.MOTION, MotionSceneAttrs.KeyTrigger.ON_CROSS, mOnCross.getText());

    MTag ret = toCommit.commit("Create KeyTrigger");
    Track.createKeyTrigger(mMotionEditor.myTrack);
    mMotionEditor.dataChanged();
    super.create();
    return ret;
  }

  @Override
  public String getName() {
    return "KeyTrigger";
  }

}
