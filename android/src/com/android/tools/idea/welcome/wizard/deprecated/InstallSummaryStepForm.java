/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard.deprecated;

import com.android.tools.idea.wizard.WizardConstants;
import com.intellij.ide.BrowserUtil;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.HTMLEditorKitBuilder;
import com.intellij.util.ui.JBUI;
import java.awt.Dimension;
import java.awt.Insets;
import java.net.URL;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

/**
 * Ui for installation summary step
 */
public final class InstallSummaryStepForm {
  private JTextPane mySummaryText;
  private JPanel myRoot;

  public InstallSummaryStepForm() {
    setupUI();
    mySummaryText.setEditorKit(HTMLEditorKitBuilder.simple());
    // There is no need to add whitespace on the top
    mySummaryText.setBorder(JBUI.Borders.empty(0, WizardConstants.STUDIO_WIZARD_INSET_SIZE, WizardConstants.STUDIO_WIZARD_INSET_SIZE,
                                               WizardConstants.STUDIO_WIZARD_INSET_SIZE));
    mySummaryText.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent event) {
        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          URL url = event.getURL();
          if (url != null) {
            BrowserUtil.browse(url);
          }
        }
      }
    });
  }

  public JComponent getRoot() {
    return myRoot;
  }

  public JTextPane getSummaryText() {
    return mySummaryText;
  }

  private void setupUI() {
    myRoot = new JPanel();
    myRoot.setLayout(new GridLayoutManager(5, 1, new Insets(0, 0, 0, 0), -1, -1));
    final JLabel label1 = new JLabel();
    label1.setText("<html>If you want to review or change any of your installation settings, click Previous.</html>");
    myRoot.add(label1,
               new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, 1, GridConstraints.SIZEPOLICY_FIXED,
                                   null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    myRoot.add(spacer1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                            GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 16), new Dimension(-1, 16), 0,
                                            false));
    final JLabel label2 = new JLabel();
    label2.setText("Current Settings:");
    myRoot.add(label2,
               new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JScrollPane scrollPane1 = new JScrollPane();
    myRoot.add(scrollPane1, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                null, 0, false));
    mySummaryText = new JTextPane();
    mySummaryText.setEditable(false);
    scrollPane1.setViewportView(mySummaryText);
    label2.setLabelFor(mySummaryText);
  }
}
