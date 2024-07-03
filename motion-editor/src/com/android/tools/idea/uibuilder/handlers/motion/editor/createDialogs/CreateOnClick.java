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
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Debug;
import java.awt.GridBagConstraints;
import java.util.ArrayList;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSeparator;

/**
 * This is the dialog that pops up when you create a OnClick in a Transition
 */
public class CreateOnClick extends BaseCreatePanel {
  static String TITLE = "Create OnClick";
  JComboBox<String> mTransitions = MEUI.makeComboBox(new String[]{});
  MTag[] mTransitionTags;

  JComboBox<String> viewIds = MEUI.makeComboBox(new String[]{"layout"});

  public CreateOnClick() {
    icon = MEIcons.CREATE_ON_CLICK;
    viewIds.setEditable(true);
    GridBagConstraints gbc = new GridBagConstraints();
    int y = 0;
    grid(gbc, 0, y++, 1, 1);
    gbc.weighty = 0;
    gbc.ipadx = MEUI.scale(60);
    gbc.insets = MEUI.dialogTitleInsets();

    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(new JLabel("CREATE ONCLICK"), gbc);
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
    add(new JLabel("View To Click"), gbc);
    grid(gbc, 0, y++);
    gbc.insets = MEUI.dialogControlInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(viewIds, gbc);

    gbc.fill = GridBagConstraints.HORIZONTAL;

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
    mTransitionTags = model.motionScene.getChildTags(MotionSceneAttrs.Tags.TRANSITION);
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
    viewIds.removeAllItems();
    viewIds.addItem("(Base Layout)");
    if (model.layout != null) {
      ArrayList<MTag> children = model.layout.getChildren();
      for (MTag child : children) {
        String id = child.getAttributeValue("id");
        if (id == null) continue;
        viewIds.addItem(Utils.stripID(id));
      }
    }

    return true;
  }

  @Override
  public String getName() {
    return "Click Handler";
  }

  @Override
  public MTag create() {
    if (DEBUG) {
      Debug.log("create");
    }
    MTag transition = mTransitionTags[mTransitions.getSelectedIndex()];
    // TODO error checking
    MeModel model = mMotionEditor.getMeModel();
    MTag.TagWriter writer = transition.getChildTagWriter(MotionSceneAttrs.Tags.ON_CLICK);

    if (viewIds.getSelectedIndex() != 0) {
      String str = (String) viewIds.getSelectedItem();
      writer.setAttribute(MotionSceneAttrs.MOTION, MotionSceneAttrs.OnClick.ATTR_TARGET_ID, addIdPrefix(str));
    }

    MTag ret = writer.commit("Create OnClick");
    Track.createOnClick(mMotionEditor.myTrack);
    mMotionEditor.setMTag(model);
    super.create();
    return ret;

  }

}
