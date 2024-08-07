/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run.state;

import com.google.idea.blaze.base.ui.UiUtil;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBCheckBox;
import javax.swing.JComponent;

/**
 * Optionally save console output to a file. All the state / serialization code is handled upstream,
 * this class just displays the UI elements.
 */
public class ConsoleOutputFileSettingsUi<T extends RunConfigurationBase<?>>
    extends SettingsEditor<T> {

  private static final BoolExperiment enabled = new BoolExperiment("save.to.file.enabled", true);

  private final JBCheckBox saveToFile =
      new JBCheckBox("Save console output to file:", /* selected= */ false);
  private final TextFieldWithBrowseButton outputFile = new TextFieldWithBrowseButton();
  private volatile boolean uiEnabled = true;

  public ConsoleOutputFileSettingsUi() {
    outputFile.addBrowseFolderListener(
        "Choose File to Save Console Output",
        "Console output would be saved to the specified file",
        /* project= */ null,
        FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
        TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    saveToFile.addActionListener(e -> outputFile.setEnabled(uiEnabled && saveToFile.isSelected()));
  }

  @Override
  public void resetEditorFrom(T config) {
    saveToFile.setSelected(config.isSaveOutputToFile());
    String fileOutputPath = config.getOutputFilePath();
    outputFile.setText(
        fileOutputPath == null ? "" : FileUtil.toSystemDependentName(fileOutputPath));
  }

  @Override
  public void applyEditorTo(T config) {
    String text = outputFile.getText();
    config.setFileOutputPath(
        StringUtil.isEmpty(text) ? null : FileUtil.toSystemIndependentName(text));
    config.setSaveOutputToFile(saveToFile.isSelected());
  }

  @Override
  protected JComponent createEditor() {
    return UiUtil.createHorizontalBox(/* gap= */ 5, saveToFile, outputFile);
  }

  public void setComponentEnabled(boolean componentEnabled) {
    uiEnabled = componentEnabled;
    updateVisibleAndEnabled();
  }

  private void updateVisibleAndEnabled() {
    if (!enabled.getValue()) {
      saveToFile.setSelected(false);
      saveToFile.setVisible(false);
      outputFile.setVisible(false);
      return;
    }
    saveToFile.setEnabled(uiEnabled);
    outputFile.setEnabled(uiEnabled && saveToFile.isSelected());
  }
}
