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

package com.android.tools.idea.sdk;

import com.android.tools.idea.io.FilePaths;
import com.android.tools.idea.ui.ApplicationUtils;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.install.FirstRunWizardDefaults;
import com.android.tools.idea.welcome.install.SdkComponentInstaller;
import com.android.tools.idea.welcome.wizard.FirstRunWizardTracker;
import com.android.tools.idea.welcome.wizard.deprecated.ConsolidatedProgressStep;
import com.android.tools.idea.welcome.wizard.deprecated.InstallComponentsPath;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.dynamic.DynamicWizard;
import com.android.tools.idea.wizard.dynamic.DynamicWizardHost;
import com.android.tools.idea.wizard.dynamic.SingleStepPath;
import com.android.tools.sdk.SdkPaths.ValidationResult;
import com.google.wireless.android.sdk.stats.SetupWizardEvent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Collections;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.tools.sdk.SdkPaths.validateAndroidSdk;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;

public class SelectSdkDialog extends DialogWrapper {
  private JPanel myPanel;
  private JdkComboBox myJdkChooser;
  private TextFieldWithBrowseButton mySdkTextFieldWithButton;
  private JBLabel mySelectSdkDescriptionLabel;
  private JBLabel mySelectSdkLabel;
  private JBLabel mySelectJdkDescriptionLabel;
  private JBLabel mySelectJdkLabel;
  private JBLabel mySpacer;

  private String myJdkHome = "";
  private String mySdkHome = "";
  private ProjectSdksModel mySdkModel;
  private final Project myProject;

  /**
   * Displays SDK selection dialog.
   *
   * @param project current project to determine a JDK if known, null otherwise
   * @param sdkPath path to Android SDK if known, null otherwise
   */
  public SelectSdkDialog(@Nullable Project project, @Nullable String sdkPath) {
    super(false);
    myProject = project;
    setupUI();

    init();

    setTitle("Select SDKs");

    mySelectJdkLabel.setLabelFor(myJdkChooser);

    mySelectSdkDescriptionLabel.setText("Please provide the path to the Android SDK.");

    mySelectJdkDescriptionLabel.setText("Please provide the path to a Java Development Kit (JDK) installation.");

    if (myProject != null && ProjectRootManager.getInstance(myProject).getProjectSdk() == null && sdkPath == null) {
      mySpacer.setVisible(true);
    }
    else if (myProject != null && ProjectRootManager.getInstance(myProject).getProjectSdk() == null) {
      mySpacer.setVisible(false);
      mySelectSdkDescriptionLabel.setVisible(false);
      mySelectSdkLabel.setVisible(false);
      mySdkTextFieldWithButton.setVisible(false);
    }
    else {
      mySpacer.setVisible(false);
      mySelectJdkDescriptionLabel.setVisible(false);
      mySelectJdkLabel.setVisible(false);
      myJdkChooser.setVisible(false);
    }

    mySdkTextFieldWithButton.setTextFieldPreferredWidth(50);

    if (sdkPath != null) {
      mySdkTextFieldWithButton.setText(sdkPath);
    }

    @Nullable String finalSdkPath = sdkPath;
    mySdkTextFieldWithButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        createDownloadingComponentsStepDialog(finalSdkPath, s -> {
          mySdkTextFieldWithButton.setText(s.getAbsolutePath());
          validate();
        });
      }
    });
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected boolean postponeValidation() {
    return false;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    String androidHome = mySdkTextFieldWithButton.getText().trim();
    String sdkError = validateAndroidSdkPath(androidHome);
    if (mySdkTextFieldWithButton.isVisible() && sdkError != null) {
      return new ValidationInfo(sdkError, mySdkTextFieldWithButton.getTextField());
    }

    if (myJdkChooser.isVisible() && myJdkChooser.getSelectedJdk() == null) {
      return new ValidationInfo("Choose JDK", myJdkChooser);
    }
    return null;
  }

  private void setupUI() {
    Condition<SdkTypeId> sdkTypeFilter = sdkType -> sdkType instanceof JavaSdkType;
    mySdkModel = new ProjectSdksModel();
    mySdkModel.reset(null);
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(8, 2, new Insets(0, 0, 0, 0), -1, -1));
    final Spacer spacer1 = new Spacer();
    myPanel.add(spacer1, new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    mySelectSdkLabel = new JBLabel();
    mySelectSdkLabel.setText("Select Android SDK:");
    myPanel.add(mySelectSdkLabel,
                new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    mySdkTextFieldWithButton = new TextFieldWithBrowseButton();
    myPanel.add(mySdkTextFieldWithButton, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                              GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                              new Dimension(300, -1), null, null, 0, false));
    mySelectSdkDescriptionLabel = new JBLabel();
    mySelectSdkDescriptionLabel.setText("");
    myPanel.add(mySelectSdkDescriptionLabel, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                                 GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                                 null, null, 0, false));
    mySelectJdkLabel = new JBLabel();
    mySelectJdkLabel.setText("Select JDK:");
    myPanel.add(mySelectJdkLabel,
                new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myJdkChooser = new JdkComboBox(myProject, mySdkModel, sdkTypeFilter, null, null, null, null);
    myPanel.add(myJdkChooser, new GridConstraints(6, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                              GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                              new Dimension(300, -1), null, null, 0, false));
    mySelectJdkDescriptionLabel = new JBLabel();
    myPanel.add(mySelectJdkDescriptionLabel, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                                 GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                                 null, null, 0, false));
    mySpacer = new JBLabel();
    mySpacer.setText(" ");
    myPanel.add(mySpacer, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                              GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                              false));
  }

  public JComponent getRootComponent() { return myPanel; }

  @Nullable
  private static String validateJdkPath(@Nullable String path) {
    if (isEmpty(path) || !JavaSdk.getInstance().isValidSdkHome(path)) {
      return "Invalid JDK path.";
    }
    return null;
  }

  @Nullable
  private static String validateAndroidSdkPath(@Nullable String path) {
    if (isEmpty(path)) {
      return "Android SDK path not specified.";
    }

    ValidationResult validationResult = validateAndroidSdk(FilePaths.stringToFile(path), false);
    if (!validationResult.success) {
      // Show error message in new line. Long lines trigger incorrect layout rendering.
      // See https://code.google.com/p/android/issues/detail?id=78291
      return String.format("Invalid Android SDK path:<br>%1$s", validationResult.message);
    } else {
      return null;
    }
  }

  @Override
  protected void doOKAction() {
    myJdkHome = myJdkChooser.getSelectedJdk() == null ? null : myJdkChooser.getSelectedJdk().getHomePath();
    mySdkHome = mySdkTextFieldWithButton.getText();
    try {
      mySdkModel.apply(null, true);
    }
    catch (ConfigurationException e) {
      Notification notification = new Notification("Android", "Error while saving JDK: " + e.getMessageHtml(), NotificationType.ERROR);
      notification.canShowFor(myProject);
      Notifications.Bus.notify(notification);
    }
    WriteAction.run(() -> ProjectRootManager.getInstance(myProject).setProjectSdk(myJdkChooser.getSelectedJdk()));
    super.doOKAction();
  }

  @Override
  protected void dispose() {
    mySdkModel.disposeUIResources();
    super.dispose();
  }

  @NotNull
  public String getJdkHome() {
    return myJdkHome;
  }

  @NotNull
  public String getAndroidHome() {
    return mySdkHome;
  }

  public static void createDownloadingComponentsStepDialog(String sdkPath, Consumer<File> onFinish) {
    DynamicWizard wizard = new DynamicWizard(null, null, "SDK Setup") {
      @Override
      public void init() {
        FirstRunWizardTracker tracker = new FirstRunWizardTracker(SetupWizardEvent.SetupWizardMode.SDK_SETUP, true);
        DownloadingComponentsStep progressStep = new DownloadingComponentsStep(myHost.getDisposable(), myHost, tracker);

        File location;
        if (isEmpty(sdkPath)) {
          location = FirstRunWizardDefaults.getInitialSdkLocation(FirstRunWizardMode.MISSING_SDK);
        }
        else {
          location = new File(sdkPath);
        }

        InstallComponentsPath path =
          new InstallComponentsPath(FirstRunWizardMode.MISSING_SDK, location, progressStep, new SdkComponentInstaller(), false, tracker);

        progressStep.setInstallComponentsPath(path);

        addPath(path);
        addPath(new SingleStepPath(progressStep));
        super.init();
      }

      @Override
      public void performFinishingActions() {
        File sdkLocation = IdeSdks.getInstance().getAndroidSdkPath();

        if (sdkLocation == null) {
          return;
        }

        String stateSdkLocationPath = myState.get(WizardConstants.KEY_SDK_INSTALL_LOCATION);
        assert stateSdkLocationPath != null;

        File stateSdkLocation = new File(stateSdkLocationPath);

        if (!FileUtil.filesEqual(sdkLocation, stateSdkLocation)) {
          File finalSdkLocation = sdkLocation;
          ApplicationUtils.invokeWriteActionAndWait(ModalityState.any(), () -> IdeSdks.getInstance().setAndroidSdkPath(finalSdkLocation));
          sdkLocation = stateSdkLocation;
        }

        // Pick up changes done by the wizard.
        onFinish.accept(sdkLocation);
      }

      @NotNull
      @Override
      protected String getProgressTitle() {
        return "Setting up SDK...";
      }

      @Override
      protected String getWizardActionDescription() {
        return "Setting up SDK...";
      }
    };
    wizard.init();
    wizard.show();
  }

  private static final class DownloadingComponentsStep extends ConsolidatedProgressStep {
    private InstallComponentsPath myInstallComponentsPath;

    private DownloadingComponentsStep(@NotNull Disposable disposable, @NotNull DynamicWizardHost host, @NotNull FirstRunWizardTracker tracker) {
      super(disposable, host, tracker);
    }

    private void setInstallComponentsPath(InstallComponentsPath installComponentsPath) {
      setPaths(Collections.singletonList(installComponentsPath));
      myInstallComponentsPath = installComponentsPath;
    }

    @Override
    public boolean isStepVisible() {
      return myInstallComponentsPath.shouldDownloadingComponentsStepBeShown();
    }
  }
}