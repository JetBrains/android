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
package com.android.tools.idea.ui.resourcechooser;

import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;

/**
 * Table model for the resource table in the resource chooser
 */
class ResourceTableContentProvider extends AbstractTableModel {
  private final ResourceChooserGroup[] myGroups;
  private final int[] myGroupIndices;
  private final int myRowCount;

  public ResourceTableContentProvider(ResourceChooserGroup[] groups) {
    myGroups = groups;
    myGroupIndices = new int[groups.length];
    int rowCount = 0;
    for (int i = 0; i < groups.length; i++) {
      myGroupIndices[i] = rowCount;
      rowCount++; // header line
      rowCount += groups[i].getItems().size();
    }
    myRowCount = rowCount;
  }

  @Override
  public int getRowCount() {
    return myRowCount;
  }

  @Override
  public int getColumnCount() {
    return 2;
  }

  @Override
  public Class<?> getColumnClass(int columnIndex) {
    return super.getColumnClass(columnIndex);
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    // Find the right resource item
    return getResource(rowIndex);
  }

  @Nullable
  private Object getResource(int rowIndex) {
    for (int groupIndex = myGroups.length - 1; groupIndex >= 0; groupIndex--) {
      if (rowIndex >= myGroupIndices[groupIndex]) {
        ResourceChooserGroup group = myGroups[groupIndex];
        if (rowIndex == myGroupIndices[groupIndex]) {
          // It's the actual heading node
          return group;
        }
        int index = rowIndex - myGroupIndices[groupIndex] - 1;
        return group.getItems().get(index);
      }
    }
    return null;
  }
}
