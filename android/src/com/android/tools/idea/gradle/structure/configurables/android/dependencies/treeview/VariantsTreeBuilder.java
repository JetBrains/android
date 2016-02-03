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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview;

import com.android.tools.idea.gradle.structure.configurables.android.treeview.AbstractPsdNode;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidModuleModel;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.IndexComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;

public class VariantsTreeBuilder extends AbstractTreeBuilder {
  public VariantsTreeBuilder(@NotNull PsdAndroidModuleModel moduleModel, @NotNull JTree tree, @NotNull DefaultTreeModel treeModel) {
    super(tree, treeModel, new VariantsTreeStructure(moduleModel), IndexComparator.INSTANCE);
  }

  @Override
  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    if (nodeDescriptor instanceof AbstractPsdNode) {
      return ((AbstractPsdNode)nodeDescriptor).isAutoExpandNode();
    }
    return super.isAutoExpandNode(nodeDescriptor);
  }
}
