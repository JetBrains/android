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
package com.android.tools.idea.uibuilder.palette;

import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationListener;
import com.intellij.designer.LightToolWindowContent;
import com.intellij.icons.AllIcons;
import com.intellij.ide.dnd.DnDAction;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDSource;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public class NlPalettePanel extends JPanel implements LightToolWindowContent, ConfigurationListener {
  private static final Insets INSETS = new Insets(0, 6, 0, 6);
  private static final double PREVIEW_SCALE = 0.5;

  @NotNull private final DnDAwareTree myTree;
  @NotNull private final NlPaletteModel myModel;
  @NotNull private final IconPreviewFactory myIconFactory;
  @NotNull private Mode myMode;
  @Nullable private ScalableDesignSurface myDesignSurface;

  public NlPalettePanel() {
    myModel = NlPaletteModel.get();
    myTree = new DnDAwareTree();
    myIconFactory = IconPreviewFactory.get();
    myMode = Mode.ICON_AND_TEXT;
    initTree();
    JScrollPane pane = ScrollPaneFactory.createScrollPane(myTree, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
    setLayout(new BorderLayout());
    add(pane, BorderLayout.CENTER);
  }

  @NotNull
  public JComponent getFocusedComponent() {
    return myTree;
  }

  public enum Mode {
    ICON_AND_TEXT("Show Icon and Text"),
    PREVIEW("Show Preview");

    private final String myMenuText;

    Mode(String menuText) {
      myMenuText = menuText;
    }

    @NotNull
    public String getMenuText() {
      return myMenuText;
    }
  }

  public void setDesignSurface(@Nullable ScalableDesignSurface designSurface) {
    if (myDesignSurface != null) {
      Configuration configuration = myDesignSurface.getConfiguration();
      configuration.removeListener(this);
    }
    myDesignSurface = designSurface;
    if (myDesignSurface != null) {
      Configuration configuration = myDesignSurface.getConfiguration();
      configuration.addListener(this);
    }
    setMode(myMode);
  }

  // ---- implements ConfigurationListener ----

  @Override
  public boolean changed(int flags) {
    setMode(myMode);
    return true;
  }

  @NotNull
  public AnAction[] getActions() {
    return new AnAction[]{new OptionAction()};
  }

  private class OptionAction extends AnAction {
    public OptionAction() {
      // todo: Find a set of different icons
      Presentation presentation = getTemplatePresentation();
      presentation.setIcon(AllIcons.General.ProjectConfigurable);
      presentation.setHoveredIcon(AllIcons.General.ProjectConfigurableBanner);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      int x = 0;
      int y = 0;
      InputEvent inputEvent = e.getInputEvent();
      if (inputEvent instanceof MouseEvent) {
        x = ((MouseEvent)inputEvent).getX();
        y = ((MouseEvent)inputEvent).getY();
      }

      showOptionPopup(inputEvent.getComponent(), x, y);
    }
  }

  private void showOptionPopup(@NotNull Component component, int x, int y) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new TogglePaletteModeAction(this, Mode.ICON_AND_TEXT));
    group.add(new TogglePaletteModeAction(this, Mode.PREVIEW));

    ActionPopupMenu popupMenu =
      ((ActionManagerImpl)ActionManager.getInstance())
        .createActionPopupMenu(ToolWindowContentUi.POPUP_PLACE, group, new MenuItemPresentationFactory(true));
    popupMenu.getComponent().show(component, x, y);
  }

  @NotNull
  public Mode getMode() {
    return myMode;
  }

  public void setMode(@NotNull Mode mode) {
    myMode = mode;
    if (mode == Mode.PREVIEW && myDesignSurface != null) {
      Configuration configuration = myDesignSurface.getConfiguration();
      myIconFactory.load(configuration, new Runnable() {
        @Override
        public void run() {
          invalidateUI();
        }
      });
    } else {
      invalidateUI();
    }
  }

  private void invalidateUI() {
    // BasicTreeUI keeps a cache of node heights. This will replace the ui and force a new node height computation.
    IJSwingUtilities.updateComponentTreeUI(myTree);
  }

  private void initTree() {
    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(null);
    DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    myTree.setModel(treeModel);
    myTree.setRowHeight(0);
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(false);
    myTree.setBorder(new EmptyBorder(INSETS));
    myTree.setToggleClickCount(1);
    ToolTipManager.sharedInstance().registerComponent(myTree);
    TreeUtil.installActions(myTree);
    createCellRenderer(myTree);
    addData(myModel, rootNode);
    expandAll(myTree, rootNode);
    myTree.setSelectionRow(0);
    new PaletteSpeedSearch(myTree);
    enableDnD(myTree);
  }


  private static void expandAll(@NotNull JTree tree, @NotNull DefaultMutableTreeNode rootNode) {
    TreePath rootPath = new TreePath(rootNode);
    tree.expandPath(rootPath);
    TreeNode child = rootNode.getLastChild();
    while (child != null) {
      tree.expandPath(rootPath.pathByAddingChild(child));
      child = rootNode.getChildBefore(child);
    }
  }

  private void createCellRenderer(@NotNull JTree tree) {
    tree.setCellRenderer(new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
        Object content = node.getUserObject();
        if (content instanceof NlPaletteItem) {
          NlPaletteItem item = (NlPaletteItem)content;
          Image image = null;
          if (myMode == Mode.PREVIEW && myDesignSurface != null) {
            image = myIconFactory.getImage(item, myDesignSurface.getConfiguration(), PREVIEW_SCALE);
          }
          if (image != null) {
            setIcon(new ImageIcon(image));
          } else {
            append(item.getTitle());
            setIcon(item.getIcon());
          }
          setToolTipText(item.getTooltip());
        }
        else if (content instanceof NlPaletteGroup) {
          NlPaletteGroup group = (NlPaletteGroup)content;
          append(group.getTitle());
          setIcon(AllIcons.Nodes.Folder);
        }
      }
    });
  }

  private static void addData(@NotNull NlPaletteModel model, @NotNull DefaultMutableTreeNode rootNode) {
    for (NlPaletteGroup group : model.getGroups()) {
      DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(group);
      for (NlPaletteItem item : group.getItems()) {
        DefaultMutableTreeNode itemNode = new DefaultMutableTreeNode(item);
        groupNode.add(itemNode);
      }
      rootNode.add(groupNode);
    }
  }

  private void enableDnD(@NotNull DnDAwareTree tree) {
    final DnDManager dndManager = DnDManager.getInstance();
    dndManager.registerSource(new PaletteDnDSource(tree), tree);
  }

  @Override
  public void dispose() {
    if (myDesignSurface != null) {
      Configuration configuration = myDesignSurface.getConfiguration();
      configuration.removeListener(this);
    }
  }

  private class PaletteDnDSource implements DnDSource {
    private final DnDAwareTree myTree;

    private PaletteDnDSource(@NotNull DnDAwareTree tree) {
      myTree = tree;
    }

    @Override
    public boolean canStartDragging(DnDAction action, Point dragOrigin) {
      TreePath path = myTree.getClosestPathForLocation(dragOrigin.x, dragOrigin.y);
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object content = node.getUserObject();
      return content instanceof NlPaletteItem;
    }

    @Override
    public DnDDragStartBean startDragging(DnDAction action, Point dragOrigin) {
      TreePath path = myTree.getClosestPathForLocation(dragOrigin.x, dragOrigin.y);
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object content = node.getUserObject();
      assert content instanceof NlPaletteItem;
      NlPaletteItem item = (NlPaletteItem)content;
      Dimension size = null;
      if (myDesignSurface != null) {
        BufferedImage image = myIconFactory.getImage(item, myDesignSurface.getConfiguration(), 1.0);
        if (image != null) {
          size = new Dimension(image.getWidth(), image.getHeight());
        }
      }
      if (size == null) {
        Rectangle bounds = myTree.getPathBounds(path);
        size = bounds != null ? bounds.getSize() : new Dimension(200, 100);
        if (myDesignSurface != null) {
          double scale = myDesignSurface.getScale();
          size.setSize(size.getWidth() / scale, size.getHeight() / scale);
        }
      }
      return new DnDDragStartBean(new ItemTransferable(new DnDTransferItem(item, size.width, size.height)));
    }

    @Nullable
    @Override
    public Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin) {
      TreePath path = myTree.getClosestPathForLocation(dragOrigin.x, dragOrigin.y);
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object content = node.getUserObject();
      assert content instanceof NlPaletteItem;
      NlPaletteItem item = (NlPaletteItem)content;
      BufferedImage image = null;
      if (myDesignSurface != null) {
        image = myIconFactory.getImage(item, myDesignSurface.getConfiguration(), myDesignSurface.getScale());
      }
      if (image == null) {
        image = (BufferedImage)DnDAwareTree.getDragImage(myTree, path, dragOrigin).getFirst();
      }
      return Pair.<Image, Point>pair(image, new Point(-image.getWidth() / 2, -image.getHeight() / 2));
    }

    @Override
    public void dragDropEnd() {
      myTree.clearSelection();
    }

    @Override
    public void dropActionChanged(int gestureModifiers) {
    }
  }

  private static final class PaletteSpeedSearch extends TreeSpeedSearch {
    PaletteSpeedSearch(@NotNull JTree tree) {
      super(tree);
    }

    @Override
    protected boolean isMatchingElement(Object element, String pattern) {
      if (element == null || pattern == null) {
        return false;
      }
      TreePath path = (TreePath)element;
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object content = node.getUserObject();
      if (!(content instanceof NlPaletteItem)) {
        return false;
      }
      NlPaletteItem item = (NlPaletteItem)content;
      return compare(item.getTitle(), pattern);
    }
  }
}
