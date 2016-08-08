/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw.importing;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.npw.AsyncValidator;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.ListenerManager;
import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.android.tools.idea.ui.properties.core.StringProperty;
import com.android.tools.idea.ui.properties.core.StringValueProperty;
import com.android.tools.idea.ui.properties.swing.IconProperty;
import com.android.tools.idea.ui.properties.swing.SelectedProperty;
import com.android.tools.idea.ui.properties.swing.TextProperty;
import com.android.tools.idea.ui.properties.swing.VisibleProperty;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.Optional;
import java.util.Set;

/**
 * Step for selecting archive to import and specifying Gradle subproject name.
 */
public final class ArchiveToGradleModuleStep extends ModelWizardStep<ArchiveToGradleModuleModel> {
  private static final Set<String> SUPPORTED_EXTENSIONS = ImmutableSet.of("jar", "aar");

  private final ListenerManager myListeners = new ListenerManager();
  private final BindingsManager myBindings = new BindingsManager();

  // An empty string means no validation errors.
  private final StringProperty myValidationString = new StringValueProperty();

  private JPanel myPanel;
  private JTextField myGradlePath;
  private TextFieldWithBrowseButton myArchivePath;
  private JBLabel myValidationStatus;
  private JCheckBox myRemoveOriginalFileCheckBox;

  public ArchiveToGradleModuleStep(@NotNull ArchiveToGradleModuleModel model) {
    super(model, AndroidBundle.message("android.wizard.module.import.library.title"));

    myArchivePath.addBrowseFolderListener(AndroidBundle.message("android.wizard.module.import.library.browse.title"),
                                          AndroidBundle.message("android.wizard.module.import.library.browse.description"),
                                          model.getProject(),
                                          new FileChooserDescriptor(true, false, true, true, false, false)
                                            .withFileFilter(ArchiveToGradleModuleStep::isValidExtension));

    myBindings.bindTwoWay(model.archive(), new TextProperty(myArchivePath.getTextField()));
    myBindings.bindTwoWay(model.gradlePath(), new TextProperty(myGradlePath));
    myBindings.bindTwoWay(model.moveArchive(), new SelectedProperty(myRemoveOriginalFileCheckBox));

    myBindings.bind(new IconProperty(myValidationStatus),
                    myValidationString.isEmpty().transform(
                      isEmpty -> Optional.ofNullable(isEmpty ? null : MessageType.ERROR.getDefaultIcon())));
    myBindings.bind(new TextProperty(myValidationStatus), myValidationString);
    myBindings.bind(new VisibleProperty(myRemoveOriginalFileCheckBox), model.inModule());

    myListeners.listen(model.archive(), updated -> model.gradlePath().set(Files.getNameWithoutExtension(model.archive().get())));

    //noinspection TestOnlyProblems
    UserInputValidator validator = new UserInputValidator(model.getProject());
    myListeners.listenAll(model.archive(), model.gradlePath()).withAndFire(validator::copyDataAndInvalidate);
  }

  static boolean isValidExtension(VirtualFile file) {
    @NonNls final String extension = file.getExtension();
    return extension != null && SUPPORTED_EXTENSIONS.contains(extension.toLowerCase());
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  @NotNull
  public JComponent getPreferredFocusComponent() {
    return myArchivePath.getTextField();
  }

  @Override
  @NotNull
  protected ObservableBool canGoForward() {
    return myValidationString.isEmpty();
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }

  @VisibleForTesting
  void setValidationString(String result) {
    myValidationString.set(result);
  }

  @VisibleForTesting
  final class UserInputValidator extends AsyncValidator<String> {

    private final Project myProject;
    private String myArchive;
    private String myGradlePath;

    public UserInputValidator(@NotNull Project project) {
      super(ApplicationManager.getApplication());
      myProject = project;
    }

    public void copyDataAndInvalidate() {
      setData(getModel().archive().get(), getModel().gradlePath().get());
      invalidate();
    }

    /**
     * Synchronized to ensure validate will always see up-to-date data when it runs.
     */
    @VisibleForTesting
    synchronized void setData(String archive, String gradlePath) {
      myArchive = archive;
      myGradlePath = gradlePath;
    }

    @Override
    protected void showValidationResult(String result) {
      setValidationString(result);
    }

    @NotNull
    @Override
    protected synchronized String validate() {
      if (Strings.isNullOrEmpty(myArchive)) {
        return AndroidBundle.message("android.wizard.module.import.library.no.path");
      }
      final File archiveFile = new File(myArchive);
      if (!archiveFile.isFile()) {
        return AndroidBundle.message("android.wizard.module.import.library.bad.path");
      }
      final VirtualFile archiveVirtualFile = VfsUtil.findFileByIoFile(archiveFile, true);
      if (!isValidExtension(archiveVirtualFile)) {
        return AndroidBundle.message("android.wizard.module.import.library.bad.extension");
      }

      if (Strings.isNullOrEmpty(myGradlePath)) {
        return AndroidBundle.message("android.wizard.module.import.library.no.name");
      }
      final int invalidCharIndex = GradleUtil.isValidGradlePath(myGradlePath);
      if (invalidCharIndex >= 0) {
        return AndroidBundle.message("android.wizard.module.import.library.bad.name", myGradlePath.charAt(invalidCharIndex));
      }

      if (GradleUtil.hasModule(myProject, myGradlePath, true)) {
        return AndroidBundle.message("android.wizard.module.import.library.taken.name", myGradlePath);
      }
      return "";
    }
  }
}
