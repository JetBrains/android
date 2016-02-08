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

import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.configurables.PsdUISettings;
import com.android.tools.idea.gradle.structure.model.PsdProblem;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidDependencyModel;
import com.android.tools.idea.gradle.structure.model.android.PsdLibraryDependencyModel;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.structure.configurables.android.dependencies.ArtifactDependencySpecs.asText;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.STYLE_WAVED;

/**
 * Model for the table displaying the "editable" dependencies of a module.
 */
class EditableDependenciesTableModel extends ListTableModel<PsdAndroidDependencyModel> {
  EditableDependenciesTableModel(@NotNull List<PsdAndroidDependencyModel> dependencies) {
    createAndSetColumnInfos();
    boolean showGroupId = PsdUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID;
    Collections.sort(dependencies, new PsdAndroidDependencyModelComparator(showGroupId));
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

  static class DependencyCellRenderer extends ColoredTableCellRenderer {
    @NotNull private final PsdAndroidDependencyModel myModel;

    DependencyCellRenderer(@NotNull PsdAndroidDependencyModel model) {
      myModel = model;
    }

    @Override
    protected void customizeCellRenderer(JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
      customizeCellRenderer();
    }

    @VisibleForTesting
    void customizeCellRenderer() {
      setIcon(myModel.getIcon());
      setIconOpaque(true);
      setFocusBorderAroundIcon(true);

      String text = myModel.getValueAsText();

      if (myModel instanceof PsdLibraryDependencyModel) {
        PsdLibraryDependencyModel library = (PsdLibraryDependencyModel)myModel;
        boolean showGroupId = PsdUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID;
        ArtifactDependencySpec spec = library.getResolvedSpec();
        ArtifactDependencySpec requestedSpec = library.getMismatchingRequestedSpec();
        if (requestedSpec != null) {
          spec = requestedSpec;
        }
        text = asText(spec, showGroupId);
      }

      PsdProblem problem = myModel.getProblem();
      if (problem != null) {
        SimpleTextAttributes textAttributes = REGULAR_ATTRIBUTES;
        JBColor waveColor = problem.getSeverity() == PsdProblem.Severity.ERROR ? JBColor.RED : JBColor.GRAY;
        textAttributes = textAttributes.derive(STYLE_WAVED, null, null, waveColor);
        append(text, textAttributes);

        setToolTipText(problem.getText());
        return;
      }

      append(text);
    }
  }
}
