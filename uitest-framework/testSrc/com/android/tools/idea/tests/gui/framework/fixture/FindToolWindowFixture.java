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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.annotations.Nullable;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.OccurenceNavigatorSupport;
import com.intellij.ui.content.Content;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.usageView.UsageViewManager;
import com.intellij.usages.impl.GroupNode;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static org.fest.reflect.core.Reflection.field;
import static org.junit.Assert.assertNotNull;

public class FindToolWindowFixture {

  public static class ContentFixture {
    @NotNull private final Content myContent;

    ContentFixture(@NotNull IdeFrameFixture parent) {
      UsageViewManager usageViewManager = UsageViewManager.getInstance(parent.getProject());
      myContent = usageViewManager.getSelectedContent();
      assertNotNull(myContent);
    }

    public ImmutableList<String> getUsageGroupNames() {
      return GuiActionRunner.execute(new GuiQuery<ImmutableList<String>>() {
        @Override
        protected ImmutableList<String> executeInEDT() {
          ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
          GroupNode rootNode = (GroupNode)getContentsTree().getModel().getRoot();
          for (GroupNode subGroup : rootNode.getSubGroups()) {
            listBuilder.add(subGroup.getGroup().getText(null));
          }
          return listBuilder.build();
        }
      });
    }

    @NotNull
    private Tree getContentsTree() {
      JComponent component = myContent.getComponent();
      OccurenceNavigatorSupport navigatorSupport = field("mySupport").ofType(OccurenceNavigatorSupport.class).in(component).get();
      assertNotNull(navigatorSupport);
      JTree tree = field("myTree").ofType(JTree.class).in(navigatorSupport).get();
      assertNotNull(tree);
      return (Tree)tree;
    }
  }
}
