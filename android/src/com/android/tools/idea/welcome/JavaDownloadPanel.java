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

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Panel for setting JDK controls.
 */
public class JavaDownloadPanel {
  private JButton myDownloadButton;
  private JPanel myRootPanel;
  private JLabel myTitle;
  private JLabel myPrompt;
  private TextFieldWithBrowseButton myPathEntry;
  private JLabel myDownloadPrompt;
  private String myUrl;

  public JavaDownloadPanel() {
    myRootPanel.setBorder(BorderFactory.createEmptyBorder(24, 0, 24, 0));
    WelcomeUIUtils.makeButtonAHyperlink(myDownloadButton);
    myDownloadButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        browse();
      }
    });
  }

  private static TextBrowseFolderListener createListener(boolean isJdk) {
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    descriptor.setTitle(String.format("Select %s Location", isJdk ? "JDK" : "JRE"));
    descriptor.setDescription(
      String.format("Select %s installation folder", isJdk ? "Java Development Kit (JDK)" : "Java Runtime Environment (JRE)"));
    return new TextBrowseFolderListener(descriptor);
  }

  public void setTitle(String title) {
    myTitle.setText(title);
  }

  private void browse() {
    if (!StringUtil.isEmpty(myUrl)) {
      BrowserUtil.browse(myUrl);
    }
  }

  public void setLinkText(String label, String link) {
    myDownloadPrompt.setText(label);
    myDownloadButton.setText(link);
  }

  public void setDownloadUrl(boolean isJdk, String url) {
    myUrl = url;
    String jdkOrJre = isJdk ? "JDK" : "JRE";
    myPrompt.setText(String.format("We could not detect an up to date %1$s. Please select a path to %1$s:", jdkOrJre));
    myPathEntry.addBrowseFolderListener(createListener(isJdk));
  }

  public void hide() {
    myRootPanel.setVisible(false);
  }
}
