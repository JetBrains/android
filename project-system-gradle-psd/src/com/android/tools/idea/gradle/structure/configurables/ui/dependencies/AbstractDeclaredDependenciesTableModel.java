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
package com.android.tools.idea.gradle.structure.configurables.ui.dependencies;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.issues.IssuesBySeverityPathTextComparator;
import com.android.tools.idea.gradle.structure.configurables.ui.AbstractPsModelTableCellRenderer;
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings;
import com.android.tools.idea.gradle.structure.model.*;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.List;

import static com.android.tools.idea.gradle.structure.model.PsIssueCollectionKt.getTooltipText;
import static com.intellij.ui.SimpleTextAttributes.*;

/**
 * Model for the table displaying the "editable" dependencies of a module.
 */
public abstract class AbstractDeclaredDependenciesTableModel<T extends PsBaseDependency> extends ListTableModel<T> {
  @NotNull private final PsModule myModule;
  @NotNull private final PsContext myContext;

  @Nullable private PsModuleDependency myHoveredDependency;

  protected AbstractDeclaredDependenciesTableModel(@NotNull PsModule module, @NotNull PsContext context) {
    myModule = module;
    myContext = context;
    createAndSetColumnInfos();
    reset();
  }

  private void createAndSetColumnInfos() {
    ColumnInfo<T, String> specColumnInfo = new ColumnInfo<T, String>("Dependency") {
      @Override
      @NotNull
      public String valueOf(T dependency) {
        return dependency.toText();
      }

      @Override
      @NotNull
      public TableCellRenderer getRenderer(T dependency) {
        return new DependencyCellRenderer(dependency, myContext, dependency == myHoveredDependency);
      }

      @Override
      @NotNull
      public String getPreferredStringValue() {
        // Just a long string to hint the width of a column
        return "com.android.support:appcompat-v7:23.1.0";
      }
    };

    ColumnInfo<T, String> scopeColumnInfo = new ColumnInfo<T, String>("Configuration") {
      @Override
      @Nullable
      public String valueOf(T dependency) {
        return dependency.getJoinedConfigurationNames();
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

  @NotNull
  protected PsModule getModule() {
    return myModule;
  }

  public void setHoveredDependency(@Nullable PsModuleDependency hoveredDependency) {
    myHoveredDependency = hoveredDependency;
  }

  public void reset() {
  }

  @NotNull
  protected PsContext getContext() {
    return myContext;
  }

  static class DependencyCellRenderer extends AbstractPsModelTableCellRenderer<PsBaseDependency> {
    @NotNull private final PsBaseDependency myDependency;
    @NotNull private final PsContext myContext;

    private final boolean myIsHovered;

    DependencyCellRenderer(@NotNull PsBaseDependency dependency, @NotNull PsContext context, boolean isHovered) {
      super(dependency);
      myDependency = dependency;
      myContext = context;
      myIsHovered = isHovered;
    }

    @Override
    protected void customizeCellRenderer(@NotNull JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
      setIcon(myDependency.getIcon());
      setIconOpaque(true);
      setFocusBorderAroundIcon(true);

      PsIssueCollection issueCollection = myContext.getAnalyzerDaemon().getIssues();
      List<PsIssue> issues = issueCollection.findIssues(myDependency.getPath(), IssuesBySeverityPathTextComparator.INSTANCE);

      setToolTipText(getTooltipText(issues, false));

      SimpleTextAttributes textAttributes;
      if (myIsHovered) {
        textAttributes = LINK_ATTRIBUTES;
      }
      else {
        textAttributes = REGULAR_ATTRIBUTES;
        if (!issues.isEmpty()) {
          PsIssue issue = issues.get(0);
          Color waveColor = issue.getSeverity().getColor();
          textAttributes = textAttributes.derive(STYLE_WAVED, null, null, waveColor);
        }
      }
      append(getText(), textAttributes);
    }

    @Override
    @NotNull
    protected String getText() {
      return displayTextOf(getModel(), myContext.getUiSettings());
    }
  }

  @NotNull
  static String displayTextOf(@NotNull PsBaseDependency dependency, @NotNull PsUISettings uiSettings) {
    String text = dependency.toText();

    if (dependency instanceof PsLibraryDependency) {
      PsLibraryDependency library = (PsLibraryDependency)dependency;
      PsArtifactDependencySpec spec = library.getSpec();
      text = spec.getDisplayText(uiSettings);
    }
    return text;
  }
}
