/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.strings;

import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.ResourceNameValidator;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.ListCellRendererWrapper;
import org.jdesktop.swingx.combobox.ListComboBoxModel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Set;

public class NewStringKeyDialog extends DialogWrapper {
  private JPanel myPanel;

  private JComboBox myResFolderCombo;
  private EditorTextField myKeyField;
  private EditorTextField myDefaultValueField;

  private String myKey;
  private String myDefaultValue;
  private VirtualFile myResFolder;

  private final ResourceNameValidator myResourceNameValidator;

  public NewStringKeyDialog(@NotNull final AndroidFacet facet, @NotNull Set<String> existing) {
    super(facet.getModule().getProject(), false);

    final VirtualFile baseDir = facet.getModule().getProject().getBaseDir();

    myResFolderCombo.setModel(new ListComboBoxModel(facet.getAllResourceDirectories()));
    myResFolderCombo.setRenderer(new ListCellRendererWrapper<VirtualFile>() {
      @Override
      public void customize(JList list, VirtualFile file, int index, boolean selected, boolean hasFocus) {
        setText(VfsUtilCore.getRelativePath(file, baseDir, File.separatorChar));
      }
    });
    myResFolderCombo.setSelectedIndex(0);

    myResourceNameValidator = ResourceNameValidator.create(false, existing, ResourceType.STRING);

    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
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

    return null;
  }

  @Override
  protected void doOKAction() {
    myKey = myKeyField.getText().trim();
    myDefaultValue = myDefaultValueField.getText().trim();
    myResFolder = (VirtualFile)myResFolderCombo.getSelectedItem();

    super.doOKAction();
  }

  public String getKey() {
    return myKey;
  }

  public String getDefaultValue() {
    return myDefaultValue;
  }

  public VirtualFile getResFolder() {
    return myResFolder;
  }
}
