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

package com.android.tools.idea.refactoring.rtl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class RtlSupportDialog extends DialogWrapper {
  private JPanel myPanel;
  private JCheckBox myAndroidManifestCheckBox;
  private JCheckBox myLayoutsCheckBox;
  private JTextArea myLabel;
  private JCheckBox myReplaceLeftRightPropertiesCheckBox;
  private JCheckBox myGenerateV17VersionsCheckBox;

  private final RtlSupportProperties myProperties;

  public RtlSupportDialog(Project project) {
    super(project, true);

    myProperties = new RtlSupportProperties();

    setTitle(AndroidBundle.message("android.refactoring.rtl.addsupport.dialog.title"));
    setOKButtonText(AndroidBundle.message("android.refactoring.rtl.addsupport.dialog.ok.button.text"));

    myLayoutsCheckBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent itemEvent) {
        final boolean isSelected = myLayoutsCheckBox.isSelected();
        myReplaceLeftRightPropertiesCheckBox.setEnabled(isSelected);
        myGenerateV17VersionsCheckBox.setEnabled(isSelected);
      }
    });

    setDefaultValues();

    init();
  }

  private void setDefaultValues() {
    myAndroidManifestCheckBox.setSelected(true);
    myLayoutsCheckBox.setSelected(true);
  }

  @Override
  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myAndroidManifestCheckBox;
  }

  public final RtlSupportProperties getProperties() {
    myProperties.updateAndroidManifest = myAndroidManifestCheckBox.isSelected();
    myProperties.updateLayouts = myLayoutsCheckBox.isSelected();
    myProperties.replaceLeftRightPropertiesOption = myReplaceLeftRightPropertiesCheckBox.isSelected();
    myProperties.generateV17resourcesOption = myGenerateV17VersionsCheckBox.isSelected();

    // When generating v17 layout file, we force replacing left/right properties by start/end properties
    if (myProperties.generateV17resourcesOption) {
      myProperties.replaceLeftRightPropertiesOption = true;
    }

    return myProperties;
  }
}
