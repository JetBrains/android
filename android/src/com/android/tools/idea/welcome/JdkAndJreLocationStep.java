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
package com.android.tools.idea.welcome;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Wizard step for JDK and JRE locations.
 */
public class JdkAndJreLocationStep extends FirstRunWizardStep {
  private JPanel myContents;
  private TextFieldWithBrowseButton myJdkPath;
  private JButton myDownloadPageLink;

  public JdkAndJreLocationStep() {
    super("Java Settings");
    myDownloadPageLink.setText(getLinkText());
    WelcomeUIUtils.makeButtonAHyperlink(myDownloadPageLink);
    myDownloadPageLink.getParent().invalidate();
    setComponent(myContents);
  }

  private static String getLinkText() {
    // TODO ARM support?
    if (SystemInfo.isMac) {
      return "Mac OS X x64";
    }
    else if (SystemInfo.isLinux) {
      return SystemInfo.is32Bit ? "Linux x86" : "Linux x64";
    }
    else if (SystemInfo.isWindows) {
      return SystemInfo.is32Bit ? "Windows x86" : "Windows x64";
    }
    else {
      return SystemInfo.OS_NAME;
    }
  }

  @Override
  public void init() {

  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @NotNull
  @Override
  public JLabel getMessageLabel() {
    return null;
  }

  @Override
  public boolean isStepVisible() {
    return needsJdk();
  }

  private boolean needsJdk() {
    return true;
  }
}
