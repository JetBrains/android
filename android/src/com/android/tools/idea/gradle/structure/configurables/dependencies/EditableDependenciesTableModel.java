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
package com.android.tools.idea.gradle.structure.configurables.dependencies;

import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidDependencyEditor;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidLibraryDependencyEditor;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.List;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;

/**
 * Model for the table displaying the "editable" dependencies of a module.
 */
class EditableDependenciesTableModel extends ListTableModel<PsdAndroidDependencyEditor> {
  // Allows users to show/hide artifact's group ID (hide in the case of lack of horizontal space.)
  private boolean myShowGroupIds;

  EditableDependenciesTableModel(List<PsdAndroidDependencyEditor> dependencies) {
    createAndSetColumnInfos();
    setItems(dependencies);
  }

  private void createAndSetColumnInfos() {
    ColumnInfo<PsdAndroidDependencyEditor, String> specColumnInfo = new ColumnInfo<PsdAndroidDependencyEditor, String>("Dependency") {
      @Override
      @NotNull
      public String valueOf(PsdAndroidDependencyEditor editor) {
        if (editor instanceof PsdAndroidLibraryDependencyEditor && !myShowGroupIds) {
          ArtifactDependencySpec spec = ((PsdAndroidLibraryDependencyEditor)editor).getSpec();
          return spec.name + GRADLE_PATH_SEPARATOR + spec.version;
        }
        return editor.getValueAsText();
      }

      @Override
      @NotNull
      public TableCellRenderer getRenderer(PsdAndroidDependencyEditor editor) {
        return new DependencyCellRenderer(editor);
      }

      @Override
      @NotNull
      public String getPreferredStringValue() {
        // Just a long string to hint the width of a column
        return "com.android.support:appcompat-v7:23.1.0";
      }
    };

    ColumnInfo<PsdAndroidDependencyEditor, String> scopeColumnInfo = new ColumnInfo<PsdAndroidDependencyEditor, String>("Scope") {
      @Override
      @Nullable
      public String valueOf(PsdAndroidDependencyEditor editor) {
        return editor.getConfigurationName();
      }

      @Override
      @NotNull
      public String getPreferredStringValue() {
        // Just a long string to hint the width of a column
        return "flavor1AndroidTestCompile";
      }
    };

    setColumnInfos(new ColumnInfo[]{specColumnInfo, scopeColumnInfo});
  }

  class DependencyCellRenderer extends DefaultTableCellRenderer {
    @NotNull private final PsdAndroidDependencyEditor myEditor;

    DependencyCellRenderer(@NotNull PsdAndroidDependencyEditor editor) {
      myEditor = editor;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      JLabel label = (JLabel)super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      label.setIcon(myEditor.getIcon());
      String toolTip = "";
      if (!myShowGroupIds && myEditor instanceof PsdAndroidLibraryDependencyEditor) {
        // Show the complete compact notation (including group ID) if the table hides group ID.
        toolTip = myEditor.getValueAsText();
      }
      label.setToolTipText(toolTip);
      return label;
    }
  }

  boolean isShowGroupIds() {
    return myShowGroupIds;
  }

  void setShowGroupIds(boolean showGroupIds) {
    myShowGroupIds = showGroupIds;
  }
}
