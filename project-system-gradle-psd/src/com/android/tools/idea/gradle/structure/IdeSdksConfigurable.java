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
import static com.android.tools.adtui.validation.Validator.Severity.ERROR;
import static com.android.tools.idea.gradle.project.sync.hyperlink.OpenGradleSettingsHyperlink.showGradleSettings;
import static com.android.tools.idea.gradle.structure.NdkProjectStructureUtilKt.supportsSideBySideNdk;
import static com.android.tools.idea.sdk.SdkPaths.validateAndroidNdk;
import static com.android.tools.idea.sdk.SdkPaths.validateAndroidSdk;
import static com.android.tools.idea.sdk.wizard.SdkQuickfixUtils.createDialogForPaths;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.intellij.openapi.fileChooser.FileChooser.chooseFile;
import static com.intellij.openapi.projectRoots.JdkUtil.checkForJdk;
import static com.intellij.openapi.util.io.FileUtilRt.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

import com.android.ide.common.repository.AgpVersion;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoManager;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.idea.gradle.ui.LabelAndFileForLocation;
import com.android.tools.idea.gradle.ui.SdkUiUtils;
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.io.FilePaths;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.SdkPaths.ValidationResult;
import com.android.tools.idea.sdk.StudioDownloader;
import com.android.tools.idea.sdk.StudioSettingsController;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.progress.StudioProgressRunner;
import com.android.tools.idea.ui.validation.validators.PathValidator;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
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
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.KeyStrokeAdapter;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.util.Function;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.JBUI;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
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

  private static final String CHOOSE_VALID_SDK_DIRECTORY_ERR = "Please choose a valid Android SDK directory.";
  private static final String CHOOSE_VALID_NDK_DIRECTORY_ERR = "Please choose a valid Android NDK directory.";

  private static final String JDK_MOVED_TEXT = "JDK location was moved to <hyperlink>Gradle Settings.</hyperlink>";

  private static final Logger LOG = Logger.getInstance(IdeSdksConfigurable.class);

  @Nullable private final Project myProject;

  @NotNull private final BiMap<String, Component> myComponentsById = HashBiMap.create();

  // These paths are system-dependent.
  @Nullable private String myOriginalNdkHomePath;
  @Nullable private String myOriginalSdkHomePath;

  private HyperlinkLabel myNdkDownloadHyperlinkLabel;
  private TextFieldWithBrowseButton mySdkLocationTextField;
  @SuppressWarnings("unused") private JPanel myWholePanel;
  @SuppressWarnings("unused") private JPanel myNdkDownloadPanel;
  @SuppressWarnings("unused") private AsyncProcessIcon myNdkCheckProcessIcon;
  private ComboboxWithBrowseButton myNdkLocationComboBox;
  private HyperlinkLabel myJdkMovedLabel;

  private DetailsComponent myDetailsComponent;
  private History myHistory;

  private String mySelectedComponentId;
  private boolean mySdkLoadingRequested = false;

  public IdeSdksConfigurable(@Nullable Project project) {
    myProject = project;
    myWholePanel.setPreferredSize(JBUI.size(700, 500));
    myWholePanel.setName(IDE_SDKS_LOCATION_VIEW);

    myDetailsComponent = new DetailsComponent(false /* no details */, false /* with border */);
    myDetailsComponent.setContent(myWholePanel);

    boolean supportsSideBySideNdk = true;
    if (myProject != null) {
      AgpVersion gradleModelNumber = GradleProjectSystemUtil.getAndroidGradleModelVersionInUse(project);
      if (gradleModelNumber != null) {
        supportsSideBySideNdk = supportsSideBySideNdk(gradleModelNumber);
      }
    }
    myNdkLocationComboBox.setEnabled(!supportsSideBySideNdk);
    myNdkDownloadHyperlinkLabel.setEnabled(!supportsSideBySideNdk);

    // We can't update The IDE-level ndk directory. Due to that disabling the ndk directory option in the default Project Structure dialog.
    if (!supportsSideBySideNdk && (myProject == null || myProject.isDefault())) {
      myNdkLocationComboBox.setEnabled(false);
    }

    if (!supportsSideBySideNdk) {
      adjustNdkQuickFixVisibility();
    }

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

    addHistoryUpdater("mySdkLocationTextField", mySdkLocationTextField.getTextField(), historyUpdater);
    if (!supportsSideBySideNdk) {
      addHistoryUpdater("myNdkLocationComboBox", myNdkLocationComboBox.getComboBox(), historyUpdater);
    }

    myJdkMovedLabel.setEnabled(myProject != null && !myProject.isDefault());
  }

  private void maybeLoadSdks(@Nullable Project project) {
    if (mySdkLoadingRequested) return;
    mySdkLoadingRequested = true;
    CardLayout layout = (CardLayout)myNdkDownloadPanel.getLayout();
    layout.show(myNdkDownloadPanel, "loading");

    ProgressIndicator logger = new StudioLoggerProgressIndicator(getClass());
    RepoManager repoManager = AndroidSdks.getInstance().tryToChooseSdkHandler().getSdkManager(logger);
    StudioProgressRunner runner = new StudioProgressRunner(false, false, "Loading Remote SDK", project);
    RepoManager.RepoLoadedListener onComplete = packages ->
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
                     new StudioDownloader(), StudioSettingsController.getInstance());
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
    mySdkLocationTextField.setText(myOriginalSdkHomePath);
    myNdkLocationComboBox.getComboBox().setSelectedItem(myOriginalNdkHomePath);
  }

  @Override
  public void apply() throws ConfigurationException {
    if (!isModified()) {
      return;
    }
    List<ProjectConfigurationError> errors = validateState();
    if (!errors.isEmpty()) {
      throw new ConfigurationException(errors.get(0).getDescription().toString());
    }
    ApplicationManager.getApplication().runWriteAction(() -> {
      // Setting the Sdk path will trigger the project sync. Set the Ndk path and Jdk path before the Sdk path to get the changes to them
      // to take effect during the sync.
      saveAndroidNdkPath();

      IdeSdks ideSdks = IdeSdks.getInstance();
      ideSdks.setAndroidSdkPath(getSdkLocation(), null, myProject);

      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        IdeSdks.updateWelcomeRunAndroidSdkAction();
      }
    });
  }

  private void saveAndroidNdkPath() {
    if (myProject == null || myProject.isDefault()) {
      return;
    }

    try {
      LocalProperties localProperties = new LocalProperties(myProject);
      localProperties.setAndroidNdkPath(getNdkLocation().toFile());
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
                                 getNdkLocation().toString());
      Messages.showErrorDialog(myProject, msg, "Android Ndk Update");
    }
  }

  private void createUIComponents() {
    myNdkCheckProcessIcon = new AsyncProcessIcon("NDK check progress");
    createSdkLocationTextField();
    createNdkLocationComboBox();
    createNdkDownloadLink();
    createJdkMovedLink();
  }

  private void createJdkMovedLink() {
    myJdkMovedLabel = new HyperlinkLabel();
    //noinspection UnstableApiUsage
    myJdkMovedLabel.setTextWithHyperlink(JDK_MOVED_TEXT);
    myJdkMovedLabel.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
        if (myProject != null) {
          showGradleSettings(myProject);
        }
      }
    });
  }

  private void createNdkLocationComboBox() {
    FileChooserDescriptor descriptor = createSingleFolderDescriptor("Choose NDK Location", file -> {
      ValidationResult validationResult = validateAndroidNdk(file, false);
      if (!validationResult.success) {
        adjustNdkQuickFixVisibility();
        String msg = validationResult.message;
        if (isEmpty(msg)) {
          msg = CHOOSE_VALID_NDK_DIRECTORY_ERR;
        }
        throw new IllegalArgumentException(msg);
      }
      JComboBox comboBox = myNdkLocationComboBox.getComboBox();
      setComboBoxFile(comboBox, file.toPath());
      return null;
    });

    myNdkLocationComboBox = new ComboboxWithBrowseButton();
    myNdkLocationComboBox.addBrowseFolderListener(myProject, descriptor);

    JComboBox comboBox = myNdkLocationComboBox.getComboBox();

    File androidNdkPath = IdeSdks.getInstance().getAndroidNdkPath();
    if (androidNdkPath != null) {
      comboBox.addItem(new LabelAndFileForLocation("Default NDK (recommended)", androidNdkPath.toPath()));
    }

    comboBox.setEditable(true);
    comboBox.setSelectedItem(getIdeAndroidSdkPath());

    comboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent event) {
        if (event.getStateChange() == ItemEvent.SELECTED) {
          Object selectedItem = event.getItem();
          if (selectedItem instanceof LabelAndFileForLocation) {
            ApplicationManager.getApplication().invokeLater(() -> setComboBoxFile(comboBox, ((LabelAndFileForLocation)selectedItem).getFile()));
          }
        }
      }
    });
    addToolTipListener(comboBox);
  }

  private static void addToolTipListener(@NotNull JComboBox comboBox) {
    Component component = comboBox.getEditor().getEditorComponent();

    component.addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        super.mouseMoved(e);
        copyItemToToolTip(comboBox);
      }
    });

    component.addKeyListener(new KeyStrokeAdapter() {
      @Override
      public void keyTyped(KeyEvent event) {
        super.keyTyped(event);
        copyItemToToolTip(comboBox);
      }
    });

    comboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        copyItemToToolTip(comboBox);
      }
    });
  }

  private static void copyItemToToolTip(@NotNull JComboBox comboBox) {
    Object item = comboBox.getEditor().getItem();
    if (item != null) {
      comboBox.setToolTipText(item.toString());
    }
    else {
      comboBox.setToolTipText("");
    }
  }

  private static void setComboBoxFile(@NotNull JComboBox comboBox, @NotNull Path file) {
    setComboBoxPath(comboBox, file.toString());
  }

  private static void setComboBoxPath(@NotNull JComboBox comboBox, @NotNull String path) {
    comboBox.setSelectedItem(toSystemDependentName(path));
  }

  private void createSdkLocationTextField() {
    FileChooserDescriptor descriptor = createSingleFolderDescriptor("Choose Android SDK Location", file -> {
      ValidationResult validationResult = validateAndroidSdk(file, false);
      if (!validationResult.success) {
        String msg = validationResult.message;
        if (isEmpty(msg)) {
          msg = CHOOSE_VALID_SDK_DIRECTORY_ERR;
        }
        throw new IllegalArgumentException(msg);
      }
      return null;
    });

    JTextField textField = new JTextField(10);
    mySdkLocationTextField = new TextFieldWithBrowseButton(textField, e -> {
      VirtualFile suggestedDir = null;
      File sdkLocation = getSdkLocation();
      if (sdkLocation.isDirectory()) {
        suggestedDir = findFileByIoFile(sdkLocation, false);
      }
      VirtualFile chosen = chooseFile(descriptor, null, suggestedDir);
      if (chosen != null) {
        File f = virtualToIoFile(chosen);
        textField.setText(toSystemDependentName(f.getPath()));
      }
    });
    textField.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        updateToolTip();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        updateToolTip();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        updateToolTip();
      }

      private void updateToolTip() {
        textField.setToolTipText(textField.getText());
      }
    });
  }

  private void createNdkDownloadLink() {
    myNdkDownloadHyperlinkLabel = new HyperlinkLabel();
    myNdkDownloadHyperlinkLabel.setHyperlinkText("", "Download", " Android NDK.");
    myNdkDownloadHyperlinkLabel.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
        if (validateAndroidSdkPath() != null) {
          Messages.showErrorDialog(getContentPanel(), "Please select a valid SDK before downloading the NDK.");
          return;
        }
        List<String> requested = ImmutableList.of(FD_NDK);
        ModelWizardDialog dialog = createDialogForPaths(myWholePanel, requested, false);
        if (dialog != null && dialog.showAndGet()) {
          File ndk = IdeSdks.getInstance().getAndroidNdkPath();
          if (ndk != null) {
            myNdkLocationComboBox.getComboBox().setSelectedItem(toSystemDependentName(ndk.getPath()));
          }
          validateState();
        }
      }
    });
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
           !Objects.equals(myOriginalNdkHomePath, getNdkLocation().toString());
  }

  /**
   * Returns the first SDK it finds that matches our default naming convention. There will be several SDKs so named, one for each build
   * target installed in the SDK; which of those this method returns is not defined.
   *
   * @return null if an SDK is unavailable or creation failed.
   */
  @Nullable
  private static Sdk getFirstDefaultAndroidSdk() {
    IdeSdks ideSdks = IdeSdks.getInstance();
    List<Sdk> allAndroidSdks = ideSdks.getEligibleAndroidSdks();
    if (!allAndroidSdks.isEmpty()) {
      return allAndroidSdks.get(0);
    }
    AndroidSdkData sdkData = AndroidSdks.getInstance().tryToChooseAndroidSdk();
    if (sdkData == null) {
      return null;
    }
    List<Sdk> sdks = ideSdks.createAndroidSdkPerAndroidTarget(sdkData.getLocationFile());
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
    Sdk sdk = getFirstDefaultAndroidSdk();
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
    Path javaHome =  IdeSdks.getInstance().getJdkPath();
    return javaHome != null ? javaHome.toString() : "";
  }

  @NotNull
  private File getSdkLocation() {
    String sdkLocation = mySdkLocationTextField.getText();
    return FilePaths.stringToFile(sdkLocation);
  }

  @NotNull
  private Path getNdkLocation() {
    return SdkUiUtils.getLocationFromComboBoxWithBrowseButton(myNdkLocationComboBox);
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

    msg = validateAndroidNdkPath();
    if (msg != null) {
      throw new ConfigurationException(msg);
    }

    return true;
  }

  @NotNull
  public List<ProjectConfigurationError> validateState() {
    List<ProjectConfigurationError> errors = new ArrayList<>();

    String msg = validateAndroidSdkPath();
    if (msg != null) {
      ProjectConfigurationError error = new ProjectConfigurationError(msg, mySdkLocationTextField.getTextField());
      errors.add(error);
    }

    msg = validateAndroidNdkPath();
    if (msg != null) {
      ProjectConfigurationError error = new ProjectConfigurationError(msg, myNdkLocationComboBox.getComboBox());
      errors.add(error);
    }

    return errors;
  }

  /**
   * @return the error message when the sdk path is not valid, {@code null} otherwise.
   */
  @Nullable
  private String validateAndroidSdkPath() {
    Validator.Result result = PathValidator.forAndroidSdkLocation().validate(getSdkLocation().toPath());
    Validator.Severity severity = result.getSeverity();
    if (severity == ERROR) {
      return result.getMessage();
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
    Object selectedItem = myNdkLocationComboBox.getComboBox().getSelectedItem();
    String value = "";
    if (selectedItem != null) {
      value = selectedItem.toString();
    }
    if (!value.isEmpty()) {
      ValidationResult validationResult = validateAndroidNdk(getNdkLocation().toFile(), false);
      if (!validationResult.success) {
        adjustNdkQuickFixVisibility();
        String msg = validationResult.message;
        if (isEmpty(msg)) {
          msg = CHOOSE_VALID_NDK_DIRECTORY_ERR;
        }
        return msg;
      }
    }
    else if (myNdkLocationComboBox.isVisible()) {
      adjustNdkQuickFixVisibility();
    }
    return null;
  }

  private void adjustNdkQuickFixVisibility() {
    boolean hasNdk = IdeSdks.getInstance().getAndroidNdkPath() != null;
    myNdkDownloadPanel.setVisible(!hasNdk);
  }

  private void hideNdkQuickfixLink() {
    myNdkDownloadPanel.setVisible(false);
  }

  /**
   * @return {@code true} if the configurable is needed: e.g. if we're missing a JDK or an Android SDK setting.
   */
  public static boolean isNeeded() {
    String jdkPath = getIdeJdkPath();
    String sdkPath = getIdeAndroidSdkPath();

    IdeSdks ideSdks = IdeSdks.getInstance();

    boolean validJdk = ideSdks.isUsingEmbeddedJdk() || (!jdkPath.isEmpty() && checkForJdk(Paths.get(jdkPath)));
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
