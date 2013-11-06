/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.structure;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.io.File;

/**
* A table cell renderer for {@link File}-typed cell values.
*/
class FileItemRenderer implements TableCellRenderer {
  private final Project myProject;
  private JPanel myEmptyPanel;

  FileItemRenderer(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public Component getTableCellRendererComponent(@NotNull JTable jTable, @Nullable Object o, boolean isSelected, boolean hasFocus, int row,
                                                 int column) {
    if (o == null || !(o instanceof File)) {
      if (myEmptyPanel == null) {
        myEmptyPanel = new JPanel();
      }
      return myEmptyPanel;
    }
    TextFieldWithBrowseButton field = new TextFieldWithBrowseButton();
    field.getTextField().setText(((File)o).getPath());
    field.addBrowseFolderListener("Choose a file", "Choose a file", myProject,
                                  FileChooserDescriptorFactory.createSingleFileDescriptor(FileTypes.UNKNOWN));
    return field;
  }
}
