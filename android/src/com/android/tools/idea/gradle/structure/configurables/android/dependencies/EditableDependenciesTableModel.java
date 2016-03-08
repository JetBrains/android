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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies;

import com.android.tools.idea.gradle.structure.configurables.ui.BaseTableCellRenderer;
import com.android.tools.idea.gradle.structure.model.PsdArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidDependencyModel;
import com.android.tools.idea.gradle.structure.model.android.PsdLibraryDependencyModel;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableCellRenderer;
import java.util.Collections;
import java.util.List;

/**
 * Model for the table displaying the "editable" dependencies of a module.
 */
class EditableDependenciesTableModel extends ListTableModel<PsdAndroidDependencyModel> {
  EditableDependenciesTableModel(@NotNull List<PsdAndroidDependencyModel> dependencies) {
    createAndSetColumnInfos();
    Collections.sort(dependencies, PsdAndroidDependencyModelComparator.INSTANCE);
    setItems(dependencies);
  }

  private void createAndSetColumnInfos() {
    ColumnInfo<PsdAndroidDependencyModel, String> specColumnInfo = new ColumnInfo<PsdAndroidDependencyModel, String>("Dependency") {
      @Override
      @NotNull
      public String valueOf(PsdAndroidDependencyModel model) {
        return model.getValueAsText();
      }

      @Override
      @NotNull
      public TableCellRenderer getRenderer(PsdAndroidDependencyModel model) {
        return new DependencyCellRenderer(model);
      }

      @Override
      @NotNull
      public String getPreferredStringValue() {
        // Just a long string to hint the width of a column
        return "com.android.support:appcompat-v7:23.1.0";
      }
    };

    ColumnInfo<PsdAndroidDependencyModel, String> scopeColumnInfo = new ColumnInfo<PsdAndroidDependencyModel, String>("Scope") {
      @Override
      @Nullable
      public String valueOf(PsdAndroidDependencyModel model) {
        return model.getConfigurationName();
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

  static class DependencyCellRenderer extends BaseTableCellRenderer<PsdAndroidDependencyModel> {
    DependencyCellRenderer(@NotNull PsdAndroidDependencyModel model) {
      super(model);
    }

    @Override
    @NotNull
    protected String getText() {
      PsdAndroidDependencyModel model = getModel();
      String text = model.getValueAsText();

      if (model instanceof PsdLibraryDependencyModel) {
        PsdLibraryDependencyModel library = (PsdLibraryDependencyModel)model;
        PsdArtifactDependencySpec spec = library.getDeclaredSpec();
        assert spec != null;
        text = spec.getDisplayText();
      }
      return text;
    }
  }
}
