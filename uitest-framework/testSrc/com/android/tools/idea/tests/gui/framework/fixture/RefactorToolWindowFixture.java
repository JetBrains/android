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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.intellij.ui.content.Content;
import com.intellij.ui.treeStructure.Tree;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.NameMatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.tree.TreeModel;
import java.awt.Component;

public class RefactorToolWindowFixture extends ToolWindowFixture {
  public RefactorToolWindowFixture(@NotNull IdeFrameFixture ideFrame) {
    super("Find", ideFrame.getProject(), ideFrame.robot());
  }

  public boolean isRefactorListEmpty() {
    Content content = getContent("Refactoring Preview");
    JComponent comp = content.getManager().getComponent();
    myRobot.printer().printComponents(System.out, comp);
    Tree tree = (Tree) myRobot.finder().findByName(comp, "UsageViewTree");
    TreeModel model = tree.getModel();

    Object root = model.getRoot();
    if (root == null) {
      return true;
    }

    int numChildren = model.getChildCount(root);
    return numChildren == 0;
  }
}
