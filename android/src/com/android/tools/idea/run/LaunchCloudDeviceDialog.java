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
package com.android.tools.idea.run;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static com.android.tools.idea.run.CloudConfiguration.Kind.SINGLE_DEVICE;

public class LaunchCloudDeviceDialog extends DialogWrapper {

  private final Project myProject;
  private JPanel myPanel;
  private CloudConfigurationComboBox myCloudConfigurationCombo;
  private ActionButton myCloudProjectIdUpdateButton;
  private CloudProjectIdLabel myCloudProjectIdLabel;
  private final CloudConfigurationProvider myCloudConfigurationProvider;


  public LaunchCloudDeviceDialog(@NotNull AndroidFacet facet) {
    super(facet.getModule().getProject(), true, IdeModalityType.PROJECT);

    myProject = facet.getModule().getProject();

    myCloudConfigurationProvider = CloudConfigurationProvider.getCloudConfigurationProvider();

    setTitle("Launch Cloud Device");

    init();

    myCloudConfigurationCombo.getComboBox().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateOkButton();
      }
    });

    myCloudProjectIdLabel.restoreChosenProjectId();
    myCloudConfigurationCombo.setFacet(facet);

    updateOkButton();
  }

  private void updateOkButton() {
    getOKAction().setEnabled(isValidGoogleCloudSelection());
  }

  private boolean isValidGoogleCloudSelection() {
    CloudConfiguration selection = (CloudConfiguration) myCloudConfigurationCombo.getComboBox().getSelectedItem();
    return selection != null && selection.getDeviceConfigurationCount() > 0 && myCloudProjectIdLabel.isProjectSpecified();
  }

  public int getSelectedMatrixConfigurationId() {
    CloudConfiguration selection = (CloudConfiguration) myCloudConfigurationCombo.getComboBox().getSelectedItem();
    if (selection == null) {
      return -1;
    }
    return selection.getId();
  }

  public String getChosenCloudProjectId() {
    return myCloudProjectIdLabel.getText();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  private void createUIComponents() {
    myCloudProjectIdLabel = new CloudProjectIdLabel(SINGLE_DEVICE);

    AnAction action = new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        if (myCloudConfigurationProvider == null) {
          return;
        }

        String selectedProjectId =
          myCloudConfigurationProvider.openCloudProjectConfigurationDialog(myProject, myCloudProjectIdLabel.getText());

        if (selectedProjectId != null) {
          myCloudProjectIdLabel.updateCloudProjectId(selectedProjectId);
          updateOkButton();
        }
      }

      @Override
      public void update(AnActionEvent event) {
        Presentation presentation = event.getPresentation();
        presentation.setIcon(AllIcons.General.Settings);
      }
    };

    myCloudProjectIdUpdateButton =
      new ActionButton(action, new PresentationFactory().getPresentation(action), "MyPlace", new Dimension(25, 25));

    myCloudConfigurationCombo = new CloudConfigurationComboBox(SINGLE_DEVICE);
    Disposer.register(myDisposable, myCloudConfigurationCombo);
  }
}
