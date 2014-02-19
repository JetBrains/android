package com.android.tools.idea.wizard;

import com.android.tools.idea.sdk.DefaultSdks;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Condition;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

/**
 * @author Eugene.Kudelevsky
 */
public class ChooseAndroidAndJavaSdkStep extends ModuleWizardStep {
  private JdkComboBox myJavaSdkCombo;
  private TextFieldWithBrowseButton myAndroidSdkLocationField;
  private JPanel myPanel;
  private JButton myNewButton;
  private ProjectSdksModel mySdksModel;

  public ChooseAndroidAndJavaSdkStep() {
    myAndroidSdkLocationField.addBrowseFolderListener(new TextBrowseFolderListener(
      new FileChooserDescriptor(false, true, false, false, false, false)));
    myJavaSdkCombo.setSetupButton(myNewButton, null, mySdksModel, new JdkComboBox.NoneJdkComboBoxItem(), null, false);
  }

  @Nullable
  private String getAndroidSdkLocation() {
    return myAndroidSdkLocationField.getText().trim();
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void updateDataModel() {
    final String location = getAndroidSdkLocation();

    if (location != null) {
      final Sdk javaSdk = myJavaSdkCombo.getSelectedJdk();

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          DefaultSdks.setDefaultAndroidHome(new File(location), javaSdk);
        }
      });
    }
  }

  @Override
  public boolean validate() throws ConfigurationException {
    if (myJavaSdkCombo.getSelectedJdk() == null) {
      throw new ConfigurationException("Specify Java SDK");
    }
    mySdksModel.apply(null, true);
    final String location = getAndroidSdkLocation();

    if (location != null) {
      if (location.length() == 0) {
        throw new ConfigurationException("Specify Android SDK location");
      }
      if (!new File(location).isDirectory()) {
        throw new ConfigurationException(location + " is not directory");
      }
      final AndroidSdkData sdkData = AndroidSdkData.getSdkData(location);

      if (sdkData == null) {
        throw new ConfigurationException("Invalid Android SDK");
      }
    }
    return true;
  }

  @Override
  public boolean isStepVisible() {
    return DefaultSdks.getDefaultJdk() == null || DefaultSdks.getDefaultAndroidHome() == null;
  }

  private void createUIComponents() {
    mySdksModel = new ProjectSdksModel();
    mySdksModel.reset(null);
    myJavaSdkCombo = new JdkComboBox(mySdksModel, new Condition<SdkTypeId>() {
      @Override
      public boolean value(SdkTypeId id) {
        return JavaSdk.getInstance().equals(id);
      }
    });
  }
}
