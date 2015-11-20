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
package com.android.tools.idea.welcome.wizard;

import com.android.utils.HtmlBuilder;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.util.ui.SwingHelper;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Provides guidance for setting up Intel® HAXM on Linux platform.
 */
public class LinuxHaxmInfoStep extends FirstRunWizardStep {
  private JPanel myRoot;
  private JEditorPane myUrlPane;

  private static final String KVM_DOCUMENTATION_URL = "http://developer.android.com/tools/devices/emulator.html#vm-linux";

  public LinuxHaxmInfoStep() {
    super("Emulator Settings");
    setComponent(myRoot);
  }

  @Override
  public void init() {

  }

  @Nullable
  @Override
  public JLabel getMessageLabel() {
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myUrlPane;
  }

  @Override
  public boolean isStepVisible() {
    return SystemInfo.isLinux;
  }

  private void createUIComponents() {
    myUrlPane = SwingHelper.createHtmlViewer(true, null, null, null);
    myUrlPane.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
    HtmlBuilder description = new HtmlBuilder();
    description.addHtml("Search for install instructions for your particular Linux configuration (");
    description.addLink("Android KVM Linux Installation", KVM_DOCUMENTATION_URL);
    description.addHtml(") that KVM is enabled for faster Android emulator performance.");
    myUrlPane.setText(description.getHtml());
    SwingHelper.setHtml(myUrlPane, description.getHtml(), UIUtil.getLabelForeground());
    myUrlPane.setBackground(UIUtil.getLabelBackground());

  }
}
