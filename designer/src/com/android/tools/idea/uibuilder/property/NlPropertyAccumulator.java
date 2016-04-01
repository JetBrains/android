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

import com.android.tools.idea.uibuilder.property.ptable.PTableGroupItem;
import com.google.common.base.Predicate;
import com.intellij.ui.ColoredTableCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;

class NlPropertyAccumulator {
  private final String myGroupName;
  private final Predicate<NlProperty> myFilter;
  private GroupNode myGroupNode;

  public NlPropertyAccumulator(@NotNull String groupName, @NotNull Predicate<NlProperty> isApplicable) {
    myGroupName = groupName;
    myFilter = isApplicable;
  }

  public boolean process(@NotNull NlProperty p) {
    if (!myFilter.apply(p)) {
      return false;
    }

    if (myGroupNode == null) {
      myGroupNode = new GroupNode(myGroupName);
    }

    myGroupNode.addChild(p);
    return true;
  }

  public boolean hasItems() {
    return myGroupNode != null && !myGroupNode.getChildren().isEmpty();
  }

  @NotNull
  public GroupNode getGroupNode() {
    return myGroupNode == null ? new GroupNode(myGroupName) : myGroupNode;
  }

  private static class GroupNode extends PTableGroupItem {
    private static final ColoredTableCellRenderer EMPTY_VALUE_RENDERER = new ColoredTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      }
    };
    private final String myStyleable;

    public GroupNode(@NotNull String styleable) {
      myStyleable = styleable;
    }

    @NotNull
    @Override
    public String getName() {
      return myStyleable;
    }

    @NotNull
    @Override
    public TableCellRenderer getCellRenderer() {
      return EMPTY_VALUE_RENDERER;
    }
  }

  public static class PropertyNamePrefixAccumulator extends NlPropertyAccumulator {
    public PropertyNamePrefixAccumulator(@NotNull String groupName, @NotNull final String prefix) {
      super(groupName, new Predicate<NlProperty>() {
        @Override
        public boolean apply(NlProperty p) {
          return p != null && p.getName().startsWith(prefix);
        }
      });
    }
  }
}
