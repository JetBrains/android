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

import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.BuildFileStatement;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.parser.Repository;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.util.List;

/**
 * Two-column table model that displays the properties of a single object as key/value pairs.
 */
public class SingleObjectTableModel extends AbstractTableModel {
  private static final int NAME_COLUMN = 0;
  private final List<BuildFileKey> myProperties;
  private final List<Object> myCurrentValues = Lists.newArrayList();
  private final GrClosableBlock myRoot;
  private final GradleBuildFile myBuildFile;
  boolean myModified;

  SingleObjectTableModel(@NotNull GradleBuildFile buildFile, @Nullable GrClosableBlock root, @NotNull List<BuildFileKey> properties) {
    myBuildFile = buildFile;
    myRoot = root;
    myProperties = properties;
    for (BuildFileKey key : myProperties) {
      myCurrentValues.add(myBuildFile.getValue(myRoot, key));
    }
  }

  @Override
  public String getColumnName(int i) {
    return "";
  }

  @Override
  public boolean isCellEditable(int row, int col) {
    return col != NAME_COLUMN;
  }

  @Override
  public void setValueAt(@NotNull Object value, int row, int col) {
    if (col == NAME_COLUMN) {
      return;
    }
    if (!Objects.equal(value, myCurrentValues.get(row))) {
      BuildFileKey key = myProperties.get(row);
      if (key == BuildFileKey.LIBRARY_REPOSITORY || key == BuildFileKey.PLUGIN_REPOSITORY) {
        List<BuildFileStatement> list = Lists.newArrayList();
        for (String s : Splitter.on(',').trimResults().split(value.toString())) {
          list.add(Repository.parse(s, myBuildFile.getProject()));
        }
        value = list;
      }
      myCurrentValues.set(row, value);
      myModified = true;
    }
  }

  @Override
  public int getRowCount() {
    return myProperties.size();
  }

  @Override
  public int getColumnCount() {
    return 2;
  }

  @Override
  @Nullable
  public Object getValueAt(int row, int col) {
    BuildFileKey key = myProperties.get(row);
    if (col == NAME_COLUMN) {
      return key.getDisplayName();
    }
    Object value = myCurrentValues.get(row);
    if (key == BuildFileKey.LIBRARY_REPOSITORY || key == BuildFileKey.PLUGIN_REPOSITORY) {
      if (value instanceof List) {
        value = Joiner.on(", ").join((List)value);
      }
    }
    return value;
  }

  /**
   * Returns a custom cell editor for use to edit the given cell, or null if the default editor should be used.
   */
  @Nullable
  public TableCellEditor getCellEditor(int row, int col) {
    if (col == NAME_COLUMN) {
      // This shouldn't happen; that column isn't editable.
      return null;
    }
    BuildFileKey key = myProperties.get(row);
    Class<?> type = key.getType().getNativeType();
    if (type == Integer.class) {
      return new IntegerCellEditor();
    }

    // TODO: Implement custom editors for other types as needed.
    return null;
  }

  public boolean isModified() {
    return myModified;
  }

  public void apply() {
    for (int i = 0; i < myProperties.size(); i++) {
      BuildFileKey property = myProperties.get(i);
      Object value = myCurrentValues.get(i);
      if (value != null) {
        myBuildFile.setValue(myRoot, property, value);
      } else {
        myBuildFile.removeValue(myRoot, property);
      }
    }
    myModified = false;
  }

  /**
   * Custom cell editor for Integer-valued table cells.
   */
  public static class IntegerCellEditor extends DefaultCellEditor {
    public IntegerCellEditor() {
      super(new JTextField());
      editorComponent.setBorder(new LineBorder(Color.black));
    }

    @Nullable
    @Override
    public Object getCellEditorValue() {
      try {
        return Integer.parseInt(((JTextField)editorComponent).getText());
      } catch (Exception e) {
        return null;
      }
    }

    @Override
    public boolean stopCellEditing() {
      String text = ((JTextField)editorComponent).getText();
      if (text != null) {
        try {
          Integer.parseInt(text);
        } catch (Exception e) {
          editorComponent.setBorder(new LineBorder(Color.red));
          return true;
        }
      }
      editorComponent.setBorder(new LineBorder(Color.black));
      return super.stopCellEditing();
    }
  }
}
