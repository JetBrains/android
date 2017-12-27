/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.hyperlink;

import com.google.common.base.Joiner;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static com.intellij.util.ui.UIUtil.getTextFieldBackground;

public class SyncIssueDetailsDialog extends JDialog {
  private JPanel contentPane;
  private JButton buttonOK;
  private JTextPane myExtraInfoDetails;

  public SyncIssueDetailsDialog(@NotNull String message, @NotNull List<String> details, @Nullable Window parent) {
    super(parent);
    setContentPane(contentPane);
    setModal(true);
    getRootPane().setDefaultButton(buttonOK);
    setTitle("Sync Issue Details");

    String separator = SystemProperties.getLineSeparator();
    String text = message + separator + separator + Joiner.on(separator).join(details);
    myExtraInfoDetails.setText(text);
    myExtraInfoDetails.setBackground(getTextFieldBackground());

    buttonOK.addActionListener(e -> onOK());
  }

  private void onOK() {
    dispose();
  }
}
