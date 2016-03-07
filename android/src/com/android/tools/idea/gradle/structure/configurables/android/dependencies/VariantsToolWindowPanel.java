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

import com.android.tools.idea.gradle.structure.configurables.PsdContext;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.DependencySelection;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.ModuleDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.VariantsTreeBuilder;
import com.android.tools.idea.gradle.structure.configurables.ui.PsdUISettings;
import com.android.tools.idea.gradle.structure.configurables.ui.ToolWindowPanel;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsdNode;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidDependencyModel;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidModuleModel;
import com.android.tools.idea.gradle.structure.model.android.PsdModuleDependencyModel;
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
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.Convertor;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.wm.impl.content.ToolWindowContentUi.POPUP_PLACE;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.ui.SimpleTextAttributes.LINK_ATTRIBUTES;
import static com.intellij.util.BitUtil.isSet;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static java.awt.Cursor.*;
import static java.awt.Event.CTRL_MASK;
import static java.awt.Event.META_MASK;
import static java.awt.event.KeyEvent.*;
import static java.awt.event.MouseEvent.MOUSE_PRESSED;
import static javax.swing.SwingUtilities.convertPointFromScreen;
import static javax.swing.tree.TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION;

class VariantsToolWindowPanel extends ToolWindowPanel implements DependencySelection {
  @NotNull private final Tree myTree;
  @NotNull private final VariantsTreeBuilder myTreeBuilder;
  @NotNull private final PsdContext myContext;
  @NotNull private final TreeSelectionListener myTreeSelectionListener;

  @NotNull private final List<SelectionListener> mySelectionListeners = Lists.newCopyOnWriteArrayList();

  private ModuleDependencyNode myHoveredNode;
  private KeyEventDispatcher myKeyEventDispatcher;

  VariantsToolWindowPanel(@NotNull PsdAndroidModuleModel moduleModel,
                          @NotNull PsdContext context,
                          @NotNull DependencySelection dependencySelection) {
    super("Resolved Dependencies", AndroidIcons.Variant, ToolWindowAnchor.RIGHT);
    myContext = context;
    setHeaderActions();

    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
    DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    myTree = new Tree(treeModel) {
      @Override
      protected void processMouseEvent(MouseEvent e) {
        int id = e.getID();
        if (id == MOUSE_PRESSED) {
          ModuleDependencyNode node = getIfHyperlink(e.getModifiers(), e.getX(), e.getY());
          if (node != null) {
            PsdModuleDependencyModel moduleDependencyModel = node.getModels().get(0);
            String name = moduleDependencyModel.getName();
            myContext.setSelectedModule(name, VariantsToolWindowPanel.this);
            // Do not call super, to avoid selecting the 'module' node when clicking a hyperlink.
            return;
          }
        }
        super.processMouseEvent(e);
      }
    };
    myTree.setExpandsSelectedPaths(true);
    myTree.setRootVisible(false);
    getHeader().setPreferredFocusedComponent(myTree);

    TreeSelectionModel selectionModel = myTree.getSelectionModel();
    selectionModel.setSelectionMode(DISCONTIGUOUS_TREE_SELECTION);

    myTreeBuilder = new VariantsTreeBuilder(moduleModel, myTree, treeModel, dependencySelection, this);

    JScrollPane scrollPane = createScrollPane(myTree);
    scrollPane.setBorder(IdeBorderFactory.createEmptyBorder());
    add(scrollPane, BorderLayout.CENTER);

    myTreeSelectionListener = new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        myTreeBuilder.updateSelection();
        PsdAndroidDependencyModel selected = getSelection();
        if (selected != null) {
          for (SelectionListener listener : mySelectionListeners) {
            listener.dependencyModelSelected(selected);
          }
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

    addHyperlinkBehaviorToModuleNodes();
  }

  private void setHeaderActions() {
    final DefaultActionGroup settingsGroup = new DefaultActionGroup();

    settingsGroup.add(new ToggleAction("Group Similar") {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return PsdUISettings.getInstance().VARIANTS_DEPENDENCIES_GROUP_VARIANTS;
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        PsdUISettings settings = PsdUISettings.getInstance();
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
        myTree.requestFocusInWindow();
        myTreeBuilder.expandAllNodes();
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

    // Remove selection listener because the selection changes when collapsing all nodes and the tree will try to use the previously
    // selected dependency, expanding nodes while restoring the selection.
    myTree.removeTreeSelectionListener(myTreeSelectionListener);

    myTreeBuilder.collapseAllNodes();
    myTree.addTreeSelectionListener(myTreeSelectionListener);
  }

  private void popupInvoked(int x, int y) {
    ModuleDependencyNode node = getNodeForLocation(ModuleDependencyNode.class, x, y);

    if (node != null) {
      PsdModuleDependencyModel moduleDependencyModel = node.getModels().get(0);
      final String name = moduleDependencyModel.getName();
      DefaultActionGroup group = new DefaultActionGroup();

      group.add(new DumbAwareAction(String.format("Display dependencies of module '%1$s'", name)) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          myContext.setSelectedModule(name, VariantsToolWindowPanel.this);
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

    TreeUIHelper.getInstance().installTreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      @Override
      public String convert(TreePath path) {
        Object last = path.getLastPathComponent();
        return last != null ? last.toString() : "";
      }
    }, true);

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

  @Override
  public void setSelection(@Nullable PsdAndroidDependencyModel selection) {
    myTree.removeTreeSelectionListener(myTreeSelectionListener);
    if (selection == null) {
      myTreeBuilder.clearSelection();
    }
    else {
      myTreeBuilder.setSelection(selection, true);
    }
    myTree.addTreeSelectionListener(myTreeSelectionListener);
  }

  void add(@NotNull SelectionListener listener) {
    PsdAndroidDependencyModel selected = getSelection();
    if (selected != null) {
      listener.dependencyModelSelected(selected);
    }
    mySelectionListeners.add(listener);
  }

  @Override
  @Nullable
  public PsdAndroidDependencyModel getSelection() {
    Set<AbstractDependencyNode> selection = myTreeBuilder.getSelectedElements(AbstractDependencyNode.class);
    if (selection.size() == 1) {
      AbstractDependencyNode node = getFirstItem(selection);
      if (node != null) {
        List<?> models = node.getModels();
        if (!models.isEmpty()) {
          return (PsdAndroidDependencyModel)models.get(0);
        }
      }
    }
    return null;
  }

  @Override
  public void dispose() {
    super.dispose();
    Disposer.dispose(myTreeBuilder);
    mySelectionListeners.clear();
    if (myKeyEventDispatcher != null) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(myKeyEventDispatcher);
    }
  }

  public interface SelectionListener {
    void dependencyModelSelected(@Nullable PsdAndroidDependencyModel model);
  }
}
