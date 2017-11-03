/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.adtui.common;

import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.google.common.truth.Truth.assertThat;

/**
 * A wrapper object that holds info related to testing a {@link ColumnTreeBuilder}.
 */
public final class ColumnTreeTestInfo {
  private final JTree myTree;
  private final JTable myTable;
  private final JScrollPane myColumnTreePane;

  public ColumnTreeTestInfo(@NotNull JTree tree, @NotNull JScrollPane columnTreePane) {
    myTree = tree;
    myColumnTreePane = columnTreePane;
    myTable = (JTable)((JPanel)columnTreePane.getViewport().getView()).getComponent(0);
  }

  @NotNull
  public JTree getTree() {
    return myTree;
  }

  @NotNull
  public JTable getTable() {
    return myTable;
  }

  @NotNull
  public JScrollPane getScrollPane() {
    return myColumnTreePane;
  }

  public void simulateLayout(@NotNull Dimension dim) {
    myColumnTreePane.setSize(dim);
    myColumnTreePane.doLayout();
    myColumnTreePane.getViewport().doLayout();
    myColumnTreePane.getViewport().getView().doLayout();
  }

  /**
   * Verify the column header labels of the underlying JTable of the ColumnTree.
   *
   * @param headerValues the length of this should match the column count of the tree.
   */
  public void verifyColumnHeaders(Object... headerValues) {
    assertThat(myTable.getColumnModel().getColumnCount()).isEqualTo(headerValues.length);
    for (int i = 0; i < myTable.getColumnModel().getColumnCount(); i++) {
      assertThat(myTable.getColumnModel().getColumn(i).getHeaderValue()).isEqualTo(headerValues[i]);
    }
  }

  /**
   * Verify the String contents that have been appended to each cell based on a particular row value.
   *
   * TODO currently this relies on the same content being added as a tag when calling {@link ColoredTreeCellRenderer#append(String, SimpleTextAttributes, Object)},
   * as we have no public-accessible way to query the appended values.
   *
   * @param value          the value which the tree cell tries to render.
   * @param rendererValues the String arrays that were appended to each cell. The length of this variadic argument should match the column count of the tree,
   *                       and the individual elements of the variadic argument should match the number of fragments in each column.
   */
  public void verifyRendererValues(@NotNull Object value, String[]... rendererValues) {
    Container container = (Container)myTree.getCellRenderer().getTreeCellRendererComponent(myTree, value, false, false, true, 0, false);
    assertThat(container).isNotNull();
    assertThat(container.getComponentCount()).isEqualTo(rendererValues.length);
    for (int i = 0; i < container.getComponentCount(); i++) {
      assertThat(container.getComponent(i) instanceof ColoredTreeCellRenderer).isTrue();
      ColoredTreeCellRenderer renderer = (ColoredTreeCellRenderer)container.getComponent(i);
      for (int j = 0; j < rendererValues[i].length; j++) {
        String expected = rendererValues[i][j];
        if (expected == null || expected.isEmpty()) {
          assertThat(renderer.getFragmentTag(j)).isAnyOf(null, "");
        }
        else {
          assertThat(renderer.getFragmentTag(j)).isEqualTo(expected);
        }
      }
    }
  }
}
