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

import javax.swing.*;
import java.awt.*;

import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public abstract class SuggestionsFormUi {
  protected JPanel myMainPanel;
  protected JPanel myContentsPanel;
  protected JBLabel myLoadingLabel;
  protected JCheckBox myShowDismissedSuggestionsCheckBox;

  protected void setViewComponent(JPanel issuesViewerPanel) {
    JScrollPane scrollPane = createScrollPane(issuesViewerPanel, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    scrollPane.setViewportBorder(IdeBorderFactory.createEmptyBorder());
    myContentsPanel.add(scrollPane, BorderLayout.CENTER);
  }
}
