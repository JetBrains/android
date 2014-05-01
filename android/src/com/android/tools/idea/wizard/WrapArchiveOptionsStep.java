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
package com.android.tools.idea.wizard;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.util.Set;

/**
 * Step for selecting archive to import and specifying Gradle subproject name.
 */
public final class WrapArchiveOptionsStep extends ModuleWizardStep {
  private static final Set<String> SUPPORTED_EXTENSIONS = ImmutableSet.of("jar", "aar");
  private final TemplateWizardState myWizardState;
  private final TemplateWizardStep.UpdateListener myListener;
  private JPanel myPanel;
  private JTextField myGradlePath;
  private TextFieldWithBrowseButton myArchivePath;
  private JBLabel myValidationStatus;
  private ValidationStatus myResult = ValidationStatus.EMPTY_PATH;

  public WrapArchiveOptionsStep(@Nullable Project project,
                                @NotNull TemplateWizardState state,
                                @Nullable TemplateWizardStep.UpdateListener listener) {
    myWizardState = state;
    myListener = listener == null ? new TemplateWizardStep.UpdateListener() {
      @Override
      public void update() {
        // Do nothing
      }
    } : listener;
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, true, true, false, false) {
      @Override
      public boolean isFileSelectable(VirtualFile file) {
        String extension = file.getExtension();
        return extension != null && SUPPORTED_EXTENSIONS.contains(extension.toLowerCase());
      }
    };
    myArchivePath.addBrowseFolderListener("Select Package", "Select jar or aar package to import as a new module", project, descriptor);
    myArchivePath.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        pathUpdated(myArchivePath.getText());
      }
    });
    myValidationStatus.setVisible(false);
    updateDataModel();
  }

  private void pathUpdated(@NotNull String newPath) {
    setDefaultModuleName(Files.getNameWithoutExtension(newPath));
    validate();
    updateDataModel();
  }

  private void setDefaultModuleName(String defaultName) {
    myGradlePath.setText(defaultName);
  }

  @Override
  public boolean validate() {
    return !MessageType.ERROR.equals(myResult.getMessageType());
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myArchivePath.getTextField();
  }

  @Override
  public void updateDataModel() {
    updateStep();
    myListener.update();
  }

  @Override
  public void updateStep() {
    String filePath = myArchivePath.getText().trim();
    String gradlePath = myGradlePath.getText().trim();

    myWizardState.put(WrapArchiveWizardPath.KEY_ARCHIVE, filePath);
    myWizardState.put(WrapArchiveWizardPath.KEY_GRADLE_PATH, gradlePath);
  }

  private enum ValidationStatus {
    OK, EMPTY_PATH, DOES_NOT_EXIST, NOT_AAR_OR_JAR, NO_MODULE_NAME, INVALID_MODULE_NAME, MODULE_EXISTS;

    public MessageType getMessageType() {
      switch (this) {
        case OK:
          return MessageType.INFO;
        case NOT_AAR_OR_JAR:
          return MessageType.WARNING;
        default:
          return MessageType.ERROR;
      }
    }
  }

}
