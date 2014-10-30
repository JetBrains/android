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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.google.common.collect.Lists;
import com.intellij.openapi.externalSystem.service.task.ui.ExternalSystemNode;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.project.Project;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.UIUtil;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiTask;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GradleToolWindowFixture extends ToolWindowFixture {

  public GradleToolWindowFixture(@NotNull Project project, @NotNull Robot robot) {
    super("Gradle", project, robot);
  }

  public void runTask(final String taskName) {
    final Content content = getContent(ExternalSystemBundle.message("tool.window.title.tasks"));
    assertNotNull(content);
    final JTree tasksTree = UIUtil.findComponentOfType(content.getComponent(), JTree.class);
    assertNotNull(tasksTree);
    final TreePath treePath = findTaskPath((ExternalSystemNode)tasksTree.getModel().getRoot(), taskName);
    final Point locationOnScreen = new Point();

    GuiActionRunner.execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        // We store screen location here because it shows weird (negative) values after 'scrollPathToVisible()' is called.
        locationOnScreen.setLocation(tasksTree.getLocationOnScreen());
        tasksTree.expandPath(treePath.getParentPath());
        tasksTree.scrollPathToVisible(treePath);
      }
    });

    Rectangle bounds = tasksTree.getPathBounds(treePath);
    assertNotNull(bounds);
    Rectangle visibleRect = tasksTree.getVisibleRect();
    Point clickLocation = new Point(locationOnScreen.x + bounds.x + bounds.width / 2 - visibleRect.x,
                                    locationOnScreen.y + bounds.y + bounds.height / 2 - visibleRect.y);
    myRobot.click(clickLocation, MouseButton.LEFT_BUTTON, 2);
  }

  @NotNull
  private static TreePath findTaskPath(@NotNull ExternalSystemNode root, @NotNull String taskName) {
    List<ExternalSystemNode> path = Lists.newArrayList();
    boolean found = fillTaskPath(root, taskName, path);
    assertTrue(found);
    return new TreePath(path.toArray());
  }

  private static boolean fillTaskPath(@NotNull ExternalSystemNode node, @NotNull String taskName, @NotNull List<ExternalSystemNode> path) {
    path.add(node);
    if (taskName.equals(node.getDescriptor().getName())) {
      return true;
    }
    for (int i = 0; i < node.getChildCount(); i++) {
      boolean found = fillTaskPath(node.getChildAt(i), taskName, path);
      if (found) {
        return true;
      }
    }
    path.remove(path.size() - 1);
    return false;
  }
}
