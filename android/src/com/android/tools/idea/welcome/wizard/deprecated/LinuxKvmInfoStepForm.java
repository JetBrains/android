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
import com.intellij.util.ui.SwingHelper;
import com.intellij.util.ui.UIUtil;
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
}
