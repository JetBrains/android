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
package com.android.tools.idea.avdmanager;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.wizard.DynamicWizardStep;
import com.android.tools.idea.wizard.WizardConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import icons.AndroidIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * Display existing AVDs and offer actions for editing/creating.
 */
public class AvdListDialog extends DialogWrapper implements AvdUiAction.AvdInfoProvider {
  private final Project myProject;
  private AvdDisplayList myAvdDisplayList;

  public AvdListDialog(@Nullable Project project) {
    super(project);
    myProject = project;
    myAvdDisplayList = new AvdDisplayList(this, project);
    myAvdDisplayList.setBorder(ourDefaultBorder);
    setTitle("AVD Manager");
    Window window = getWindow();
    if (window == null) {
      assert ApplicationManager.getApplication().isUnitTestMode();
    } else {
      window.setPreferredSize(WizardConstants.DEFAULT_WIZARD_WINDOW_SIZE);
    }
  }

  @Override
  public void init() {
    super.init();
  }

  @Nullable
  @Override
  protected JComponent createNorthPanel() {
    return DynamicWizardStep
      .createWizardStepHeader(WizardConstants.ANDROID_NPW_HEADER_COLOR, AndroidIcons.Wizards.NewProjectMascotGreen, "Your Virtual Devices");
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myAvdDisplayList;
  }

  @Nullable
  @Override
  public AvdInfo getAvdInfo() {
    return null;
  }

  @Override
  public void refreshAvds() {
    myAvdDisplayList.refreshAvds();
  }

  @Nullable
  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public void notifyRun() {
    if (isShowing()) {
      close(DialogWrapper.CANCEL_EXIT_CODE);
    }
  }

  @Override
  @Nullable
  protected Border createContentPaneBorder() {
    return null;
  }

  @Nullable
  @Override
  protected JComponent createSouthPanel() {
    JComponent panel = super.createSouthPanel();
    if (panel != null) {
      panel.setBorder(ourDefaultBorder);
    }
    return panel;
  }

  @Nullable
  public AvdInfo getSelected() {
    return myAvdDisplayList.getAvdInfo();
  }
}
