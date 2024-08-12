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

import com.android.SdkConstants;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.res.IdeResourceNameValidator;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.CommonBundle;
import com.intellij.ide.actions.TemplateKindCombo;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.TextFieldWithAutoCompletion.StringsCompletionProvider;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.PlatformIcons;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import org.jetbrains.android.actions.CreateResourceFileDialogBase;
import org.jetbrains.android.actions.CreateTypedResourceFileAction;
import org.jetbrains.android.actions.ElementCreatingValidator;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.DeviceConfiguratorPanel;
import org.jetbrains.android.util.AndroidBundle;

/**
 * Dialog to decide where and how to create a resource file of a given type (which base res/
 * directory, which subdirectory, and how to name the new file).
 */
public class BlazeCreateResourceFileDialog extends CreateResourceFileDialogBase {
  private JTextField myFileNameField;
  private TemplateKindCombo myResourceTypeCombo;
  private JPanel myPanel;
  private JLabel myUpDownHint;
  private JLabel myResTypeLabel;
  private JPanel myDeviceConfiguratorWrapper;
  private JBLabel myErrorLabel;
  private JTextField myDirectoryNameTextField;
  private JPanel myRootElementFieldWrapper;
  private JBLabel myRootElementLabel;
  private JLabel myFileNameLabel;
  private ComboboxWithBrowseButton myResDirCombo;
  private JBLabel myResDirLabel;
  private TextFieldWithAutoCompletion<String> myRootElementField;
  private ElementCreatingValidator myValidator;
  private ValidatorFactory myValidatorFactory;

  private final DeviceConfiguratorPanel myDeviceConfiguratorPanel;
  private final AndroidFacet myFacet;
  private PsiDirectory myResDirectory;

  public BlazeCreateResourceFileDialog(
      AndroidFacet facet,
      Collection<CreateTypedResourceFileAction> actions,
      ResourceFolderType folderType,
      String filename,
      String rootElement,
      FolderConfiguration folderConfiguration,
      boolean chooseFileName,
      boolean chooseModule,
      PsiDirectory resDirectory,
      DataContext dataContext,
      ValidatorFactory validatorFactory) {
    super(facet.getModule().getProject());
    setupUi();
    myFacet = facet;
    myResDirectory = resDirectory;
    myValidatorFactory = validatorFactory;

    myResTypeLabel.setLabelFor(myResourceTypeCombo);
    myResourceTypeCombo.registerUpDownHint(myFileNameField);
    myUpDownHint.setIcon(PlatformIcons.UP_DOWN_ARROWS);
    String selectedTemplate = setupSubActions(actions, myResourceTypeCombo, folderType);

    myDeviceConfiguratorPanel =
        setupDeviceConfigurationPanel(myDirectoryNameTextField, myResourceTypeCombo, myErrorLabel);
    if (folderConfiguration != null) {
      myDeviceConfiguratorPanel.init(folderConfiguration);
    }

    myResourceTypeCombo
        .getComboBox()
        .addActionListener(
            e -> {
              myDeviceConfiguratorPanel.applyEditors();
              updateRootElementTextField();
            });

    if (folderType != null && selectedTemplate != null) {
      final boolean v = folderType == ResourceFolderType.LAYOUT;
      myRootElementLabel.setVisible(v);
      myRootElementFieldWrapper.setVisible(v);

      myResTypeLabel.setVisible(false);
      myResourceTypeCombo.setVisible(false);
      myUpDownHint.setVisible(false);
      myResourceTypeCombo.setSelectedName(selectedTemplate);
    } else {
      // Select values by default if not otherwise specified
      myResourceTypeCombo.setSelectedName(ResourceConstants.FD_RES_VALUES);
    }

    boolean validateImmediately = false;
    if (filename != null && getNameError(filename) != null) {
      chooseFileName = true;
      validateImmediately = true;
    }

    if (filename != null) {
      if (!chooseFileName) {
        myFileNameField.setVisible(false);
        myFileNameLabel.setVisible(false);
      }
      myFileNameField.setText(filename);
    }

    Project project = myFacet.getModule().getProject();
    // Set up UI to choose the base directory if needed (use context to prune selection).
    // There may be a resource directory already pre-selected, in which case hide the UI by default.
    myResDirLabel.setVisible(false);
    myResDirCombo.setVisible(false);
    myResDirCombo.addBrowseFolderListener(
        project, FileChooserDescriptorFactory.createSingleFolderDescriptor());
    if (myResDirectory == null) {
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
          // As a last resort, if we have poor context,
          // e.g., from File > New w/ a .java file open, set up the UI.
          BlazeCreateResourceUtils.setupResDirectoryChoices(
              project, contextFile, myResDirLabel, myResDirCombo);
        }
      } else {
        // As a last resort, if we have no context, set up the UI.
        BlazeCreateResourceUtils.setupResDirectoryChoices(
            project, null, myResDirLabel, myResDirCombo);
      }
    }

    myDeviceConfiguratorPanel.updateAll();
    myDeviceConfiguratorWrapper.add(myDeviceConfiguratorPanel, BorderLayout.CENTER);
    updateOkAction();
    updateRootElementTextField();

    if (rootElement != null) {
      myRootElementLabel.setVisible(false);
      myRootElementFieldWrapper.setVisible(false);
      myRootElementField.setText(rootElement);
    }
    init();

    setTitle(AndroidBundle.message("new.resource.dialog.title"));

    myFileNameField
        .getDocument()
        .addDocumentListener(
            new DocumentAdapter() {
              @Override
              public void textChanged(DocumentEvent event) {
                validateName();
              }
            });
    myResourceTypeCombo.getComboBox().addActionListener(actionEvent -> validateName());
    if (validateImmediately) {
      validateName();
    }
  }

  @Override
  protected void updateOkAction() {
    boolean enabled = myDirectoryNameTextField.getText().length() > 0;
    enabled = enabled && getNameError(myFileNameField.getText()) == null;
    setOKActionEnabled(enabled);
  }

  @Nullable
  private String getNameError(String fileName) {
    String typeName = myResourceTypeCombo.getSelectedName();
    if (typeName != null) {
      ResourceFolderType type = ResourceFolderType.getFolderType(typeName);
      if (type != null) {
        IdeResourceNameValidator validator =
            IdeResourceNameValidator.forFilename(type, SdkConstants.DOT_XML);
        return validator.getErrorText(fileName);
      }
    }

    return null;
  }

  private void validateName() {
    setErrorText(getNameError(myFileNameField.getText()));
    updateOkAction();
  }

  private void updateRootElementTextField() {
    final CreateTypedResourceFileAction action = getSelectedAction(myResourceTypeCombo);

    if (action != null) {
      final Module module = myFacet.getModule();
      final List<String> allowedTagNames = action.getSortedAllowedTagNames(myFacet);
      myRootElementField =
          new TextFieldWithAutoCompletion<>(
              module.getProject(),
              new StringsCompletionProvider(allowedTagNames, null),
              true,
              null);
      myRootElementField.setEnabled(allowedTagNames.size() > 1);
      myRootElementField.setText(action.isChooseTagName() ? "" : action.getDefaultRootTag(module));
      myRootElementFieldWrapper.removeAll();
      myRootElementFieldWrapper.add(myRootElementField, BorderLayout.CENTER);
      myRootElementLabel.setLabelFor(myRootElementField);
    }
  }

  @VisibleForTesting
  @Override
  public String getFileName() {
    return myFileNameField.getText().trim();
  }

  @Override
  protected void doOKAction() {
    String fileName = myFileNameField.getText().trim();
    final CreateTypedResourceFileAction action = getSelectedAction(myResourceTypeCombo);
    assert action != null;

    if (fileName.length() == 0) {
      Messages.showErrorDialog(
          myPanel,
          AndroidBundle.message("file.name.not.specified.error"),
          CommonBundle.getErrorTitle());
      return;
    }

    String rootElement = getRootElement();
    if (!action.isChooseTagName() && rootElement.length() == 0) {
      Messages.showErrorDialog(
          myPanel,
          AndroidBundle.message("root.element.not.specified.error"),
          CommonBundle.getErrorTitle());
      return;
    }

    final String subdirName = getSubdirName();
    if (subdirName.length() == 0) {
      Messages.showErrorDialog(
          myPanel,
          AndroidBundle.message("directory.not.specified.error"),
          CommonBundle.getErrorTitle());
      return;
    }

    final String errorMessage = getNameError(fileName);
    if (errorMessage != null) {
      Messages.showErrorDialog(myPanel, errorMessage, CommonBundle.getErrorTitle());
      return;
    }
    PsiDirectory resDir = getResourceDirectory();
    if (resDir == null) {
      Messages.showErrorDialog(
          myPanel,
          AndroidBundle.message("check.resource.dir.error", myFacet.getModule()),
          CommonBundle.getErrorTitle());
      super.doOKAction();
      return;
    }

    myValidator = myValidatorFactory.create(resDir, subdirName, rootElement);
    if (myValidator.checkInput(fileName) && myValidator.canClose(fileName)) {
      super.doOKAction();
    }
  }

  @Override
  public PsiElement[] getCreatedElements() {
    return myValidator != null ? myValidator.getCreatedElements() : PsiElement.EMPTY_ARRAY;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "BlazeCreateResourceFileDialog";
  }

  @Nullable
  private PsiDirectory getResourceDirectory() {
    if (myResDirectory != null) {
      return myResDirectory;
    }
    return BlazeCreateResourceUtils.getResDirFromUI(
        myFacet.getModule().getProject(), myResDirCombo);
  }

  private String getSubdirName() {
    return myDirectoryNameTextField.getText().trim();
  }

  protected String getRootElement() {
    return myRootElementField.getText().trim();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    String name = myFileNameField.getText();
    if (name.length() == 0 || getNameError(name) != null) {
      return myFileNameField;
    } else if (myResourceTypeCombo.isVisible()) {
      return myResourceTypeCombo;
    } else if (myRootElementFieldWrapper.isVisible()) {
      return myRootElementField;
    }
    return myDirectoryNameTextField;
  }

  /** Initially generated by IntelliJ from a .form file. */
  private void setupUi() {
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(7, 3, new Insets(0, 0, 0, 0), -1, -1));
    myPanel.setPreferredSize(new Dimension(800, 400));
    myFileNameLabel = new JLabel();
    myFileNameLabel.setText("File name:");
    myFileNameLabel.setDisplayedMnemonic('F');
    myFileNameLabel.setDisplayedMnemonicIndex(0);
    myPanel.add(
        myFileNameLabel,
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
    myFileNameField = new JTextField();
    myPanel.add(
        myFileNameField,
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
    myResTypeLabel = new JLabel();
    myResTypeLabel.setText("Resource type:");
    myResTypeLabel.setDisplayedMnemonic('R');
    myResTypeLabel.setDisplayedMnemonicIndex(0);
    myPanel.add(
        myResTypeLabel,
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
    myUpDownHint = new JLabel();
    myUpDownHint.setToolTipText("Pressing Up or Down arrows while in editor changes the kind");
    myPanel.add(
        myUpDownHint,
        new GridConstraints(
            0,
            2,
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
    myResourceTypeCombo = new TemplateKindCombo();
    myPanel.add(
        myResourceTypeCombo,
        new GridConstraints(
            1,
            1,
            1,
            1,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_FIXED,
            null,
            null,
            null,
            0,
            false));
    myDeviceConfiguratorWrapper = new JPanel();
    myDeviceConfiguratorWrapper.setLayout(new BorderLayout(0, 0));
    myPanel.add(
        myDeviceConfiguratorWrapper,
        new GridConstraints(
            5,
            0,
            1,
            3,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            null,
            null,
            null,
            0,
            false));
    myErrorLabel = new JBLabel();
    myPanel.add(
        myErrorLabel,
        new GridConstraints(
            6,
            0,
            1,
            3,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED,
            null,
            null,
            null,
            0,
            false));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setText("Sub-directory:");
    jBLabel1.setDisplayedMnemonic('Y');
    jBLabel1.setDisplayedMnemonicIndex(12);
    myPanel.add(
        jBLabel1,
        new GridConstraints(
            4,
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
    myDirectoryNameTextField.setEditable(true);
    myDirectoryNameTextField.setEnabled(true);
    myPanel.add(
        myDirectoryNameTextField,
        new GridConstraints(
            4,
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
    myRootElementLabel = new JBLabel();
    myRootElementLabel.setText("Root element:");
    myRootElementLabel.setDisplayedMnemonic('E');
    myRootElementLabel.setDisplayedMnemonicIndex(5);
    myPanel.add(
        myRootElementLabel,
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
    myRootElementFieldWrapper = new JPanel();
    myRootElementFieldWrapper.setLayout(new BorderLayout(0, 0));
    myPanel.add(
        myRootElementFieldWrapper,
        new GridConstraints(
            2,
            1,
            1,
            1,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
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
    myResDirCombo = new ComboboxWithBrowseButton();
    myPanel.add(
        myResDirCombo,
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
    myFileNameLabel.setLabelFor(myFileNameField);
    jBLabel1.setLabelFor(myDirectoryNameTextField);
  }
}
