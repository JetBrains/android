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

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.PsAndroidDependencyComparator;
import com.android.tools.idea.gradle.structure.configurables.issues.IssuesByTypeComparator;
import com.android.tools.idea.gradle.structure.configurables.ui.AbstractPsModelTableCellRenderer;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsIssue;
import com.android.tools.idea.gradle.structure.model.PsIssueCollection;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidLibraryDependency;
import com.android.tools.idea.gradle.structure.model.android.PsModuleDependency;
import com.google.common.collect.Lists;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.structure.model.PsIssueCollection.getTooltipText;
import static com.intellij.ui.SimpleTextAttributes.*;

/**
 * Model for the table displaying the "editable" dependencies of a module.
 */
class DeclaredDependenciesTableModel extends ListTableModel<PsAndroidDependency> {
  @NotNull private final PsAndroidModule myModule;
  @NotNull private final PsContext myContext;

  @Nullable private PsModuleDependency myHoveredDependency;

  DeclaredDependenciesTableModel(@NotNull PsAndroidModule module, @NotNull PsContext context) {
    myModule = module;
    myContext = context;
    createAndSetColumnInfos();
    reset();
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
        return new DependencyCellRenderer(dependency, myContext, dependency == myHoveredDependency);
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

  void setHoveredDependency(@Nullable PsModuleDependency hoveredDependency) {
    myHoveredDependency = hoveredDependency;
  }

  void reset() {
    List<PsAndroidDependency> dependencies = Lists.newArrayList();
    myModule.forEachDeclaredDependency(dependencies::add);
    Collections.sort(dependencies, PsAndroidDependencyComparator.INSTANCE);
    setItems(dependencies);
  }

  @Nullable
  PsAndroidLibraryDependency findDependency(@NotNull PsArtifactDependencySpec spec) {
    for (PsAndroidDependency dependency : getItems()) {
      if (dependency instanceof PsAndroidLibraryDependency) {
        PsAndroidLibraryDependency libraryDependency = (PsAndroidLibraryDependency)dependency;
        if (spec.equals(libraryDependency.getDeclaredSpec())) {
          return libraryDependency;
        }
      }
    }
    return null;
  }

  static class DependencyCellRenderer extends AbstractPsModelTableCellRenderer<PsAndroidDependency> {
    @NotNull private final PsAndroidDependency myDependency;
    @NotNull private final PsContext myContext;

    private final boolean myIsHovered;

    DependencyCellRenderer(@NotNull PsAndroidDependency dependency, @NotNull PsContext context, boolean isHovered) {
      super(dependency);
      myDependency = dependency;
      myContext = context;
      myIsHovered = isHovered;
    }

    @Override
    protected void customizeCellRenderer(JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
      setIcon(myDependency.getIcon());
      setIconOpaque(true);
      setFocusBorderAroundIcon(true);

      PsIssueCollection issueCollection = myContext.getDaemonAnalyzer().getIssues();
      List<PsIssue> issues = issueCollection.findIssues(myDependency, IssuesByTypeComparator.INSTANCE);

      setToolTipText(getTooltipText(issues));

      SimpleTextAttributes textAttributes;
      if (myIsHovered) {
        textAttributes = LINK_ATTRIBUTES;
      }
      else {
        textAttributes = REGULAR_ATTRIBUTES;
        if (!issues.isEmpty()) {
          PsIssue issue = issues.get(0);
          Color waveColor = issue.getType().getColor();
          textAttributes = textAttributes.derive(STYLE_WAVED, null, null, waveColor);
        }
      }
      append(getText(), textAttributes);
    }

    @Override
    @NotNull
    protected String getText() {
      PsAndroidDependency dependency = getModel();
      String text = dependency.getValueAsText();

      if (dependency instanceof PsAndroidLibraryDependency) {
        PsAndroidLibraryDependency library = (PsAndroidLibraryDependency)dependency;
        PsArtifactDependencySpec spec = library.getDeclaredSpec();
        assert spec != null;
        text = spec.getDisplayText();
      }
      return text;
    }
  }
}
