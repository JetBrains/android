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
import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.jetbrains.android.actions.CreateResourceDirectoryDialogBase;
import org.jetbrains.android.actions.ElementCreatingValidator;
import org.jetbrains.android.uipreview.DeviceConfiguratorPanel;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Dialog to decide where to create a res/ subdirectory (e.g., layout/, values-foo/, etc.) and how
 * to name the subdirectory based on resource type and chosen configuration.
 */
public class BlazeCreateResourceDirectoryDialog extends CreateResourceDirectoryDialogBase {

  private JComboBox myResourceTypeComboBox;
  private JPanel myDeviceConfiguratorWrapper;
  private JTextField myDirectoryNameTextField;
  private JPanel myContentPanel;
  private JBLabel myErrorLabel;
  private ComboboxWithBrowseButton myResDirCombo;
  private JBLabel myResDirLabel;

  private final DeviceConfiguratorPanel myDeviceConfiguratorPanel;
  private ElementCreatingValidator myValidator;
  private ValidatorFactory myValidatorFactory;
  private PsiDirectory myResDirectory;
  private DataContext myDataContext;

  public BlazeCreateResourceDirectoryDialog(
      Project project,
      @Nullable Module module,
      @Nullable ResourceFolderType resType,
      @Nullable PsiDirectory resDirectory,
      @Nullable DataContext dataContext,
      ValidatorFactory validatorFactory) {
    super(project);
    setupUi();
    myResDirectory = resDirectory;
    myDataContext = dataContext;
    myValidatorFactory = validatorFactory;
    myResourceTypeComboBox.setModel(new EnumComboBoxModel<>(ResourceFolderType.class));
    myResourceTypeComboBox.setRenderer(
        new ListCellRendererWrapper() {
          @Override
          public void customize(
              JList list, Object value, int index, boolean selected, boolean hasFocus) {
            if (value instanceof ResourceFolderType) {
              setText(((ResourceFolderType) value).getName());
            }
          }
        });

    myDeviceConfiguratorPanel =
        setupDeviceConfigurationPanel(
            myResourceTypeComboBox, myDirectoryNameTextField, myErrorLabel);
    myDeviceConfiguratorWrapper.add(myDeviceConfiguratorPanel, BorderLayout.CENTER);
    myResourceTypeComboBox.addActionListener(e -> myDeviceConfiguratorPanel.applyEditors());

    if (resType != null) {
      myResourceTypeComboBox.setSelectedItem(resType);
      myResourceTypeComboBox.setEnabled(false);
    } else {
      // Select values by default if not otherwise specified
      myResourceTypeComboBox.setSelectedItem(ResourceFolderType.VALUES);
    }

    // If myResDirectory is known before this, just use that.
    myResDirLabel.setVisible(false);
    myResDirCombo.setVisible(false);
    myResDirCombo.addBrowseFolderListener(
        project, FileChooserDescriptorFactory.createSingleFolderDescriptor());
    if (myResDirectory == null) {
      assert dataContext != null;
      assert module != null;
      // Try to figure out from context (e.g., right click in project view).
      VirtualFile contextFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
      if (contextFile != null) {
        PsiManager manager = PsiManager.getInstance(project);
        VirtualFile virtualDirectory =
            BlazeCreateResourceUtils.getResDirFromDataContext(contextFile);
        PsiDirectory directory =
            virtualDirectory != null ? manager.findDirectory(virtualDirectory) : null;
        if (directory != null) {
          myResDirectory = directory;
        } else {
          // As a last resort, if we have poor context
          // e.g., from File > New w/ a .java file open, set up the UI.
          BlazeCreateResourceUtils.setupResDirectoryChoices(
              module.getProject(), contextFile, myResDirLabel, myResDirCombo);
        }
      }
    }

    myDeviceConfiguratorPanel.updateAll();
    setOKActionEnabled(myDirectoryNameTextField.getText().length() > 0);
    init();
  }

  @Override
  protected void doOKAction() {
    final String dirName = myDirectoryNameTextField.getText();
    assert dirName != null;
    PsiDirectory resourceDirectory = getResourceDirectory();
    if (resourceDirectory == null) {
      Module module = LangDataKeys.MODULE.getData(myDataContext);
      Messages.showErrorDialog(
          AndroidBundle.message("check.resource.dir.error", module), CommonBundle.getErrorTitle());
      // Not much the user can do, just close the dialog.
      super.doOKAction();
      return;
    }
    myValidator = myValidatorFactory.create(resourceDirectory);
    if (myValidator.checkInput(dirName) && myValidator.canClose(dirName)) {
      super.doOKAction();
    }
  }

  @Override
  protected String getDimensionServiceKey() {
    return "BlazeCreateResourceDirectoryDialog";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    if (myResourceTypeComboBox.isEnabled()) {
      return myResourceTypeComboBox;
    } else {
      return myDirectoryNameTextField;
    }
  }

  @Override
  @NotNull
  public PsiElement[] getCreatedElements() {
    return myValidator != null ? myValidator.getCreatedElements() : PsiElement.EMPTY_ARRAY;
  }

  @Nullable
  private PsiDirectory getResourceDirectory() {
    if (myResDirectory != null) {
      return myResDirectory;
    }
    if (myResDirCombo.isVisible()) {
      Module contextModule = LangDataKeys.MODULE.getData(myDataContext);
      assert contextModule != null;
      return BlazeCreateResourceUtils.getResDirFromUI(contextModule.getProject(), myResDirCombo);
    }
    return null;
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }

  /** Initially generated by IntelliJ from a .form file. */
  private void setupUi() {
    myContentPanel = new JPanel();
    myContentPanel.setLayout(new GridLayoutManager(5, 2, new Insets(0, 0, 0, 0), -1, -1));
    myContentPanel.setPreferredSize(new Dimension(800, 400));
    myResourceTypeComboBox = new JComboBox();
    myContentPanel.add(
        myResourceTypeComboBox,
        new GridConstraints(
            1,
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
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setText("Resource type:");
    jBLabel1.setDisplayedMnemonic('R');
    jBLabel1.setDisplayedMnemonicIndex(0);
    myContentPanel.add(
        jBLabel1,
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
    myDeviceConfiguratorWrapper = new JPanel();
    myDeviceConfiguratorWrapper.setLayout(new BorderLayout(0, 0));
    myContentPanel.add(
        myDeviceConfiguratorWrapper,
        new GridConstraints(
            3,
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
    final JLabel label1 = new JLabel();
    label1.setText("Directory name:");
    label1.setDisplayedMnemonic('D');
    label1.setDisplayedMnemonicIndex(0);
    myContentPanel.add(
        label1,
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
    myDirectoryNameTextField = new JTextField();
    myDirectoryNameTextField.setEnabled(true);
    myContentPanel.add(
        myDirectoryNameTextField,
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
    myErrorLabel = new JBLabel();
    myContentPanel.add(
        myErrorLabel,
        new GridConstraints(
            4,
            0,
            1,
            2,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_FIXED,
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
    myContentPanel.add(
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
    myContentPanel.add(
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
    jBLabel1.setLabelFor(myResourceTypeComboBox);
    label1.setLabelFor(myDirectoryNameTextField);
    myResDirLabel.setLabelFor(myResourceTypeComboBox);
  }
}
