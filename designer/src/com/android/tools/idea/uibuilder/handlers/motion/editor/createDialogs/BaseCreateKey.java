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

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEUI;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import java.awt.CardLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;

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
    gbc.insets = MEUI.insets(2, 5, 1, 5);

    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(new JLabel(title), gbc);
    grid(gbc, 0, y++, 2, 1);
    gbc.weighty = 0;
    gbc.anchor = GridBagConstraints.CENTER;
    add(new JSeparator(), gbc);

    JRadioButton b1, b2;
    grid(gbc, 0, y, 1, 1);
    gbc.weighty = 0;
    gbc.anchor = GridBagConstraints.CENTER;
    add(b1 = new JRadioButton("TAG"), gbc);
    grid(gbc, 1, y++, 1, 1);
    gbc.weighty = 0;
    gbc.anchor = GridBagConstraints.CENTER;
    add(b2 = new JRadioButton("ID"), gbc);
    ButtonGroup group = new ButtonGroup();
    group.add(b1);
    group.add(b2);
    CardLayout cardLayout = new CardLayout();
    JPanel cardpanel = new JPanel(cardLayout);
    cardpanel.add(mMatchTag = newTextField("tag or regex", 15), "tag");
    String[] opt = {"views",
      "rot",
      "button1",
    };
    mViewList = MEUI.makeComboBox(opt);
    b1.addActionListener((e) -> {
      cardLayout.show(cardpanel, "tag");
      mUseTag = true;
    });
    b2.addActionListener((e) -> {
      cardLayout.show(cardpanel, "id");
      mUseTag = false;
    });
    cardpanel.add(mMatchTag, "tag");
    cardpanel.add(mViewList, "id");
    grid(gbc, 0, y++, 2, 1);
    gbc.anchor = GridBagConstraints.CENTER;
    add(cardpanel, gbc);
    return y;
  }

  void populateTags(String[] layoutViewNames) {
    mViewList.setModel(new DefaultComboBoxModel<String>(layoutViewNames));
  }

  public String getMotionTarget() {
    if (mUseTag) {
      return mMatchTag.getText();
    } else {
      return "@+id/" + mViewList.getSelectedItem();
    }
  }
}
