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

import com.android.tools.idea.editors.layoutInspector.ui.RollOverTree;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.google.common.collect.ImmutableList;
import org.fest.swing.cell.JTreeCellReader;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JTreeFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class LayoutInspectorFixture {

  private final Robot myRobot;

  public LayoutInspectorFixture(@NotNull Robot robot) {
    myRobot = robot;
  }

  @NotNull
  public ImmutableList<String> getLayoutElements() {
    ImmutableList.Builder<String> builder = new ImmutableList.Builder<>();
    JTree tree = GuiTests.waitUntilShowing(myRobot, Matchers.byType(RollOverTree.class));
    JTreeFixture treeFixture = new JTreeFixture(myRobot, tree);
    treeFixture.replaceCellReader(TREE_NODE_CELL_READER);
    for (int i = 0; i < tree.getRowCount(); i++) {
      String element = treeFixture.valueAt(i);
      if (element != null) {
        builder.add(element.substring(0, element.indexOf("@")).trim());
      }
    }
    return builder.build();
  }

  private static final JTreeCellReader TREE_NODE_CELL_READER = (jTree, modelValue) -> {
    if (modelValue != null) {
      return modelValue.toString();
    }
    return null;
  };
}
