/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.NumberFormatter;

import static com.android.tools.idea.gradle.project.GradleExperimentalSettings.ENABLE_NEW_PSD_SYSTEM_PROPERTY;
import static com.android.tools.idea.gradle.util.Projects.isBuildWithGradle;

public class GradleExperimentalSettingsConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  @NotNull private final GradleExperimentalSettings mySettings;

  private JPanel myPanel;
  private JCheckBox myEnableModuleSelectionOnImportCheckBox;
  private JSpinner myModuleNumberSpinner;
  private JCheckBox mySkipSourceGenOnSyncCheckbox;
  private JCheckBox myLoadAllTestArtifactsCheckbox;
  private JCheckBox myUseNewProjectStructureCheckBox;
  private JCheckBox myGroupNativeSourcesByArtifact;

  private boolean myLoadAllTestArtifactsChanged;
  private boolean myGroupNativeSourcesByArtifactChanged;

  public GradleExperimentalSettingsConfigurable() {
    mySettings = GradleExperimentalSettings.getInstance();
    //myUseNewProjectStructureCheckBox.setVisible(SystemProperties.getBooleanProperty(ENABLE_NEW_PSD_SYSTEM_PROPERTY, false));
  }

  @Override
  @NotNull
  public String getId() {
    return "gradle.experimental";
  }

  @Override
  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "Experimental";
  }

  @Override
  @Nullable
  public String getHelpTopic() {
    return null;
  }

  @Override
  @NotNull
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public boolean isModified() {
    if (mySettings.SELECT_MODULES_ON_PROJECT_IMPORT != isModuleSelectionOnImportEnabled() ||
        mySettings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC != isSkipSourceGenOnSync() ||
        mySettings.LOAD_ALL_TEST_ARTIFACTS != isLoadAllTestArtifacts() ||
        mySettings.USE_NEW_PROJECT_STRUCTURE_DIALOG != isUseNewProjectStructureDialog() ||
        mySettings.GROUP_NATIVE_SOURCES_BY_ARTIFACT != isGroupNativeSourcesByArtifact()) {
      return true;
    }
    Integer value = getMaxModuleCountForSourceGen();
    return value != null && mySettings.MAX_MODULE_COUNT_FOR_SOURCE_GEN != value;
  }

  @Override
  public void apply() throws ConfigurationException {
    mySettings.SELECT_MODULES_ON_PROJECT_IMPORT = isModuleSelectionOnImportEnabled();
    mySettings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC = isSkipSourceGenOnSync();

    boolean loadAllTestArtifacts = isLoadAllTestArtifacts();
    if (mySettings.LOAD_ALL_TEST_ARTIFACTS != loadAllTestArtifacts) {
      mySettings.LOAD_ALL_TEST_ARTIFACTS = loadAllTestArtifacts;
      myLoadAllTestArtifactsChanged = true;
    }

    mySettings.USE_NEW_PROJECT_STRUCTURE_DIALOG = isUseNewProjectStructureDialog();

    Integer value = getMaxModuleCountForSourceGen();
    if (value != null) {
      mySettings.MAX_MODULE_COUNT_FOR_SOURCE_GEN = value;
    }

    boolean isGroupNativeSourcesByArtifact = isGroupNativeSourcesByArtifact();
    if (mySettings.GROUP_NATIVE_SOURCES_BY_ARTIFACT != isGroupNativeSourcesByArtifact) {
      mySettings.GROUP_NATIVE_SOURCES_BY_ARTIFACT = isGroupNativeSourcesByArtifact;
      myGroupNativeSourcesByArtifactChanged = true;
    }
  }

  @Nullable
  private Integer getMaxModuleCountForSourceGen() {
    Object value = myModuleNumberSpinner.getValue();
    return value instanceof Integer ? (Integer)value : null;
  }

  private boolean isModuleSelectionOnImportEnabled() {
    return myEnableModuleSelectionOnImportCheckBox.isSelected();
  }

  private boolean isSkipSourceGenOnSync() {
    return mySkipSourceGenOnSyncCheckbox.isSelected();
  }

  private boolean isLoadAllTestArtifacts() {
    return myLoadAllTestArtifactsCheckbox.isSelected();
  }

  private boolean isUseNewProjectStructureDialog() {
    return myUseNewProjectStructureCheckBox.isSelected();
  }

  private boolean isGroupNativeSourcesByArtifact() {
    return myGroupNativeSourcesByArtifact.isSelected();
  }

  @Override
  public void reset() {
    myEnableModuleSelectionOnImportCheckBox.setSelected(mySettings.SELECT_MODULES_ON_PROJECT_IMPORT);
    mySkipSourceGenOnSyncCheckbox.setSelected(mySettings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC);
    myModuleNumberSpinner.setValue(mySettings.MAX_MODULE_COUNT_FOR_SOURCE_GEN);
    myLoadAllTestArtifactsCheckbox.setSelected(mySettings.LOAD_ALL_TEST_ARTIFACTS);
    myUseNewProjectStructureCheckBox.setSelected(mySettings.USE_NEW_PROJECT_STRUCTURE_DIALOG);
    myGroupNativeSourcesByArtifact.setSelected(mySettings.GROUP_NATIVE_SOURCES_BY_ARTIFACT);
  }

  @Override
  public void disposeUIResources() {
    if (myLoadAllTestArtifactsChanged) {
      // Need to invoke later due to new "Dumb mode" rules introduced in IDEA 15
      // See: DumbService#allowStartingDumbModeInside
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          syncAllGradleProjects();
        }
      }, ModalityState.NON_MODAL);
    }

    if (myGroupNativeSourcesByArtifactChanged) {
      for (final Project project : ProjectManager.getInstance().getOpenProjects()) {
        ProjectView.getInstance(project).refresh();
      }
    }
  }

  private static void syncAllGradleProjects() {
    for (final Project project : ProjectManager.getInstance().getOpenProjects()) {
      if (isBuildWithGradle(project)) {
        new Task.Backgroundable(project, "Gradle Sync") {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            if (!project.isDisposed()) {
              // Sync project with cached model. If there is no cache, the IDE will perform a full sync.
              GradleProjectImporter.getInstance().requestProjectSync(project, true, false, false, null);
            }
          }
        }.queue();
      }
    }
  }

  private void createUIComponents() {
    int value = GradleExperimentalSettings.getInstance().MAX_MODULE_COUNT_FOR_SOURCE_GEN;
    myModuleNumberSpinner = new JSpinner(new SpinnerNumberModel(value, 0, Integer.MAX_VALUE, 1));
    // Force the spinner to accept numbers only.
    JComponent editor = myModuleNumberSpinner.getEditor();
    if (editor instanceof JSpinner.NumberEditor) {
      JFormattedTextField textField = ((JSpinner.NumberEditor)editor).getTextField();
      JFormattedTextField.AbstractFormatter formatter = textField.getFormatter();
      if (formatter instanceof NumberFormatter) {
        ((NumberFormatter)formatter).setAllowsInvalid(false);
      }
    }
  }
}
