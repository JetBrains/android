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
import org.jetbrains.annotations.NotNull;

import javax.swing.table.DefaultTableModel;
import java.util.List;

public class GradleEditorEntityTableModel extends DefaultTableModel {

  @Override
  public int getColumnCount() {
    return 1;
  }

  @Override
  public Class<?> getColumnClass(int columnIndex) {
    return Object.class;
  }

  public void add(@NotNull GradleEditorEntity entity) {
    addRow(new Object[] { entity });
  }

  public void setData(@NotNull List<GradleEditorEntity> entities) {
    int i = 0;
    int rows = getRowCount();
    for (GradleEditorEntity entity : entities) {
      if (i < rows) {
        setValueAt(entity, i++, 0);
      }
      else {
        add(entity);
      }
    }

    if (rows > entities.size()) {
      for (int j = rows - 1; j >= entities.size(); j--) {
        removeRow(j);
      }
    }
  }
}
