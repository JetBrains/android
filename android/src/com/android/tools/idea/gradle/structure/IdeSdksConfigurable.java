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

import static com.android.SdkConstants.FD_NDK;
import static com.android.SdkConstants.NDK_DIR_PROPERTY;
import static com.android.tools.idea.io.FilePaths.toSystemDependentPath;
import static com.android.tools.idea.npw.PathValidationResult.validateLocation;
import static com.android.tools.idea.sdk.IdeSdks.getJdkFromJavaHome;
import static com.android.tools.idea.sdk.SdkPaths.validateAndroidNdk;
import static com.android.tools.idea.sdk.SdkPaths.validateAndroidSdk;
import static com.android.tools.idea.sdk.wizard.SdkQuickfixUtils.createDialogForPaths;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;
import static com.intellij.openapi.fileChooser.FileChooser.chooseFile;
import static com.intellij.openapi.projectRoots.JdkUtil.checkForJdk;
import static com.intellij.openapi.util.io.FileUtilRt.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.ui.UIUtil.getLabelBackground;
import static com.intellij.util.ui.UIUtil.getTextFieldBackground;

import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoManager;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.npw.PathValidationResult;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeSdks;
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
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
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
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows the user set global Android SDK and JDK locations that are used for Gradle-based Android projects.
 */
public class IdeSdksConfigurable implements Place.Navigator, Configurable {
  @NonNls private static final String SDKS_PLACE = "sdks.place";
  @NonNls public static final String IDE_SDKS_LOCATION_VIEW = "IdeSdksView";

  private static final String CHOOSE_VALID_JDK_DIRECTORY_ERR_FORMAT = "Please choose a valid JDK %s directory.";
  private static final String CHOOSE_VALID_SDK_DIRECTORY_ERR = "Please choose a valid Android SDK directory.";
  private static final String CHOOSE_VALID_NDK_DIRECTORY_ERR = "Please choose a valid Android NDK directory.";

  private static final Logger LOG = Logger.getInstance(IdeSdksConfigurable.class);

  @Nullable private final Configurable myHost;
  @Nullable private final Project myProject;

  @NotNull private final BiMap<String, Component> myComponentsById = HashBiMap.create();

  // These paths are system-dependent.
  @NotNull private String myUserSelectedJdkHomePath = "";
  @Nullable private String myOriginalNdkHomePath;
  @Nullable private String myOriginalSdkHomePath;
  @Nullable private String myOriginalJdkHomePath;
  @Nullable private JdkLocationType myOriginalJdkLocationType;

  private HyperlinkLabel myNdkDownloadHyperlinkLabel;
  private HyperlinkLabel myNdkResetHyperlinkLabel;
  private TextFieldWithBrowseButton mySdkLocationTextField;
  private TextFieldWithBrowseButton myNdkLocationTextField;
  private TextFieldWithBrowseButton myJdkLocationTextField;
  private JPanel myWholePanel;
  private JPanel myNdkDownloadPanel;
  @SuppressWarnings("unused") private AsyncProcessIcon myNdkCheckProcessIcon;
  private JRadioButton myUseJavaHomeEnvironmentVariableRadioButton;
  private JRadioButton myUseEmbeddedJdkRadioButton;
  private JRadioButton myOtherRadioButton;

  private DetailsComponent myDetailsComponent;
  private History myHistory;

  private String mySelectedComponentId;
  private boolean mySdkLoadingRequested = false;

  private enum JdkLocationType {
    JAVA_HOME,
    EMBEDDED,
    USER_DEFINED
  }

  public IdeSdksConfigurable(@Nullable Configurable host, @Nullable Project project) {
    myHost = host;
    myProject = project;
    myWholePanel.setPreferredSize(JBUI.size(700, 500));
    myWholePanel.setName(IDE_SDKS_LOCATION_VIEW);

    myDetailsComponent = new DetailsComponent();
    myDetailsComponent.setContent(myWholePanel);
    myDetailsComponent.setText("SDK Location");

    // We can't update The IDE-level ndk directory. Due to that disabling the ndk directory option in the default Project Structure dialog.
    if (myProject == null || myProject.isDefault()) {
      myNdkLocationTextField.setEnabled(false);
    }

    adjustNdkQuickFixVisibility();

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

    myUseJavaHomeEnvironmentVariableRadioButton.addChangeListener(event -> {
      if (myUseJavaHomeEnvironmentVariableRadioButton.isSelected()) {
        String javaHome = getJdkFromJavaHome();
        if (javaHome == null) {
          javaHome = "";
        }
        setJdkFieldText(javaHome, false/* button disabled */);
      }
    });

    myUseEmbeddedJdkRadioButton.addChangeListener(event -> {
      if (myUseEmbeddedJdkRadioButton.isSelected()) {
        String embeddedJdkPath = EmbeddedDistributionPaths.getInstance().getEmbeddedJdkPath().getPath();
        setJdkFieldText(embeddedJdkPath, false /* button disabled */);
      }
    });

    myOtherRadioButton.addChangeListener( event -> {
      if (myOtherRadioButton.isSelected()) {
        setJdkFieldText(myUserSelectedJdkHomePath, true /* button enabled */);
        myJdkLocationTextField.getTextField().requestFocus();
      }
    });

    addHistoryUpdater("mySdkLocationTextField", mySdkLocationTextField.getTextField(), historyUpdater);
    addHistoryUpdater("myJdkLocationTextField", myJdkLocationTextField.getTextField(), historyUpdater);
    addHistoryUpdater("myNdkLocationTextField", myNdkLocationTextField.getTextField(), historyUpdater);
  }

  private void setJdkFieldText(@NotNull String path, boolean enableButton) {
    myJdkLocationTextField.setText(path);
    updateJdkTextField(enableButton);
  }

  private void maybeLoadSdks(@Nullable Project project) {
    if (mySdkLoadingRequested) return;
    mySdkLoadingRequested = true;
    CardLayout layout = (CardLayout)myNdkDownloadPanel.getLayout();
    layout.show(myNdkDownloadPanel, "loading");

    ProgressIndicator logger = new StudioLoggerProgressIndicator(getClass());
    RepoManager repoManager = AndroidSdks.getInstance().tryToChooseSdkHandler().getSdkManager(logger);
    StudioProgressRunner runner = new StudioProgressRunner(false, false, "Loading Remote SDK", project);
    RepoManager.RepoLoadedCallback onComplete = packages ->
      ApplicationManager.getApplication().invokeLater(() -> {
        if (packages.getRemotePackages().get(FD_NDK) != null) {
          layout.show(myNdkDownloadPanel, "link");
        }
        else {
          myNdkDownloadPanel.setVisible(false);
        }
      }, ModalityState.any());
    Runnable onError = () -> ApplicationManager.getApplication().invokeLater(
      () -> myNdkDownloadPanel.setVisible(false),
      ModalityState.any());
    repoManager.load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, ImmutableList.of(onComplete), ImmutableList.of(onError), runner,
                     new StudioDownloader(), StudioSettingsController.getInstance(), false);
  }

  private void addHistoryUpdater(@NotNull String id, @NotNull Component c, @NotNull FocusListener historyUpdater) {
    myComponentsById.put(id, c);
    c.addFocusListener(historyUpdater);
  }

  @Override
  public void disposeUIResources() {
    mySdkLoadingRequested = false;
  }

  @Override
  public void reset() {
    myOriginalSdkHomePath = getIdeAndroidSdkPath();
    myOriginalNdkHomePath = getIdeNdkPath();
    myOriginalJdkHomePath = getIdeJdkPath();
    mySdkLocationTextField.setText(myOriginalSdkHomePath);
    myNdkLocationTextField.setText(myOriginalNdkHomePath);

    myJdkLocationTextField.setText(myOriginalJdkHomePath);
    myUserSelectedJdkHomePath = myOriginalJdkHomePath;

    IdeSdks ideSdks = IdeSdks.getInstance();
    if (ideSdks.isUsingJavaHomeJdk()) {
      myOriginalJdkLocationType = JdkLocationType.JAVA_HOME;
      myUseJavaHomeEnvironmentVariableRadioButton.setSelected(true);
    }
    else if (ideSdks.isUsingEmbeddedJdk()) {
      myOriginalJdkLocationType = JdkLocationType.EMBEDDED;
      myUseEmbeddedJdkRadioButton.setSelected(true);
    }
    else {
      myOriginalJdkLocationType = JdkLocationType.USER_DEFINED;
      myOtherRadioButton.setSelected(true);
    }
    updateJdkTextField(myOriginalJdkLocationType == JdkLocationType.USER_DEFINED);
  }

  private void updateJdkTextField(boolean enableButton) {
    Color background = enableButton ? getTextFieldBackground() : getLabelBackground();
    JTextField textField = myJdkLocationTextField.getTextField();
    textField.setBackground(background);
    textField.setEditable(false);
    myJdkLocationTextField.getButton().setEnabled(enableButton);
  }

  @Override
  public void apply() throws ConfigurationException {
    if (!isModified()) {
      return;
    }
    if (validateJdkPath(getJdkLocation()) == null) {
      throw new ConfigurationException(generateChooseValidJdkDirectoryError());
    }
    ApplicationManager.getApplication().runWriteAction(() -> {
      // Setting the Sdk path will trigger the project sync. Set the Ndk path and Jdk path before the Sdk path to get the changes to them
      // to take effect during the sync.
      saveAndroidNdkPath();

      IdeSdks ideSdks = IdeSdks.getInstance();
      ideSdks.setJdkPath(getJdkLocation());
      ideSdks.setAndroidSdkPath(getSdkLocation(), myProject);

      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        IdeSdks.updateWelcomeRunAndroidSdkAction();
      }
    });
  }

  @NotNull
  public static String generateChooseValidJdkDirectoryError() {
    return String.format(CHOOSE_VALID_JDK_DIRECTORY_ERR_FORMAT, IdeSdks.getInstance().getRunningVersionOrDefault().getDescription());
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
      VirtualFile chosen = chooseFile(descriptor, null, suggestedDir);
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
        File androidNdkPath = IdeSdks.getInstance().getAndroidNdkPath();
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
          File ndk = IdeSdks.getInstance().getAndroidNdkPath();
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
    myJdkLocationTextField = new TextFieldWithBrowseButton(textField, e -> chooseJdkLocation());
  }

  public void chooseJdkLocation() {
    myJdkLocationTextField.getTextField().requestFocus();

    VirtualFile suggestedDir = null;
    File jdkLocation = getUserSelectedJdkLocation();
    if (jdkLocation.isDirectory()) {
      suggestedDir = findFileByIoFile(jdkLocation, false);
    }
    VirtualFile chosen = chooseFile(createSingleFolderDescriptor("Choose JDK Location", file -> {
      if (validateJdkPath(file) == null) {
        throw new IllegalArgumentException(generateChooseValidJdkDirectoryError());
      }
      return null;
    }), null, suggestedDir);
    if (chosen != null) {
      File validJdkLocation = validateJdkPath(virtualToIoFile(chosen));
      assert validJdkLocation != null;
      myUserSelectedJdkHomePath = validJdkLocation.getPath();
      myJdkLocationTextField.setText(myUserSelectedJdkHomePath);
    }
  }

  private void installValidationListener(@NotNull JTextField textField) {
    if (myHost instanceof AndroidProjectStructureConfigurable) {
      textField.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(@NotNull DocumentEvent e) {
          ((AndroidProjectStructureConfigurable)myHost).requestValidation();
        }
      });
    }
  }

  @NotNull
  private static FileChooserDescriptor createSingleFolderDescriptor(@NotNull String title, @NotNull Function<File, Void> validation) {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
      @Override
      public void validateSelectedFiles(@NotNull VirtualFile[] files) {
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
    maybeLoadSdks(myProject);
    return myDetailsComponent.getComponent();
  }

  @NotNull
  public JComponent getContentPanel() {
    return myWholePanel;
  }

  @Override
  public boolean isModified() {
    return !Objects.equals(myOriginalSdkHomePath, getSdkLocation().getPath()) ||
           !Objects.equals(myOriginalNdkHomePath, getNdkLocation().getPath()) ||
           !Objects.equals(myOriginalJdkHomePath, getJdkLocation().getPath()) ||
           myOriginalJdkLocationType != getCurrentJdkLocationType();
  }

  private JdkLocationType getCurrentJdkLocationType() {
    if (myUseJavaHomeEnvironmentVariableRadioButton.isSelected()) {
      return JdkLocationType.JAVA_HOME;
    }
    if (myUseEmbeddedJdkRadioButton.isSelected()) {
      return JdkLocationType.EMBEDDED;
    }
    return JdkLocationType.USER_DEFINED;
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
    IdeSdks ideSdks = IdeSdks.getInstance();
    List<Sdk> allAndroidSdks = ideSdks.getEligibleAndroidSdks();
    if (!allAndroidSdks.isEmpty()) {
      return allAndroidSdks.get(0);
    }
    if (!create) {
      return null;
    }
    AndroidSdkData sdkData = AndroidSdks.getInstance().tryToChooseAndroidSdk();
    if (sdkData == null) {
      return null;
    }
    List<Sdk> sdks = ideSdks.createAndroidSdkPerAndroidTarget(sdkData.getLocation());
    return !sdks.isEmpty() ? sdks.get(0) : null;
  }

  /**
   * @return what the IDE is using as the home path for the Android SDK for new projects.
   */
  @NotNull
  private static String getIdeAndroidSdkPath() {
    File path = IdeSdks.getInstance().getAndroidSdkPath();
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
  private String getIdeNdkPath() {
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
      File path = IdeSdks.getInstance().getAndroidNdkPath();
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
  private static String getIdeJdkPath() {
    File javaHome =  IdeSdks.getInstance().getJdkPath();
    return javaHome != null ? javaHome.getPath() : "";
  }

  @NotNull
  private File getSdkLocation() {
    String sdkLocation = mySdkLocationTextField.getText();
    return toSystemDependentPath(sdkLocation);
  }

  @NotNull
  private File getNdkLocation() {
    String ndkLocation = myNdkLocationTextField.getText();
    return toSystemDependentPath(ndkLocation);
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

    if (!myUseEmbeddedJdkRadioButton.isSelected()) {
      if (validateJdkPath(getJdkLocation()) == null) {
        throw new ConfigurationException(generateChooseValidJdkDirectoryError());
      }
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

    if (validateJdkPath(getJdkLocation()) == null) {
      ProjectConfigurationError error =
        new ProjectConfigurationError(generateChooseValidJdkDirectoryError(), myJdkLocationTextField.getTextField());
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
    //noinspection deprecation
    PathValidationResult wizardValidationResult =
      validateLocation(getSdkLocation().getAbsolutePath(), "Android SDK location", false,
                       PathValidationResult.WritableCheckMode.DO_NOT_CHECK);
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
    // As NDK is required for the projects with NDK modules, considering the empty value as legal.
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
    boolean hasNdk = IdeSdks.getInstance().getAndroidNdkPath() != null;
    myNdkDownloadPanel.setVisible(!hasNdk);
    myNdkResetHyperlinkLabel.setVisible(hasNdk);
  }

  private void hideNdkQuickfixLink() {
    myNdkResetHyperlinkLabel.setVisible(false);
    myNdkDownloadPanel.setVisible(false);
  }

  @NotNull
  private File getUserSelectedJdkLocation() {
    String jdkLocation = nullToEmpty(myUserSelectedJdkHomePath);
    return toSystemDependentPath(jdkLocation);
  }

  @NotNull
  private File getJdkLocation() {
    String jdkLocation = myJdkLocationTextField.getText();
    return toSystemDependentPath(jdkLocation);
  }

  /**
   * Validates that the given directory belongs to a valid JDK installation.
   * @param file the directory to validate.
   * @return the path of the JDK installation if valid, or {@code null} if the path is not valid.
   */
  @Nullable
  private File validateJdkPath(@NotNull File file) {
    File possiblePath = IdeSdks.getInstance().validateJdkPath(file);
    if (possiblePath != null) {
      myJdkLocationTextField.setText(possiblePath.getPath());
      return possiblePath;
    }
    return null;
  }

  /**
   * @return {@code true} if the configurable is needed: e.g. if we're missing a JDK or an Android SDK setting.
   */
  public static boolean isNeeded() {
    String jdkPath = getIdeJdkPath();
    String sdkPath = getIdeAndroidSdkPath();

    IdeSdks ideSdks = IdeSdks.getInstance();

    boolean validJdk = ideSdks.isUsingEmbeddedJdk() || (!jdkPath.isEmpty() && checkForJdk(new File(jdkPath)));
    boolean validSdk = !sdkPath.isEmpty() && ideSdks.isValidAndroidSdkPath(new File(sdkPath));

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
        if (requestFocus && c != null) {
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
