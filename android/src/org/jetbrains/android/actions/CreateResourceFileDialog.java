/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.actions;

import com.android.builder.model.SourceProvider;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.res.ResourceNameValidator;
import com.google.common.collect.Maps;
import com.intellij.CommonBundle;
import com.intellij.application.options.ModulesComboBox;
import com.intellij.ide.actions.TemplateKindCombo;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.DeviceConfiguratorPanel;
import org.jetbrains.android.uipreview.InvalidOptionValueException;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

public class CreateResourceFileDialog extends DialogWrapper {
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
  private InputValidator myValidator;

  private final Map<ResourceFolderType, CreateTypedResourceFileAction> myResType2ActionMap = Maps.newEnumMap(ResourceFolderType.class);
  private final DeviceConfiguratorPanel myDeviceConfiguratorPanel;
  private final AndroidFacet myFacet;
  private PsiDirectory myResDirectory;

  public CreateResourceFileDialog(@NotNull AndroidFacet facet,
                                  Collection<CreateTypedResourceFileAction> actions,
                                  @Nullable ResourceFolderType predefinedResourceType,
                                  @Nullable String predefinedFileName,
                                  @Nullable String predefinedRootElement,
                                  @Nullable FolderConfiguration predefinedConfig,
                                  boolean chooseFileName,
                                  @NotNull Module module,
                                  boolean chooseModule,
                                  @Nullable PsiDirectory resDirectory) {
    super(facet.getModule().getProject());
    myFacet = facet;
    myResDirectory = resDirectory;

    myResTypeLabel.setLabelFor(myResourceTypeCombo);
    myResourceTypeCombo.registerUpDownHint(myFileNameField);
    myUpDownHint.setIcon(PlatformIcons.UP_DOWN_ARROWS);
    CreateTypedResourceFileAction[] actionArray = actions.toArray(new CreateTypedResourceFileAction[actions.size()]);

    Arrays.sort(actionArray, new Comparator<CreateTypedResourceFileAction>() {
      @Override
      public int compare(CreateTypedResourceFileAction a1, CreateTypedResourceFileAction a2) {
        return a1.toString().compareTo(a2.toString());
      }
    });
    String selectedTemplate = null;

    for (CreateTypedResourceFileAction action : actionArray) {
      ResourceFolderType resType = action.getResourceFolderType();
      assert !myResType2ActionMap.containsKey(resType);
      myResType2ActionMap.put(resType, action);
      myResourceTypeCombo.addItem(action.toString(), null, resType.getName());

      if (predefinedResourceType != null && predefinedResourceType == resType) {
        selectedTemplate = resType.getName();
      }
    }

    myDeviceConfiguratorPanel = new DeviceConfiguratorPanel() {
      @Override
      public void applyEditors() {
        try {
          doApplyEditors();
          final FolderConfiguration config = myDeviceConfiguratorPanel.getConfiguration();
          final CreateTypedResourceFileAction selectedAction = getSelectedAction();
          myErrorLabel.setText("");
          myDirectoryNameTextField.setText("");
          if (selectedAction != null) {
            final ResourceFolderType resFolderType = selectedAction.getResourceFolderType();
            myDirectoryNameTextField.setText(config.getFolderName(resFolderType));
          }
        }
        catch (InvalidOptionValueException e) {
          myErrorLabel.setText("<html><body><font color=\"red\">" + e.getMessage() + "</font></body></html>");
          myDirectoryNameTextField.setText("");
        }
        updateOkAction();
      }
    };
    if (predefinedConfig != null) {
      myDeviceConfiguratorPanel.init(predefinedConfig);
    }

    myResourceTypeCombo.getComboBox().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myDeviceConfiguratorPanel.applyEditors();
        updateRootElementTextField();
      }
    });

    if (predefinedResourceType != null && selectedTemplate != null) {
      final boolean v = predefinedResourceType == ResourceFolderType.LAYOUT;
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

    if (chooseFileName) {
      predefinedFileName = ResourceHelper.prependResourcePrefix(module, predefinedFileName);
    }

    boolean validateImmediately = false;
    if (predefinedFileName != null && getNameError(predefinedFileName) != null) {
      chooseFileName = true;
      validateImmediately = true;
    }

    if (predefinedFileName != null) {
      if (!chooseFileName) {
        myFileNameField.setVisible(false);
        myFileNameLabel.setVisible(false);
      }
      myFileNameField.setText(predefinedFileName);
    }

    final Set<Module> modulesSet = new HashSet<Module>();
    modulesSet.add(module);
    for (AndroidFacet depFacet : AndroidUtils.getAllAndroidDependencies(module, true)) {
      modulesSet.add(depFacet.getModule());
    }

    myModuleCombo.setModules(modulesSet);

    if (!chooseModule || modulesSet.size() == 1) {
      myModuleLabel.setVisible(false);
      myModuleCombo.setVisible(false);
    }
    myModuleCombo.setSelectedModule(module);

   CreateResourceActionBase.updateSourceSetCombo(mySourceSetLabel, mySourceSetCombo,
                                                 modulesSet.size() == 1 ? AndroidFacet.getInstance(modulesSet.iterator().next()) : null,
                                                 myResDirectory);

    myDeviceConfiguratorPanel.updateAll();
    myDeviceConfiguratorWrapper.add(myDeviceConfiguratorPanel, BorderLayout.CENTER);
    updateOkAction();
    updateRootElementTextField();

    if (predefinedRootElement != null) {
      myRootElementLabel.setVisible(false);
      myRootElementFieldWrapper.setVisible(false);
      myRootElementField.setText(predefinedRootElement);
    }
    init();

    setTitle(AndroidBundle.message("new.resource.dialog.title"));

    myFileNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      public void textChanged(DocumentEvent event) {
        validateName();
      }
    });
    myResourceTypeCombo.getComboBox().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        validateName();
      }
    });
    if (validateImmediately) {
      validateName();
    }
  }

  private void updateOkAction() {
    boolean enabled = myDirectoryNameTextField.getText().length() > 0;
    enabled = enabled && getNameError(myFileNameField.getText()) == null;
    setOKActionEnabled(enabled);
  }

  @Nullable
  private String getNameError(@NotNull String fileName) {
    String typeName = myResourceTypeCombo.getSelectedName();
    if (typeName != null) {
      ResourceFolderType type = ResourceFolderType.getFolderType(typeName);
      if (type != null) {
        ResourceNameValidator validator = ResourceNameValidator.create(true, type);
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
    final CreateTypedResourceFileAction action = getSelectedAction();

    if (action != null) {
      final List<String> allowedTagNames = action.getSortedAllowedTagNames(myFacet);
      myRootElementField = new TextFieldWithAutoCompletion<String>(
        myFacet.getModule().getProject(), new TextFieldWithAutoCompletion.StringsCompletionProvider(allowedTagNames, null), true, null);
      myRootElementField.setEnabled(allowedTagNames.size() > 1);
      myRootElementField.setText(!action.isChooseTagName()
                                 ? action.getDefaultRootTag()
                                 : "");
      myRootElementFieldWrapper.removeAll();
      myRootElementFieldWrapper.add(myRootElementField, BorderLayout.CENTER);
      myRootElementLabel.setLabelFor(myRootElementField);
    }
  }

  @NotNull
  public String getFileName() {
    return myFileNameField.getText().trim();
  }

  @Nullable
  protected InputValidator createValidator(@NotNull String subdirName) {
    return null;
  }

  @Override
  protected void doOKAction() {
    String fileName = myFileNameField.getText().trim();
    final CreateTypedResourceFileAction action = getSelectedAction();
    assert action != null;

    if (fileName.length() == 0) {
      Messages.showErrorDialog(myPanel, AndroidBundle.message("file.name.not.specified.error"), CommonBundle.getErrorTitle());
      return;
    }

    if (!action.isChooseTagName() && getRootElement().length() == 0) {
      Messages.showErrorDialog(myPanel, AndroidBundle.message("root.element.not.specified.error"), CommonBundle.getErrorTitle());
      return;
    }

    final String subdirName = getSubdirName();
    if (subdirName.length() == 0) {
      Messages.showErrorDialog(myPanel, AndroidBundle.message("directory.not.specified.error"), CommonBundle.getErrorTitle());
      return;
    }

    final String errorMessage = getNameError(fileName);
    if (errorMessage != null) {
      Messages.showErrorDialog(myPanel, errorMessage, CommonBundle.getErrorTitle());
      return;
    }
    myValidator = createValidator(subdirName);
    if (myValidator == null || myValidator.checkInput(fileName) && myValidator.canClose(fileName)) {
      super.doOKAction();
    }
  }

  @Override
  protected String getDimensionServiceKey() {
    return "AndroidCreateResourceFileDialog";
  }

  @Nullable
  public SourceProvider getSourceProvider() {
    return CreateResourceActionBase.getSourceProvider(mySourceSetCombo);
  }

  @Contract("_,true -> !null")
  @Nullable
  public PsiDirectory getResourceDirectory(@Nullable DataContext context, boolean create) {
    if (myResDirectory != null) {
      return myResDirectory;
    }
    if (context != null) {
      Module module = LangDataKeys.MODULE.getData(context);
      assert module != null;
      return CreateResourceActionBase.getResourceDirectory(getSourceProvider(), module, create);
    }

    return null;
  }

  @NotNull
  public Module getSelectedModule() {
    return myModuleCombo.getSelectedModule();
  }

  @NotNull
  public String getSubdirName() {
    return myDirectoryNameTextField.getText().trim();
  }

  @NotNull
  protected String getRootElement() {
    return myRootElementField.getText().trim();
  }

  public InputValidator getValidator() {
    return myValidator;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    String name = myFileNameField.getText();
    if (name.length() == 0 || name.equals(ResourceHelper.prependResourcePrefix(getSelectedModule(), null)) || getNameError(name) != null) {
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

  @Nullable
  @Override
  protected String getHelpId() {
    return "reference.new.resource.file";
  }

  private CreateTypedResourceFileAction getSelectedAction() {
    ResourceFolderType folderType = ResourceFolderType.getFolderType(myResourceTypeCombo.getSelectedName());
    if (folderType == null) {
      return null;
    }
    return myResType2ActionMap.get(folderType);
  }
}
