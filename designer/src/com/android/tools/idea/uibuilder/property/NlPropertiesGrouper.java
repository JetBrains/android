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
package com.android.tools.idea.uibuilder.property;

import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.ptable.PTableGroupItem;
import com.android.tools.idea.uibuilder.property.ptable.PTableItem;
import com.google.common.collect.Lists;
import com.intellij.ui.ColoredTableCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.util.List;

public class NlPropertiesGrouper {
  public List<PTableItem> group(List<NlProperty> properties) {
    List<PTableItem> result = Lists.newArrayListWithExpectedSize(properties.size());

    PTableGroupItem layoutGroup = new PTableGroupItem() {
      @Override
      public String getName() {
        return "layout";
      }

      @NotNull
      @Override
      public TableCellRenderer getCellRenderer() {
        return new ColoredTableCellRenderer() {
          @Override
          protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
          }
        };
      }
    };
    List<PTableItem> layoutProps = Lists.newArrayList();

    for (NlProperty p : properties) {
      if (p.getName().startsWith("layout")) {
        layoutProps.add(p);
      } else {
        result.add(p);
      }
    }

    layoutGroup.setChildren(layoutProps);
    result.set(0, layoutGroup);

    return result;
  }
}
