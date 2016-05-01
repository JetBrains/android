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
package com.android.tools.idea.uibuilder.property.ptable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.util.Collections;
import java.util.List;

public abstract class PTableItem {
  private TableCellRenderer myCellRenderer;
  private PTableItem myParent;

  public boolean hasChildren() {
    return false;
  }

  public List<PTableItem> getChildren() {
    return Collections.emptyList();
  }

  @Nullable
  public PTableItem getParent() {
    return myParent;
  }

  public void setParent(PTableItem parent) {
    myParent = parent;
  }

  @NotNull
  public TableCellRenderer getCellRenderer() {
    if (myCellRenderer == null) {
      myCellRenderer = new DefaultTableCellRenderer();
    }
    return myCellRenderer;
  }

  public boolean isExpanded() {
    return false;
  }

  public void setExpanded(boolean expanded) {
  }

  @NotNull
  public abstract String getName();

  @Nullable
  public abstract String getValue();

  @Nullable
  public abstract String getResolvedValue();

  public abstract boolean isDefaultValue(@Nullable String value);

  public abstract void setValue(@Nullable Object value);

  @Nullable
  public String getTooltipText() {
    return null;
  }

  @Nullable
  public PTableCellEditor getCellEditor() {
    return null;
  }

  public boolean isEditable(int col) {
    return false;
  }
}
