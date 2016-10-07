/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.util.ui;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.awt.*;

import static com.intellij.icons.AllIcons.General.CollapseAll;
import static com.intellij.icons.AllIcons.General.ExpandAll;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;

/**
 * Panel that looks like an IDEA tool window. It has header with title and, optionally, action buttons.
 */
public class ToolWindowAlikePanel extends JPanel {
  private final Header myHeader;

  @NotNull
  public static ToolWindowAlikePanel createTreePanel(@NotNull String title, @NotNull JTree tree) {
    ToolWindowAlikePanel panel = new ToolWindowAlikePanel(title, createScrollPane(tree));

    Object root = tree.getModel().getRoot();
    if (root instanceof TreeNode && ((TreeNode)root).getChildCount() > 0) {
      TreeExpander expander = new DefaultTreeExpander(tree);
      CommonActionsManager actions = CommonActionsManager.getInstance();

      AnAction expandAllAction = actions.createExpandAllAction(expander, tree);
      expandAllAction.getTemplatePresentation().setIcon(ExpandAll);

      AnAction collapseAllAction = actions.createCollapseAllAction(expander, tree);
      collapseAllAction.getTemplatePresentation().setIcon(CollapseAll);

      panel.setAdditionalTitleActions(expandAllAction, collapseAllAction);
    }

    return panel;
  }

  public ToolWindowAlikePanel(@NotNull String title, @NotNull JComponent contents) {
    super(new BorderLayout());
    myHeader = new Header(title);
    add(myHeader, BorderLayout.NORTH);
    add(contents, BorderLayout.CENTER);
  }

  public void setAdditionalTitleActions(@NotNull AnAction... actions) {
    myHeader.setAdditionalActions(actions);
  }
}
