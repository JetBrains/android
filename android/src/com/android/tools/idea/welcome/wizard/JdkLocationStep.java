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

import com.android.tools.idea.sdk.Jdks;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.config.JdkDetection;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * Wizard step for specifying JDK location.
 */
public class JdkLocationStep extends FirstRunWizardStep {
  private final ScopedStateStore.Key<String> myPathKey;
  @NotNull private final FirstRunWizardMode myMode;
  private JPanel myContents;
  private TextFieldWithBrowseButton myJdkPath;
  private com.intellij.ui.HyperlinkLabel myDownloadPageLink;
  private JLabel myError;
  private JButton myDetectButton;
  // Show errors only after the user touched the value
  private boolean myUserInput = false;

  public JdkLocationStep(@NotNull ScopedStateStore.Key<String> pathKey, @NotNull FirstRunWizardMode mode) {
    super("Java Settings");
    myPathKey = pathKey;
    myMode = mode;
    myDownloadPageLink.setHyperlinkText(getLinkText());
    myDownloadPageLink.setHyperlinkTarget(Jdks.DOWNLOAD_JDK_7_URL);
    myDownloadPageLink.getParent().invalidate();
    setComponent(myContents);
    myError.setForeground(JBColor.red);
    FileChooserDescriptor folderDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    myJdkPath.addBrowseFolderListener("Select JDK Location", "Select compatible JDK location", null, folderDescriptor);
    myError.setText(null);
    myDetectButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myDetectButton.setEnabled(false);
        JdkDetection.startWithProgressIndicator(new JdkDetection.JdkDetectionResult() {
          @Override
          public void onSuccess(String newJdkPath) {
            if (newJdkPath == null) {
              String message = AndroidBundle.message("android.wizard.jdk.autodetect.result.not.found");
              ExternalSystemUiUtil.showBalloon(myJdkPath, MessageType.INFO, message);
            }
            else {
              myJdkPath.setText(newJdkPath);
            }
            myDetectButton.setEnabled(true);
          }

          @Override
          public void onCancel() {
            myDetectButton.setEnabled(true);
          }
        });
      }
    });
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
  public boolean validate() {
    String path = myState.get(myPathKey);
    if (!StringUtil.isEmpty(path)) {
      myUserInput = true;
    }
    File userInput = StringUtil.isEmptyOrSpaces(path) ? null : new File(path);
    String message = JdkDetection.validateJdkLocation(userInput);
    if (myUserInput) {
      setErrorHtml(message);
    }
    return StringUtil.isEmpty(message);
  }

  @Override
  public void init() {
    register(myPathKey, myJdkPath);
  }

  @Override
  public boolean isStepVisible() {
    return !myMode.hasValidJdkLocation();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myJdkPath;
  }

  @NotNull
  @Override
  public JLabel getMessageLabel() {
    return myError;
  }
}
