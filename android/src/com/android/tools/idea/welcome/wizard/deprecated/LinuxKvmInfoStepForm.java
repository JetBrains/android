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

import com.android.utils.HtmlBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.SwingHelper;
import com.intellij.util.ui.UIUtil;
import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;

/**
 * UI for the LinuxKvmInfoStep
 */
public class LinuxKvmInfoStepForm {
  private JPanel myRoot;
  private JEditorPane myUrlPane;

  @VisibleForTesting
  public static final String KVM_DOCUMENTATION_URL = "http://developer.android.com/r/studio-ui/emulator-kvm-setup.html";

  public LinuxKvmInfoStepForm() {
    HtmlBuilder description = new HtmlBuilder();
        try {
      setupUI();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    description.addHtml("Follow ");
    description.addLink("Configure hardware acceleration for the Android Emulator", KVM_DOCUMENTATION_URL);
    description.addHtml(" to enable KVM and achieve better performance.");

    SwingHelper.setHtml(myUrlPane, description.getHtml(), UIUtil.getLabelForeground());
  }

  public JComponent getRoot() {
    return myRoot;
  }

  public JEditorPane getUrlPane() {
    return myUrlPane;
  }

  private void createUIComponents() {
    myUrlPane = SwingHelper.createHtmlViewer(true, null, null, null);
    myUrlPane.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
    myUrlPane.setBackground(UIUtil.getLabelBackground());

    myUrlPane.setText(" ");
  }

  private void setupUI() {
    createUIComponents();
    myRoot = new JPanel();
    myRoot.setLayout(new GridLayoutManager(6, 2, new Insets(0, 0, 0, 0), -1, -1));
    myRoot.setMinimumSize(new Dimension(400, 174));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setText("<html>We have detected that your system can run the Android emulator in an accelerated performance mode.</html>");
    myRoot.add(jBLabel1,
               new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, 1, GridConstraints.SIZEPOLICY_FIXED,
                                   null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    myRoot.add(spacer1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                            GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 16), null, 0, false));
    final JBLabel jBLabel2 = new JBLabel();
    jBLabel2.setText(
      "<html>Linux-based systems support virtual machine acceleration through the KVM (Kernel-based Virtual Machine) software package.</html>");
    myRoot.add(jBLabel2, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                             GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(780, -1), null, 0, false));
    final Spacer spacer2 = new Spacer();
    myRoot.add(spacer2, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                            GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 16), null, 0, false));
    myRoot.add(myUrlPane, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                              GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(150, 50), null, 0, false));
  }

  public JComponent getRootComponent() { return myRoot; }
}
