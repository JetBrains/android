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
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.parser.NamedObject;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.ItemRemovable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import java.io.File;
import java.util.List;

/**
 * Table model that allows display/editing of lists of generic {@linkplain NamedObject} instances in a table. Each instance has a row in
 * the table, and properties are in columns.
 */
public class NamedObjectTableModel extends AbstractTableModel implements ItemRemovable {
  private static final int NAME_COLUMN = 0;
  private final List<NamedObject> myItems = Lists.newArrayList();
  private final List<BuildFileKey> myProperties;
  private final GradleBuildFile myBuildFile;
  private final BuildFileKey myRoot;
  private boolean myModified = false;

  NamedObjectTableModel(@NotNull GradleBuildFile buildFile, @NotNull BuildFileKey root, @NotNull List<BuildFileKey> properties) {
    myBuildFile = buildFile;
    myRoot = root;
    myProperties = properties;
    Object value = buildFile.getValue(root);
    if (value instanceof List) {
      myItems.addAll((List)value);
    }
  }

  @Override
  @NotNull
  public String getColumnName(int i) {
    if (i == NAME_COLUMN) {
      return "Name";
    } else {
      return getKey(i).getDisplayName();
    }
  }

  @Override
  @NotNull
  public Class<?> getColumnClass(int i) {
    if (i == NAME_COLUMN) {
      return String.class;
    }
    switch (getKey(i).getType()) {
      case INTEGER:
        return Integer.class;
      case BOOLEAN:
        return NamedObjectPanel.ThreeStateBoolean.class;
      case FILE:
      case FILE_AS_STRING:
        return File.class;
      case STRING:
      case CLOSURE:
      case REFERENCE:
        return String.class;
      default:
        assert false : getKey(i).getType();
        return String.class;
    }
  }

  @Override
  public boolean isCellEditable(int row, int col) {
    return col == NAME_COLUMN || getKey(col).getType() != BuildFileKey.Type.CLOSURE;
  }

  @Override
  public void setValueAt(@Nullable Object o, int row, int col) {
    if (col == NAME_COLUMN) {
      if (o == null) {
        return;
      }
      myItems.get(row).setName((String)o);
    } else {
      BuildFileKey key = getKey(col);
      BuildFileKey.Type type = key.getType();
      switch(type) {
        case BOOLEAN:
          NamedObjectPanel.ThreeStateBoolean b = (NamedObjectPanel.ThreeStateBoolean)o;
          o = b != null ? b.getValue() : null;
          break;
        case FILE:
        case FILE_AS_STRING:
          if (o != null && !StringUtil.isEmptyOrSpaces(o.toString())) {
            o = new File(FileUtil.toSystemIndependentName(o.toString()));
          } else {
            o = null;
          }
          break;
        case REFERENCE:
        case STRING:
          if ("".equals(o)) {
            o = null;
          }
          break;
        default:
          break;
      }
      Object oldValue = myItems.get(row).getValue(key);
      if (!Objects.equal(oldValue, o)) {
        myItems.get(row).setValue(key, o);
      }
    }
    myModified = true;
  }

  @Override
  public int getRowCount() {
    return myItems.size();
  }

  @Override
  public int getColumnCount() {
    return myProperties.size() + 1;
  }

  @Override
  @Nullable
  public Object getValueAt(int row, int col) {
    NamedObject item = myItems.get(row);
    if (col == NAME_COLUMN) {
      return item.getName();
    } else {
      Object o = item.getValue(getKey(col));
      BuildFileKey.Type type = getKey(col).getType();
      if (type == BuildFileKey.Type.BOOLEAN) {
        return NamedObjectPanel.ThreeStateBoolean.forValue((Boolean)o);
      } else if (type == BuildFileKey.Type.FILE || type == BuildFileKey.Type.FILE_AS_STRING) {
        return o != null ? o.toString() : null;
      } else {
        return o;
      }
    }
  }

  private BuildFileKey getKey(int i) {
    return myProperties.get(i - 1);
  }

  public void apply() {
    if (!myModified) {
      return;
    }
    myBuildFile.setValue(myRoot, myItems);
    myModified = false;
  }

  public boolean isModified() {
    return myModified;
  }

  public void addRow() {
    myItems.add(new NamedObject(""));
    // We don't treat it as modified until the user actually edits a cell.
  }

  @Override
  public void removeRow(int idx) {
    myItems.remove(idx);
    myModified = true;
  }
}
