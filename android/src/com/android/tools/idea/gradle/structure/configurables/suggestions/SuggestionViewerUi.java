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

import com.android.tools.adtui.HtmlLabel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;

public abstract class SuggestionViewerUi {
  public static final String SUGGESTION_VIEWER_NAME = "SuggestionViewer";

  protected JPanel myPanel;
  protected HtmlLabel myText;
  protected JButton myUpdateButton;
  protected JPanel myButtonPanel;
  protected JPanel myTextPanel;

  public SuggestionViewerUi(boolean isLast) {
    myPanel.setName(SUGGESTION_VIEWER_NAME);
    myPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    myPanel.setBackground(UIUtil.getTextFieldBackground());
    myButtonPanel.setBackground(UIUtil.getTextFieldBackground());
    myTextPanel.setBackground(UIUtil.getTextFieldBackground());
    myText.setBackground(UIUtil.getTextFieldBackground());
    HtmlLabel.setUpAsHtmlLabel(myText, myUpdateButton.getFont());
    // The last item does not need a separator line at the bottom.
    if (isLast) {
      myPanel.setBorder(JBUI.Borders.empty());
    }
  }
}
