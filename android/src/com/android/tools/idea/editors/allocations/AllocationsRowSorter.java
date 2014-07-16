/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.allocations;

import com.android.tools.idea.editors.allocations.AllocationsTableUtil.Column;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.util.ArrayList;
import java.util.List;

public class AllocationsRowSorter extends TableRowSorter<TableModel> {
  public AllocationsRowSorter(@NotNull TableModel model) {
    setModel(model);
    setMaxSortKeys(1);
  }

  @Override
  public void setSortKeys(@Nullable List<? extends SortKey> sortKeys) {
    List<SortKey> keys = sortKeys == null ? new ArrayList<SortKey>() : new ArrayList<SortKey>(sortKeys);
    // Does secondary sorting (breaks ties) on allocation size
    if (keys.size() > 0 && keys.get(0).getColumn() != Column.ALLOCATION_SIZE.ordinal()) {
      keys.add(1, new SortKey(Column.ALLOCATION_SIZE.ordinal(), keys.get(0).getSortOrder()));
    }
    super.setSortKeys(keys);
  }
}
