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
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.*;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractBaseTreeBuilder.MatchingNodeCollector;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsdNode;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsModuleDependency;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.PopupHandler;
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
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

import static com.android.tools.idea.gradle.structure.configurables.android.dependencies.UiUtil.setUp;
import static com.intellij.icons.AllIcons.Actions.Collapseall;
import static com.intellij.icons.AllIcons.Actions.Expandall;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.util.ui.tree.TreeUtil.ensureSelection;
import static java.awt.event.MouseEvent.MOUSE_PRESSED;

class DeclaredDependenciesPanel extends AbstractDeclaredDependenciesPanel {
  @NotNull private final PsContext myContext;

  @NotNull private final Tree myTree;
  @NotNull private final DeclaredDependenciesTreeBuilder myTreeBuilder;
  @NotNull private final TreeSelectionListener myTreeSelectionListener;
  @NotNull private final NodeHyperlinkSupport<ModuleDependencyNode> myHyperlinkSupport;

  @NotNull private final EventDispatcher<SelectionListener> myEventDispatcher = EventDispatcher.create(SelectionListener.class);

  private boolean myIgnoreTreeSelectionEvents;

  DeclaredDependenciesPanel(@NotNull PsProject project, @NotNull PsContext context) {
    super("All Dependencies", context, project, null);
    myContext = context;

    DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    myTree = new Tree(treeModel) {
      @Override
      protected void processMouseEvent(MouseEvent e) {
        int id = e.getID();
        if (id == MOUSE_PRESSED) {
          ModuleDependencyNode node = myHyperlinkSupport.getIfHyperlink(e.getModifiers(), e.getX(), e.getY());
          if (node != null) {
            PsModuleDependency moduleDependency = node.getModels().get(0);
            String name = moduleDependency.getName();
            myContext.setSelectedModule(name, DeclaredDependenciesPanel.this);
            // Do not call super, to avoid selecting the 'module' node when clicking a hyperlink.
            return;
          }
        }
        super.processMouseEvent(e);
      }
    };

    getContentsPanel().add(createActionsPanel(), BorderLayout.NORTH);

    JScrollPane scrollPane = setUp(myTree);
    getContentsPanel().add(scrollPane, BorderLayout.CENTER);

    myTreeBuilder = new DeclaredDependenciesTreeBuilder(project, myTree, treeModel);

    myTreeSelectionListener = new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        final NodeSelectionDetector detector = new NodeSelectionDetector();

        if (!myIgnoreTreeSelectionEvents) {
          final AbstractDependencyNode<? extends PsAndroidDependency> selection = getSelection();
          myIgnoreTreeSelectionEvents = true;
          myTreeBuilder.updateSelection(new MatchingNodeCollector() {
            @Override
            protected void done(@NotNull List<AbstractPsdNode> matchingNodes) {
              for (AbstractPsdNode node : matchingNodes) {
                detector.add(node);
              }
              myIgnoreTreeSelectionEvents = false;

              List<AbstractDependencyNode<? extends PsAndroidDependency>> singleSelection = Collections.emptyList();
              if (selection != null) {
                singleSelection = detector.getSingleTypeSelection();
              }
              notifySelectionChanged(singleSelection);
            }
          });
        }
      }
    };
    myTree.addTreeSelectionListener(myTreeSelectionListener);
    myTree.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        popupInvoked(x, y);
      }
    });

    myTreeBuilder.getInitialized().doWhenDone(new Runnable() {
      @Override
      public void run() {
        doEnsureSelection();
      }
    });

    myHyperlinkSupport = new NodeHyperlinkSupport<ModuleDependencyNode>(myTree, ModuleDependencyNode.class);
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

  private void popupInvoked(int x, int y) {
    ModuleDependencyNode node = myHyperlinkSupport.getNodeForLocation(x, y);

    if (node != null) {
      PsModuleDependency moduleDependency = node.getModels().get(0);

      String name = moduleDependency.getName();
      DefaultActionGroup group = new DefaultActionGroup();
      group.add(new GoToModuleAction(name, getContext(), myTree));

      ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu("", group);
      popupMenu.getComponent().show(myTree, x, y);
    }
  }


  void add(@NotNull SelectionListener listener) {
    myEventDispatcher.addListener(listener);
  }

  @Override
  @NotNull
  protected List<AnAction> getExtraToolbarActions() {
    List<AnAction> actions = Lists.newArrayList();

    actions.add(new AbstractBaseExpandAllAction(myTree, Expandall) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myTree.requestFocusInWindow();
        myTreeBuilder.expandAllNodes();
        doEnsureSelection();
      }
    });

    actions.add(new AbstractBaseCollapseAllAction(myTree, Collapseall) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myTreeBuilder.clearSelection();
        notifySelectionChanged(Collections.<AbstractDependencyNode<? extends PsAndroidDependency>>emptyList());

        myTree.requestFocusInWindow();
        myTreeBuilder.collapseAllNodes();
        doEnsureSelection();
      }
    });

    return actions;
  }

  private void doEnsureSelection() {
    ensureSelection(myTree);
  }

  @Override
  public void dispose() {
    Disposer.dispose(myTreeBuilder);
    Disposer.dispose(myHyperlinkSupport);
  }

  public interface SelectionListener extends EventListener {
    void dependencySelected(@NotNull List<AbstractDependencyNode<? extends PsAndroidDependency>> selectedNodes);
  }

  private static class NodeSelectionDetector {
    private final Map<String, List<AbstractDependencyNode<? extends PsAndroidDependency>>> mySelection = Maps.newHashMap();

    void add(@NotNull AbstractPsdNode node) {
      String key = null;
      if (node instanceof ModuleDependencyNode) {
        key = ((ModuleDependencyNode)node).getModels().get(0).getGradlePath();
      }
      if (node instanceof LibraryDependencyNode) {
        key = ((LibraryDependencyNode)node).getModels().get(0).getResolvedSpec().toString();
      }
      if (key != null) {
        List<AbstractDependencyNode<? extends PsAndroidDependency>> nodes = mySelection.get(key);
        if (nodes == null) {
          nodes = Lists.newArrayList();
          mySelection.put(key, nodes);
        }
        nodes.add(((AbstractDependencyNode<? extends PsAndroidDependency>)node));
      }
    }

    @NotNull
    List<AbstractDependencyNode<? extends PsAndroidDependency>> getSingleTypeSelection() {
      Set<String> keys = mySelection.keySet();
      if (keys.size() == 1) {
        // Only notify selection if all the selected nodes refer to the same dependency.
        return mySelection.get(getFirstItem(keys));
      }
      return Collections.emptyList();
    }
  }
}
