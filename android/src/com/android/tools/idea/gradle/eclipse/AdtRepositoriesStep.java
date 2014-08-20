package com.android.tools.idea.gradle.eclipse;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.projectImport.ProjectImportWizardStep;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.android.actions.RunAndroidSdkManagerAction;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

class AdtRepositoriesStep extends ProjectImportWizardStep implements ActionListener {
  private JButton mySdkManagerButton;
  private JBLabel myGoogleLabel;
  private JBLabel mySupportLabel;
  private JPanel myPanel;
  private JButton myRefreshButton;

  AdtRepositoriesStep(WizardContext context) {
    super(context);

    mySdkManagerButton.addActionListener(this);
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void updateStep() {
    super.updateStep();

    GradleImport importer = AdtImportProvider.getImporter(getWizardContext());
    if (importer != null) {
      mySupportLabel.setVisible(importer.needSupportRepository() && importer.isMissingSupportRepository());
      myGoogleLabel.setVisible(importer.needGoogleRepository() && importer.isMissingGoogleRepository());
    }
  }

  @Override
  public void updateDataModel() {
  }

  @Override
  public boolean isStepVisible() {
    GradleImport importer = AdtImportProvider.getImporter(getWizardContext());
    if (importer != null) {
      return importer.needGoogleRepository() && importer.isMissingGoogleRepository() ||
        importer.needSupportRepository() && importer.isMissingSupportRepository();
    }

    return false;
  }

  @Override
  public void actionPerformed(ActionEvent actionEvent) {
    Object source = actionEvent.getSource();
    if (source == mySdkManagerButton) {
      GradleImport importer = AdtImportProvider.getImporter(getWizardContext());
      if (importer != null) {
        File location = importer.getSdkLocation();
        if (location != null) {
          RunAndroidSdkManagerAction.runSpecificSdkManager(null, location);
        }
      }
    } else if (source == myRefreshButton) {
      updateStep();
    }
  }

  @Override
  public String getName() {
    return "Missing Repositories";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return mySdkManagerButton;
  }
}
