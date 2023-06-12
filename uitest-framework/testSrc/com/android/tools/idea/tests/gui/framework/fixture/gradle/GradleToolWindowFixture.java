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
package com.android.tools.idea.tests.gui.framework.fixture.gradle;

import static com.intellij.util.ui.UIUtil.findComponentOfType;
import static org.fest.swing.core.MouseButton.LEFT_BUTTON;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.tests.gui.framework.fixture.ToolWindowFixture;
import com.intellij.openapi.externalSystem.view.TaskNode;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.jetbrains.annotations.NotNull;

public class GradleToolWindowFixture extends ToolWindowFixture {
  // Name of the content tab that contains TaskTree.
  @NotNull private static final String TASK_TREE_CONTENT_NAME = "";

  public GradleToolWindowFixture(@NotNull Project project, @NotNull Robot robot) {
    super("Gradle", project, robot);
  }

  public void runTask(@NotNull final String taskName) {
    final Tree tasksTree = findComponentOfType(getContent(TASK_TREE_CONTENT_NAME).getComponent(), Tree.class);

    GuiTask.execute(() -> {
      TreeUtil.expandAll(tasksTree);
      PlatformTestUtil.waitWhileBusy(tasksTree);
    });

    Object root = tasksTree.getModel().getRoot();
    final TreePath treePath = findTaskPath((DefaultMutableTreeNode)root, taskName);

    Point clickLocation = GuiQuery.getNonNull(
      () -> {
        // We store screen location here because it shows weird (negative) values after 'scrollPathToVisible()' is called.
        Point locationOnScreen = tasksTree.getLocationOnScreen();
        tasksTree.expandPath(treePath.getParentPath());
        tasksTree.scrollPathToVisible(treePath);
        Rectangle bounds = tasksTree.getPathBounds(treePath);
        bounds.translate(0, bounds.height / 2); // Make sure we are not under the horizontal scroll bar
        tasksTree.scrollRectToVisible(bounds);
        Rectangle visibleRect = tasksTree.getVisibleRect();
        return new Point(locationOnScreen.x + bounds.x + bounds.width / 2 - visibleRect.x,
                         locationOnScreen.y + bounds.y - visibleRect.y);
      });
    myRobot.click(clickLocation, LEFT_BUTTON, 2);
  }

  @NotNull
  private static TreePath findTaskPath(@NotNull DefaultMutableTreeNode root, @NotNull String taskName) {
    List<DefaultMutableTreeNode> path = new ArrayList<>();
    boolean found = fillTaskPath(root, taskName, path);
    assertTrue("Failed to find task '" + taskName + "'", found);
    return new TreePath(path.toArray());
  }

  private static boolean fillTaskPath(@NotNull DefaultMutableTreeNode node,
                                      @NotNull String taskName,
                                      @NotNull List<DefaultMutableTreeNode> path) {
    path.add(node);

    Object userObject = node.getUserObject();
    if (userObject instanceof TaskNode) {
      TaskNode taskNode = (TaskNode)userObject;
      if (taskName.equals(taskNode.getName())) {
        return true;
      }
    }
    for (int i = 0; i < node.getChildCount(); i++) {
      boolean found = fillTaskPath((DefaultMutableTreeNode)node.getChildAt(i), taskName, path);
      if (found) {
        return true;
      }
    }
    if (!path.isEmpty()) {
      path.remove(path.size() - 1);
    }
    return false;
  }
}
