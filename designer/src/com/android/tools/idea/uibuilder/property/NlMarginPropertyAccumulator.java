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
package com.android.tools.idea.uibuilder.property;

import com.android.tools.adtui.ptable.PTable;
import com.android.tools.adtui.ptable.PTableCellRenderer;
import com.android.tools.adtui.ptable.PTableGroupItem;
import com.android.tools.adtui.ptable.PTableItem;
import com.google.common.collect.ImmutableSet;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableCellRenderer;
import java.util.Set;

public class NlMarginPropertyAccumulator extends NlPropertyAccumulator {
  private static final TableCellRenderer MARGIN_VALUE_RENDERER = createTableCellRenderer();

  private final String myAllMargin;
  private final String myLeftMargin;
  private final String myRightMargin;
  private final String myStartMargin;
  private final String myEndMargin;
  private final String myTopMargin;
  private final String myBottomMargin;
  private final Set<String> myPropertyNames;

  public NlMarginPropertyAccumulator(@NotNull String groupName,
                                     @NotNull String allMargin,
                                     @NotNull String leftMargin,
                                     @NotNull String rightMargin,
                                     @NotNull String startMargin,
                                     @NotNull String endMargin,
                                     @NotNull String topMargin,
                                     @NotNull String bottomMargin) {
    super(groupName, allMargin);
    myAllMargin = allMargin;
    myLeftMargin = leftMargin;
    myRightMargin = rightMargin;
    myStartMargin = startMargin;
    myEndMargin = endMargin;
    myTopMargin = topMargin;
    myBottomMargin = bottomMargin;
    myPropertyNames = ImmutableSet.of(allMargin, leftMargin, rightMargin, startMargin, endMargin, topMargin, bottomMargin);
  }

  @Override
  protected boolean isApplicable(@NotNull NlPropertyItem property) {
    return myPropertyNames.contains(property.getName());
  }

  @NotNull
  @Override
  protected PTableGroupItem createGroupNode(@NotNull String groupName, @NotNull String prefix) {
    return new MarginGroupNode(groupName, prefix);
  }

  private static TableCellRenderer createTableCellRenderer() {
    return new PTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(@NotNull PTable table, @NotNull PTableItem value,
                                           boolean selected, boolean hasFocus, int row, int column) {
        if (!(value instanceof MarginGroupNode)) {
          return;
        }
        MarginGroupNode node = (MarginGroupNode)value;
        NlMarginPropertyAccumulator accumulator = node.getAccumulator();
        append("[");
        append(node, accumulator.myAllMargin, null);
        append(", ");
        append(node, accumulator.myLeftMargin, accumulator.myStartMargin);
        append(", ");
        append(node, accumulator.myTopMargin, null);
        append(", ");
        append(node, accumulator.myRightMargin, accumulator.myEndMargin);
        append(", ");
        append(node, accumulator.myBottomMargin, null);
        append("]");
      }

      private void append(@NotNull MarginGroupNode node, @NotNull String propertyName, @Nullable String propertyOverride) {
        SimpleTextAttributes attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
        PTableItem property = node.getItemByName(propertyName);
        PTableItem override = propertyOverride != null ? node.getItemByName(propertyOverride) : null;
        String value = null;
        if (override != null) {
          value = override.getResolvedValue();
          if (!override.isDefaultValue(value)) {
            attributes = SimpleTextAttributes.SYNTHETIC_ATTRIBUTES;
          }
        }
        if (property != null && value == null) {
          value = property.getResolvedValue();
          if (!property.isDefaultValue(value)) {
            attributes = SimpleTextAttributes.SYNTHETIC_ATTRIBUTES;
          }
        }
        if (value == null) {
          value = "?";
        }
        append(value, attributes);
      }
    };
  }

  private class MarginGroupNode extends AccumulatorGroupNode {

    private MarginGroupNode(@NotNull String name, @NotNull String prefix) {
      super(name, prefix);
    }

    @NotNull
    @Override
    public TableCellRenderer getCellRenderer() {
      return MARGIN_VALUE_RENDERER;
    }

    private NlMarginPropertyAccumulator getAccumulator() {
      return NlMarginPropertyAccumulator.this;
    }
  }
}
