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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.module;

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.PsAndroidDependencyComparator;
import com.android.tools.idea.gradle.structure.configurables.ui.BaseTableCellRenderer;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.PsLibraryDependency;
import com.google.common.collect.Lists;
import com.intellij.util.containers.Predicate;
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
class EditableDependenciesTableModel extends ListTableModel<PsAndroidDependency> {
  EditableDependenciesTableModel(@NotNull PsAndroidModule module) {
    createAndSetColumnInfos();
    final List<PsAndroidDependency> dependencies = Lists.newArrayList();
    module.forEachDeclaredDependency(new Predicate<PsAndroidDependency>() {
      @Override
      public boolean apply(@Nullable PsAndroidDependency dependency) {
        dependencies.add(dependency);
        return true;
      }
    });
    Collections.sort(dependencies, PsAndroidDependencyComparator.INSTANCE);
    setItems(dependencies);
  }

  private void createAndSetColumnInfos() {
    ColumnInfo<PsAndroidDependency, String> specColumnInfo = new ColumnInfo<PsAndroidDependency, String>("Dependency") {
      @Override
      @NotNull
      public String valueOf(PsAndroidDependency dependency) {
        return dependency.getValueAsText();
      }

      @Override
      @NotNull
      public TableCellRenderer getRenderer(PsAndroidDependency dependency) {
        return new DependencyCellRenderer(dependency);
      }

      @Override
      @NotNull
      public String getPreferredStringValue() {
        // Just a long string to hint the width of a column
        return "com.android.support:appcompat-v7:23.1.0";
      }
    };

    ColumnInfo<PsAndroidDependency, String> scopeColumnInfo = new ColumnInfo<PsAndroidDependency, String>("Scope") {
      @Override
      @Nullable
      public String valueOf(PsAndroidDependency dependency) {
        return dependency.getConfigurationName();
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

  static class DependencyCellRenderer extends BaseTableCellRenderer<PsAndroidDependency> {
    DependencyCellRenderer(@NotNull PsAndroidDependency dependency) {
      super(dependency);
    }

    @Override
    @NotNull
    protected String getText() {
      PsAndroidDependency dependency = getModel();
      String text = dependency.getValueAsText();

      if (dependency instanceof PsLibraryDependency) {
        PsLibraryDependency library = (PsLibraryDependency)dependency;
        PsArtifactDependencySpec spec = library.getDeclaredSpec();
        assert spec != null;
        text = spec.getDisplayText();
      }
      return text;
    }
  }
}
