/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.ui.treeview;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

import static javax.swing.Box.createHorizontalStrut;
import static org.jetbrains.android.util.AndroidUiUtil.setUpAsHtmlLabel;

public class MultiLineTreeCellRenderer extends JPanel implements TreeCellRenderer, HyperlinkListener {
  @NotNull private final JLabel myIconLabel;
  private final JTextPane myTextPane;

  public MultiLineTreeCellRenderer() {
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
    myIconLabel = new JLabel();
    add(myIconLabel);

    add(createHorizontalStrut(4));

    myTextPane = new JTextPane();
    setUpAsHtmlLabel(myTextPane);
    myTextPane.addHyperlinkListener(this);
    add(myTextPane);
  }

  @Override
  public Component getTreeCellRendererComponent(JTree tree,
                                                Object value,
                                                boolean selected,
                                                boolean expanded,
                                                boolean leaf,
                                                int row,
                                                boolean hasFocus) {
    String text = tree.convertValueToText(value, selected, expanded, leaf, row, hasFocus);
    setEnabled(tree.isEnabled());
    myIconLabel.setIcon(getIcon(value));
    myTextPane.setText(text);
    return this;
  }

  @Nullable
  public Icon getIcon(@Nullable Object value) {
    return null;
  }

  @Override
  public void hyperlinkUpdate(HyperlinkEvent e) {
  }
}
