/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard.deprecated;

import static com.android.tools.idea.gradle.structure.IdeSdksConfigurable.generateChooseValidJdkDirectoryError;
import static com.android.tools.idea.gradle.structure.IdeSdksConfigurable.getLocationFromComboBoxWithBrowseButton;
import static com.android.tools.idea.gradle.structure.IdeSdksConfigurable.setUpJdkWarningLabelAndLink;
import static com.android.tools.idea.io.FilePaths.toSystemDependentPath;
import static com.android.tools.idea.sdk.IdeSdks.getJdkFromJavaHome;
import static com.android.tools.idea.sdk.IdeSdks.isSameAsJavaHomeJdk;
import static com.android.tools.idea.welcome.wizard.JdkSetupStepKt.createSingleFolderDescriptor;
import static com.android.tools.idea.wizard.WizardConstants.KEY_JDK_LOCATION;
import static com.intellij.openapi.util.io.FileUtilRt.toSystemDependentName;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.structure.IdeSdksConfigurable;
import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.HyperlinkLabel;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wizard step for JDK setup.
 * @deprecated use {@link com.android.tools.idea.welcome.wizard.JdkSetupStep}
 */
public class JdkSetupStep extends FirstRunWizardStep {
  @SuppressWarnings("unused") private JPanel myRootPanel;
  @SuppressWarnings("unused") private ComboboxWithBrowseButton myJdkLocationComboBox;
  @SuppressWarnings("unused") private HyperlinkLabel myJdkWarningLink;
  @SuppressWarnings("unused") private JLabel myJdkWarningLabel;

  public JdkSetupStep() {
    super("Select default JDK Location");
    setUpJdkLocationComboBox();
    setUpJdkWarningLabelAndLink(myJdkWarningLabel, myJdkWarningLink);
    setComponent(myRootPanel);
  }

  private void setUpJdkLocationComboBox() {
    FileChooserDescriptor descriptor = createSingleFolderDescriptor(file -> {
      File validatedFile = validateJdkPath(file);
      if (validatedFile == null) {
        throw new IllegalArgumentException(generateChooseValidJdkDirectoryError());
      }
      setJdkLocationComboBox(file);
      return null;
    });

    myJdkLocationComboBox.addBrowseFolderListener(getProject(), descriptor);
    JComboBox comboBox = myJdkLocationComboBox.getComboBox();
    IdeSdks ideSdks = IdeSdks.getInstance();
    File embeddedPath = ideSdks.getEmbeddedJdkPath();
    if (embeddedPath != null) {
      File validatedPath = validateJdkPath(embeddedPath);
      if (validatedPath != null) {
        comboBox.addItem(new IdeSdksConfigurable.LabelAndFileForLocation("Embedded JDK", validatedPath));
      }
    }
    String javaHomePath = getJdkFromJavaHome();
    if (javaHomePath != null) {
      File validatedPath = validateJdkPath(new File(javaHomePath));
      if (validatedPath != null) {
        comboBox.addItem(new IdeSdksConfigurable.LabelAndFileForLocation("JAVA_HOME", validatedPath));
      }
    }
    comboBox.setEditable(true);
    comboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent event) {
        if (event.getStateChange() == ItemEvent.SELECTED) {
          Object selectedItem = event.getItem();
          if (selectedItem instanceof IdeSdksConfigurable.LabelAndFileForLocation) {
            ApplicationManager.getApplication().invokeLater(() -> setJdkLocationComboBox(((IdeSdksConfigurable.LabelAndFileForLocation)selectedItem).getFile()));
          }
        }
      }
    });
    setJdkLocationComboBox(embeddedPath);
  }

  private void setJdkLocationComboBox(@Nullable File path) {
    if (path == null) {
      myJdkLocationComboBox.getComboBox().setSelectedItem(null);
    }
    else {
      myJdkLocationComboBox.getComboBox().setSelectedItem(toSystemDependentName(path.getPath()));
    }
    setJdkWarningVisibility();
    updateIsValidPath();
  }

  private void setJdkWarningVisibility() {
    boolean visible = !isSameAsJavaHomeJdk(getJdkLocation());
    myJdkWarningLink.setVisible(visible);
    myJdkWarningLabel.setVisible(visible);
  }

  private void updateIsValidPath() {
    invokeUpdate(null);
  }

  @Nullable
  private File validateJdkPath(@NotNull File file) {
    File possiblePath = IdeSdks.getInstance().validateJdkPath(file);
    if (possiblePath != null) {
      setJdkLocationComboBox(possiblePath);
      return possiblePath;
    }
    return null;
  }

  @Override
  public void init() {
    // Apply default selection
    IdeSdks ideSdks = IdeSdks.getInstance();
    File embeddedPath = ideSdks.getEmbeddedJdkPath();
    setJdkLocationComboBox(embeddedPath);
  }

  @Nullable
  @Override
  public JLabel getMessageLabel() {
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myRootPanel;
  }

  @Override
  public boolean validate() {
    if (!isValidJdkPath()) {
      return false;
    }
    return super.validate();
  }

  private boolean isValidJdkPath() {
    return validateJdkPath(getJdkLocation()) != null;
  }

  @Override
  public boolean commitStep() {
    if (!isValidJdkPath()) {
      return false;
    }
    File path = toSystemDependentPath(getJdkLocation().getPath());
    ApplicationManager.getApplication().runWriteAction(() -> IdeSdks.getInstance().setJdkPath(path));
    myState.put(KEY_JDK_LOCATION, path.getPath());
    return true;
  }

  @NotNull
  private File getJdkLocation() {
    return getLocationFromComboBoxWithBrowseButton(myJdkLocationComboBox);
  }

  @Override
  public boolean isStepVisible() {
    return StudioFlags.NPW_SHOW_JDK_STEP.get() && Boolean.TRUE.equals(myState.get(FirstRunWizard.KEY_CUSTOM_INSTALL));
  }
}
