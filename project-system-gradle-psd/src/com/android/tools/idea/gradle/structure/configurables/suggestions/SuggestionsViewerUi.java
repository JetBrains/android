/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.VerticalLayout;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.UIUtil;
import java.awt.Insets;
import javax.swing.*;

public abstract class SuggestionsViewerUi {
  protected JBLabel myEmptyIssuesLabel;
  protected JPanel myMainPanel;

  public SuggestionsViewerUi() {
    setupUI();
    myMainPanel.setLayout(new VerticalLayout(0));
  }

  private void setupUI() {
    myMainPanel = new JPanel();
    myMainPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 4, 0, 4), -1, -1));
    myEmptyIssuesLabel = new JBLabel();
    myEmptyIssuesLabel.setFontColor(UIUtil.FontColor.BRIGHTER);
    myEmptyIssuesLabel.setHorizontalAlignment(0);
    myEmptyIssuesLabel.setText("No messages to display");
    myMainPanel.add(myEmptyIssuesLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                            null, 0, false));
  }
}
