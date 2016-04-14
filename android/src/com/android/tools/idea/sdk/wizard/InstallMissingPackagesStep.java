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
package com.android.tools.idea.sdk.wizard;

import com.android.repository.api.RepoPackage;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.swing.SelectedProperty;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.utils.HtmlBuilder;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Step used to prompt the user to open the standalone SDK manager
 * to install skipped packages that were skipped in the {@link InstallSelectedPackagesStep}
 */
public final class InstallMissingPackagesStep extends ModelWizardStep<HandleSkippedInstallationsModel> {
  private JPanel myRootPanel;
  private JPanel myOptionsPanel;
  private JBRadioButton myStandaloneSdkManagerButton;
  private JBRadioButton mySkipInstallationButton;
  private JBLabel myErrorMessageLabel;
  private BindingsManager myBindings = new BindingsManager();

  public InstallMissingPackagesStep(@NotNull HandleSkippedInstallationsModel model) {
    super(model, "Install Missing Packages");
  }

  private void createUIComponents() {
    myOptionsPanel = new JPanel(new FlowLayout(FlowLayout.TRAILING));
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Override
  protected boolean shouldShow() {
    return !getModel().getSkippedInstallRequests().isEmpty();
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    ButtonGroup optionsGroup = new ButtonGroup();
    optionsGroup.add(myStandaloneSdkManagerButton);
    optionsGroup.add(mySkipInstallationButton);
    myStandaloneSdkManagerButton.setSelected(true);
    myBindings.bind(getModel().useStandaloneSdkManager(), new SelectedProperty(myStandaloneSdkManagerButton));

    myErrorMessageLabel.setText(generateErrorDisplayMessage());
  }

  private String generateErrorDisplayMessage() {
    HtmlBuilder warningBuilder = new HtmlBuilder();
    warningBuilder.openHtmlBody();
    warningBuilder.add("The following packages were not installed.");
    warningBuilder.newline();
    warningBuilder.newline();
    warningBuilder.beginList();
    for (RepoPackage problemPkg : getModel().getSkippedInstallRequests()) {
      warningBuilder.listItem().add(problemPkg.getDisplayName());
    }
    warningBuilder.endList();
    warningBuilder.newline();
    warningBuilder.add("Would you like to exit ").add(ApplicationNamesInfo.getInstance().getFullProductName())
      .add(" and install the following packages using the standalone SDK manager?");
    warningBuilder.closeHtmlBody();
    return warningBuilder.getHtml();
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
  }
}