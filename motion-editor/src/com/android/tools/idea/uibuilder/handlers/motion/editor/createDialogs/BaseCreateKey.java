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

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEUI;
import java.awt.CardLayout;
import java.awt.GridBagConstraints;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JTextField;

/**
 * Base class of creating of KeyFrame objects
 */
public class BaseCreateKey extends BaseCreatePanel {
  protected JTextField mMatchTag;
  protected JComboBox<String> mViewList;
  private boolean mUseTag;

  int createTop(GridBagConstraints gbc, String title) {
    int y = 0;
    grid(gbc, 0, y++, 2, 1);
    gbc.weighty = 0;
    gbc.ipadx = MEUI.scale(60);
    gbc.insets = MEUI.dialogTitleInsets();

    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(new JLabel(title), gbc);
    grid(gbc, 0, y++, 2, 1);
    gbc.weighty = 0;
    gbc.insets = MEUI.dialogSeparatorInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(new JSeparator(), gbc);

    JRadioButton tagButton, idButton;
    grid(gbc, 0, y, 1, 1);
    gbc.weighty = 0;
    gbc.insets = MEUI.dialogLabelInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(tagButton = new JRadioButton("TAG"), gbc);

    grid(gbc, 1, y, 1, 1);
    gbc.weighty = 0;
    gbc.insets = MEUI.dialogControlInsets();
    gbc.anchor = GridBagConstraints.CENTER;
    add(idButton = new JRadioButton("ID"), gbc);

    ButtonGroup group = new ButtonGroup();
    group.add(tagButton);
    group.add(idButton);
    CardLayout cardLayout = new CardLayout();
    JPanel cardpanel = new JPanel(cardLayout);
    cardpanel.add(mMatchTag = newTextField("tag or regex", 15), "tag");
    String[] opt = {"views",
      "rot",
      "button1",
    };
    mViewList = MEUI.makeComboBox(opt);
    tagButton.addActionListener((e) -> {
      cardLayout.show(cardpanel, "tag");
      mUseTag = true;
    });
    idButton.addActionListener((e) -> {
      cardLayout.show(cardpanel, "id");
      mUseTag = false;
    });
    cardpanel.add(mMatchTag, "tag");
    cardpanel.add(mViewList, "id");
    grid(gbc, 0, ++y, 2, 1);
    gbc.anchor = GridBagConstraints.CENTER;
    gbc.insets = MEUI.dialogControlInsets();
    add(cardpanel, gbc);

    cardLayout.show(cardpanel, "id");
    idButton.setSelected(true);

    return ++y;
  }

  void populateTags(String[] layoutViewNames) {
    mViewList.setModel(new DefaultComboBoxModel<String>(layoutViewNames));
  }

  public String getMotionTarget() {
    if (mUseTag) {
      return mMatchTag.getText();
    }
    else {
      return "@+id/" + mViewList.getSelectedItem();
    }
  }
}
