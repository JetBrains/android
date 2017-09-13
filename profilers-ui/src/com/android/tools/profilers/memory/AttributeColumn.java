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
import com.android.tools.profilers.ProfilerLayout;
import com.android.tools.profilers.memory.adapters.MemoryObject;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.util.Comparator;
import java.util.function.Supplier;

class AttributeColumn<T extends MemoryObject> {
  private final String myName;
  private final Supplier<ColoredTreeCellRenderer> myRendererSuppier;
  private final int myHeaderAlignment;
  private final int myPreferredWidth;
  private final Comparator<MemoryObjectTreeNode<T>> myComparator;

  public AttributeColumn(@NotNull String name,
                         @NotNull Supplier<ColoredTreeCellRenderer> rendererSupplier,
                         int headerAlignment,
                         int preferredWidth,
                         @NotNull Comparator<MemoryObjectTreeNode<T>> comparator) {
    myName = name;
    myRendererSuppier = rendererSupplier;
    myHeaderAlignment = headerAlignment;
    myPreferredWidth = preferredWidth;
    myComparator = comparator;
  }

  @NotNull
  public ColumnTreeBuilder.ColumnBuilder getBuilder() {
    Border border;
    if (myHeaderAlignment == SwingConstants.LEFT) {
      border = ProfilerLayout.TABLE_COLUMN_HEADER_BORDER;
    } else {
      border = ProfilerLayout.TABLE_COLUMN_RIGHT_ALIGNED_HEADER_BORDER;
    }

    return new ColumnTreeBuilder.ColumnBuilder()
      .setName(myName)
      .setRenderer(myRendererSuppier.get())
      .setHeaderAlignment(myHeaderAlignment)
      .setPreferredWidth(myPreferredWidth)
      .setComparator(myComparator)
      .setHeaderBorder(border);
  }

  public Comparator<MemoryObjectTreeNode<T>> getComparator() {
    return myComparator;
  }
}
