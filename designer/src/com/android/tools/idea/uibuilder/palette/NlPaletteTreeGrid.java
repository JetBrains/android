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
package com.android.tools.idea.uibuilder.palette;

import com.android.tools.adtui.treegrid.TreeGrid;
import com.android.tools.idea.uibuilder.analytics.NlUsageTrackerManager;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.google.wireless.android.sdk.stats.LayoutEditorEvent;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.function.Supplier;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public class NlPaletteTreeGrid extends JPanel {
  private final Project myProject;
  private final DependencyManager myDependencyManager;
  private final Runnable myCloseAutoHideCallback;
  private final JList<Palette.Group> myCategoryList;
  private final TreeGrid<Palette.Item> myTree;
  private final DesignSurface mySurface;
  private PaletteMode myMode;
  private SelectionListener myListener;

  public NlPaletteTreeGrid(@NotNull Project project,
                           @NotNull DependencyManager dependencyManager,
                           @NotNull Runnable closeAutoHideCallback,
                           @Nullable DesignSurface designSurface) {
    myProject = project;
    myDependencyManager = dependencyManager;
    myCloseAutoHideCallback = closeAutoHideCallback;
    mySurface = designSurface;
    myMode = PaletteMode.ICON_AND_NAME;
    myTree = new TreeGrid<>();
    //noinspection unchecked
    myCategoryList = new JBList();
    myCategoryList.setBackground(UIUtil.getPanelBackground());
    myCategoryList.setForeground(UIManager.getColor("Panel.foreground"));
    myCategoryList.addListSelectionListener(event -> myTree.setVisibleSection(myCategoryList.getSelectedValue()));
    myCategoryList.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));

    JScrollPane categoryPane = ScrollPaneFactory.createScrollPane(myCategoryList, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
    categoryPane.setBorder(BorderFactory.createEmptyBorder());

    JScrollPane paletteScrollPane = ScrollPaneFactory.createScrollPane(myTree, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
    paletteScrollPane.setBorder(BorderFactory.createEmptyBorder());
    Splitter palette = new Splitter(false, 0.4f);
    palette.setFirstComponent(categoryPane);
    palette.setSecondComponent(paletteScrollPane);

    setLayout(new BorderLayout());
    add(palette, BorderLayout.CENTER);
    setFocusCycleRoot(true);
    setFocusTraversalPolicy(new MyFocusTraversalPolicy(myCategoryList, myTree));
  }

  @Override
  public void requestFocus() {
    NlUsageTrackerManager.getInstance(mySurface).logAction(LayoutEditorEvent.LayoutEditorEventType.SHOW_PALETTE);
    myTree.requestFocus();
  }

  @NotNull
  public PaletteMode getMode() {
    return myMode;
  }

  public void setMode(@NotNull PaletteMode mode) {
    myMode = mode;
    int fixedCellWidth = -1;
    int fixedCellHeight = -1;
    int border = mode.getBorder();
    int orientation = JList.HORIZONTAL_WRAP;
    switch (mode) {
      case ICON_AND_NAME:
        orientation = JList.VERTICAL;
        break;
      case LARGE_ICONS:
        fixedCellWidth = fixedCellHeight = border + 24 + border;
        break;
      case SMALL_ICONS:
        fixedCellWidth = fixedCellHeight = border + 16 + border;
        break;
    }
    myTree.setFixedCellWidth(fixedCellWidth);
    myTree.setFixedCellHeight(fixedCellHeight);
    myTree.setLayoutOrientation(orientation);
    //noinspection unchecked
    myTree.setCellRenderer(new MyCellRenderer(myDependencyManager, mode));
  }

  public void populateUiModel(@NotNull Palette palette, @NotNull DesignSurface designSurface) {
    myCategoryList.setModel(new TreeCategoryProvider(palette));
    AbstractTreeStructure provider = myCategoryList.getModel().getSize() == 1 ? new SingleListTreeProvider(myProject, palette)
                                                                              : new TreeProvider(myProject, palette);
    myTree.setModel(provider);
    myTree.setTransferHandler(new MyItemTransferHandler(designSurface, this::getSelectedItem));
    myTree.addMouseListener(createMouseListenerForLoadMissingDependency());
    myTree.addListSelectionListener(event -> fireSelectionChanged(myTree.getSelectedElement()));
    setMode(myMode);
  }

  @NotNull
  protected MouseListener createMouseListenerForLoadMissingDependency() {
    return new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent event) {
        Palette.Item item = getSelectedItem();
        if (item != null && myDependencyManager.needsLibraryLoad(item)) {
          if (!myDependencyManager.ensureLibraryIsIncluded(item)) {
            clearSelection();
          }
        }
      }
    };
  }

  public void setSelectionListener(@NotNull SelectionListener listener) {
    myListener = listener;
  }

  public void fireSelectionChanged(@Nullable Palette.Item item) {
    if (myListener != null) {
      myListener.selectionChanged(item);
    }
  }

  @Nullable
  public Palette.Item getSelectedItem() {
    return myTree.getSelectedElement();
  }

  private void clearSelection() {
    myTree.setSelectedElement(null);
  }

  @TestOnly
  @NotNull
  public JList<Palette.Group> getCategoryList() {
    return myCategoryList;
  }

  @TestOnly
  @NotNull
  public TreeGrid<Palette.Item> getComponentTree() {
    return myTree;
  }

  private class MyItemTransferHandler extends ItemTransferHandler {

    public MyItemTransferHandler(@NotNull DesignSurface designSurface, @NotNull Supplier<Palette.Item> itemSupplier) {
      super(designSurface, itemSupplier);
    }

    @Override
    protected Transferable createTransferable(@NotNull JComponent component) {
      Transferable transferable = super.createTransferable(component);
      myCloseAutoHideCallback.run();
      return transferable;
    }
  }

  private static class MyCellRenderer extends ColoredListCellRenderer<Palette.Item> {
    private final DependencyManager myDependencyManager;
    private final PaletteMode myMode;

    private MyCellRenderer(@NotNull DependencyManager dependencyManager, @NotNull PaletteMode mode) {
      myDependencyManager = dependencyManager;
      myMode = mode;
      int padding = mode.getBorder();
      setIpad(new JBInsets(padding, Math.max(4, padding), padding, padding));
      setBorder(BorderFactory.createEmptyBorder());
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList list, @NotNull Palette.Item item, int index, boolean selected, boolean hasFocus) {
      switch (myMode) {
        case ICON_AND_NAME:
          setIcon(myDependencyManager.createItemIcon(item, list));
          append(item.getTitle());
          break;

        case LARGE_ICONS:
          setIcon(myDependencyManager.createLargeItemIcon(item, list));
          break;

        case SMALL_ICONS:
          setIcon(myDependencyManager.createItemIcon(item, list));
          break;
      }
    }
  }

  private static class MyFocusTraversalPolicy extends FocusTraversalPolicy {
    private final JList<Palette.Group> myCategoryList;
    private final TreeGrid<Palette.Item> myTree;

    private MyFocusTraversalPolicy(@NotNull JList<Palette.Group> categoryList, @NotNull TreeGrid<Palette.Item> tree) {
      myCategoryList = categoryList;
      myTree = tree;
    }

    @Override
    public Component getComponentAfter(Container container, Component component) {
      return getOtherComponent(component);
    }

    @Override
    public Component getComponentBefore(Container container, Component component) {
      return getOtherComponent(component);
    }

    @Override
    public Component getFirstComponent(Container container) {
      return getTreeComponent();
    }

    @Override
    public Component getLastComponent(Container container) {
      return myCategoryList;
    }

    @Override
    public Component getDefaultComponent(Container container) {
      return getTreeComponent();
    }

    @NotNull
    private Component getOtherComponent(Component component) {
      if (component != null && SwingUtilities.isDescendingFrom(component, myTree)) {
        return myCategoryList;
      }
      else {
        return getTreeComponent();
      }
    }

    @NotNull
    private Component getTreeComponent() {
      Component component = myTree.getFocusRecipient();
      if (component != null) {
        return component;
      }
      return myTree;
    }
  }
}
