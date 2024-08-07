/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run.exporter;

import com.google.common.collect.ImmutableList;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.text.UniqueNameGenerator;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/** Table model used by the 'export run configurations' UI. */
class ExportRunConfigurationTableModel extends AbstractTableModel {

  private static final ImmutableList<String> COLUMN_NAMES =
      ImmutableList.of("Export", "Name", "Output filename");
  private static final ImmutableList<Class<?>> COLUMN_CLASSES =
      ImmutableList.of(Boolean.class, String.class, String.class);

  final Boolean[] enabled;
  final String[] names;
  final String[] paths;

  ExportRunConfigurationTableModel(List<RunConfiguration> configurations) {
    enabled = new Boolean[configurations.size()];
    names = new String[configurations.size()];
    paths = new String[configurations.size()];

    UniqueNameGenerator nameGenerator = new UniqueNameGenerator();
    for (int i = 0; i < configurations.size(); i++) {
      RunConfiguration config = configurations.get(i);
      enabled[i] = false;
      names[i] = config.getName();
      paths[i] =
          nameGenerator.generateUniqueName(FileUtil.sanitizeFileName(config.getName()), "", ".xml");
    }
  }

  @Override
  public int getColumnCount() {
    return 3;
  }

  @Override
  public Class<?> getColumnClass(int columnIndex) {
    return COLUMN_CLASSES.get(columnIndex);
  }

  @Override
  public String getColumnName(int column) {
    return COLUMN_NAMES.get(column);
  }

  @Override
  public int getRowCount() {
    return enabled.length;
  }

  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return columnIndex != 1;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    switch (columnIndex) {
      case 0:
        return enabled[rowIndex];
      case 1:
        return names[rowIndex];
      case 2:
        return paths[rowIndex];
      default:
        throw new RuntimeException("Invalid column index: " + columnIndex);
    }
  }

  @Override
  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    switch (columnIndex) {
      case 0:
        enabled[rowIndex] = (Boolean) aValue;
        return;
      case 1:
        names[rowIndex] = (String) aValue;
        return;
      case 2:
        paths[rowIndex] = (String) aValue;
        return;
      default:
        throw new RuntimeException("Invalid column index: " + columnIndex);
    }
  }
}
