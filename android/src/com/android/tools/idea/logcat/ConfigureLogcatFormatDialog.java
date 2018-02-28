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
package com.android.tools.idea.logcat;

import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.logcat.LogCatHeader;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.time.Instant;

/**
 * A dialog allowing users to customize a final-pass format to be used by
 * {@link AndroidLogcatFormatter}.
 */
public class ConfigureLogcatFormatDialog extends DialogWrapper {
  private final AndroidLogcatPreferences myPreferences;
  private final AndroidLogcatFormatter myFormatter;
  private final String mySampleOutput;

  private JPanel topPanel;
  private JCheckBox myShowTimeCheckBox;
  private JCheckBox myShowPidTidCheckBox;
  private JCheckBox myShowPackageNameCheckBox;
  private JCheckBox myShowTag;
  private JLabel myDemoLabel;
  private String myFormatString;

  ConfigureLogcatFormatDialog(@NotNull Project project, @NotNull AndroidLogcatFormatter formatter) {
    super(project, false, IdeModalityType.PROJECT);
    init();
    setTitle(AndroidBundle.message("android.configure.logcat.header.title"));

    myPreferences = AndroidLogcatPreferences.getInstance(project);
    myFormatter = formatter;

    Instant timestamp = Instant.ofEpochMilli(1_517_955_388_555L);
    LogCatHeader header = new LogCatHeader(LogLevel.INFO, 123, 456, "com.android.sample", "SampleTag", timestamp);

    mySampleOutput = myFormatter.formatMessageFull(header, "This is a sample message");

    myFormatString = myPreferences.LOGCAT_FORMAT_STRING;
    myShowTimeCheckBox.setSelected(myFormatString.isEmpty() || myFormatString.contains("%1$s"));
    myShowPidTidCheckBox.setSelected(myFormatString.isEmpty() || myFormatString.contains("%2$s"));
    myShowPackageNameCheckBox.setSelected(myFormatString.isEmpty() || myFormatString.contains("%3$s"));
    myShowTag.setSelected(myFormatString.isEmpty() || myFormatString.contains("%5$s"));

    ItemListener checkboxListener = new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        rebuildFormatString();
      }
    };
    myShowTimeCheckBox.addItemListener(checkboxListener);
    myShowPidTidCheckBox.addItemListener(checkboxListener);
    myShowPackageNameCheckBox.addItemListener(checkboxListener);
    myShowTag.addItemListener(checkboxListener);

    updateDemoLabel();
  }

  private void rebuildFormatString() {
    if (myShowTimeCheckBox.isSelected() &&
        myShowPidTidCheckBox.isSelected() &&
        myShowPackageNameCheckBox.isSelected() &&
        myShowTag.isSelected()) {
      // Disable format string if there's nothing to filter out
      myFormatString = "";
    }
    else {
      myFormatString = AndroidLogcatFormatter.createCustomFormat(
        myShowTimeCheckBox.isSelected(),
        myShowPidTidCheckBox.isSelected(),
        myShowPackageNameCheckBox.isSelected(),
        myShowTag.isSelected());
    }

    updateDemoLabel();
  }

  private void updateDemoLabel() {
    myDemoLabel.setText(myFormatter.formatMessage(myFormatString, mySampleOutput));
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return topPanel;
  }

  @Override
  protected void doOKAction() {
    myPreferences.LOGCAT_FORMAT_STRING = myFormatString;
    super.doOKAction();
  }
}
