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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.ui.Splitter;
import org.fest.swing.cell.JTreeCellReader;
import org.fest.swing.fixture.JTreeFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.nio.file.Path;

import static org.fest.reflect.core.Reflection.method;

public class ApkViewerFixture extends EditorFixture {

  private final Component myTarget;

  private ApkViewerFixture(@NotNull IdeFrameFixture ideFrameFixture, @NotNull Component target) {
    super(ideFrameFixture.robot(), ideFrameFixture);
    myTarget = target;
  }

  @NotNull
  public static ApkViewerFixture find(@NotNull IdeFrameFixture ideFrameFixture) {
    Component target = ideFrameFixture.robot().finder().findByName(ideFrameFixture.target(), "apkViwerContainer", Splitter.class, true);
    return new ApkViewerFixture(ideFrameFixture, target);
  }

  private static final JTreeCellReader TREE_NODE_CELL_READER =
    (tree, modelValue) -> method("getPath").withReturnType(Path.class).in(((DefaultMutableTreeNode)modelValue).getUserObject()).invoke()
      .getFileName().toString();

  @NotNull
  public ImmutableList<String> getApkEntries() {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    JTree tree = robot.finder().findByType((Container)myTarget, JTree.class, true);
    JTreeFixture treeFixture = new JTreeFixture(robot, tree);
    treeFixture.replaceCellReader(TREE_NODE_CELL_READER);
    for (int i = 0; i < tree.getRowCount(); i++) {
      builder.add(treeFixture.valueAt(i));
    }
    return builder.build();
  }

  public ApkViewerFixture clickApkEntry(@NotNull String entryName) {
    JTree tree = robot.finder().findByType((Container)myTarget, JTree.class, true);
    JTreeFixture treeFixture = new JTreeFixture(robot, tree);
    treeFixture.replaceCellReader(TREE_NODE_CELL_READER);
    for (int i = 0; i < tree.getRowCount(); i++) {
      if (treeFixture.valueAt(i).equals(entryName)) {
        treeFixture.clickRow(i);
      }
    }
    return this;
  }
}
