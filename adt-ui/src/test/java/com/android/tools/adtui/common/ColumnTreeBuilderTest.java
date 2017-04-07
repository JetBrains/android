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
package com.android.tools.adtui.common;

import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.LoadingNode;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class ColumnTreeBuilderTest {

  @Test
  public void testTreeFillsViewportIfSmallerThanViewport() throws Exception {
    // Prepare
    DefaultTreeModel treeModel = new DefaultTreeModel(new LoadingNode());
    Tree tree = new Tree(treeModel);

    ColumnTreeBuilder builder = new ColumnTreeBuilder(tree)
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("A")
                   .setPreferredWidth(30)
                   .setHeaderAlignment(SwingConstants.LEADING)
                   .setRenderer(new MyEmptyRenderer()))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("B")
                   .setPreferredWidth(60)
                   .setHeaderAlignment(SwingConstants.TRAILING)
                   .setRenderer(new MyEmptyRenderer()));

    JScrollPane columnTreePane = (JScrollPane)builder.build();
    columnTreePane.setPreferredSize(new Dimension(100, 100));
    assertThat(tree.getHeight()).isEqualTo(0);

    // Act: Simulate layout
    columnTreePane.setSize(new Dimension(100, 100));
    columnTreePane.doLayout();
    columnTreePane.getViewport().doLayout();
    columnTreePane.getViewport().getView().doLayout();

    // Assert: Check the tree height has been extended to the whole viewport
    assertThat(tree.getHeight()).isGreaterThan(0);
    assertThat(tree.getHeight()).isEqualTo(columnTreePane.getViewport().getHeight());
  }

  private class MyEmptyRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
    }
  }
}