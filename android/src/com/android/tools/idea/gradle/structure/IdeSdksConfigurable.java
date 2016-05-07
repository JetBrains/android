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

import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoManager;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.npw.WizardUtils;
import com.android.tools.idea.npw.WizardUtils.WritableCheckMode;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.Jdks;
import com.android.tools.idea.sdk.SdkPaths.ValidationResult;
import com.android.tools.idea.sdk.StudioDownloader;
import com.android.tools.idea.sdk.StudioSettingsController;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.progress.StudioProgressRunner;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
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
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.DetailsComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.util.Function;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.android.actions.RunAndroidSdkManagerAction;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.android.SdkConstants.FD_NDK;
import static com.android.SdkConstants.NDK_DIR_PROPERTY;
import static com.android.tools.idea.npw.WizardUtils.validateLocation;
import static com.android.tools.idea.sdk.SdkPaths.validateAndroidNdk;
import static com.android.tools.idea.sdk.SdkPaths.validateAndroidSdk;
import static com.android.tools.idea.sdk.wizard.SdkQuickfixUtils.createDialogForPaths;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.intellij.openapi.projectRoots.JavaSdkVersion.JDK_1_8;
import static com.intellij.openapi.util.io.FileUtilRt.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.jetbrains.android.sdk.AndroidSdkUtils.tryToChooseAndroidSdk;

/**
 * Allows the user set global Android SDK and JDK locations that are used for Gradle-based Android projects.
 */
public class IdeSdksConfigurable extends BaseConfigurable implements Place.Navigator {
  @NonNls private static final String SDKS_PLACE = "sdks.place";

  private static final String CHOOSE_VALID_JDK_DIRECTORY_ERR = "Please choose a valid JDK directory.";
  private static final String CHOOSE_VALID_SDK_DIRECTORY_ERR = "Please choose a valid Android SDK directory.";
  private static final String CHOOSE_VALID_NDK_DIRECTORY_ERR = "Please choose a valid Android NDK directory.";

  private static final Logger LOG = Logger.getInstance(IdeSdksConfigurable.class);

  @Nullable private final BaseConfigurable myHost;
  @Nullable private final Project myProject;

  @NotNull private final BiMap<String, Component> myComponentsById = HashBiMap.create();

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
  @SuppressWarnings("unused") private AsyncProcessIcon myNdkCheckProcessIcon;

  private DetailsComponent myDetailsComponent;
  private History myHistory;

  private String mySelectedComponentId;

  public IdeSdksConfigurable(@Nullable BaseConfigurable host, @Nullable Project project) {
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

    adjustNdkQuickFixVisibility();

    CardLayout layout = (CardLayout)myNdkDownloadPanel.getLayout();
    layout.show(myNdkDownloadPanel, "loading");

    ProgressIndicator logger = new StudioLoggerProgressIndicator(getClass());
    RepoManager repoManager = AndroidSdkUtils.tryToChooseSdkHandler().getSdkManager(logger);
    StudioProgressRunner runner = new StudioProgressRunner(false, true, false, "Loading Remote SDK", true, project);
    RepoManager.RepoLoadedCallback onComplete = packages -> {
      if (packages.getRemotePackages().get(FD_NDK) != null) {
        layout.show(myNdkDownloadPanel, "link");
      }
      else {
        myNdkDownloadPanel.setVisible(false);
      }
    };
    Runnable onError = () -> myNdkDownloadPanel.setVisible(false);
    repoManager.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, ImmutableList.of(onComplete), ImmutableList.of(onError), runner,
                     new StudioDownloader(), StudioSettingsController.getInstance(), false);

    FocusListener historyUpdater = new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        if (myHistory != null) {
          String id = myComponentsById.inverse().get(e.getComponent());
          mySelectedComponentId = id;
          if (id != null) {
            myHistory.pushQueryPlace();
          }
        }
      }
    };

    installValidationListener(mySdkLocationTextField.getTextField());
    installValidationListener(myJdkLocationTextField.getTextField());
    installValidationListener(myNdkLocationTextField.getTextField());

    addHistoryUpdater("mySdkLocationTextField", mySdkLocationTextField.getTextField(), historyUpdater);
    addHistoryUpdater("myJdkLocationTextField", myJdkLocationTextField.getTextField(), historyUpdater);
    addHistoryUpdater("myNdkLocationTextField", myNdkLocationTextField.getTextField(), historyUpdater);
  }

  private void addHistoryUpdater(@NotNull String id, @NotNull Component c, @NotNull FocusListener historyUpdater) {
    myComponentsById.put(id, c);
    c.addFocusListener(historyUpdater);
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
    if (!isModified()) {
      return;
    }
    ApplicationManager.getApplication().runWriteAction(() -> {
      // Setting the Sdk path will trigger the project sync. Set the Ndk path and Jdk path before the Sdk path to get the changes to them
      // to take effect during the sync.
      saveAndroidNdkPath();
      IdeSdks.setJdkPath(getJdkLocation());
      IdeSdks.setAndroidSdkPath(getSdkLocation(), myProject);

      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        RunAndroidSdkManagerAction.updateInWelcomePage(myDetailsComponent.getComponent());
      }
    });
  }

  private void saveAndroidNdkPath() {
    if (myProject == null || myProject.isDefault()) {
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
                                 "and sync the project with Gradle files.", myProject.getName(), cause, NDK_DIR_PROPERTY,
                                 getNdkLocation().getPath());
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
                                                             file -> validateAndroidSdk(file, false));
  }

  private void createNdkLocationTextField() {
    myNdkLocationTextField = createTextFieldWithBrowseButton(
      "Choose Android NDK Location", CHOOSE_VALID_NDK_DIRECTORY_ERR,
      file -> validateAndroidNdk(file, false));
  }

  @NotNull
  private TextFieldWithBrowseButton createTextFieldWithBrowseButton(@NotNull String title,
                                                                    @NotNull String errorMessage,
                                                                    @NotNull Function<File, ValidationResult> validation) {
    FileChooserDescriptor descriptor = createSingleFolderDescriptor(title, file -> {
      ValidationResult validationResult = validation.fun(file);
      if (!validationResult.success) {
        String msg = validationResult.message;
        if (isEmpty(msg)) {
          msg = errorMessage;
        }
        throw new IllegalArgumentException(msg);
      }
      return null;
    });

    JTextField textField = new JTextField(10);
    return new TextFieldWithBrowseButton(textField, e -> {
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
    });
  }

  private void createNdkResetLink() {
    myNdkResetHyperlinkLabel = new HyperlinkLabel();
    myNdkResetHyperlinkLabel.setHyperlinkText("", "Select", " default NDK");
    myNdkResetHyperlinkLabel.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        // known non-null since otherwise we won't show the link
        File androidNdkPath = IdeSdks.getAndroidNdkPath();
        assert androidNdkPath != null;
        myNdkLocationTextField.setText(androidNdkPath.getPath());
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
        List<String> requested = ImmutableList.of(FD_NDK);
        ModelWizardDialog dialog = createDialogForPaths(myWholePanel, requested, false);
        if (dialog != null && dialog.showAndGet()) {
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
    myJdkLocationTextField = new TextFieldWithBrowseButton(textField, e -> {
      chooseJdkLocation();
    });
  }

  public void chooseJdkLocation() {
    myJdkLocationTextField.getTextField().requestFocus();

    VirtualFile suggestedDir = null;
    File jdkLocation = getJdkLocation();
    if (jdkLocation.isDirectory()) {
      suggestedDir = findFileByIoFile(jdkLocation, false);
    }
    VirtualFile chosen = FileChooser.chooseFile(createSingleFolderDescriptor("Choose JDK Location", file -> {
      File validJdkLocation = validateJdkPath(file);
      if (validJdkLocation == null) {
        throw new IllegalArgumentException(CHOOSE_VALID_JDK_DIRECTORY_ERR);
      }
      return null;
    }), null, suggestedDir);
    if (chosen != null) {
      File validJdkLocation = validateJdkPath(virtualToIoFile(chosen));
      assert validJdkLocation != null;
      myJdkLocationTextField.setText(validJdkLocation.getPath());
    }
  }

  private void installValidationListener(@NotNull JTextField textField) {
    if (myHost instanceof AndroidProjectStructureConfigurable) {
      textField.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(DocumentEvent e) {
          ((AndroidProjectStructureConfigurable)myHost).requestValidation();
        }
      });
    }
  }

  @NotNull
  private static FileChooserDescriptor createSingleFolderDescriptor(@NotNull String title, @NotNull Function<File, Void> validation) {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
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
    return !myOriginalSdkHomePath.equals(getSdkLocation().getPath()) ||
           !myOriginalNdkHomePath.equals(getNdkLocation().getPath()) ||
           !myOriginalJdkHomePath.equals(getJdkLocation().getPath());
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
    }
    else {
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
    Component toFocus = myComponentsById.get(mySelectedComponentId);
    return toFocus instanceof JComponent ? (JComponent)toFocus : mySdkLocationTextField.getTextField();
  }

  public boolean validate() throws ConfigurationException {
    String msg = validateAndroidSdkPath();
    if (msg != null) {
      throw new ConfigurationException(msg);
    }

    File validJdkLocation = validateJdkPath(getJdkLocation());
    if (validJdkLocation == null) {
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

    File jdkLocation = validateJdkPath(getJdkLocation());
    if (jdkLocation == null) {
      ProjectConfigurationError error =
        new ProjectConfigurationError(CHOOSE_VALID_JDK_DIRECTORY_ERR, myJdkLocationTextField.getTextField());
      errors.add(error);
    }
    else {
      JavaSdkVersion version = Jdks.findVersion(jdkLocation);
      if (version == null || !version.isAtLeast(JDK_1_8)) {
        ProjectConfigurationError error =
          new ProjectConfigurationError("Please choose JDK 8 or newer", myJdkLocationTextField.getTextField());
        errors.add(error);
      }
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
    //noinspection deprecation
    WizardUtils.ValidationResult wizardValidationResult =
      validateLocation(getSdkLocation().getAbsolutePath(), "Android SDK location", false, WritableCheckMode.DO_NOT_CHECK);
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
        adjustNdkQuickFixVisibility();
        String msg = validationResult.message;
        if (isEmpty(msg)) {
          msg = CHOOSE_VALID_NDK_DIRECTORY_ERR;
        }
        return msg;
      }
    }
    else if (myNdkLocationTextField.isVisible()) {
      adjustNdkQuickFixVisibility();
    }
    return null;
  }

  private void adjustNdkQuickFixVisibility() {
    boolean hasNdk = IdeSdks.getAndroidNdkPath() != null;
    myNdkDownloadPanel.setVisible(!hasNdk);
    myNdkResetHyperlinkLabel.setVisible(hasNdk);
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

  /**
   * Validates that the given directory belongs to a JDK installation.
   * @param file the directory to validate.
   * @return the path of the JDK installation if valid, or {@code null} if the path is not valid.
   */
  @Nullable
  private File validateJdkPath(@NotNull File file) {
    if (JavaSdk.checkForJdk(file)) {
      return file;
    }
    if (SystemInfo.isMac) {
      File potentialPath = new File(file, IdeSdks.MAC_JDK_CONTENT_PATH);
      if (potentialPath.isDirectory() && JavaSdk.checkForJdk(potentialPath)) {
        myJdkLocationTextField.setText(potentialPath.getPath());
        return potentialPath;
      }
    }
    return null;
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

  @Override
  public void setHistory(History history) {
    myHistory = history;
  }

  @Override
  public ActionCallback navigateTo(@Nullable Place place, boolean requestFocus) {
    if (place != null) {
      Object path = place.getPath(SDKS_PLACE);
      if (path instanceof String) {
        Component c = myComponentsById.get(path);
        if (c != null) {
          c.requestFocusInWindow();
        }
      }
    }
    return ActionCallback.DONE;
  }

  @Override
  public void queryPlace(@NotNull Place place) {
    place.putPath(SDKS_PLACE, mySelectedComponentId);
  }
}
