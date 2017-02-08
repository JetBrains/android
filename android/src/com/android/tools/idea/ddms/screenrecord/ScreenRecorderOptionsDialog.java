/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.ddms.screenrecord;

import com.android.ddmlib.ScreenRecorderOptions;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

public class ScreenRecorderOptionsDialog extends DialogWrapper {
  @NonNls private static final String SCREENRECORDER_DIMENSIONS_KEY = "ScreenshotRecorder.Options.Dimensions";

  private static final int DEFAULT_BITRATE_MBPS = 4;

  private static int ourBitRateMbps = DEFAULT_BITRATE_MBPS;
  private static int ourWidth;
  private static int ourHeight;
  private static boolean ourShowTouches;

  private JPanel myPanel;
  private JTextField myBitRateTextField;
  private JTextField myWidthTextField;
  private JTextField myHeightTextField;
  private JCheckBox myShowTouchCheckBox;

  public ScreenRecorderOptionsDialog(@NotNull Project project) {
    super(project, true);

    if (ourWidth > 0) {
      myWidthTextField.setText(Integer.toString(ourWidth));
    }

    if (ourHeight > 0) {
      myHeightTextField.setText(Integer.toString(ourHeight));
    }

    if (ourBitRateMbps > 0) {
      myBitRateTextField.setText(Integer.toString(ourBitRateMbps));
    }

    myShowTouchCheckBox.setSelected(ourShowTouches);

    setTitle("Screen Recorder Options");
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return SCREENRECORDER_DIMENSIONS_KEY;
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return "https://developer.android.com/r/studio-ui/am-video.html";
  }

  @Override
  protected void doHelpAction() {
    String helpId = getHelpId();
    assert helpId != null; // Otherwise, doHelpAction would not be triggered
    BrowserUtil.browse(helpId);
  }

  @Override
  protected void createDefaultActions() {
    super.createDefaultActions();
    getOKAction().putValue(Action.NAME, AndroidBundle.message("android.ddms.screenrecord.options.ok.button.text"));
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    ValidationInfo info = validateIntegerMultipleOf(myBitRateTextField, 1, "Bit Rate must be an integer.");
    if (info != null) {
      return info;
    }

    // MediaEncoder prefers sizes that are multiples of 16 (https://code.google.com/p/android/issues/detail?id=37769).
    info = validateIntegerMultipleOf(myWidthTextField, 16, "Width must be an integer.");
    if (info != null) {
      return info;
    }

    info = validateIntegerMultipleOf(myHeightTextField, 16, "Height must be an integer.");
    if (info != null) {
      return info;
    }

    return super.doValidate();
  }

  @Nullable
  private static ValidationInfo validateIntegerMultipleOf(JTextField textField, int multiple, String errorMessage) {
    String s = getText(textField);
    if (s.isEmpty()) {
      return null;
    }

    int x;
    try {
      x = Integer.parseInt(s);
    }
    catch (NumberFormatException e) {
      return new ValidationInfo(errorMessage, textField);
    }

    return (x % multiple > 0) ? new ValidationInfo("Must be a multiple of " + multiple, textField) : null;
  }

  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  @Override
  protected void doOKAction() {
    ourBitRateMbps = getIntegerValue(myBitRateTextField);
    ourHeight = getIntegerValue(myHeightTextField);
    ourWidth = getIntegerValue(myWidthTextField);
    ourShowTouches = myShowTouchCheckBox.isSelected();
    super.doOKAction();
  }

  private static int getIntegerValue(JTextField textField) {
    String s = getText(textField);
    return s.isEmpty() ? 0 : Integer.parseInt(s);
  }

  private static String getText(JTextField textField) {
    Document doc = textField.getDocument();
    try {
      return doc.getText(0, doc.getLength()).trim();
    }
    catch (BadLocationException e) { // can't happen
      return "";
    }
  }

  public ScreenRecorderOptions getOptions() {
    return new ScreenRecorderOptions.Builder().setBitRate(ourBitRateMbps).setSize(ourWidth, ourHeight).setShowTouches(ourShowTouches).build();
  }
}
