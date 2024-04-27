/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;

import static com.android.tools.idea.avdmanager.AvdWizardUtils.TITLE_FONT;

/**
 * A help panel that displays help text and error messaging for AVD options.
 *
 * Titles are obtained from each component using {@link JComponent#getClientProperty(Object)}
 * using TITLE_KEY as they key to retrieve them. Descriptions are obtained from the tooltip text
 * of every component.
 */
public class AvdConfigurationOptionHelpPanel extends JPanel {

  /**
   * Key used to obtain descriptions for components set previously with Component.setClientProperty(TITLE_KEY, description)
   */
  public static final String TITLE_KEY = "TITLE";

  private HaxmAlert myHaxmAlert;
  private JBLabel myTitle;
  private JSeparator mySeparator;
  private JPanel myContentPanel;
  private JPanel myRoot;
  private JBLabel myDescription;

  public AvdConfigurationOptionHelpPanel() {
    mySeparator.setForeground(JBColor.foreground());
    myRoot.setBackground(JBColor.WHITE);
    myTitle.setFont(TITLE_FONT);
    add(myRoot);
  }

  public void clearValues() {
    myTitle.setText(null);
    myDescription.setText(null);
    ((CardLayout)myContentPanel.getLayout()).show(myContentPanel, "NothingSelected");
  }

  public void setValues(JComponent component) {
    String title = (String)component.getClientProperty(TITLE_KEY);
    myTitle.setText(title);
    if (title != null) {
      String text = component.getToolTipText();
      myDescription.setText(text);
      ((CardLayout)myContentPanel.getLayout()).show(myContentPanel, "Info");
    }
    else {
      myDescription.setText(null);
    }
  }

  public void setSystemImageDescription(SystemImageDescription desc) {
    myHaxmAlert.setSystemImageDescription(desc);
  }
}
