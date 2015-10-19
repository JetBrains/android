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

package com.android.tools.idea.gradle.structure;

import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.npw.WizardUtils;
import com.android.tools.idea.sdk.*;
import com.android.tools.idea.sdk.SdkPaths.ValidationResult;
import com.android.tools.idea.sdk.wizard.SdkQuickfixWizard;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.DetailsComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.util.Function;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.android.actions.RunAndroidSdkManagerAction;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.android.SdkConstants.NDK_DIR_PROPERTY;
import static com.android.tools.idea.sdk.SdkPaths.validateAndroidNdk;
import static com.android.tools.idea.sdk.SdkPaths.validateAndroidSdk;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.intellij.openapi.util.io.FileUtilRt.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.jetbrains.android.sdk.AndroidSdkUtils.tryToChooseAndroidSdk;

/**
 * Allows the user set global Android SDK and JDK locations that are used for Gradle-based Android projects.
 */
public class DefaultSdksConfigurable extends BaseConfigurable {
  private static final String CHOOSE_VALID_JDK_DIRECTORY_ERR = "Please choose a valid JDK directory.";
  private static final String CHOOSE_VALID_SDK_DIRECTORY_ERR = "Please choose a valid Android SDK directory.";
  private static final String CHOOSE_VALID_NDK_DIRECTORY_ERR = "Please choose a valid Android NDK directory.";

  private static final Logger LOG = Logger.getInstance(DefaultSdksConfigurable.class);

  @Nullable private final AndroidProjectStructureConfigurable myHost;
  @Nullable private final Project myProject;

  // These paths are system-dependent.
  private String myOriginalJdkHomePath;
  private String myOriginalNdkHomePath;
  private String myOriginalSdkHomePath;

  private HyperlinkLabel myNdkDownloadHyperlinkLabel;
  private HyperlinkLabel myNdkResetHyperlinkLabel;
  private TextFieldWithBrowseButton mySdkLocationTextField;
  private TextFieldWithBrowseButton myNdkLocationTextField;
  private TextFieldWithBrowseButton myJdkLocationTextField;
  private JPanel myWholePanel;
  private JPanel myNdkDownloadPanel;
  private AsyncProcessIcon myNdkCheckProcessIcon;

  private DetailsComponent myDetailsComponent;

  public DefaultSdksConfigurable(@Nullable AndroidProjectStructureConfigurable host, @Nullable Project project) {
    myHost = host;
    myProject = project;
    myWholePanel.setPreferredSize(JBUI.size(700, 500));

    myDetailsComponent = new DetailsComponent();
    myDetailsComponent.setContent(myWholePanel);
    myDetailsComponent.setText("SDK Location");

    // We can't update The IDE-level ndk directory. Due to that disabling the ndk directory option in the default Project Structure dialog.
    if (myProject == null || myProject.isDefault()) {
      myNdkLocationTextField.setEnabled(false);
    }
    final CardLayout layout = (CardLayout)myNdkDownloadPanel.getLayout();
    layout.show(myNdkDownloadPanel, "loading");
    final SdkState sdkState = SdkState.getInstance(AndroidSdkUtils.tryToChooseAndroidSdk());
    sdkState.loadAsync(SdkState.DEFAULT_EXPIRATION_PERIOD_MS, false, null, new SdkLoadedCallback(true) {
      @Override
      public void doRun(@NotNull SdkPackages packages) {
        if (!sdkState.getPackages().getRemotePkgInfos().get(PkgType.PKG_NDK).isEmpty()) {
          layout.show(myNdkDownloadPanel, "link");
        }
        else {
          myNdkDownloadPanel.setVisible(false);
        }
      }
    }, new DispatchRunnable() {
      @Override
      public void doRun() {
        myNdkDownloadPanel.setVisible(false);
      }
    }, false);
  }

  @Override
  public void disposeUIResources() {
  }

  @Override
  public void reset() {
    myOriginalSdkHomePath = getDefaultSdkPath();
    myOriginalNdkHomePath = getDefaultNdkPath();
    myOriginalJdkHomePath = getDefaultJdkPath();

    mySdkLocationTextField.setText(myOriginalSdkHomePath);
    myNdkLocationTextField.setText(myOriginalNdkHomePath);
    myJdkLocationTextField.setText(myOriginalJdkHomePath);
  }

  @Override
  public void apply() throws ConfigurationException {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        IdeSdks.setJdkPath(getJdkLocation());
        IdeSdks.setAndroidSdkPath(getSdkLocation(), myProject);
        saveAndroidNdkPath();

        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          RunAndroidSdkManagerAction.updateInWelcomePage(myDetailsComponent.getComponent());
        }
      }
    });
  }

  private void saveAndroidNdkPath() {
    if(myProject == null || myProject.isDefault()) {
      return;
    }

    try {
      LocalProperties localProperties = new LocalProperties(myProject);
      localProperties.setAndroidNdkPath(getNdkLocation());
      localProperties.save();
    }
    catch (IOException e) {
      LOG.info(String.format("Unable to update local.properties file in project '%1$s'.", myProject.getName()), e);
      String cause = e.getMessage();
      if (isNullOrEmpty(cause)) {
        cause = "[Unknown]";
      }
      String msg = String.format("Unable to update local.properties file in project '%1$s'.\n\n" +
                                 "Cause: %2$s\n\n" +
                                 "Please manually update the file's '%3$s' property value to \n" +
                                 "'%4$s'\n" +
                                 "and sync the project with Gradle files.", myProject.getName(), cause,
                                 NDK_DIR_PROPERTY, getNdkLocation().getPath());
      Messages.showErrorDialog(myProject, msg, "Android Ndk Update");
    }
  }

  private void createUIComponents() {
    myNdkCheckProcessIcon = new AsyncProcessIcon("NDK check progress");
    createSdkLocationTextField();
    createJdkLocationTextField();
    createNdkLocationTextField();
    createNdkDownloadLink();
    createNdkResetLink();
  }

  private void createSdkLocationTextField() {
    mySdkLocationTextField = createTextFieldWithBrowseButton("Choose Android SDK Location", CHOOSE_VALID_SDK_DIRECTORY_ERR,
                                                             new Function<File, ValidationResult>() {
                                                               @Override
                                                               public ValidationResult fun(File file) {
                                                                 return validateAndroidSdk(file, false);
                                                               }
                                                             });
  }

  private void createNdkLocationTextField() {
    myNdkLocationTextField = createTextFieldWithBrowseButton(
      "Choose Android NDK Location", CHOOSE_VALID_NDK_DIRECTORY_ERR,
      new Function<File, ValidationResult>() {
        @Override
        public ValidationResult fun(File file) {
          return validateAndroidNdk(file, false);
        }
      });
  }

  private TextFieldWithBrowseButton createTextFieldWithBrowseButton(String title, final String errorMessagae, final Function<File,
    ValidationResult> validation) {
    final FileChooserDescriptor descriptor = createSingleFolderDescriptor(title, new Function<File, Void>() {
      @Override
      public Void fun(File file) {
        ValidationResult validationResult = validation.fun(file);
        if (!validationResult.success) {
          String msg = validationResult.message;
          if (isEmpty(msg)) {
            msg = errorMessagae;
          }
          throw new IllegalArgumentException(msg);
        }
        return null;
      }
    });

    final JTextField textField = new JTextField(10);
    installValidationListener(textField);
    return new TextFieldWithBrowseButton(textField, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        VirtualFile suggestedDir = null;
        File ndkLocation = getNdkLocation();
        if (ndkLocation.isDirectory()) {
          suggestedDir = findFileByIoFile(ndkLocation, false);
        }
        VirtualFile chosen = FileChooser.chooseFile(descriptor, null, suggestedDir);
        if (chosen != null) {
          File f = virtualToIoFile(chosen);
          textField.setText(f.getPath());
        }
      }
    });
  }

  private void createNdkResetLink() {
    myNdkResetHyperlinkLabel = new HyperlinkLabel();
    myNdkResetHyperlinkLabel.setHyperlinkText("", "Select", " default NDK");
    myNdkResetHyperlinkLabel.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        // known non-null since otherwise we won't show the link
        //noinspection ConstantConditions
        myNdkLocationTextField.setText(IdeSdks.getAndroidNdkPath().getPath());
      }
    });
  }

  private void createNdkDownloadLink() {
    myNdkDownloadHyperlinkLabel = new HyperlinkLabel();
    myNdkDownloadHyperlinkLabel.setHyperlinkText("", "Download", " Android NDK.");
    myNdkDownloadHyperlinkLabel.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        if (validateAndroidSdkPath() != null) {
          Messages.showErrorDialog(getContentPanel(), "Please select a valid SDK before downloading the NDK.");
          return;
        }
        List<IPkgDesc> requested = ImmutableList.of(PkgDesc.Builder.newNdk(FullRevision.NOT_SPECIFIED).create());
        SdkQuickfixWizard wizard = new SdkQuickfixWizard(null, null, requested);
        wizard.init();
        if (wizard.showAndGet()) {
          File ndk = IdeSdks.getAndroidNdkPath();
          if (ndk != null) {
            myNdkLocationTextField.setText(ndk.getPath());
          }
          validateState();
        }
      }
    });
  }

  private void createJdkLocationTextField() {
    JTextField textField = new JTextField(10);
    myJdkLocationTextField = new TextFieldWithBrowseButton(textField, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        chooseJdkLocation();
      }
    });
    installValidationListener(textField);
  }

  public void chooseJdkLocation() {
    myJdkLocationTextField.getTextField().requestFocus();

    VirtualFile suggestedDir = null;
    File jdkLocation = getJdkLocation();
    if (jdkLocation.isDirectory()) {
      suggestedDir = findFileByIoFile(jdkLocation, false);
    }
    VirtualFile chosen = FileChooser.chooseFile(createSingleFolderDescriptor("Choose JDK Location", new Function<File, Void>() {
      @Override
      public Void fun(File file) {
        if (!validateAndUpdateJdkPath(file)) {
          throw new IllegalArgumentException(CHOOSE_VALID_JDK_DIRECTORY_ERR);
        }
        return null;
      }
    }), null, suggestedDir);
    if (chosen != null) {
      File f = virtualToIoFile(chosen);
      myJdkLocationTextField.setText(f.getPath());
    }
  }

  private void installValidationListener(@NotNull JTextField textField) {
    if (myHost != null) {
      textField.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(DocumentEvent e) {
          myHost.requestValidation();
        }
      });
    }
  }

  @NotNull
  private static FileChooserDescriptor createSingleFolderDescriptor(@NotNull String title, @NotNull final Function<File, Void> validation) {
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
      @Override
      public void validateSelectedFiles(VirtualFile[] files) throws Exception {
        for (VirtualFile virtualFile : files) {
          File file = virtualToIoFile(virtualFile);
          validation.fun(file);
        }
      }
    };
    if (SystemInfo.isMac) {
      descriptor.withShowHiddenFiles(true);
    }
    descriptor.setTitle(title);
    return descriptor;
  }

  @Override
  public String getDisplayName() {
    return "SDK Location";
  }

  @Override
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myDetailsComponent.getComponent();
  }

  @NotNull
  public JComponent getContentPanel() {
    return myWholePanel;
  }

  @Override
  public boolean isModified() {
    return !myOriginalSdkHomePath.equals(getSdkLocation().getPath())
           || !myOriginalNdkHomePath.equals(getNdkLocation().getPath())
           || !myOriginalJdkHomePath.equals(getJdkLocation().getPath());
  }

  /**
   * Returns the first SDK it finds that matches our default naming convention. There will be several SDKs so named, one for each build
   * target installed in the SDK; which of those this method returns is not defined.
   *
   * @param create True if this method should attempt to create an SDK if one does not exist.
   * @return null if an SDK is unavailable or creation failed.
   */
  @Nullable
  private static Sdk getFirstDefaultAndroidSdk(boolean create) {
    List<Sdk> allAndroidSdks = IdeSdks.getEligibleAndroidSdks();
    if (!allAndroidSdks.isEmpty()) {
      return allAndroidSdks.get(0);
    }
    if (!create) {
      return null;
    }
    AndroidSdkData sdkData = tryToChooseAndroidSdk();
    if (sdkData == null) {
      return null;
    }
    List<Sdk> sdks = IdeSdks.createAndroidSdkPerAndroidTarget(sdkData.getLocation());
    return !sdks.isEmpty() ? sdks.get(0) : null;
  }

  /**
   * @return what the IDE is using as the home path for the Android SDK for new projects.
   */
  @NotNull
  private static String getDefaultSdkPath() {
    File path = IdeSdks.getAndroidSdkPath();
    if (path != null) {
      return path.getPath();
    }
    Sdk sdk = getFirstDefaultAndroidSdk(true);
    if (sdk != null) {
      String sdkHome = sdk.getHomePath();
      if (sdkHome != null) {
        return toSystemDependentName(sdkHome);
      }
    }
    return "";
  }

  /**
   * @return the appropriate NDK path for a given project, i.e the project's ndk path for a real project and the default NDK path default
   * project.
   */
  @NotNull
  private String getDefaultNdkPath() {
    if (myProject != null && !myProject.isDefault()) {
      try {
        File androidNdkPath = new LocalProperties(myProject).getAndroidNdkPath();
        if (androidNdkPath != null) {
          return androidNdkPath.getPath();
        }
      }
      catch (IOException e) {
        LOG.info(String.format("Unable to read local.properties file in project '%1$s'.", myProject.getName()), e);
      }
    } else {
      File path = IdeSdks.getAndroidNdkPath();
      if (path != null) {
        return path.getPath();
      }
    }
    return "";
  }

  /**
   * @return what the IDE is using as the home path for the JDK.
   */
  @NotNull
  private static String getDefaultJdkPath() {
    File javaHome = IdeSdks.getJdkPath();
    return javaHome != null ? javaHome.getPath() : "";
  }

  @NotNull
  private File getSdkLocation() {
    String sdkLocation = mySdkLocationTextField.getText();
    return new File(toSystemDependentName(sdkLocation));
  }

  @NotNull
  private File getNdkLocation() {
    String ndkLocation = myNdkLocationTextField.getText();
    return new File(toSystemDependentName(ndkLocation));
  }

  @Override
  @NotNull
  public JComponent getPreferredFocusedComponent() {
    return mySdkLocationTextField.getTextField();
  }

  public boolean validate() throws ConfigurationException {
    String msg = validateAndroidSdkPath();
    if (msg != null) {
      throw new ConfigurationException(msg);
    }

    if (!validateAndUpdateJdkPath(getJdkLocation())) {
      throw new ConfigurationException(CHOOSE_VALID_JDK_DIRECTORY_ERR);
    }

    msg = validateAndroidNdkPath();
    if (msg != null) {
      throw new ConfigurationException(msg);
    }

    return true;
  }

  @NotNull
  public List<ProjectConfigurationError> validateState() {
    List<ProjectConfigurationError> errors = Lists.newArrayList();

    String msg = validateAndroidSdkPath();
    if (msg != null) {
      ProjectConfigurationError error = new ProjectConfigurationError(msg, mySdkLocationTextField.getTextField());
      errors.add(error);
    }

    if (!validateAndUpdateJdkPath(getJdkLocation())) {
      ProjectConfigurationError error =
        new ProjectConfigurationError(CHOOSE_VALID_JDK_DIRECTORY_ERR, myJdkLocationTextField.getTextField());
      errors.add(error);
    }

    msg = validateAndroidNdkPath();
    if (msg != null) {
      ProjectConfigurationError error = new ProjectConfigurationError(msg, myNdkLocationTextField.getTextField());
      errors.add(error);
    }

    return errors;
  }

  /**
   * @return the error message when the sdk path is not valid, {@code null} otherwise.
   */
  @Nullable
  private String validateAndroidSdkPath() {
    WizardUtils.ValidationResult wizardValidationResult =
      WizardUtils.validateLocation(getSdkLocation().getAbsolutePath(), "Android SDK location", false, false);
    if (!wizardValidationResult.isOk()) {
      return wizardValidationResult.getFormattedMessage();
    }
    ValidationResult validationResult = validateAndroidSdk(getSdkLocation(), false);
    if (!validationResult.success) {
      String msg = validationResult.message;
      if (isEmpty(msg)) {
        msg = CHOOSE_VALID_SDK_DIRECTORY_ERR;
      }
      return msg;
    }
    return null;
  }

  /**
   * @return the error message when the ndk path is not valid, {@code null} otherwise.
   */
  @Nullable
  private String validateAndroidNdkPath() {
    hideNdkQuickfixLink();
    // As Ndk is required with for the projects with ndk modules, considering the empty value as legal.
    if (!myNdkLocationTextField.getText().isEmpty()) {
      ValidationResult validationResult = validateAndroidNdk(getNdkLocation(), false);
      if (!validationResult.success) {
        showNdkQuickfixLink();
        String msg = validationResult.message;
        if (isEmpty(msg)) {
          msg = CHOOSE_VALID_NDK_DIRECTORY_ERR;
        }
        return msg;
      }
    }
    else if (myNdkLocationTextField.isVisible()) {
      showNdkQuickfixLink();
    }
    return null;
  }

  private void showNdkQuickfixLink() {
    if (IdeSdks.getAndroidNdkPath() == null) {
      myNdkDownloadPanel.setVisible(true);
    }
    else {
      myNdkResetHyperlinkLabel.setVisible(true);
    }
  }

  private void hideNdkQuickfixLink() {
    myNdkResetHyperlinkLabel.setVisible(false);
    myNdkDownloadPanel.setVisible(false);
  }

  @NotNull
  private File getJdkLocation() {
    String jdkLocation = myJdkLocationTextField.getText();
    return new File(toSystemDependentName(jdkLocation));
  }

  private boolean validateAndUpdateJdkPath(@NotNull File file) {
    if (JavaSdk.checkForJdk(file)) {
      return true;
    }
    if (SystemInfo.isMac) {
      File potentialPath = new File(file, IdeSdks.MAC_JDK_CONTENT_PATH);
      if (potentialPath.isDirectory() && JavaSdk.checkForJdk(potentialPath)) {
        myJdkLocationTextField.setText(potentialPath.getPath());
        return true;
      }
    }
    return false;
  }

  /**
   * @return {@code true} if the configurable is needed: e.g. if we're missing a JDK or an Android SDK setting.
   */
  public static boolean isNeeded() {
    String jdkPath = getDefaultJdkPath();
    String sdkPath = getDefaultSdkPath();
    boolean validJdk = !jdkPath.isEmpty() && JavaSdk.checkForJdk(new File(jdkPath));
    boolean validSdk = !sdkPath.isEmpty() && IdeSdks.isValidAndroidSdkPath(new File(sdkPath));
    return !validJdk || !validSdk;
  }
}
