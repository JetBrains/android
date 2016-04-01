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

import com.android.builder.model.SourceProvider;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.psi.PsiDirectory;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.DeviceConfiguratorPanel;
import org.jetbrains.android.uipreview.InvalidOptionValueException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class CreateResourceDirectoryDialog extends DialogWrapper {
  private JComboBox myResourceTypeComboBox;
  private JPanel myDeviceConfiguratorWrapper;
  private JTextField myDirectoryNameTextField;
  private JPanel myContentPanel;
  private JBLabel myErrorLabel;
  private JComboBox mySourceSetCombo;
  private JBLabel mySourceSetLabel;

  private final DeviceConfiguratorPanel myDeviceConfiguratorPanel;
  private InputValidator myValidator;
  private PsiDirectory myResDirectory;

  public CreateResourceDirectoryDialog(@NotNull Project project, @Nullable ResourceFolderType resType,
                                       @Nullable PsiDirectory resDirectory, @Nullable Module module) {
    super(project);
    myResDirectory = resDirectory;
    myResourceTypeComboBox.setModel(new EnumComboBoxModel<ResourceFolderType>(ResourceFolderType.class));
    myResourceTypeComboBox.setRenderer(new ListCellRendererWrapper() {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof ResourceFolderType) {
          setText(((ResourceFolderType)value).getName());
        }
      }
    });

    myDeviceConfiguratorPanel = new DeviceConfiguratorPanel() {
      @Override
      public void applyEditors() {
        try {
          doApplyEditors();
          final FolderConfiguration config = myDeviceConfiguratorPanel.getConfiguration();
          final ResourceFolderType selectedResourceType = (ResourceFolderType)myResourceTypeComboBox.getSelectedItem();
          myDirectoryNameTextField.setText(selectedResourceType != null ? config.getFolderName(selectedResourceType) : "");
          myErrorLabel.setText("");
        }
        catch (InvalidOptionValueException e) {
          myErrorLabel.setText("<html><body><font color=\"red\">" + e.getMessage() + "</font></body></html>");
          myDirectoryNameTextField.setText("");
        }
        setOKActionEnabled(myDirectoryNameTextField.getText().length() > 0);
      }
    };

    myDeviceConfiguratorWrapper.add(myDeviceConfiguratorPanel, BorderLayout.CENTER);
    myResourceTypeComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myDeviceConfiguratorPanel.applyEditors();
      }
    });

    if (resType != null) {
      myResourceTypeComboBox.setSelectedItem(resType);
      myResourceTypeComboBox.setEnabled(false);
    } else {
      // Select values by default if not otherwise specified
      myResourceTypeComboBox.setSelectedItem(ResourceFolderType.VALUES);
    }

    AndroidFacet facet = module != null ? AndroidFacet.getInstance(module) : null;
    CreateResourceActionBase.updateSourceSetCombo(mySourceSetLabel, mySourceSetCombo, facet, myResDirectory);

    myDeviceConfiguratorPanel.updateAll();
    setOKActionEnabled(myDirectoryNameTextField.getText().length() > 0);
    init();
  }

  protected abstract InputValidator createValidator();

  @Override
  protected void doOKAction() {
    final String dirName = myDirectoryNameTextField.getText();
    assert dirName != null;
    myValidator = createValidator();
    if (myValidator.checkInput(dirName) && myValidator.canClose(dirName)) {
      super.doOKAction();
    }
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return "reference.new.resource.directory";
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

  public InputValidator getValidator() {
    return myValidator;
  }

  @Nullable
  private SourceProvider getSourceProvider() {
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

  @Override
  protected JComponent createCenterPanel() {
    return myContentPanel;
  }
}
