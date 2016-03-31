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
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.details.ModuleDependencyDetails;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.details.ProjectLibraryDependencyDetails;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.project.treeview.DeclaredDependenciesTreeBuilder;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractPsNodeTreeBuilder.MatchingNodeCollector;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.GoToModuleAction;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.LibraryDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.ModuleDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.issues.IssuesRenderer;
import com.android.tools.idea.gradle.structure.configurables.issues.IssuesViewer;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractBaseCollapseAllAction;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractBaseExpandAllAction;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.NodeHyperlinkSupport;
import com.android.tools.idea.gradle.structure.model.PsIssue;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsModuleDependency;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.navigation.Place;
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
  @NotNull private final IssuesViewer myIssuesViewer;

  @NotNull private final EventDispatcher<SelectionListener> myEventDispatcher = EventDispatcher.create(SelectionListener.class);

  private boolean myIgnoreTreeSelectionEvents;

  DeclaredDependenciesPanel(@NotNull PsContext context) {
    super("All Dependencies", context, null);
    myContext = context;

    initializeDependencyDetails();

    myIssuesViewer = new IssuesViewer(myContext, new IssuesRenderer() {
      @Override
      @NotNull
      public String render(@NotNull List<PsIssue> issues) {
        StringBuilder buffer = new StringBuilder();
        buffer.append("<html><body><ol>");

        for (PsIssue issue : issues) {
          buffer.append("<li>")
                .append(issue.getPath().toHtml()).append(": ").append(issue.getText()).append("<p>&nbsp;</p>")
                .append("</li>");
        }

        buffer.append("</ul></body></html");
        return buffer.toString();
      }
    });
    setIssuesViewer(myIssuesViewer);

    DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    myTree = new Tree(treeModel) {
      @Override
      protected void processMouseEvent(MouseEvent e) {
        int id = e.getID();
        if (id == MOUSE_PRESSED) {
          ModuleDependencyNode node = myHyperlinkSupport.getIfHyperlink(e);
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

    final JScrollPane scrollPane = setUp(myTree);
    getContentsPanel().add(scrollPane, BorderLayout.CENTER);

    myTreeBuilder = new DeclaredDependenciesTreeBuilder(myContext.getProject(), myTree, treeModel);

    myTreeSelectionListener = new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        final NodeSelectionDetector detector = new NodeSelectionDetector();

        if (!myIgnoreTreeSelectionEvents) {
          final AbstractDependencyNode<? extends PsAndroidDependency> selection = getSelection();
          myIgnoreTreeSelectionEvents = true;
          myTreeBuilder.updateSelection(new MatchingNodeCollector() {
            @Override
            protected void done(@NotNull List<AbstractPsModelNode> matchingNodes) {
              for (AbstractPsModelNode node : matchingNodes) {
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

          PsAndroidDependency selected = selection != null ? selection.getModels().get(0) : null;
          updateDetails(selected);
          updateIssues(selection);
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

    myHyperlinkSupport = new NodeHyperlinkSupport<ModuleDependencyNode>(myTree, ModuleDependencyNode.class, myContext, true);
  }

  private void updateIssues(@Nullable AbstractDependencyNode<? extends PsAndroidDependency> selection) {
    List<PsIssue> issues = Lists.newArrayList();

    if (selection != null) {
      for (PsAndroidDependency selected : selection.getModels()) {
        issues.addAll(myContext.getDaemonAnalyzer().getIssues().findIssues(selected, null));
      }
    }

    myIssuesViewer.display(issues);
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

  private void initializeDependencyDetails() {
    addDetails(new ProjectLibraryDependencyDetails());
    addDetails(new ModuleDependencyDetails(getContext(), false));
  }

  void add(@NotNull SelectionListener listener) {
    myEventDispatcher.addListener(listener, this);
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

  @Override
  public ActionCallback navigateTo(@Nullable Place place, boolean requestFocus) {
    // TODO implement
    return ActionCallback.DONE;
  }

  @Override
  public void queryPlace(@NotNull Place place) {
    // TODO implement
  }

  public interface SelectionListener extends EventListener {
    void dependencySelected(@NotNull List<AbstractDependencyNode<? extends PsAndroidDependency>> selectedNodes);
  }

  private static class NodeSelectionDetector {
    private final Map<String, List<AbstractDependencyNode<? extends PsAndroidDependency>>> mySelection = Maps.newHashMap();

    void add(@NotNull AbstractPsModelNode node) {
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
