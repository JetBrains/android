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
package com.android.tools.idea.gradle.project;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.settings.LocationSettingType;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.io.File;

/**
 * Dialog where users select the path of the local Gradle installation to use when importing a project.
 */
public class ChooseGradleHomeDialog extends DialogWrapper {
  @NotNull private final GradleInstallationManager myInstallationManager;

  private TextFieldWithBrowseButton myGradleHomePathField;
  private JBLabel myGradleHomeLabel;
  private JPanel myPanel;

  protected ChooseGradleHomeDialog() {
    super(null);
    myInstallationManager = ServiceManager.getService(GradleInstallationManager.class);
    init();
    initValidation();
    setTitle("Import Gradle Project");


    FileChooserDescriptor fileChooserDescriptor = GradleUtil.getGradleHomeFileChooserDescriptor();
    myGradleHomePathField.addBrowseFolderListener("", GradleBundle.message("gradle.settings.text.home.path"), null, fileChooserDescriptor,
                                                  TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT, false);

    myGradleHomeLabel.setLabelFor(myGradleHomePathField.getTextField());
    // This prevents the weird sizing in Linux.
    getPeer().getWindow().pack();

    myGradleHomePathField.setText(GradleUtil.getLastUsedGradleHome());
    myGradleHomePathField.getTextField().getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        initValidation();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        initValidation();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
      }
    });
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    LocationSettingType locationSettingType = validateLocation();
    switch (locationSettingType) {
      case EXPLICIT_CORRECT:
        return super.doValidate();
      default:
        return new ValidationInfo(locationSettingType.getDescription(GradleConstants.SYSTEM_ID), myGradleHomePathField.getTextField());
    }
  }

  @NotNull
  private LocationSettingType validateLocation() {
    String gradleHome = myGradleHomePathField.getText();
    if (gradleHome.isEmpty()) {
      return LocationSettingType.UNKNOWN;
    }
    File gradleHomePath = new File(FileUtil.toSystemDependentName(gradleHome));
    return myInstallationManager.isGradleSdkHome(gradleHomePath)
           ? LocationSettingType.EXPLICIT_CORRECT
           : LocationSettingType.EXPLICIT_INCORRECT;
  }

  void storeLastUsedGradleHome() {
    GradleUtil.storeLastUsedGradleHome(myGradleHomePathField.getText());
  }
}
