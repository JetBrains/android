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

import com.android.tools.adtui.ptable.PTable;
import com.android.tools.adtui.ptable.PTableCellRenderer;
import com.android.tools.adtui.ptable.PTableGroupItem;
import com.android.tools.adtui.ptable.PTableItem;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.table.TableCellRenderer;
import java.util.function.Predicate;

class NlPropertyAccumulator {
  private static final TableCellRenderer EMPTY_VALUE_RENDERER = createTableCellRenderer();

  private final String myGroupName;
  private final String myPrefix;
  private final Predicate<NlPropertyItem> myFilter;
  private PTableGroupItem myGroupNode;

  public NlPropertyAccumulator(@NotNull String groupName, @NotNull String prefix) {
    myGroupName = groupName;
    myPrefix = prefix;
    myFilter = null;
  }

  public NlPropertyAccumulator(@NotNull String groupName, @NotNull String prefix, @NotNull Predicate<NlPropertyItem> isApplicable) {
    myGroupName = groupName;
    myPrefix = prefix;
    myFilter = isApplicable;
  }

  protected boolean isApplicable(@NotNull NlPropertyItem p) {
    assert myFilter != null;
    return myFilter.test(p);
  }

  public boolean process(@NotNull NlPropertyItem p) {
    if (!isApplicable(p)) {
      return false;
    }

    if (myGroupNode == null) {
      myGroupNode = createGroupNode(myGroupName, myPrefix);
    }

    myGroupNode.addChild(p);
    return true;
  }

  public boolean hasItems() {
    return myGroupNode != null && !myGroupNode.getChildren().isEmpty();
  }

  @NotNull
  public PTableGroupItem getGroupNode() {
    return myGroupNode == null ? createGroupNode(myGroupName, myPrefix) : myGroupNode;
  }

  @NotNull
  protected PTableGroupItem createGroupNode(@NotNull String groupName, @NotNull String prefix) {
    return new AccumulatorGroupNode(groupName, prefix);
  }

  private static TableCellRenderer createTableCellRenderer() {
    return new PTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(@NotNull PTable table, @NotNull PTableItem value,
                                           boolean selected, boolean hasFocus, int row, int column) {
      }
    };
  }

  static class AccumulatorGroupNode extends NlPTableGroupItem {
    private final String myName;
    private final String myPrefix;

    AccumulatorGroupNode(@NotNull String name, @NotNull String prefix) {
      myName = name;
      myPrefix = prefix;
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @NotNull
    @Override
    public String getChildLabel(@NotNull PTableItem item) {
      String label = StringUtil.trimStart(item.getName(), myPrefix);
      if (label.isEmpty()) {
        return "all";
      }
      return StringUtil.decapitalize(label);
    }

    @NotNull
    @Override
    public TableCellRenderer getCellRenderer() {
      return EMPTY_VALUE_RENDERER;
    }
  }

  public static class PropertyNamePrefixAccumulator extends NlPropertyAccumulator {
    public PropertyNamePrefixAccumulator(@NotNull String groupName, @NotNull final String prefix) {
      super(groupName, prefix, p -> p != null && p.getName().startsWith(prefix));
    }
  }
}
