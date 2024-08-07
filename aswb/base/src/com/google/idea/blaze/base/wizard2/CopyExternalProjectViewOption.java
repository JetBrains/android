/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.wizard2;

import com.google.idea.blaze.base.projectview.ProjectViewStorageManager;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.TextFieldWithStoredHistory;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.annotation.Nullable;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;

/** Copies an external project view from anywhere on the user's file system */
public class CopyExternalProjectViewOption implements BlazeSelectProjectViewOption {
  private static final String LAST_WORKSPACE_PATH = "copy-external.last-project-view-path";

  final BlazeWizardUserSettings userSettings;
  final JComponent component;
  final TextFieldWithStoredHistory projectViewPathField;

  public CopyExternalProjectViewOption(BlazeNewProjectBuilder builder) {
    this.userSettings = builder.getUserSettings();

    this.projectViewPathField = new TextFieldWithStoredHistory(LAST_WORKSPACE_PATH);
    projectViewPathField.setHistorySize(BlazeNewProjectBuilder.HISTORY_SIZE);
    projectViewPathField.setText(userSettings.get(LAST_WORKSPACE_PATH, ""));
    projectViewPathField.setMinimumAndPreferredWidth(MINIMUM_FIELD_WIDTH);

    JButton button = new JButton("...");
    button.addActionListener(action -> chooseWorkspacePath());
    int buttonSize = projectViewPathField.getPreferredSize().height;
    button.setPreferredSize(new Dimension(buttonSize, buttonSize));

    JComponent box =
        UiUtil.createHorizontalBox(
            HORIZONTAL_LAYOUT_GAP, new JLabel("Project view:"), projectViewPathField, button);
    UiUtil.setPreferredWidth(box, PREFERRED_COMPONENT_WIDTH);
    this.component = box;
  }

  @Override
  public String getOptionName() {
    return "copy-external";
  }

  @Override
  public String getDescription() {
    return "Copy external";
  }

  @Override
  public JComponent getUiComponent() {
    return component;
  }

  @Override
  public void validateAndUpdateBuilder(BlazeNewProjectBuilder builder)
      throws ConfigurationException {
    if (getProjectViewPath().isEmpty()) {
      throw new ConfigurationException("Path to project view file cannot be empty.");
    }
    File file = new File(getProjectViewPath());
    if (!file.exists()) {
      throw new ConfigurationException("Project view file does not exist.");
    }
    if (file.isDirectory()) {
      throw new ConfigurationException("Specified path is a directory, not a file");
    }
  }

  @Nullable
  @Override
  public String getInitialProjectViewText() {
    try {
      byte[] bytes = Files.readAllBytes(Paths.get(getProjectViewPath()));
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (IOException e) {
      return null;
    }
  }

  @Override
  public void commit() {
    userSettings.put(LAST_WORKSPACE_PATH, getProjectViewPath());
    projectViewPathField.addCurrentTextToHistory();
  }

  private String getProjectViewPath() {
    return projectViewPathField.getText().trim();
  }

  private void chooseWorkspacePath() {
    FileChooserDescriptor descriptor =
        new FileChooserDescriptor(true, false, false, false, false, false)
            .withShowHiddenFiles(true) // Show root project view file
            .withHideIgnored(false)
            .withTitle("Select Project View File")
            .withDescription("Select a project view file to import.")
            .withFileFilter(
                virtualFile ->
                    ProjectViewStorageManager.isProjectViewFile(new File(virtualFile.getPath())));
    // File filters are broken for the native Mac file chooser.
    descriptor.setForcedToUseIdeaFileChooser(true);
    FileChooserDialog chooser =
        FileChooserFactory.getInstance().createFileChooser(descriptor, null, null);

    File startingLocation = null;
    String projectViewPath = getProjectViewPath();
    if (!projectViewPath.isEmpty()) {
      File fileLocation = new File(projectViewPath);
      if (fileLocation.exists()) {
        startingLocation = fileLocation;
      }
    }
    final VirtualFile[] files;
    if (startingLocation != null) {
      VirtualFile toSelect =
          LocalFileSystem.getInstance().refreshAndFindFileByPath(startingLocation.getPath());
      files = chooser.choose(null, toSelect);
    } else {
      files = chooser.choose(null);
    }
    if (files.length == 0) {
      return;
    }
    VirtualFile file = files[0];
    projectViewPathField.setText(file.getPath());
  }
}
