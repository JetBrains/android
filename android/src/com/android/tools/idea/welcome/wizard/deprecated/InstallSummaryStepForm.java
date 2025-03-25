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
import com.intellij.util.ui.HTMLEditorKitBuilder;
import com.intellij.util.ui.JBUI;
import java.net.URL;
import javax.swing.JComponent;
import javax.swing.JPanel;
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
}
