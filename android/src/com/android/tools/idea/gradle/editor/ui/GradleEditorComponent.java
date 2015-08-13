/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.editor.ui;

import com.android.tools.idea.gradle.editor.entity.GradleEditorEntity;
import com.android.tools.idea.gradle.editor.entity.GradleEditorEntityGroup;
import com.google.common.collect.Maps;
import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * UI component which serves as a visual representation of the target <code>build.gradle</code> file.
 */
public class GradleEditorComponent extends JBScrollPane {

  private final Map<String, GradleEditorEntityTable> myTablesByGroupName = Maps.newHashMap();
  private final Map<String, JBPanel> myPanelsByGroupName = Maps.newHashMap();
  private final JBPanel myCanvas = new JBPanel(new GridBagLayout());
  @NotNull private final Project myProject;

  public GradleEditorComponent(@NotNull Project project, @NotNull List<GradleEditorEntityGroup> groups) {
    myProject = project;
    setViewportView(myCanvas);
    myCanvas.setBackground(GradleEditorUiConstants.BACKGROUND_COLOR);
    setData(groups);
  }

  private void addUiForGroup(@NotNull GradleEditorEntityGroup group) {
    JBPanel panel = new JBPanel(new BorderLayout());
    panel.setBackground(UIUtil.getTableBackground());
    panel.setBorder(IdeBorderFactory.createTitledBorder(group.getName()));
    GradleEditorEntityTable table = new GradleEditorEntityTable(myProject);
    for (GradleEditorEntity entity : group.getEntities()) {
      table.getModel().add(entity);
    }
    panel.add(table);
    addUiForGroup(group.getName(), panel, table);
  }

  private void addUiForGroup(@NotNull String groupName, @NotNull JBPanel component, @NotNull GradleEditorEntityTable table) {
    GridBagConstraints constraints = new GridBag().weightx(1).anchor(GridBagConstraints.WEST).fillCellHorizontally().coverLine()
      .insets(8, 8, 8, 8);
    myCanvas.add(component, constraints);
    myTablesByGroupName.put(groupName, table);
    myPanelsByGroupName.put(groupName, component);
  }

  /**
   * Instructs current component to flush all changes made by a user to the underlying gradle config.
   */
  public void flushChanges() {
    for (GradleEditorEntityTable table : myTablesByGroupName.values()) {
      if (table.isEditing()) {
        table.getCellEditor().stopCellEditing();
      }
    }
  }

  /**
   * Configures current control to represent given groups (discarding all previously shown information).
   *
   * @param groups  groups to show
   */
  public void setData(@NotNull List<GradleEditorEntityGroup> groups) {
    // Most likely use-case is that just table's data should be updated.
    boolean sameTables = groups.size() == myTablesByGroupName.size();
    for (GradleEditorEntityGroup group : groups) {
      GradleEditorEntityTable table = myTablesByGroupName.get(group.getName());
      if (table == null) {
        sameTables = false;
      }
      else {
        if (table.isEditing()) {
          table.getCellEditor().stopCellEditing();
        }
        table.getModel().setData(group.getEntities());
      }
    }

    if (sameTables) {
      return;
    }

    myCanvas.removeAll();
    Map<String, GradleEditorEntityTable> tablesByGroupName = Maps.newHashMap(myTablesByGroupName);
    myTablesByGroupName.clear();
    Map<String, JBPanel> panelsByGroupName = Maps.newHashMap(myPanelsByGroupName);
    myPanelsByGroupName.clear();
    for (GradleEditorEntityGroup group : groups) {
      JBPanel panel = panelsByGroupName.get(group.getName());
      GradleEditorEntityTable table = tablesByGroupName.get(group.getName());
      if (panel != null && table != null) {
        addUiForGroup(group.getName(), panel, table);
      }
      else {
        addUiForGroup(group);
      }
    }

    myCanvas.add(new JLabel(" "), new GridBag().weighty(1));
  }
}
