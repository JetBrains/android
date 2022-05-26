/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors.strings.action;

import com.android.resources.ResourceType;
import com.android.tools.idea.editors.strings.VirtualFiles;
import com.android.tools.idea.editors.strings.model.StringResourceKey;
import com.android.tools.idea.res.IdeResourceNameValidator;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.SimpleListCellRenderer;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

public class NewStringKeyDialog extends DialogWrapper {
  private JPanel myPanel;

  private JComboBox<VirtualFile> myResourceFolderComboBox;
  private EditorTextField myKeyField;
  private EditorTextField myDefaultValueField;

  private final AndroidFacet myFacet;
  private final InputValidatorEx myResourceNameValidator;
  private final Collection<StringResourceKey> myKeys;

  private String myKey;
  private String myDefaultValue;
  private VirtualFile myResFolder;

  NewStringKeyDialog(@NotNull AndroidFacet facet, @NotNull Collection<StringResourceKey> keys) {
    super(facet.getModule().getProject(), false);

    myFacet = facet;
    myResourceNameValidator = IdeResourceNameValidator.forResourceName(ResourceType.STRING);
    myKeys = keys;

    init();
    setTitle("Add Key");
    setButtonName(getOKAction(), "okButton");
    setButtonName(getCancelAction(), "cancelButton");
  }

  private void setButtonName(@NotNull Action action, @NotNull String name) {
    Component button = getButton(action);
    assert button != null;

    button.setName(name);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  private void createUIComponents() {
    myResourceFolderComboBox = new ComboBox<>(ResourceFolderManager.getInstance(myFacet).getFolders().toArray(VirtualFile.EMPTY_ARRAY));
    myResourceFolderComboBox.setName("resourceFolderComboBox");

    myResourceFolderComboBox.setRenderer(SimpleListCellRenderer.create(
      "", folder -> VirtualFiles.toString(folder, myFacet.getModule().getProject())));
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myKeyField;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    if (myKeyField.getText().isEmpty()) {
      return new ValidationInfo("Key cannot be empty", myKeyField);
    }

    String key = myKeyField.getText().trim();
    String error = myResourceNameValidator.getErrorText(key);
    if (error != null) {
      return new ValidationInfo(error, myKeyField);
    }

    if (myDefaultValueField.getText().isEmpty()) {
      return new ValidationInfo("Default Value cannot be empty", myDefaultValueField);
    }

    VirtualFile folder = (VirtualFile)myResourceFolderComboBox.getSelectedItem();

    if (myKeys.contains(new StringResourceKey(key, folder))) {
      assert folder != null;
      return new ValidationInfo(key + " already exists in " + VirtualFiles.toString(folder, myFacet.getModule().getProject()));
    }

    return null;
  }

  @Override
  protected void doOKAction() {
    myKey = myKeyField.getText().trim();
    myDefaultValue = myDefaultValueField.getText().trim();
    myResFolder = (VirtualFile)myResourceFolderComboBox.getSelectedItem();

    super.doOKAction();
  }

  @NotNull
  StringResourceKey getKey() {
    return new StringResourceKey(myKey, myResFolder);
  }

  public String getDefaultValue() {
    return myDefaultValue;
  }
}
