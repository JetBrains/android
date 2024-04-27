/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.avdmanager.skincombobox;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.ui.components.fields.ExtendableTextField;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;
import javax.swing.JTextField;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class Editor extends BasicComboBoxEditor {
  @NotNull
  private final SkinComboBoxModel myModel;

  @Nullable
  private final Project myProject;

  @NotNull
  private final Function<Project, Optional<Path>> myChoosePath;

  Editor(@NotNull SkinComboBoxModel model, @Nullable Project project) {
    this(model, project, Editor::choosePath);
  }

  @VisibleForTesting
  Editor(@NotNull SkinComboBoxModel model, @Nullable Project project, @NotNull Function<Project, Optional<Path>> choosePath) {
    myModel = model;
    myProject = project;
    myChoosePath = choosePath;
  }

  @NotNull
  private static Optional<Path> choosePath(@Nullable Project project) {
    return Optional.ofNullable(FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFolderDescriptor(), project, null))
      .map(VirtualFile::toNioPath);
  }

  @NotNull
  @Override
  protected JTextField createEditorComponent() {
    var textField = new ExtendableTextField();
    textField.setEditable(false);

    textField.addExtension(ExtendableTextComponent.Extension.create(AllIcons.General.OpenDisk,
                                                                    AllIcons.General.OpenDiskHover,
                                                                    null,
                                                                    this::choosePath));

    return textField;
  }

  @VisibleForTesting
  void choosePath() {
    myChoosePath.apply(myProject)
      .map(myModel::getSkin)
      .ifPresent(skin -> {
        myModel.addElement(skin);
        myModel.setSelectedItem(skin);
      });
  }
}
