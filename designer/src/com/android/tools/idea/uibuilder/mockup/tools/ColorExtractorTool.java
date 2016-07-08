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
package com.android.tools.idea.uibuilder.mockup.tools;

import com.android.tools.idea.uibuilder.mockup.colorextractor.ExtractedColor;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import java.util.Collection;

/**
 * Tool for the mockup editor displaying the control to extract and save the color
 * from the mockup
 */
public class ColorExtractorTool implements MockupTool {

  private JComponent toolPanel;

  public ColorExtractorTool(Collection<ExtractedColor> colors) {
    JPanel content = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 5, true, false));

    toolPanel = new JBScrollPane(content,
                                 ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                 ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    for (ExtractedColor color : colors) {
      JPanel component = new ColorPanel(color).getComponent();
      component.setMaximumSize(component.getPreferredSize());
      content.add(component, 0);
    }
  }

  @Override
  public JComponent getToolPanel() {
    return toolPanel;
  }

  @Override
  public String getTitle() {
    return "Color Extractor";
  }
}
