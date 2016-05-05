/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.actions;

import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.android.utils.HtmlBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.android.uipreview.DeviceConfiguratorPanel;
import org.jetbrains.android.uipreview.InvalidOptionValueException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Interface for dialogs that create new resource subdirectories (e.g., layout-large).
 */
public abstract class CreateResourceDirectoryDialogBase extends DialogWrapper {

  protected CreateResourceDirectoryDialogBase(@Nullable Project project) {
    super(project);
  }

  /**
   * After a user clicks OK and the dialog is validated, call this to get the created resource directory.
   *
   * @return the created resource directory (or an empty array if failed and the dialog closed).
   */
  @NotNull
  public abstract PsiElement[] getCreatedElements();

  /**
   * Depending on context, the validation rules may be different, or the context may want to perform other actions before validation.
   * This provides a hook to create a custom validator.
   */
  public interface ValidatorFactory {
    /**
     * Create the validator, given the final res/ directory
     * @param resourceDirectory the chosen res/ directory
     * @return a validator
     */
    @NotNull
    ElementCreatingValidator create(@NotNull PsiDirectory resourceDirectory);
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return "reference.new.resource.directory";
  }

  protected DeviceConfiguratorPanel setupDeviceConfigurationPanel(
    final JComboBox resourceTypeComboBox,
    final JTextField directoryNameTextField,
    final JBLabel errorLabel) {
    return new DeviceConfiguratorPanel() {
      @Override
      public void applyEditors() {
        try {
          doApplyEditors();
          final FolderConfiguration config = this.getConfiguration();
          final ResourceFolderType selectedResourceType = (ResourceFolderType)resourceTypeComboBox.getSelectedItem();
          directoryNameTextField.setText(selectedResourceType != null ? config.getFolderName(selectedResourceType) : "");
          errorLabel.setText("");
        }
        catch (InvalidOptionValueException e) {
          errorLabel.setText(new HtmlBuilder()
                               .openHtmlBody()
                               .coloredText(JBColor.RED, e.getMessage())
                               .closeHtmlBody()
                               .getHtml());
          directoryNameTextField.setText("");
        }
        setOKActionEnabled(directoryNameTextField.getText().length() > 0);
      }
    };
  }
}
