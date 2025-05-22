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

import static com.android.tools.adtui.validation.Validator.Severity.ERROR;
import static com.android.tools.sdk.SdkPaths.validateAndroidSdk;
import static com.intellij.openapi.fileChooser.FileChooser.chooseFile;
import static com.intellij.openapi.util.io.FileUtilRt.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

import com.android.tools.adtui.validation.Validator;
import com.android.tools.idea.io.FilePaths;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.ui.validation.validators.PathValidator;
import com.android.tools.sdk.AndroidSdkData;
import com.android.tools.sdk.AndroidSdkPath;
import com.android.tools.sdk.SdkPaths.ValidationResult;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.DetailsComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.util.Function;
import com.intellij.util.ui.JBUI;
import java.awt.Component;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows the user set global Android SDK locations that are used for Android projects.
 */
public class IdeSdksConfigurable implements Place.Navigator, Configurable {
  @NonNls private static final String SDKS_PLACE = "sdks.place";
  @NonNls public static final String IDE_SDKS_LOCATION_VIEW = "IdeSdksView";

  private static final String CHOOSE_VALID_SDK_DIRECTORY_ERR = "Please choose a valid Android SDK directory.";

  @NotNull private final BiMap<String, Component> myComponentsById = HashBiMap.create();

  // These paths are system-dependent.
  @Nullable private String myOriginalSdkHomePath;

  private TextFieldWithBrowseButton mySdkLocationTextField;
  @SuppressWarnings("unused") private JPanel myWholePanel;

  private final DetailsComponent myDetailsComponent;
  private History myHistory;

  private String mySelectedComponentId;

  public IdeSdksConfigurable() {
    myWholePanel.setPreferredSize(JBUI.size(700, 500));
    myWholePanel.setName(IDE_SDKS_LOCATION_VIEW);

    myDetailsComponent = new DetailsComponent(false /* no details */, false /* with border */);
    myDetailsComponent.setContent(myWholePanel);

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
  }

  private void addHistoryUpdater(@NotNull String id, @NotNull Component c, @NotNull FocusListener historyUpdater) {
    myComponentsById.put(id, c);
    c.addFocusListener(historyUpdater);
  }

  @Override
  public void reset() {
    myOriginalSdkHomePath = getIdeAndroidSdkPath();
    mySdkLocationTextField.setText(myOriginalSdkHomePath);
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
      IdeSdks ideSdks = IdeSdks.getInstance();
      ideSdks.setAndroidSdkPath(getSdkLocation());

      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        IdeSdks.updateWelcomeRunAndroidSdkAction();
      }
    });
  }

  private void createUIComponents() {
    createSdkLocationTextField();
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
      ValidationResult validationResult = validateAndroidSdk(file.toPath(), false);
      if (!validationResult.success) {
        String msg = validationResult.message;
        if (isEmpty(msg)) {
          msg = CHOOSE_VALID_SDK_DIRECTORY_ERR;
        }
        throw new IllegalArgumentException(msg);
      }
      return null;
    });

    JTextField textField = new JTextField(67);
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
    return AndroidBundle.message("configurable.IdeSdksConfigurable.display.name");
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

  @Override
  public boolean isModified() {
    return !Objects.equals(myOriginalSdkHomePath, getSdkLocation().getPath());
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

  @NotNull
  private File getSdkLocation() {
    String sdkLocation = mySdkLocationTextField.getText();
    return FilePaths.stringToFile(sdkLocation);
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    Component toFocus = myComponentsById.get(mySelectedComponentId);
    return toFocus instanceof JComponent ? (JComponent)toFocus : mySdkLocationTextField.getTextField();
  }

  public boolean validate() throws ConfigurationException {
    String msg = validateAndroidSdkPath();
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
    ValidationResult validationResult = validateAndroidSdk(getSdkLocation().toPath(), false);
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
   * @return {@code true} if the configurable is needed: e.g. if we're missing an Android SDK setting.
   */
  public static boolean isNeeded() {
    String sdkPath = getIdeAndroidSdkPath();
    boolean validSdk = !sdkPath.isEmpty() && AndroidSdkPath.isValid(new File(sdkPath));
    return !validSdk;
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
