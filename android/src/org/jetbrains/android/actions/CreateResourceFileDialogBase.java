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

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.android.utils.HtmlBuilder;
import com.google.common.collect.Maps;
import com.intellij.ide.actions.TemplateKindCombo;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * Interface for dialogs that create new resource files.
 */
public abstract class CreateResourceFileDialogBase extends DialogWrapper {

  protected final Map<ResourceFolderType, CreateTypedResourceFileAction> myResType2ActionMap = Maps.newEnumMap(ResourceFolderType.class);

  protected CreateResourceFileDialogBase(@Nullable Project project) {
    super(project);
  }

  /**
   * After a user clicks OK and the dialog is validated, call this to get the created resource file.
   *
   * @return the created resource file (or an empty array if failed and the dialog closed).
   */
  @NotNull
  public abstract PsiElement[] getCreatedElements();

  /**
   * Get the name of the new resource file.
   *
   * @return filename
   */
  @VisibleForTesting
  @NotNull
  public abstract String getFileName();

  /**
   * Depending on context, the validation rules may be different, or the context may want to perform other actions before validation.
   * This provides a hook to create a custom validator.
   */
  public interface ValidatorFactory {

    /**
     * Create the validator, given the final chosen parameters
     *
     * @param resourceDirectory the chosen res/ directory
     * @param subdirName        the chosen sub directory (e.g., color)
     * @param rootElement       the chosen root tag (e.g., <selector>)
     * @return a validator
     */
    @NotNull
    ElementCreatingValidator create(@NotNull PsiDirectory resourceDirectory, @NotNull String subdirName,
                                    @Nullable String rootElement);
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return "reference.new.resource.file";
  }

  protected String setupSubActions(@NotNull Collection<CreateTypedResourceFileAction> actions,
                                   @NotNull TemplateKindCombo resourceTypeCombo,
                                   @Nullable ResourceFolderType folderType) {
    CreateTypedResourceFileAction[] actionArray = actions.toArray(new CreateTypedResourceFileAction[actions.size()]);

    Arrays.sort(actionArray, (a1, a2) -> a1.toString().compareTo(a2.toString()));
    String selectedTemplate = null;

    for (CreateTypedResourceFileAction action : actionArray) {
      ResourceFolderType resType = action.getResourceFolderType();
      assert !myResType2ActionMap.containsKey(resType);
      myResType2ActionMap.put(resType, action);
      resourceTypeCombo.addItem(action.toString(), null, resType.getName());

      if (folderType != null && folderType == resType) {
        selectedTemplate = resType.getName();
      }
    }
    return selectedTemplate;
  }

  protected CreateTypedResourceFileAction getSelectedAction(TemplateKindCombo resourceTypeCombo) {
    ResourceFolderType folderType = ResourceFolderType.getFolderType(resourceTypeCombo.getSelectedName());
    if (folderType == null) {
      return null;
    }
    return myResType2ActionMap.get(folderType);
  }

  protected DeviceConfiguratorPanel setupDeviceConfigurationPanel(final JTextField directoryNameTextField,
                                                                  final TemplateKindCombo resourceTypeCombo,
                                                                  final JBLabel errorLabel) {
    return new DeviceConfiguratorPanel() {
      @Override
      public void applyEditors() {
        try {
          doApplyEditors();
          final FolderConfiguration config = this.getConfiguration();
          final CreateTypedResourceFileAction selectedAction = getSelectedAction(resourceTypeCombo);
          errorLabel.setText("");
          directoryNameTextField.setText("");
          if (selectedAction != null) {
            final ResourceFolderType resFolderType = selectedAction.getResourceFolderType();
            directoryNameTextField.setText(config.getFolderName(resFolderType));
          }
        }
        catch (InvalidOptionValueException e) {
          errorLabel.setText(new HtmlBuilder()
                               .openHtmlBody()
                               .coloredText(JBColor.RED, e.getMessage())
                               .closeHtmlBody()
                               .getHtml());
          directoryNameTextField.setText("");
        }
        updateOkAction();
      }
    };
  }

  protected abstract void updateOkAction();
}
