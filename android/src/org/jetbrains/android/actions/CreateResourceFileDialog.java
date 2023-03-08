// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.android.actions;

import com.android.SdkConstants;
import com.android.tools.idea.res.AndroidDependenciesCache;
import com.google.common.annotations.VisibleForTesting;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.res.IdeResourceNameValidator;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.intellij.CommonBundle;
import com.intellij.application.options.ModulesComboBox;
import com.intellij.ide.actions.TemplateKindCombo;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.PlatformIcons;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.DeviceConfiguratorPanel;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dialog to decide where and how to create a resource file of a given type
 * (which base res/ directory, which subdirectory, and how to name the new file).
 */
public class CreateResourceFileDialog extends CreateResourceFileDialogBase {
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
  private ModulesComboBox myModuleCombo;
  private JBLabel myModuleLabel;
  private JComboBox mySourceSetCombo;
  private JBLabel mySourceSetLabel;
  private TextFieldWithAutoCompletion<String> myRootElementField;
  private ElementCreatingValidator myValidator;
  private CreateResourceFileDialogBase.ValidatorFactory myValidatorFactory;

  private final DeviceConfiguratorPanel myDeviceConfiguratorPanel;
  private final AndroidFacet myFacet;
  private PsiDirectory myResDirectory;

  public CreateResourceFileDialog(@NotNull AndroidFacet facet,
                                  @NotNull Collection<CreateTypedResourceFileAction> actions,
                                  @Nullable ResourceFolderType folderType,
                                  @Nullable String filename,
                                  @Nullable String rootElement,
                                  @Nullable FolderConfiguration folderConfiguration,
                                  boolean chooseFileName,
                                  boolean chooseModule,
                                  @Nullable PsiDirectory resDirectory,
                                  @NotNull CreateResourceFileDialogBase.ValidatorFactory validatorFactory) {
    super(facet.getModule().getProject());
    Module module = facet.getModule();
    myFacet = facet;
    myResDirectory = resDirectory;
    myValidatorFactory = validatorFactory;

    myResTypeLabel.setLabelFor(myResourceTypeCombo);
    myUpDownHint.setIcon(PlatformIcons.UP_DOWN_ARROWS);
    String selectedTemplate = setupSubActions(actions, myResourceTypeCombo, folderType);

    myDeviceConfiguratorPanel = setupDeviceConfigurationPanel(myDirectoryNameTextField, myResourceTypeCombo, myErrorLabel);
    if (folderConfiguration != null) {
      myDeviceConfiguratorPanel.init(folderConfiguration);
    }

    myResourceTypeCombo.getComboBox().addActionListener(e -> {
      myDeviceConfiguratorPanel.applyEditors();
      updateRootElementTextField();
    });

    if (folderType != null && selectedTemplate != null) {
      myResTypeLabel.setVisible(false);
      myResourceTypeCombo.setVisible(false);
      myUpDownHint.setVisible(false);
      myResourceTypeCombo.setSelectedName(selectedTemplate);
    }
    else {
      // Select values by default if not otherwise specified
      myResourceTypeCombo.setSelectedName(ResourceConstants.FD_RES_VALUES);
      myResourceTypeCombo.registerUpDownHint(myFileNameField);
    }

    if (chooseFileName) {
      filename = IdeResourcesUtil.prependResourcePrefix(module, filename, folderType);
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

    final Set<Module> modulesSet = new HashSet<>();
    modulesSet.add(module);
    for (AndroidFacet depFacet : AndroidDependenciesCache.getAllAndroidDependencies(module, true)) {
      modulesSet.add(depFacet.getModule());
    }

    myModuleCombo.setModules(modulesSet);

    if (!chooseModule || modulesSet.size() == 1) {
      myModuleLabel.setVisible(false);
      myModuleCombo.setVisible(false);
    }
    myModuleCombo.setSelectedModule(module);
    myModuleCombo.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        CreateResourceDialogUtils
          .updateSourceSetCombo(mySourceSetLabel, mySourceSetCombo, AndroidFacet.getInstance(getSelectedModule()), myResDirectory);
      }
    });

    CreateResourceDialogUtils
      .updateSourceSetCombo(mySourceSetLabel, mySourceSetCombo, AndroidFacet.getInstance(getSelectedModule()), myResDirectory);

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

    myFileNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(@NotNull DocumentEvent event) {
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
    boolean enabled = !myDirectoryNameTextField.getText().isEmpty();
    enabled = enabled && getNameError(myFileNameField.getText()) == null;
    setOKActionEnabled(enabled);
  }

  @Nullable
  private ResourceFolderType getSelectedFolderType() {
    return ResourceFolderType.getFolderType(myResourceTypeCombo.getSelectedName());
  }

  @Nullable
  private String getNameError(@NotNull String fileName) {
    ResourceFolderType type = getSelectedFolderType();
    if (type != null) {
      IdeResourceNameValidator validator = IdeResourceNameValidator.forFilename(type, SdkConstants.DOT_XML);
      return validator.getErrorText(fileName);
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
      myRootElementField = new TextFieldWithAutoCompletion<>(
        module.getProject(), new TextFieldWithAutoCompletion.StringsCompletionProvider(allowedTagNames, null), true, null);
      myRootElementField.setEnabled(allowedTagNames.size() > 1);
      myRootElementField.setText(action.isChooseTagName() ? "" : action.getDefaultRootTag(module));
      myRootElementFieldWrapper.removeAll();
      myRootElementFieldWrapper.add(myRootElementField, BorderLayout.CENTER);
      myRootElementLabel.setLabelFor(myRootElementField);
    }
  }

  @VisibleForTesting
  @NotNull
  @Override
  public String getFileName() {
    return myFileNameField.getText().trim();
  }

  @Override
  protected void doOKAction() {
    String fileName = myFileNameField.getText().trim();
    final CreateTypedResourceFileAction action = getSelectedAction(myResourceTypeCombo);
    assert action != null;

    if (fileName.isEmpty()) {
      Messages.showErrorDialog(myPanel, AndroidBundle.message("file.name.not.specified.error"), CommonBundle.getErrorTitle());
      return;
    }

    String rootElement = getRootElement();
    if (!action.isChooseTagName() && rootElement.isEmpty()) {
      Messages.showErrorDialog(myPanel, AndroidBundle.message("root.element.not.specified.error"), CommonBundle.getErrorTitle());
      return;
    }

    final String subdirName = getSubdirName();
    if (subdirName.isEmpty()) {
      Messages.showErrorDialog(myPanel, AndroidBundle.message("directory.not.specified.error"), CommonBundle.getErrorTitle());
      return;
    }

    final String errorMessage = getNameError(fileName);
    if (errorMessage != null) {
      Messages.showErrorDialog(myPanel, errorMessage, CommonBundle.getErrorTitle());
      return;
    }
    PsiDirectory resDir = getResourceDirectory();
    if (resDir == null) {
      Messages.showErrorDialog(myPanel, AndroidBundle.message("check.resource.dir.error", getSelectedModule()),
                               CommonBundle.getErrorTitle());
      super.doOKAction();
      return;
    }

    myValidator = myValidatorFactory.create(resDir, subdirName, rootElement);
    if (myValidator.checkInput(fileName) && myValidator.canClose(fileName)) {
      super.doOKAction();
    }
  }

  @NotNull
  @Override
  public PsiElement[] getCreatedElements() {
    return myValidator != null ? myValidator.getCreatedElements() : PsiElement.EMPTY_ARRAY;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "AndroidCreateResourceFileDialog";
  }

  @Nullable
  private PsiDirectory getResourceDirectory() {
    if (myResDirectory != null) {
      return myResDirectory;
    }
    Module module = getSelectedModule();
    return CreateResourceDialogUtils.getOrCreateResourceDirectory(mySourceSetCombo, module);
  }

  @NotNull
  private Module getSelectedModule() {
    Module module = myModuleCombo.getSelectedModule();
    assert module != null;
    return module;
  }

  @NotNull
  private String getSubdirName() {
    return myDirectoryNameTextField.getText().trim();
  }

  @NotNull
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
    if (name.isEmpty()
        || name.equals(IdeResourcesUtil.prependResourcePrefix(getSelectedModule(), null, getSelectedFolderType()))
        || getNameError(name) != null) {
      return myFileNameField;
    }
    else if (myResourceTypeCombo.isVisible()) {
      return myResourceTypeCombo;
    }
    else if (myModuleCombo.isVisible()) {
      return myModuleCombo;
    }
    else if (myRootElementFieldWrapper.isVisible()) {
      return myRootElementField;
    }
    return myDirectoryNameTextField;
  }
}
