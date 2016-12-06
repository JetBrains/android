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
package com.android.tools.profilers.memory;

import com.android.tools.adtui.common.ColumnTreeBuilder;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Comparator;
import java.util.function.Supplier;

class AttributeColumn {
  private final String myName;
  private final Supplier<ColoredTreeCellRenderer> myRendererSuppier;
  private final int myHeaderAlignment;
  private final int myPreferredWidth;
  private final SortOrder mySortOrder;
  private final Comparator<MemoryObjectTreeNode> myComparator;

  public AttributeColumn(@NotNull String name,
                         @NotNull Supplier<ColoredTreeCellRenderer> rendererSupplier,
                         int headerAlignment,
                         int preferredWidth,
                         @NotNull SortOrder sortOrder,
                         @NotNull Comparator<MemoryObjectTreeNode> comparator) {
    myName = name;
    myRendererSuppier = rendererSupplier;
    myHeaderAlignment = headerAlignment;
    myPreferredWidth = preferredWidth;
    mySortOrder = sortOrder;
    myComparator = comparator;
  }

  @NotNull
  public ColumnTreeBuilder.ColumnBuilder getBuilder() {
    return new ColumnTreeBuilder.ColumnBuilder()
      .setName(myName)
      .setRenderer(myRendererSuppier.get())
      .setHeaderAlignment(myHeaderAlignment)
      .setPreferredWidth(myPreferredWidth)
      .setInitialOrder(mySortOrder)
      .setComparator(myComparator);
  }
}
