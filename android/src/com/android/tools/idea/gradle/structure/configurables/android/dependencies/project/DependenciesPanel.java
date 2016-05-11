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
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.AbstractDependenciesPanel;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.details.ModuleDependencyDetails;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.details.MultipleLibraryDependenciesDetails;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.*;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractPsNodeTreeBuilder.MatchingNodeCollector;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.graph.DependenciesTreeBuilder;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.graph.DependenciesTreeRootNode;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.graph.DependenciesTreeRootNode.DependencyCollectorFunction;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.graph.DependenciesTreeStructure;
import com.android.tools.idea.gradle.structure.configurables.issues.IssuesRenderer;
import com.android.tools.idea.gradle.structure.configurables.issues.IssuesViewer;
import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeEventDispatcher;
import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeListener;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractBaseCollapseAllAction;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractBaseExpandAllAction;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.NodeHyperlinkSupport;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsIssue;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsProject;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.tools.idea.gradle.structure.configurables.ui.UiUtil.setUp;
import static com.intellij.icons.AllIcons.Actions.Collapseall;
import static com.intellij.icons.AllIcons.Actions.Expandall;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.util.ui.tree.TreeUtil.ensureSelection;
import static java.awt.event.MouseEvent.MOUSE_PRESSED;

class DependenciesPanel extends AbstractDependenciesPanel {
  @NotNull private final PsContext myContext;

  @NotNull private final Tree myTree;
  @NotNull private final DependenciesTreeBuilder myTreeBuilder;
  @NotNull private final NodeHyperlinkSupport<ModuleDependencyNode> myHyperlinkSupport;

  @NotNull private final SelectionChangeEventDispatcher<List<AbstractDependencyNode<? extends PsAndroidDependency>>> myEventDispatcher =
    new SelectionChangeEventDispatcher<>();

  private boolean myIgnoreTreeSelectionEvents;

  DependenciesPanel(@NotNull PsModule fakeModule, @NotNull PsContext context) {
    super("All Dependencies", context, null);
    myContext = context;

    initializeDependencyDetails();

    setIssuesViewer(new IssuesViewer(myContext, new IssuesRenderer()));

    DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    myTree = new Tree(treeModel) {
      @Override
      protected void processMouseEvent(MouseEvent e) {
        int id = e.getID();
        if (id == MOUSE_PRESSED) {
          ModuleDependencyNode node = myHyperlinkSupport.getIfHyperlink(e);
          if (node != null) {
            PsModuleDependency moduleDependency = node.getFirstModel();
            String name = moduleDependency.getName();
            myContext.setSelectedModule(name, DependenciesPanel.this);
            // Do not call super, to avoid selecting the 'module' node when clicking a hyperlink.
            return;
          }
        }
        super.processMouseEvent(e);
      }
    };

    getContentsPanel().add(createActionsPanel(), BorderLayout.NORTH);

    DependenciesTreeStructure treeStructure = new DependenciesTreeStructure(createRootNode());
    myTreeBuilder = new DependenciesTreeBuilder(myTree, treeModel, treeStructure);

    JScrollPane scrollPane = setUp(myTreeBuilder);
    getContentsPanel().add(scrollPane, BorderLayout.CENTER);

    TreeSelectionListener treeSelectionListener = e -> {
      if (!myIgnoreTreeSelectionEvents) {
        List<AbstractDependencyNode<? extends PsAndroidDependency>> selection = getMatchingSelection();
        PsAndroidDependency selected = !selection.isEmpty() ? selection.get(0).getFirstModel() : null;

        if (selected == null) {
          notifySelectionChanged(Collections.emptyList());
        }
        else {
          NodeSelectionDetector detector = new NodeSelectionDetector();
          myTreeBuilder.collectNodesMatchingCurrentSelection(selected, new MatchingNodeCollector() {
            @Override
            protected void done(@NotNull List<AbstractPsModelNode> matchingNodes) {
              matchingNodes.forEach(detector::add);

              List<AbstractDependencyNode<? extends PsAndroidDependency>> singleSelection = Collections.emptyList();
              if (!selection.isEmpty()) {
                singleSelection = detector.getSingleTypeSelection();
              }
              notifySelectionChanged(singleSelection);
            }
          });
        }

        updateDetails(selected);
        updateIssues(selection);
      }
      myIgnoreTreeSelectionEvents = false;
    };
    myTree.addTreeSelectionListener(treeSelectionListener);
    myTree.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        popupInvoked(x, y);
      }
    });

    myTreeBuilder.getInitialized().doWhenDone(this::doEnsureSelection);

    myHyperlinkSupport = new NodeHyperlinkSupport<>(myTree, ModuleDependencyNode.class, myContext, true);

    PsModule.DependenciesChangeListener dependenciesChangeListener = event -> {
      fakeModule.setModified(true);
      if (event instanceof PsModule.LibraryDependencyAddedEvent) {
        PsArtifactDependencySpec spec = ((PsModule.LibraryDependencyAddedEvent)event).getSpec();
        myTreeBuilder.reset(() -> {
          LibraryDependencyNode found = myTreeBuilder.find(spec);
          if (found != null) {
            myTreeBuilder.select(found);
          }
        });
      }
      else if (event != null) {
        myTreeBuilder.reset(null);
      }
    };
    myContext.getProject().forEachModule(module -> module.add(dependenciesChangeListener, this));
  }

  @NotNull
  private DependenciesTreeRootNode<PsProject> createRootNode() {
    return new DependenciesTreeRootNode<>(myContext.getProject(), new DependencyCollectorFunction<PsProject>() {
      @Override
      public DependenciesTreeRootNode.DependencyCollector apply(PsProject project) {
        DependenciesTreeRootNode.DependencyCollector collector = new DependenciesTreeRootNode.DependencyCollector();
        project.forEachModule(module -> collectDeclaredDependencies(module, collector));
        return collector;
      }
    });
  }

  @SuppressWarnings("unchecked")
  @NotNull
  private List<AbstractDependencyNode<? extends PsAndroidDependency>> getMatchingSelection() {
    List<AbstractDependencyNode<? extends PsAndroidDependency>> selection = Lists.newArrayList();
    List<AbstractDependencyNode> matchingSelection = myTreeBuilder.getMatchingSelection(AbstractDependencyNode.class);
    for (AbstractDependencyNode node : matchingSelection) {
      selection.add(node);
    }
    return selection;
  }

  private void updateIssues(@NotNull List<AbstractDependencyNode<? extends PsAndroidDependency>> selection) {
    List<PsIssue> issues = Lists.newArrayList();
    for (AbstractDependencyNode<? extends PsAndroidDependency> node : selection) {
      for (PsAndroidDependency dependency : node.getModels()) {
        issues.addAll(myContext.getAnalyzerDaemon().getIssues().findIssues(dependency, null));
      }
    }
    displayIssues(issues);
  }

  private void notifySelectionChanged(@NotNull List<AbstractDependencyNode<? extends PsAndroidDependency>> selected) {
    myEventDispatcher.selectionChanged(selected);
  }

  private void popupInvoked(int x, int y) {
    ModuleDependencyNode node = myHyperlinkSupport.getNodeForLocation(x, y);

    if (node != null) {
      PsModuleDependency moduleDependency = node.getFirstModel();

      String name = moduleDependency.getName();
      DefaultActionGroup group = new DefaultActionGroup();
      group.add(new GoToModuleAction(name, getContext(), myTree));

      ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu("", group);
      popupMenu.getComponent().show(myTree, x, y);
    }
  }

  private void initializeDependencyDetails() {
    addDetails(new MultipleLibraryDependenciesDetails());
    addDetails(new ModuleDependencyDetails(getContext(), false));
  }

  @Override
  @NotNull
  public JComponent getPreferredFocusedComponent() {
    return myTree;
  }

  void add(@NotNull SelectionChangeListener<List<AbstractDependencyNode<? extends PsAndroidDependency>>> listener) {
    myEventDispatcher.addListener(listener, this);
  }

  @Override
  @NotNull
  protected List<AnAction> getExtraToolbarActions() {
    List<AnAction> actions = Lists.newArrayList();

    actions.add(new SelectNodesMatchingCurrentSelectionAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myIgnoreTreeSelectionEvents = true;
        super.actionPerformed(e);
      }

      @Override
      @NotNull
      protected AbstractPsNodeTreeBuilder getTreeBuilder() {
        return myTreeBuilder;
      }
    });
    actions.add(new Separator());

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
        notifySelectionChanged(Collections.emptyList());

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
  public void selectDependency(@Nullable String dependency) {
  }

  @Override
  public ActionCallback navigateTo(@Nullable Place place, boolean requestFocus) {
    // TODO implement
    return ActionCallback.DONE;
  }

  @Override
  @NotNull
  protected String getPlaceName() {
    return "dependencies.graph.project";
  }

  private static class NodeSelectionDetector {
    @NotNull private final Map<String, List<AbstractDependencyNode<? extends PsAndroidDependency>>> mySelection = Maps.newHashMap();

    void add(@NotNull AbstractPsModelNode node) {
      String key = null;
      if (node instanceof ModuleDependencyNode) {
        key = ((ModuleDependencyNode)node).getFirstModel().getGradlePath();
      }
      if (node instanceof LibraryDependencyNode) {
        key = ((LibraryDependencyNode)node).getFirstModel().getResolvedSpec().toString();
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
  }}
