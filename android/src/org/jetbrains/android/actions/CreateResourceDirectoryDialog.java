/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.google.common.annotations.VisibleForTesting;
import com.android.resources.ResourceFolderType;
import com.google.common.collect.ImmutableList;
import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBLabel;
import java.util.Collection;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.DeviceConfiguratorPanel;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import org.jetbrains.annotations.TestOnly;

/**
 * Dialog to decide where to create a res/ subdirectory (e.g., layout/, values-foo/, etc.)
 * and how to name it (based on chosen configuration)
 */
public class CreateResourceDirectoryDialog extends CreateResourceDirectoryDialogBase {
  private JComboBox<ResourceFolderType> myResourceTypeComboBox;
  private JPanel myDeviceConfiguratorWrapper;
  private JTextField myDirectoryNameTextField;
  private JPanel myContentPanel;
  private JBLabel myErrorLabel;
  private JComboBox mySourceSetCombo;
  private JBLabel mySourceSetLabel;

  private final DeviceConfiguratorPanel myDeviceConfiguratorPanel;
  private ElementCreatingValidator myValidator;
  private final ValidatorFactory myValidatorFactory;
  private final PsiDirectory myResDirectory;
  private final DataContext myDataContext;
  private final boolean myForceDirectoryDoesNotExist;

  /**
   * A dialog to create a variant resource folder. It doesn't allow to create an existing folder and shows the alert message.
   * @see #CreateResourceDirectoryDialog(Project, Module, ResourceFolderType, PsiDirectory, DataContext, ValidatorFactory, boolean)
   */
  public CreateResourceDirectoryDialog(@NotNull Project project, @Nullable Module module, @Nullable ResourceFolderType resType,
                                       @Nullable PsiDirectory resDirectory, @Nullable DataContext dataContext,
                                       @NotNull ValidatorFactory validatorFactory) {
    this(project,module, resType, resDirectory, dataContext, validatorFactory, false);
  }

  /**
   * A dialog to create a variant resource folder for the given resource type. The forceDirectoryDoesNotExist indicates if it can create
   * existing directory (in that case, it does nothing and close the dialog.). When forceDirectoryDoesNotExist is false, it doesn't allow to
   * create an existing folder and shows the alert message.
   */
  public CreateResourceDirectoryDialog(@NotNull Project project, @Nullable Module module, @Nullable ResourceFolderType resType,
                                       @Nullable PsiDirectory resDirectory, @Nullable DataContext dataContext,
                                       @NotNull ValidatorFactory validatorFactory, boolean forceDirectoryDoesNotExist) {
    super(project);
    myResDirectory = resDirectory;
    myDataContext = dataContext;
    myValidatorFactory = validatorFactory;
    myForceDirectoryDoesNotExist = forceDirectoryDoesNotExist;
    myResourceTypeComboBox.setModel(new EnumComboBoxModel<>(ResourceFolderType.class));
    myResourceTypeComboBox.setRenderer(SimpleListCellRenderer.create("", ResourceFolderType::getName));

    myDeviceConfiguratorPanel = setupDeviceConfigurationPanel(myResourceTypeComboBox, myDirectoryNameTextField, myErrorLabel);
    myDeviceConfiguratorWrapper.add(myDeviceConfiguratorPanel, BorderLayout.CENTER);
    myResourceTypeComboBox.addActionListener(event -> myDeviceConfiguratorPanel.applyEditors());

    if (resType != null) {
      myResourceTypeComboBox.setSelectedItem(resType);
      myResourceTypeComboBox.setEnabled(false);
    }
    else {
      // Select values by default if not otherwise specified
      myResourceTypeComboBox.setSelectedItem(ResourceFolderType.VALUES);
    }

    AndroidFacet facet = module != null ? AndroidFacet.getInstance(module) : null;
    CreateResourceDialogUtils.updateSourceSetCombo(mySourceSetLabel, mySourceSetCombo, facet, myResDirectory);

    myDeviceConfiguratorPanel.updateAll();
    setOKActionEnabled(!myDirectoryNameTextField.getText().isEmpty());
    init();
  }

  @Nullable
  @VisibleForTesting
  @Override
  public ValidationInfo doValidate() {
    PsiDirectory directory = getResourceDirectory(myDataContext);

    if (directory == null) {
      return null;
    }

    PsiFileSystemItem subdirectory = directory.findSubdirectory(myDirectoryNameTextField.getText());

    if (subdirectory != null && !myForceDirectoryDoesNotExist) {
      return new ValidationInfo(subdirectory.getVirtualFile().getPresentableUrl() + " already exists. Use a different qualifier.");
    }

    return null;
  }

  @Override
  protected void doOKAction() {
    final String dirName = myDirectoryNameTextField.getText();
    assert dirName != null;
    PsiDirectory resourceDirectory = getResourceDirectory(myDataContext);
    if (resourceDirectory == null) {
      Module module = PlatformCoreDataKeys.MODULE.getData(myDataContext);
      Messages.showErrorDialog(AndroidBundle.message("check.resource.dir.error", module),
                               CommonBundle.getErrorTitle());
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
    return "AndroidCreateResourceDirectoryDialog";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    if (myResourceTypeComboBox.isEnabled()) {
      return myResourceTypeComboBox;
    }
    else {
      return myDirectoryNameTextField;
    }
  }

  @VisibleForTesting
  JTextComponent getDirectoryNameTextField() {
    return myDirectoryNameTextField;
  }

  @TestOnly
  Collection<String> getSourceSets() {
    int size = mySourceSetCombo.getModel().getSize();
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    for (int i = 0; i < size; i++) {
      builder.add(mySourceSetCombo.getModel().getElementAt(i).toString());
    }
    return builder.build();
  }

  @Override
  @NotNull
  public PsiElement[] getCreatedElements() {
    return myValidator != null ? myValidator.getCreatedElements() : PsiElement.EMPTY_ARRAY;
  }

  @Nullable
  private PsiDirectory getResourceDirectory(@Nullable DataContext context) {
    if (myResDirectory != null) {
      return myResDirectory;
    }
    if (context != null) {
      Module module = PlatformCoreDataKeys.MODULE.getData(context);
      assert module != null;
      return CreateResourceDialogUtils.getOrCreateResourceDirectory(mySourceSetCombo, module);
    }

    return null;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }
}
