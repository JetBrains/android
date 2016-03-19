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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.project;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.AbstractDeclaredDependenciesPanel;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.project.treeview.DeclaredDependenciesTreeBuilder;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractBaseTreeBuilder.MatchingNodeCollector;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.LibraryDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsdNode;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.EventListener;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.gradle.structure.configurables.android.dependencies.UiUtil.setUp;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

class DeclaredDependenciesPanel extends AbstractDeclaredDependenciesPanel {
  @NotNull private final Tree myTree;
  @NotNull private final DeclaredDependenciesTreeBuilder myTreeBuilder;
  @NotNull private final TreeSelectionListener myTreeSelectionListener;

  @NotNull private final EventDispatcher<SelectionListener> myEventDispatcher = EventDispatcher.create(SelectionListener.class);

  DeclaredDependenciesPanel(@NotNull PsProject project, @NotNull PsContext context) {
    super("All Dependencies", context, project, null);

    DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    myTree = new Tree(treeModel);

    JScrollPane scrollPane = setUp(myTree);
    getContentsPanel().add(scrollPane, BorderLayout.CENTER);

    myTreeBuilder = new DeclaredDependenciesTreeBuilder(project, myTree, treeModel);

    myTreeSelectionListener = new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        final List<AbstractDependencyNode<? extends PsAndroidDependency>> selectedNodes = Lists.newArrayList();

        myTreeBuilder.updateSelection(new MatchingNodeCollector() {
          @Override
          protected void done(@NotNull List<AbstractPsdNode> matchingNodes) {
            for (AbstractPsdNode node : matchingNodes) {
              if (node instanceof LibraryDependencyNode) {
                selectedNodes.add(((LibraryDependencyNode)node));
              }
            }
          }
        });

        if (getSelection() != null) {
          notifySelectionChanged(selectedNodes);
        }
      }
    };
    myTree.addTreeSelectionListener(myTreeSelectionListener);
  }

  @Nullable
  private AbstractDependencyNode<? extends PsAndroidDependency> getSelection() {
    Set<AbstractDependencyNode> selectedElements = myTreeBuilder.getSelectedElements(AbstractDependencyNode.class);
    if (selectedElements.size() == 1) {
      //noinspection unchecked
      return getFirstItem(selectedElements);
    }
    return null;
  }

  private void notifySelectionChanged(@NotNull List<AbstractDependencyNode<? extends PsAndroidDependency>> selected) {
    myEventDispatcher.getMulticaster().dependencySelected(selected);
  }

  void add(@NotNull SelectionListener listener) {
    myEventDispatcher.addListener(listener);
  }

  @Override
  public void dispose() {
    Disposer.dispose(myTreeBuilder);
  }

  public interface SelectionListener extends EventListener {
    void dependencySelected(@NotNull List<AbstractDependencyNode<? extends PsAndroidDependency>> selectedNodes);
  }
}
