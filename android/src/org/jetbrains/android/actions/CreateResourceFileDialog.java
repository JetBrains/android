// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.android.actions;

import static com.intellij.codeInsight.AutoPopupController.SHOW_BOTTOM_PANEL_IN_LOOKUP_UI;

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
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
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
  private ValidatorFactory myValidatorFactory;

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
    setupUI();
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

    if (folderType == ResourceFolderType.RAW) {
      myRootElementLabel.setVisible(false);
      myRootElementFieldWrapper.setVisible(false);
      myRootElementField.setVisible(false);
    }
    else if (rootElement != null) {
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
      try {
        final Module module = myFacet.getModule();
        final List<String> allowedTagNames = action.getSortedAllowedTagNames(myFacet);
        myRootElementField = new TextFieldWithAutoCompletion<>(
          module.getProject(), new TextFieldWithAutoCompletion.StringsCompletionProvider(allowedTagNames, null), true, null) {
          @Override
          protected @NotNull EditorEx createEditor() {
            EditorEx editor = super.createEditor();
            editor.putUserData(SHOW_BOTTOM_PANEL_IN_LOOKUP_UI, false);
            return editor;
          }
        };
        myRootElementField.addSettingsProvider(editor -> {
          Color bgColor = myRootElementField.isEnabled() ? null : UIManager.getColor("Panel.background");
          editor.setBackgroundColor(bgColor);
        });
        myRootElementField.setEnabled(allowedTagNames.size() > 1);
        myRootElementField.setText(action.isChooseTagName() ? "" : action.getDefaultRootTag(module));
        myRootElementFieldWrapper.removeAll();
        myRootElementFieldWrapper.add(myRootElementField, BorderLayout.CENTER);
        myRootElementLabel.setLabelFor(myRootElementField);
      }
      catch (Throwable ignore) {
      }
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
    if (!action.isChooseTagName() &&
        action.getResourceFolderType() != ResourceFolderType.RAW &&
        (rootElement == null || rootElement.isEmpty())) {
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

  @Nullable
  protected String getRootElement() {
    return myRootElementField == null ? null : myRootElementField.getText().trim();
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

  private void setupUI() {
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(8, 3, new Insets(0, 0, 0, 0), -1, -1));
    myPanel.setPreferredSize(new Dimension(800, 400));
    myFileNameLabel = new JLabel();
    myFileNameLabel.setText("File name:");
    myFileNameLabel.setDisplayedMnemonic('F');
    myFileNameLabel.setDisplayedMnemonicIndex(0);
    myPanel.add(myFileNameLabel,
                new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myFileNameField = new JTextField();
    myPanel.add(myFileNameField, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                     new Dimension(150, -1), null, 0, false));
    myResTypeLabel = new JLabel();
    myResTypeLabel.setText("Resource type:");
    myResTypeLabel.setDisplayedMnemonic('R');
    myResTypeLabel.setDisplayedMnemonicIndex(0);
    myPanel.add(myResTypeLabel,
                new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myUpDownHint = new JLabel();
    myUpDownHint.setToolTipText("Pressing Up or Down arrows while in editor changes the kind");
    myPanel.add(myUpDownHint,
                new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myResourceTypeCombo = new TemplateKindCombo();
    myPanel.add(myResourceTypeCombo, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myDeviceConfiguratorWrapper = new JPanel();
    myDeviceConfiguratorWrapper.setLayout(new BorderLayout(0, 0));
    myPanel.add(myDeviceConfiguratorWrapper, new GridConstraints(6, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    myErrorLabel = new JBLabel();
    myPanel.add(myErrorLabel,
                new GridConstraints(7, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setText("Directory name:");
    jBLabel1.setDisplayedMnemonic('Y');
    jBLabel1.setDisplayedMnemonicIndex(8);
    myPanel.add(jBLabel1,
                new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myDirectoryNameTextField = new JTextField();
    myDirectoryNameTextField.setEditable(true);
    myDirectoryNameTextField.setEnabled(true);
    myPanel.add(myDirectoryNameTextField, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                              GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                              new Dimension(150, -1), null, 0, false));
    myRootElementLabel = new JBLabel();
    myRootElementLabel.setText("Root element:");
    myRootElementLabel.setDisplayedMnemonic('E');
    myRootElementLabel.setDisplayedMnemonicIndex(5);
    myPanel.add(myRootElementLabel,
                new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myRootElementFieldWrapper = new JPanel();
    myRootElementFieldWrapper.setLayout(new BorderLayout(0, 0));
    myPanel.add(myRootElementFieldWrapper, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myModuleLabel = new JBLabel();
    myModuleLabel.setText("Module:");
    myModuleLabel.setDisplayedMnemonic('M');
    myModuleLabel.setDisplayedMnemonicIndex(0);
    myPanel.add(myModuleLabel,
                new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myModuleCombo = new ModulesComboBox();
    myPanel.add(myModuleCombo, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                   GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                   0, false));
    mySourceSetLabel = new JBLabel();
    mySourceSetLabel.setText("Source set:");
    mySourceSetLabel.setDisplayedMnemonic('S');
    mySourceSetLabel.setDisplayedMnemonicIndex(0);
    myPanel.add(mySourceSetLabel,
                new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    mySourceSetCombo = new JComboBox();
    myPanel.add(mySourceSetCombo, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                      null, 0, false));
    myFileNameLabel.setLabelFor(myFileNameField);
    jBLabel1.setLabelFor(myDirectoryNameTextField);
    myModuleLabel.setLabelFor(myModuleCombo);
    mySourceSetLabel.setLabelFor(myModuleCombo);
  }
}
