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
package com.google.idea.blaze.android.resources.actions;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.IdeResourceNameValidator;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.jetbrains.android.actions.CreateXmlResourceDialog;
import org.jetbrains.android.actions.CreateXmlResourcePanel;
import org.jetbrains.android.actions.CreateXmlResourceSubdirPanel;
import org.jetbrains.android.actions.CreateXmlResourceSubdirPanel.Parent;

/**
 * Embeddable UI for selecting how to create a new resource value (which XML file and directories to
 * place it).
 */
public class BlazeCreateXmlResourcePanel implements CreateXmlResourcePanel, Parent {

  private JPanel myPanel;
  private JTextField myNameField;
  private JTextField myValueField;
  private JBLabel myValueLabel;
  private JBLabel myNameLabel;
  private JComboBox myFileNameCombo;
  private JBLabel myResDirLabel;
  private ComboboxWithBrowseButton myResDirCombo;
  private JBLabel myFileNameLabel;

  private final Module myModule;
  private final ResourceType myResourceType;

  private JPanel myDirectoriesPanel;
  private JBLabel myDirectoriesLabel;
  private CreateXmlResourceSubdirPanel mySubdirPanel;

  private final IdeResourceNameValidator myResourceNameValidator;
  @Nullable private VirtualFile myContextFile;
  @Nullable private VirtualFile myResDirectory;

  public BlazeCreateXmlResourcePanel(
      Module module,
      ResourceType resourceType,
      ResourceFolderType folderType,
      @Nullable String resourceName,
      @Nullable String resourceValue,
      boolean chooseName,
      boolean chooseValue,
      boolean chooseFilename,
      @Nullable VirtualFile defaultFile,
      @Nullable VirtualFile contextFile,
      Function<Module, IdeResourceNameValidator> nameValidatorFactory) {
    setupUi();
    setChangeNameVisible(false);
    setChangeValueVisible(false);
    setChangeFileNameVisible(chooseFilename);
    myModule = module;
    myContextFile = contextFile;
    if (chooseName) {
      setChangeNameVisible(true);
    }
    if (!StringUtil.isEmpty(resourceName)) {
      myNameField.setText(resourceName);
    }

    if (chooseValue) {
      setChangeValueVisible(true);
      if (!StringUtil.isEmpty(resourceValue)) {
        myValueField.setText(resourceValue);
      }
    }

    myResourceType = resourceType;

    ApplicationManager.getApplication().assertReadAccessAllowed();
    // Set up UI to choose the base directory if needed (use context to prune selection).
    myResDirLabel.setVisible(false);
    myResDirCombo.setVisible(false);
    myResDirCombo.addBrowseFolderListener(
        module.getProject(), FileChooserDescriptorFactory.createSingleFolderDescriptor());
    setupResourceDirectoryCombo();

    if (defaultFile == null) {
      final String defaultFileName = IdeResourcesUtil.getDefaultResourceFileName(myResourceType);

      if (defaultFileName != null) {
        myFileNameCombo.getEditor().setItem(defaultFileName);
      }
    }

    myDirectoriesLabel.setLabelFor(myDirectoriesPanel);
    mySubdirPanel =
        new CreateXmlResourceSubdirPanel(module.getProject(), folderType, myDirectoriesPanel, this);
    myResourceNameValidator = nameValidatorFactory.apply(getModule());

    if (defaultFile != null) {
      resetFromFile(defaultFile, module.getProject());
    }
  }

  private void setupResourceDirectoryCombo() {
    Project project = myModule.getProject();
    if (myContextFile != null) {
      // Try to figure out res/ dir from context
      // (e.g., while refactoring an xml file that's a child of a res/ directory).
      // We currently take the parent and hide the combo box.
      PsiManager manager = PsiManager.getInstance(project);
      VirtualFile virtualDirectory =
          BlazeCreateResourceUtils.getResDirFromDataContext(myContextFile);
      PsiDirectory directory =
          virtualDirectory != null ? manager.findDirectory(virtualDirectory) : null;
      if (directory != null) {
        myResDirectory = directory.getVirtualFile();
      } else {
        // As a last resort, if we have poor context,
        // e.g., quick fix from within a .java file, set up the UI
        // based on the deps of the .java file.
        BlazeCreateResourceUtils.setupResDirectoryChoices(
            project, myContextFile, myResDirLabel, myResDirCombo);
      }
    } else {
      // As a last resort, if we have no context at all, set up some UI.
      BlazeCreateResourceUtils.setupResDirectoryChoices(
          project, null, myResDirLabel, myResDirCombo);
    }
  }

  @Override
  public void resetToDefault() {
    String defaultFileName = IdeResourcesUtil.getDefaultResourceFileName(myResourceType);
    if (defaultFileName != null) {
      myFileNameCombo.getEditor().setItem(defaultFileName);
    }
    setupResourceDirectoryCombo();
    mySubdirPanel.resetToDefault();
  }

  @Override
  public void resetFromFile(VirtualFile file, Project project) {
    final VirtualFile parent = file.getParent();
    if (parent == null) {
      return;
    }
    mySubdirPanel.resetFromFile(parent);
    myFileNameCombo.getEditor().setItem(file.getName());
    setupResourceDirectoryCombo();
    myPanel.repaint();
  }

  @Override
  public String getResourceName() {
    return myNameField.getText().trim();
  }

  @Override
  public ResourceType getType() {
    return myResourceType;
  }

  @Override
  public String getValue() {
    return myValueField.getText().trim();
  }

  @Override
  public VirtualFile getResourceDirectory() {
    if (myResDirectory != null) {
      return myResDirectory;
    }
    if (myResDirCombo.isVisible()) {
      PsiDirectory directory =
          BlazeCreateResourceUtils.getResDirFromUI(myModule.getProject(), myResDirCombo);
      return directory != null ? directory.getVirtualFile() : null;
    }
    return null;
  }

  @Override
  public List<String> getDirNames() {
    return mySubdirPanel.getDirNames();
  }

  @Override
  public String getFileName() {
    return ((String) myFileNameCombo.getEditor().getItem()).trim();
  }

  @Override
  public ValidationInfo doValidate() {
    String resourceName = getResourceName();
    VirtualFile resourceDir = getResourceDirectory();
    List<String> directoryNames = getDirNames();
    String fileName = getFileName();

    if (myNameField.isVisible() && resourceName.isEmpty()) {
      return new ValidationInfo("specify resource name", myNameField);
    } else if (myNameField.isVisible()
        && !IdeResourcesUtil.isCorrectAndroidResourceName(resourceName)) {
      return new ValidationInfo(resourceName + " is not correct resource name", myNameField);
    } else if (fileName.isEmpty()) {
      return new ValidationInfo("specify file name", myFileNameCombo);
    } else if (resourceDir == null) {
      return new ValidationInfo("specify a resource directory", myResDirCombo);
    } else if (directoryNames.isEmpty()) {
      return new ValidationInfo("choose directories", myDirectoriesPanel);
    }

    return CreateXmlResourceDialog.checkIfResourceAlreadyExists(
        myModule.getProject(),
        resourceDir,
        resourceName,
        null,
        myResourceType,
        directoryNames,
        fileName);
  }

  @Override
  public IdeResourceNameValidator getResourceNameValidator() {
    return myResourceNameValidator;
  }

  @Override
  public Module getModule() {
    return myModule;
  }

  /** @see CreateXmlResourceDialog#getPreferredFocusedComponent() */
  @Override
  public JComponent getPreferredFocusedComponent() {
    String name = myNameField.getText();
    if (name.isEmpty()) {
      return myNameField;
    } else if (myValueField.isVisible()) {
      return myValueField;
    } else {
      return myFileNameCombo;
    }
  }

  @Override
  public JComponent getPanel() {
    return myPanel;
  }

  // @Override #api223
  public void setAllowValueEditing(boolean enabled) {}

  private void setChangeFileNameVisible(boolean isVisible) {
    myFileNameLabel.setVisible(isVisible);
    myFileNameCombo.setVisible(isVisible);
  }

  private void setChangeValueVisible(boolean isVisible) {
    myValueField.setVisible(isVisible);
    myValueLabel.setVisible(isVisible);
  }

  private void setChangeNameVisible(boolean isVisible) {
    myNameField.setVisible(isVisible);
    myNameLabel.setVisible(isVisible);
  }

  // Only public for CreateXmlResourceSubdirPanel.Parent
  @Override
  public void updateFilesCombo(List<VirtualFile> directories) {
    final Object oldItem = myFileNameCombo.getEditor().getItem();
    final Set<String> fileNameSet = new HashSet<>();

    for (VirtualFile dir : directories) {
      for (VirtualFile file : dir.getChildren()) {
        fileNameSet.add(file.getName());
      }
    }
    final List<String> fileNames = new ArrayList<>(fileNameSet);
    Collections.sort(fileNames);
    myFileNameCombo.setModel(new DefaultComboBoxModel(fileNames.toArray()));
    myFileNameCombo.getEditor().setItem(oldItem);
  }

  /** Initially generated by IntelliJ from a .form file. */
  private void setupUi() {
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(6, 2, new Insets(0, 0, 5, 0), -1, -1));
    myNameLabel = new JBLabel();
    myNameLabel.setText("Resource name:");
    myNameLabel.setDisplayedMnemonic('N');
    myNameLabel.setDisplayedMnemonicIndex(9);
    myPanel.add(
        myNameLabel,
        new GridConstraints(
            0,
            0,
            1,
            1,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED,
            null,
            null,
            null,
            0,
            false));
    myNameField = new JTextField();
    myPanel.add(
        myNameField,
        new GridConstraints(
            0,
            1,
            1,
            1,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_WANT_GROW,
            GridConstraints.SIZEPOLICY_FIXED,
            null,
            new Dimension(150, -1),
            null,
            0,
            false));
    myFileNameLabel = new JBLabel();
    myFileNameLabel.setText("File name:");
    myFileNameLabel.setDisplayedMnemonic('F');
    myFileNameLabel.setDisplayedMnemonicIndex(0);
    myPanel.add(
        myFileNameLabel,
        new GridConstraints(
            3,
            0,
            1,
            1,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED,
            null,
            null,
            null,
            0,
            false));
    myDirectoriesPanel = new JPanel();
    myDirectoriesPanel.setLayout(new BorderLayout(0, 0));
    myPanel.add(
        myDirectoriesPanel,
        new GridConstraints(
            5,
            0,
            1,
            2,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            null,
            null,
            null,
            0,
            false));
    myDirectoriesLabel = new JBLabel();
    myDirectoriesLabel.setText("Create the resource in directories:");
    myDirectoriesLabel.setDisplayedMnemonic('C');
    myDirectoriesLabel.setDisplayedMnemonicIndex(0);
    myPanel.add(
        myDirectoriesLabel,
        new GridConstraints(
            4,
            0,
            1,
            2,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED,
            null,
            null,
            null,
            0,
            false));
    myValueLabel = new JBLabel();
    myValueLabel.setText("Resource value:");
    myValueLabel.setDisplayedMnemonic('V');
    myValueLabel.setDisplayedMnemonicIndex(9);
    myPanel.add(
        myValueLabel,
        new GridConstraints(
            1,
            0,
            1,
            1,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED,
            null,
            null,
            null,
            0,
            false));
    myValueField = new JTextField();
    myPanel.add(
        myValueField,
        new GridConstraints(
            1,
            1,
            1,
            1,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_WANT_GROW,
            GridConstraints.SIZEPOLICY_FIXED,
            null,
            new Dimension(150, -1),
            null,
            0,
            false));
    myFileNameCombo = new JComboBox();
    myFileNameCombo.setEditable(true);
    myPanel.add(
        myFileNameCombo,
        new GridConstraints(
            3,
            1,
            1,
            1,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_FIXED,
            null,
            null,
            null,
            0,
            false));
    myResDirLabel = new JBLabel();
    myResDirLabel.setText("Base directory:");
    myResDirLabel.setDisplayedMnemonic('B');
    myResDirLabel.setDisplayedMnemonicIndex(0);
    myPanel.add(
        myResDirLabel,
        new GridConstraints(
            2,
            0,
            1,
            1,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED,
            null,
            null,
            null,
            0,
            false));
    myResDirCombo = new ComboboxWithBrowseButton();
    myPanel.add(
        myResDirCombo,
        new GridConstraints(
            2,
            1,
            1,
            1,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_FIXED,
            null,
            null,
            null,
            0,
            false));
    myNameLabel.setLabelFor(myNameField);
    myFileNameLabel.setLabelFor(myFileNameCombo);
    myValueLabel.setLabelFor(myValueField);
  }
}
