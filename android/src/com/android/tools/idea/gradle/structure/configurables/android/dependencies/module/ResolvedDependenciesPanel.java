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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.module;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.module.treeview.DependencySelection;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.module.treeview.ResolvedDependenciesTreeBuilder;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.ModuleDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings;
import com.android.tools.idea.gradle.structure.configurables.ui.ToolWindowPanel;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsdNode;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.PsModuleDependency;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.Convertor;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EventListener;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.gradle.structure.configurables.android.dependencies.UiUtil.setUp;
import static com.intellij.openapi.wm.impl.content.ToolWindowContentUi.POPUP_PLACE;
import static com.intellij.ui.SimpleTextAttributes.LINK_ATTRIBUTES;
import static com.intellij.util.BitUtil.isSet;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static java.awt.Cursor.*;
import static java.awt.Event.CTRL_MASK;
import static java.awt.Event.META_MASK;
import static java.awt.event.KeyEvent.*;
import static java.awt.event.MouseEvent.MOUSE_PRESSED;
import static javax.swing.SwingUtilities.convertPointFromScreen;

class ResolvedDependenciesPanel extends ToolWindowPanel implements DependencySelection {
  @NotNull private final Tree myTree;
  @NotNull private final ResolvedDependenciesTreeBuilder myTreeBuilder;
  @NotNull private final PsContext myContext;
  @NotNull private final TreeSelectionListener myTreeSelectionListener;

  @NotNull private final EventDispatcher<SelectionListener> myEventDispatcher = EventDispatcher.create(SelectionListener.class);

  private ModuleDependencyNode myHoveredNode;
  private KeyEventDispatcher myKeyEventDispatcher;

  ResolvedDependenciesPanel(@NotNull PsAndroidModule module,
                            @NotNull PsContext context,
                            @NotNull DependencySelection dependencySelection) {
    super("Resolved Dependencies", AndroidIcons.Variant, ToolWindowAnchor.RIGHT);
    myContext = context;
    setHeaderActions();

    DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    myTree = new Tree(treeModel) {
      @Override
      protected void processMouseEvent(MouseEvent e) {
        int id = e.getID();
        if (id == MOUSE_PRESSED) {
          ModuleDependencyNode node = getIfHyperlink(e.getModifiers(), e.getX(), e.getY());
          if (node != null) {
            PsModuleDependency moduleDependency = node.getModels().get(0);
            String name = moduleDependency.getName();
            myContext.setSelectedModule(name, ResolvedDependenciesPanel.this);
            // Do not call super, to avoid selecting the 'module' node when clicking a hyperlink.
            return;
          }
        }
        super.processMouseEvent(e);
      }
    };

    getHeader().setPreferredFocusedComponent(myTree);

    myTreeBuilder = new ResolvedDependenciesTreeBuilder(module, myTree, treeModel, dependencySelection, this);

    JScrollPane scrollPane = setUp(myTree);
    add(scrollPane, BorderLayout.CENTER);

    myTreeSelectionListener = new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        if (myIgnoreTreeSelectionEvents) {
          return;
        }

        myTreeBuilder.updateSelection();
        PsAndroidDependency selected = getSelection();
        if (selected == null) {
          AbstractPsdNode selectedNode = getSelectionIfSingle();
          if (selectedNode != null && !(selectedNode instanceof AbstractDependencyNode)) {
            // A non-dependency node was selected (e.g. a variant/artifact node)
            notifySelectionChanged(null);
          }
        }
        else {
          notifySelectionChanged(selected);
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

    TreeUIHelper.getInstance().installTreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      @Override
      public String convert(TreePath path) {
        Object last = path.getLastPathComponent();
        return last != null ? last.toString() : "";
      }
    }, true);

    addHyperlinkBehaviorToModuleNodes();
  }

  private void notifySelectionChanged(@Nullable PsAndroidDependency selected) {
    myEventDispatcher.getMulticaster().dependencySelected(selected);
  }

  private void setHeaderActions() {
    final DefaultActionGroup settingsGroup = new DefaultActionGroup();

    settingsGroup.add(new ToggleAction("Group Similar") {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return PsUISettings.getInstance().VARIANTS_DEPENDENCIES_GROUP_VARIANTS;
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        PsUISettings settings = PsUISettings.getInstance();
        if (settings.VARIANTS_DEPENDENCIES_GROUP_VARIANTS != state) {
          settings.VARIANTS_DEPENDENCIES_GROUP_VARIANTS = state;
          settings.fireUISettingsChanged();
        }
      }
    });

    List<AnAction> additionalActions = Lists.newArrayList();

    additionalActions.add(new DumbAwareAction("Expand All", "", AllIcons.General.ExpandAll) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myIgnoreTreeSelectionEvents = true;
        myTree.requestFocusInWindow();
        myTreeBuilder.expandAllNodes();
        myIgnoreTreeSelectionEvents = false;
      }
    });

    additionalActions.add(new DumbAwareAction("Collapse All", "", AllIcons.General.CollapseAll) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        collapseAllNodes();
      }
    });

    additionalActions.add(Separator.getInstance());

    additionalActions.add(new DumbAwareAction("", "", AllIcons.General.Gear) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        InputEvent inputEvent = e.getInputEvent();
        ActionManagerImpl actionManager = (ActionManagerImpl)ActionManager.getInstance();
        ActionPopupMenu popupMenu =
          actionManager.createActionPopupMenu(POPUP_PLACE, settingsGroup, new MenuItemPresentationFactory(true));
        int x = 0;
        int y = 0;
        if (inputEvent instanceof MouseEvent) {
          x = ((MouseEvent)inputEvent).getX();
          y = ((MouseEvent)inputEvent).getY();
        }
        popupMenu.getComponent().show(inputEvent.getComponent(), x, y);
      }
    });

    getHeader().setAdditionalActions(additionalActions);
  }

  private void collapseAllNodes() {
    myTree.requestFocusInWindow();

    myIgnoreTreeSelectionEvents = true;
    myTreeBuilder.collapseAllNodes();
    myIgnoreTreeSelectionEvents = false;
  }

  private void popupInvoked(int x, int y) {
    ModuleDependencyNode node = getNodeForLocation(ModuleDependencyNode.class, x, y);

    if (node != null) {
      PsModuleDependency moduleDependency = node.getModels().get(0);

      final String name = moduleDependency.getName();
      DefaultActionGroup group = new DefaultActionGroup();

      group.add(new DumbAwareAction(String.format("Display dependencies of module '%1$s'", name)) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          myContext.setSelectedModule(name, ResolvedDependenciesPanel.this);
        }
      });

      ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu("", group);
      popupMenu.getComponent().show(myTree, x, y);
    }
  }

  private void addHyperlinkBehaviorToModuleNodes() {
    myTree.setCellRenderer(new NodeRenderer() {
      @Override
      protected SimpleTextAttributes getSimpleTextAttributes(PresentableNodeDescriptor node, Color color) {
        if (myHoveredNode != null && myHoveredNode == node) {
          return LINK_ATTRIBUTES;
        }
        return super.getSimpleTextAttributes(node, color);
      }
    });

    MouseAdapter mouseListener = new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        Cursor cursor = getDefaultCursor();
        ModuleDependencyNode node = getIfHyperlink(e.getModifiers(), e.getX(), e.getY());
        if (node != null) {
          cursor = getPredefinedCursor(HAND_CURSOR);
        }
        setHoveredNode(node);
        myTree.setCursor(cursor);
      }
    };
    myTree.addMouseMotionListener(mouseListener);

    // Make the cursor change to 'hand' if the mouse pointer is over a 'module' node and the user presses Ctrl or Cmd.
    myKeyEventDispatcher = new KeyEventDispatcher() {
      @Override
      public boolean dispatchKeyEvent(KeyEvent e) {
        ModuleDependencyNode node = null;
        if (e.getID() == KEY_PRESSED) {
          Cursor cursor = getDefaultCursor();
          if (isControlOrMetaKey(e)) {
            node = getNodeUnderMousePointer(ModuleDependencyNode.class);
            if (node != null) {
              cursor = getPredefinedCursor(HAND_CURSOR);
            }
          }
          setHoveredNode(node);
          myTree.setCursor(cursor);
        }
        else if (e.getID() == KEY_RELEASED) {
          if (isControlOrMetaKey(e)) {
            setHoveredNode(null);
          }
          myTree.setCursor(getDefaultCursor());
        }
        return false;
      }

      private boolean isControlOrMetaKey(@NotNull KeyEvent e) {
        return e.getKeyCode() == VK_META || e.getKeyCode() == VK_CONTROL;
      }
    };

    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(myKeyEventDispatcher);
  }

  private void setHoveredNode(@Nullable ModuleDependencyNode node) {
    myHoveredNode = node;
    if (myHoveredNode != null) {
      // Force color change of the node.
      myHoveredNode.getPresentation().clearText();
    }
    myTree.repaint();
  }

  @Nullable
  private ModuleDependencyNode getIfHyperlink(int modifiers, int x, int y) {
    if (isSet(modifiers, CTRL_MASK) || isSet(modifiers, META_MASK)) {
      return getNodeForLocation(ModuleDependencyNode.class, x, y);
    }
    return null;
  }

  private <T extends AbstractPsdNode> T getNodeUnderMousePointer(@NotNull Class<T> nodeType) {
    PointerInfo pointerInfo = MouseInfo.getPointerInfo();
    if (pointerInfo != null) {
      Point location = pointerInfo.getLocation();
      convertPointFromScreen(location, myTree);
      return getNodeForLocation(nodeType, location.x, location.y);
    }
    return null;
  }

  @Nullable
  private <T extends AbstractPsdNode> T getNodeForLocation(@NotNull Class<T> nodeType, int x, int y) {
    Object userObject = null;

    TreePath path = myTree.getPathForLocation(x, y);
    if (path != null) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      if (node != null) {
        userObject = node.getUserObject();
      }
    }

    return nodeType.isInstance(userObject) ? nodeType.cast(userObject) : null;
  }

  private boolean myIgnoreTreeSelectionEvents;

  @Override
  public void setSelection(@Nullable PsAndroidDependency selection) {
    if (selection == null) {
      myTreeBuilder.clearSelection();
    }
    else {
      myIgnoreTreeSelectionEvents = true;
      myTreeBuilder.selectMatchingNodes(selection, true);
      myIgnoreTreeSelectionEvents = false;
    }
  }

  void add(@NotNull SelectionListener listener) {
    myEventDispatcher.addListener(listener);
  }

  @Override
  @Nullable
  public PsAndroidDependency getSelection() {
    AbstractPsdNode selection = getSelectionIfSingle();
    if (selection instanceof AbstractDependencyNode) {
      AbstractDependencyNode node = (AbstractDependencyNode)selection;
      List<?> models = node.getModels();
      if (!models.isEmpty()) {
        return (PsAndroidDependency)models.get(0);
      }
    }
    return null;
  }
  
  @Nullable
  private AbstractPsdNode getSelectionIfSingle() {
    Set<AbstractPsdNode> selection = myTreeBuilder.getSelectedElements(AbstractPsdNode.class);
    if (selection.size() == 1) {
      return getFirstItem(selection);
    }
    return null;
  }

  @Override
  public void dispose() {
    super.dispose();
    Disposer.dispose(myTreeBuilder);
    if (myKeyEventDispatcher != null) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(myKeyEventDispatcher);
    }
  }

  public interface SelectionListener extends EventListener {
    void dependencySelected(@Nullable PsAndroidDependency dependency);
  }
}
