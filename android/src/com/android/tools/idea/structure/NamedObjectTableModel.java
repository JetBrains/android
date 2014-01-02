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

import com.android.tools.idea.gradle.parser.*;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
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
    Class<?> clazz = getKey(i).getType().getNativeType();
    if (clazz == Boolean.class) {
      return NamedObjectPanel.ThreeStateBoolean.class;
    } else {
      return clazz;
    }
  }

  @Override
  public boolean isCellEditable(int row, int col) {
    return col == NAME_COLUMN || getKey(col).getType() != BuildFileKeyType.CLOSURE;
  }

  @Override
  public void setValueAt(@Nullable Object o, int row, int col) {
    NamedObject no = myItems.get(row);
    if (col == NAME_COLUMN) {
      if (o == null) {
        return;
      }
      no.setName((String)o);
    } else {
      BuildFileKey key = getKey(col);
      Class<?> type = key.getType().getNativeType();
      if (type == Boolean.class) {
          NamedObjectPanel.ThreeStateBoolean b = (NamedObjectPanel.ThreeStateBoolean)o;
          o = b != null ? b.getValue() : null;
      } else if (type == File.class) {
          if (o != null && !StringUtil.isEmptyOrSpaces(o.toString())) {
            // If the file lives under the project root, use a relative path.
            File file = new File(FileUtil.toSystemDependentName(o.toString()));
            if (FileUtil.isAncestor(new File(myBuildFile.getProject().getBasePath()), file, false)) {
              File parent = VfsUtilCore.virtualToIoFile(myBuildFile.getFile().getParent());
              o = new File(FileUtilRt.getRelativePath(parent, file));
            } else {
              o = file;
            }
          } else {
            o = null;
          }
      } else if (type == String.class) {
          if ("".equals(o)) {
            o = null;
          }
      }
      Object oldValue = no.getValue(key);
      if (!Objects.equal(oldValue, o)) {
        no.setValue(key, o);
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
    NamedObject no = myItems.get(row);
    if (col == NAME_COLUMN) {
      return no.getName();
    } else {
      Object o = no.getValue(getKey(col));
      if (o == GradleBuildFile.UNRECOGNIZED_VALUE) {
        return null;
      }
      Class<?> type = getKey(col).getType().getNativeType();
      if (type == Boolean.class) {
        return NamedObjectPanel.ThreeStateBoolean.forValue((Boolean)o);
      } else if (type == File.class) {
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
