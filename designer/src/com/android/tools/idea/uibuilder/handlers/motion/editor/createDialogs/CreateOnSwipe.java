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

import java.util.Locale;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JSeparator;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.util.ArrayList;

/**
 * This is the dialog that pops up when you create a OnSwipe in a Transition
 */
public class CreateOnSwipe extends BaseCreatePanel {
  static String TITLE = "Create onSwipe";
  JComboBox<String> mTransitions = MEUI.makeComboBox(new String[]{});
  MTag[] mTransitionTags;
  JComboBox<String> mAnchorIdBox = MEUI.makeComboBox(new String[]{""});
  JComboBox<String> mDragDirection;
  JComboBox<String> mAnchorSide;

  String[] options = {"autoComplete",
    "Jump to Start",
    "Jump to End",
    "Animate to Start",
    "Animate to End"
  };
  JComboBox<String> comboBox = new JComboBox<>(options);

  public CreateOnSwipe() {
    icon = MEIcons.CREATE_ON_SWIPE;

    GridBagConstraints gbc = new GridBagConstraints();
    int y = 0;
    grid(gbc, 0, y++, 1, 1);
    gbc.weighty = 0;
    gbc.ipadx = MEUI.scale(60);
    gbc.insets = MEUI.dialogLabelInsets();

    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(new JLabel("CREATE ONSWIPE"), gbc);
    grid(gbc, 0, y++);
    gbc.weighty = 0;
    gbc.insets = MEUI.dialogSeparatorInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(new JSeparator(), gbc);

    grid(gbc, 0, y++);
    gbc.weighty = 0;
    gbc.insets = MEUI.dialogLabelInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(new JLabel("In Transition"), gbc);
    grid(gbc, 0, y++);
    gbc.insets = MEUI.dialogControlInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(mTransitions, gbc);

    grid(gbc, 0, y++);
    gbc.weighty = 0;
    gbc.insets = MEUI.dialogLabelInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(new JLabel("Drag Direction"), gbc);
    grid(gbc, 0, y++);
    gbc.insets = MEUI.dialogControlInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(mDragDirection = newComboBox("dragUp", "dragDown", "dragLeft", "dragRight"), gbc);

    grid(gbc, 0, y++);
    gbc.weighty = 0;
    gbc.insets = MEUI.dialogLabelInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(new JLabel("Anchor Side"), gbc);
    grid(gbc, 0, y++);
    gbc.insets = MEUI.dialogControlInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(mAnchorSide = newComboBox("Top", "Left", "Bottom", "Right"), gbc);

    grid(gbc, 0, y++);
    gbc.weighty = 0;
    gbc.insets = MEUI.dialogLabelInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(new JLabel("Anchor ID"), gbc);
    grid(gbc, 0, y++);
    gbc.insets = MEUI.dialogControlInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(mAnchorIdBox, gbc);

    gbc.weighty = 1;
    grid(gbc, 0, y++, 2, 1);
    add(new JComponent() {
    }, gbc);
    gbc.weighty = 0;
    gbc.weightx = 1;
    gbc.insets = MEUI.dialogBottomButtonInsets();
    gbc.anchor = GridBagConstraints.SOUTHEAST;
    //noinspection UnusedAssignment This is to maintain the pattern
    grid(gbc, 0, y++, 2, 1);
    JButton ok = new JButton("Add");
    add(ok, gbc);
    ok.addActionListener(e -> create());
  }

  @Override
  protected boolean populateDialog() {

    MeModel model = mMotionEditor.getMeModel();
    mTransitionTags = model.motionScene.getChildTags("Transition");
    if (mTransitionTags.length == 0) {
      showPreconditionDialog("You must create a transition first");
      return false;
    }
    mTransitions.removeAllItems();

    for (int i = 0; i < mTransitionTags.length; i++) {
      String id = Utils.stripID(mTransitionTags[i].getAttributeValue(MotionSceneAttrs.Transition.ATTR_ID));
      String start = Utils.stripID(mTransitionTags[i].getAttributeValue(MotionSceneAttrs.Transition.ATTR_CONSTRAINTSET_START));
      String end = Utils.stripID(mTransitionTags[i].getAttributeValue(MotionSceneAttrs.Transition.ATTR_CONSTRAINTSET_END));
      mTransitions.addItem(Utils.formatTransition(id, start, end));
    }
    {
      MTag tag = mMotionEditor.getSelectedTag();
      if (tag != null && tag.getTagName().equals("Transition")) {
        for (int i = 0; i < mTransitionTags.length; i++) {
          if (mTransitionTags[i] == tag) {
            mTransitions.setSelectedIndex(i);
          }
        }
      }
    }
    mAnchorIdBox.removeAllItems();
    mAnchorIdBox.addItem("(none)");
    if (model.layout != null) {
      ArrayList<MTag> children = model.layout.getChildren();
      for (MTag child : children) {
        String id = child.getAttributeValue("id");
        if (id == null) continue;
        mAnchorIdBox.addItem(Utils.stripID(id));
      }
    }
    return true;
  }

  @Override
  public MTag create() {
    if (DEBUG) {
      Debug.log("create");
    }
    MTag transition = mTransitionTags[mTransitions.getSelectedIndex()];
    // TODO error checking

    MeModel model = mMotionEditor.getMeModel();
    MTag.TagWriter writer = transition.getChildTagWriter(MotionSceneAttrs.Tags.ON_SWIPE);

    if (mAnchorIdBox.getSelectedIndex() != 0) {
      String str = (String) mAnchorIdBox.getSelectedItem();
      writer.setAttribute(MotionSceneAttrs.MOTION, MotionSceneAttrs.OnSwipe.ATTR_TOUCH_ANCHOR_ID, addIdPrefix(str));
    }

    if (mDragDirection.getSelectedIndex() != 0) {
      String str = (String) mDragDirection.getSelectedItem();
      writer.setAttribute(MotionSceneAttrs.MOTION, MotionSceneAttrs.OnSwipe.ATTR_DRAG_DIRECTION, str);
    }
    if (mAnchorSide.getSelectedIndex() != 0) {
      String str = (String) mAnchorSide.getSelectedItem();
      if (str != null) {
        str = str.toLowerCase(Locale.getDefault());
      }
      writer.setAttribute(MotionSceneAttrs.MOTION, MotionSceneAttrs.OnSwipe.ATTR_TOUCH_ANCHOR_SIDE, str);
    }

    MTag ret = writer.commit("Create OnSwipe");
    Track.createOnSwipe(mMotionEditor.myTrack);
    mMotionEditor.setMTag(model);
    super.create();
    return ret;
  }

  @Override
  public String getName() {
    return "Swipe Handler";
  }

}
