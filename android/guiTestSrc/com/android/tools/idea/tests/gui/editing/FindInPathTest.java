/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.editing;

import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.FindDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.usages.impl.GroupNode;
import com.intellij.usages.impl.UsageViewImpl;
import com.intellij.usages.impl.UsageViewTreeModelBuilder.TargetsRootNode;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.edt.GuiQuery;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import javax.swing.tree.TreeModel;

import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@BelongsToTestGroups({PROJECT_SUPPORT})
public class FindInPathTest extends GuiTestCase {

  @Test
  @IdeGuiTest
  public void testResultsOnlyInGeneratedCode() throws Exception {
    IdeFrameFixture project = importSimpleApplication();

    Tree usageTree = triggerFindInPath(project, "ActionBarDivider");
    TreeModel treeModel = usageTree.getModel();

    assertGroupCount(treeModel, 1);
    assertGroup(treeModel, 1, "UsagesInGeneratedCode");
  }

  @Test
  @IdeGuiTest
  public void testResultsInBothProdAndGeneratedCode() throws Exception {
    IdeFrameFixture project = importSimpleApplication();

    Tree usageTree = triggerFindInPath(project, "DarkActionBar");
    TreeModel treeModel = usageTree.getModel();

    assertGroupCount(treeModel, 2);
    assertGroup(treeModel, 1, "UsagesInGeneratedCode");
    assertGroup(treeModel, 2, "CodeUsages");
  }

  @NotNull
  private Tree triggerFindInPath(@NotNull IdeFrameFixture project, String text) {
    project.invokeMenuPath("Edit", "Find", "Find in Path...");
    FindDialogFixture findDialog = FindDialogFixture.find(myRobot);
    findDialog.setTextToFind(text);
    findDialog.clickFind();
    project.waitForBackgroundTasksToFinish();

    return myRobot.finder().find(new GenericTypeMatcher<Tree>(Tree.class) {
      @Override
      protected boolean isMatching(@NotNull Tree tree) {
        // The usage tree is created as anonymous class inside UsageViewImpl
        return tree.getClass().getName().startsWith(UsageViewImpl.class.getName());
      }
    });
  }

  private static void assertGroupCount(@NotNull final TreeModel treeModel, final int groupCount) {
    //noinspection ConstantConditions
    assertTrue(groupCount == execute(new GuiQuery<Integer>() {
      @Override
      protected Integer executeInEDT() throws Throwable {
        return treeModel.getChildCount(treeModel.getRoot()) - 1;
      }
    }).intValue());

    // The first node of tree should always be "Targets" node
    assertEquals("Targets", execute(new GuiQuery<Object>() {
      @Override
      protected Object executeInEDT() throws Throwable {
        TargetsRootNode node = (TargetsRootNode)treeModel.getChild(treeModel.getRoot(), 0);
        return node.getUserObject();
      }
    }));
  }

  private static void assertGroup(@NotNull final TreeModel treeModel, final int nodeIndex, @NotNull String groupText) {
    assertEquals(groupText, execute(new GuiQuery<String>() {
      @Override
      protected String executeInEDT() throws Throwable {
        GroupNode groupNode = (GroupNode)treeModel.getChild(treeModel.getRoot(), nodeIndex);
        return groupNode.getGroup().toString();
      }
    }));
  }
}
