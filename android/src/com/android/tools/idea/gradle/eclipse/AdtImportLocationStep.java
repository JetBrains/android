/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.eclipse;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.util.projectWizard.ProjectWizardUtil;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportWizardStep;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;

class AdtImportLocationStep extends ProjectImportWizardStep {
  private JPanel myPanel;
  private TextFieldWithBrowseButton myDestDirText;
  private JBLabel myDestinationLabel;
  private boolean myIsPathChangedByUser;
  private File mySourceProject;

  public AdtImportLocationStep(WizardContext context) {
    super(context);

    myDestinationLabel.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));


    String prev = context.getProjectFileDirectory();
    mySourceProject = new File(FileUtil.toSystemDependentName(prev));

    String name = new File(prev).getName();
    //noinspection ConstantConditions
    context.setProjectFileDirectory(null);
    String defaultDir = context.getProjectFileDirectory();
    int index = 0;
    File file;
    do {
      String suffix = index == 0 ? "" : Integer.toString(index);
      index++;
      file = new File(defaultDir, name + suffix);
    } while (file.exists());
    myDestDirText.setText(file.getPath());
    context.setProjectFileDirectory(prev);

    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    descriptor.setTitle("Choose Destination Directory");
    descriptor.setDescription("Pick a directory to import the given Eclipse Android project into");
    myDestDirText.addBrowseFolderListener(new TextBrowseFolderListener(descriptor) {
      @Override
      protected void onFileChosen(@NotNull VirtualFile chosenFile) {
        super.onFileChosen(chosenFile);
        myIsPathChangedByUser = true;
      }

      @Override
      public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        myIsPathChangedByUser = true;
      }
    });
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public String getName() {
    return "ADT Import Location";
  }

  @Override
  public void updateDataModel() {
    WizardContext context = getWizardContext();
    context.setProjectFileDirectory(getProjectFileDirectory());

    AdtImportBuilder builder = (AdtImportBuilder)context.getProjectBuilder();
    if (builder != null) {
      builder.setSelectedProject(mySourceProject);
    }
  }

  public String getProjectFileDirectory() {
    return FileUtil.toSystemIndependentName(myDestDirText.getText().trim());
  }

  public String getProjectFilePath() {
    return getProjectFileDirectory() +
           (getWizardContext().getProject() == null ? ProjectFileType.DOT_DEFAULT_EXTENSION : ModuleFileType.DOT_DEFAULT_EXTENSION);
  }

  @Override
  public boolean validate() throws ConfigurationException {
    WizardContext context = getWizardContext();

    GradleImport importer = AdtImportProvider.getImporter(context);
    if (importer != null) {
      List<String> errors = importer.getErrors();
      if (!errors.isEmpty()) {
        throw new ConfigurationException(errors.get(0));
      }
    }

    // The following code is based on similar code in com.intellij.ide.util.newProjectWizard.ProjectNameStep

    String projectFileDirectory = getProjectFileDirectory();
    if (projectFileDirectory.isEmpty()) {
      throw new ConfigurationException(String.format("Enter %1$s file location", context.getPresentationName()));
    }

    boolean shouldPromptCreation = myIsPathChangedByUser;
    if (!ProjectWizardUtil.createDirectoryIfNotExists(String.format("The %1$s file directory\n", context.getPresentationName()),
                                  projectFileDirectory, shouldPromptCreation)) {
      return false;
    }

    boolean shouldContinue = true;

    File projectFile = new File(getProjectFileDirectory());
    String title = "New Project";
    if (projectFile.isFile()) {
      shouldContinue = false;
      String message = String.format("%s exists and is a file.\nPlease specify a different project location",
                                     projectFile.getAbsolutePath());
      Messages.showErrorDialog(message, title);
    }
    else if (projectFile.isDirectory()) {
      File[] files = projectFile.listFiles();
      if (files != null && files.length > 0) {
        String message = String.format("%1$s folder already exists and is not empty.\nIts content may be overwritten.\nContinue?",
                                       projectFile.getAbsolutePath());
        int answer = Messages.showYesNoDialog(message, title, Messages.getQuestionIcon());
        shouldContinue = answer == 0;
      }
    }
    return shouldContinue;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myDestDirText.getTextField();
  }
}
