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
import java.util.Arrays;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSeparator;
import javax.swing.JTextField;

/**
 * This is the dialog that pops up when you create a KeyPosition
 */
public class CreateKeyPosition extends BaseCreateKey {
  public static final boolean DEBUG = false;
  static String TITLE = "Create ConstraintSet";
  private static final String KEY_TAG = MotionSceneAttrs.Tags.KEY_POSITION;

  private final JTextField mPosition;
  private final JComboBox<String> mType;
  protected final JTextField mPercentX;
  protected final JTextField mPercentY;
  private final String POS_PROMPT = "0-100";
  private final String PERCENT_PROMPT = "float";
  String[] options = {"deltaRelative",
    "pathRelative",
    "parentRelative",
  };
  JComboBox<String> comboBox = MEUI.makeComboBox(options);
  MTag mKeyFrameSet;
  private MTag mSelectedTransition;

  public CreateKeyPosition() {
    icon = MEIcons.CREATE_TRANSITION;

    GridBagConstraints gbc = new GridBagConstraints();
    int y = createTop(gbc, "CREATE KEY POSITION");

    grid(gbc, 0, y++, 2, 1);
    gbc.weighty = 0;
    gbc.insets = MEUI.dialogSeparatorInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(new JSeparator(), gbc);

    grid(gbc, 0, y++, 2, 1);
    gbc.weighty = 0;
    gbc.insets = MEUI.dialogLabelInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(new JLabel("Position"), gbc);
    grid(gbc, 0, y++, 2, 1);
    gbc.insets = MEUI.dialogControlInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(mPosition = newTextField(POS_PROMPT, 15), gbc);

    grid(gbc, 0, y++, 2, 1);
    gbc.insets = MEUI.dialogLabelInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(new JLabel("Type"), gbc);
    grid(gbc, 0, y++, 2, 1);
    gbc.insets = MEUI.dialogControlInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(mType = comboBox, gbc);

    grid(gbc, 0, y++, 2, 1);
    gbc.weighty = 0;
    gbc.insets = MEUI.dialogLabelInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(new JLabel("PercentX"), gbc);
    grid(gbc, 0, y++, 2, 1);
    gbc.insets = MEUI.dialogControlInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(mPercentX = newTextField(PERCENT_PROMPT, 15), gbc);

    grid(gbc, 0, y++, 2, 1);
    gbc.weighty = 0;
    gbc.insets = MEUI.dialogLabelInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(new JLabel("PercentY"), gbc);
    grid(gbc, 0, y++, 2, 1);
    gbc.insets = MEUI.dialogControlInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(mPercentY = newTextField(PERCENT_PROMPT, 15), gbc);

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
        mMatchTag.setText(selected[0].getAttributeValue("motionTarget"));
        String type = selected[0].getAttributeValue("keyPositionType");
        for (int i = 0; i < options.length; i++) {
          if (options[i].equals(type)) {
            mType.setSelectedIndex(i);
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
    String tag = getMotionTarget();
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

    keyPosition.setAttribute("motion", "motionTarget", tag);
    try {
      int posInt = Integer.parseInt(pos.trim());
      keyPosition.setAttribute("motion", "framePosition", pos.trim());
    } catch (Exception ex) {
      showErrorDialog("was not able to parse \"" + pos.trim() + "\"");
      return null;
    }

    keyPosition.setAttribute("motion", "keyPositionType", (String) mType.getSelectedItem());

    String xStr = mPercentX.getText().trim();
    if (xStr.length() > 0 && !xStr.equals(PERCENT_PROMPT)) {
      try {
        float f = Float.parseFloat(xStr);
        keyPosition.setAttribute("motion", "percentX", xStr);
      } catch (NumberFormatException ex) {
        showErrorDialog("was not able to parse \"" + xStr + "\"");

      }
    }
    String yStr = mPercentY.getText().trim();
    if (yStr.length() > 0 && !yStr.equals(PERCENT_PROMPT)) {
      try {
        float f = Float.parseFloat(yStr);
        keyPosition.setAttribute("motion", "percentY", yStr);
      } catch (NumberFormatException ex) {
        showErrorDialog("was not able to parse \"" + yStr + "\"");
      }
    }

    MTag ret = toCommit.commit("Create KeyPosition");
    Track.createKeyPosition(mMotionEditor.myTrack);
    mMotionEditor.dataChanged();
    super.create();
    return ret;
  }

  @Override
  public String getName() {
    return "KeyPosition";
  }

}
