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
package com.android.tools.idea.gradle.structure.configurables.suggestions;

import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBLabel;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import javax.swing.*;
import java.awt.*;
import javax.swing.border.TitledBorder;

import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public abstract class SuggestionsFormUi {
  protected JPanel myMainPanel;
  protected JPanel myContentsPanel;
  protected JBLabel myLoadingLabel;
  protected JCheckBox myShowDismissedSuggestionsCheckBox;

  public SuggestionsFormUi() {
    setupUI();
  }

  protected void setViewComponent(JPanel issuesViewerPanel) {
    JScrollPane scrollPane =
      createScrollPane(issuesViewerPanel, VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    scrollPane.setViewportBorder(IdeBorderFactory.createEmptyBorder());
    myContentsPanel.add(scrollPane, BorderLayout.CENTER);
  }

  private void setupUI() {
    myMainPanel = new JPanel();
    myMainPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    myMainPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), null, TitledBorder.DEFAULT_JUSTIFICATION,
                                                           TitledBorder.DEFAULT_POSITION, null, null));
    myContentsPanel = new JPanel();
    myContentsPanel.setLayout(new BorderLayout(0, 0));
    myMainPanel.add(myContentsPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                         null, null, 0, false));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new BorderLayout(0, 0));
    panel1.setMinimumSize(new Dimension(60, 24));
    panel1.setPreferredSize(new Dimension(60, 24));
    myContentsPanel.add(panel1, BorderLayout.SOUTH);
    myShowDismissedSuggestionsCheckBox = new JCheckBox();
    myShowDismissedSuggestionsCheckBox.setText("Show dismissed suggestions");
    myShowDismissedSuggestionsCheckBox.setMnemonic('S');
    myShowDismissedSuggestionsCheckBox.setDisplayedMnemonicIndex(0);
    myShowDismissedSuggestionsCheckBox.setVisible(false);
    panel1.add(myShowDismissedSuggestionsCheckBox, BorderLayout.WEST);
    myLoadingLabel = new JBLabel();
    myLoadingLabel.setText("Loading... ");
    panel1.add(myLoadingLabel, BorderLayout.EAST);
  }
}
