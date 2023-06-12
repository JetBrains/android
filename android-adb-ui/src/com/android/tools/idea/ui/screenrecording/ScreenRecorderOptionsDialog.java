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
package com.android.tools.idea.ui.screenrecording;

import static com.android.tools.idea.ui.screenrecording.ScreenRecorderAction.MAX_RECORDING_DURATION_MINUTES;
import static com.android.tools.idea.ui.screenrecording.ScreenRecorderAction.MAX_RECORDING_DURATION_MINUTES_LEGACY;

import com.android.tools.idea.help.AndroidWebHelpProvider;
import com.android.tools.idea.ui.AndroidAdbUiBundle;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBLabel;
import javax.swing.Action;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A dialog for setting the options for a screen recording.
 * Copied from com.android.tools.idea.ddms.screenrecord.ScreenRecorderOptionsDialog
 * TODO(b/235094713): Add tests
 */
class ScreenRecorderOptionsDialog extends DialogWrapper {
  @NonNls private static final String SCREENRECORDER_DIMENSIONS_KEY = "ScreenshotRecorder.Options.Dimensions";
  private final DefaultComboBoxModel<Integer> myComboBoxModel = new DefaultComboBoxModel<>(new Integer[]{100, 50, 30});

  private JPanel myPanel;
  @VisibleForTesting JTextField myBitRateTextField;
  @VisibleForTesting JCheckBox myShowTouchCheckBox;
  private JCheckBox myEmulatorRecordingCheckBox;
  private JComboBox<Integer> myResolutionPercentComboBox;
  private JBLabel myRecordingLengthLabel;

  public ScreenRecorderOptionsDialog(@NotNull Project project, boolean isEmulator, int apiLevel) {
    super(project, true);

    ScreenRecorderPersistentOptions options = ScreenRecorderPersistentOptions.getInstance();

    myResolutionPercentComboBox.setModel(myComboBoxModel);
    myComboBoxModel.setSelectedItem(options.getResolutionPercent());

    if (options.getBitRateMbps() > 0) {
      myBitRateTextField.setText(Integer.toString(options.getBitRateMbps()));
    }

    myShowTouchCheckBox.setSelected(options.getShowTaps());
    myEmulatorRecordingCheckBox.setSelected(options.getUseEmulatorRecording());
    myEmulatorRecordingCheckBox.setVisible(isEmulator);
    myEmulatorRecordingCheckBox.addItemListener(event -> updateMaxRecordingLengthLabel(isEmulator, apiLevel));

    updateMaxRecordingLengthLabel(isEmulator, apiLevel);

    setTitle("Screen Recorder Options");
    init();
  }

  private void updateMaxRecordingLengthLabel(boolean isEmulator, int apiLevel) {
    int maxRecordingDurationMin = isEmulator && myEmulatorRecordingCheckBox.isSelected() || apiLevel >= 34 ?
                                  MAX_RECORDING_DURATION_MINUTES : MAX_RECORDING_DURATION_MINUTES_LEGACY;
    myRecordingLengthLabel.setText(AndroidAdbUiBundle.message("screenrecord.options.info", maxRecordingDurationMin));
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
    return AndroidWebHelpProvider.HELP_PREFIX + "r/studio-ui/am-video.html";
  }

  @Override
  protected void createDefaultActions() {
    super.createDefaultActions();
    getOKAction().putValue(Action.NAME, AndroidAdbUiBundle.message("screenrecord.options.ok.button.text"));
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    ValidationInfo info =
      validateInteger(myBitRateTextField, AndroidAdbUiBundle.message("screenrecord.options.bit.rate.invalid"));
    if (info != null) {
      return info;
    }

    return super.doValidate();
  }

  @Nullable
  private static ValidationInfo validateInteger(JTextField textField, String errorMessage) {
    String s = getText(textField);
    if (s.isEmpty()) {
      return null;
    }

    try {
      Integer.parseInt(s);
    }
    catch (NumberFormatException e) {
      return new ValidationInfo(errorMessage, textField);
    }

    return null;
  }

  @Override
  protected void doOKAction() {
    ScreenRecorderPersistentOptions options = ScreenRecorderPersistentOptions.getInstance();
    options.setBitRateMbps(getIntegerValue(myBitRateTextField));
    options.setResolutionPercent((Integer)myComboBoxModel.getSelectedItem());
    options.setShowTaps(myShowTouchCheckBox.isSelected());
    options.setUseEmulatorRecording(myEmulatorRecordingCheckBox.isSelected());
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

  public boolean getUseEmulatorRecording() {
    return ScreenRecorderPersistentOptions.getInstance().getUseEmulatorRecording();
  }
}
