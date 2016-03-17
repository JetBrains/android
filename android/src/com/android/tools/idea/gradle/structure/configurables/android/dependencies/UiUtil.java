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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies;

import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreeSelectionModel;

import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static javax.swing.tree.TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION;

public final class UiUtil {
  private UiUtil() {
  }

  @NotNull
  public static JBSplitter createMainVerticalSplitter() {
    return new OnePixelSplitter(false, "psd.dependencies.main.vertical.splitter.proportion", .75f);
  }

  public static JScrollPane setUp(@NotNull Tree tree) {
    tree.setExpandsSelectedPaths(true);
    tree.setRootVisible(false);
    TreeSelectionModel selectionModel = tree.getSelectionModel();
    selectionModel.setSelectionMode(DISCONTIGUOUS_TREE_SELECTION);

    JScrollPane scrollPane = createScrollPane(tree);
    scrollPane.setBorder(IdeBorderFactory.createEmptyBorder());
    return scrollPane;
  }
}
