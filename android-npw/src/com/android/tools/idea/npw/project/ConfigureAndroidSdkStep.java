package com.android.tools.idea.npw.project;

import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.updater.configure.SetupSdkApplicationService;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.install.FirstRunWizardDefaults;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.intellij.ui.components.JBLabel;
import java.io.File;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ConfigureAndroidSdkStep extends ModelWizardStep.WithoutModel {

  private JPanel myPanel;
  private JButton myInstallSDKButton;
  private JBLabel myMessage;
  private final BoolValueProperty myProperty = new BoolValueProperty(false);

  public ConfigureAndroidSdkStep() {
    super("Configure Android SDK");
    myInstallSDKButton.addActionListener(e -> {
      File initialSdkLocation = FirstRunWizardDefaults.getInitialSdkLocation(FirstRunWizardMode.MISSING_SDK);
      SetupSdkApplicationService.getInstance().showSdkSetupWizard(initialSdkLocation.getPath(), null);

      boolean success = IdeSdks.getInstance().getAndroidSdkPath() != null;
      myProperty.set(success);
      if (success) {
        myMessage.setText("Android SDK is installed successfully.");
        myInstallSDKButton.setVisible(false);
      }
    });
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myProperty;
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myPanel;
  }
}
