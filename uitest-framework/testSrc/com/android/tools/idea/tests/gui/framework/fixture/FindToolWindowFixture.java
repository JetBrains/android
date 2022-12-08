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

import com.google.common.collect.ImmutableList;
import com.intellij.ide.OccurenceNavigatorSupport;
import com.intellij.ui.content.Content;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.usageView.UsageViewContentManager;
import com.intellij.usages.impl.GroupNode;
import org.fest.swing.edt.GuiQuery;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.google.common.base.Verify.verifyNotNull;
import static org.fest.reflect.core.Reflection.field;

public class FindToolWindowFixture {

  public static class ContentFixture {
    @NotNull private final Content myContent;

    ContentFixture(@NotNull IdeFrameFixture parent) {
      UsageViewContentManager usageViewManager = UsageViewContentManager.getInstance(parent.getProject());
      myContent = verifyNotNull(usageViewManager.getSelectedContent());
    }

    @NotNull
    public ImmutableList<String> getUsageGroupNames() {
      return GuiQuery.getNonNull(
        () -> {
          ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
          GroupNode rootNode = (GroupNode)getContentsTree().getModel().getRoot();
          for (GroupNode subGroup : rootNode.getSubGroups()) {
            listBuilder.add(subGroup.getGroup().getText(null));
          }
          return listBuilder.build();
        });
    }

    /**
     * Checks if the text is present in content tree of find tool window.
     *
     * @param text search text that needs to checked in content tree.
     */
    public boolean contains(String text) {
      return GuiQuery.getNonNull(
        () -> {
          GroupNode rootNode = (GroupNode)getContentsTree().getModel().getRoot();
          return rootNode.getSubGroups().toString().contains(text);
        }
      );
    }

    @NotNull
    private Tree getContentsTree() {
      JComponent component = myContent.getComponent();
      OccurenceNavigatorSupport navigatorSupport = field("mySupport").ofType(OccurenceNavigatorSupport.class).in(component).get();
      return (Tree)field("myTree").ofType(JTree.class).in(navigatorSupport).get();
    }
  }
}
