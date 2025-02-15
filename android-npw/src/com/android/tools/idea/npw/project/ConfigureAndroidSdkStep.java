package com.android.tools.idea.npw.project;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.wizard.SetupSdkApplicationService;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.install.FirstRunWizardDefaults;
import com.android.tools.idea.welcome.install.SdkComponentInstaller;
import com.android.tools.idea.welcome.wizard.FirstRunWizardTracker;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.google.wireless.android.sdk.stats.SetupWizardEvent;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.Insets;
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
    setupUI();
    myInstallSDKButton.addActionListener(e -> {
      File initialSdkLocation = FirstRunWizardDefaults.getInitialSdkLocation(FirstRunWizardMode.MISSING_SDK);
      boolean useDeprecatedWizard = !StudioFlags.SDK_SETUP_MIGRATED_WIZARD_ENABLED.get();
      SetupSdkApplicationService.getInstance().showSdkSetupWizard(
        initialSdkLocation.getPath(),
        null,
        new SdkComponentInstaller(),
        new FirstRunWizardTracker(SetupWizardEvent.SetupWizardMode.SDK_SETUP, useDeprecatedWizard),
        useDeprecatedWizard
      );

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

  private void setupUI() {
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
    myMessage = new JBLabel();
    myMessage.setText("In order to create an Android project, you need to have the Android SDK installed. ");
    myPanel.add(myMessage, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                               false));
    final Spacer spacer1 = new Spacer();
    myPanel.add(spacer1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    myInstallSDKButton = new JButton();
    myInstallSDKButton.setText("Install SDK");
    myInstallSDKButton.setMnemonic('I');
    myInstallSDKButton.setDisplayedMnemonicIndex(0);
    myPanel.add(myInstallSDKButton, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
  }
}
