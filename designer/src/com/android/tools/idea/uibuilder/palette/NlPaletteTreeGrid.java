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

import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.splitter.ComponentsSplitter;
import com.android.tools.adtui.treegrid.TreeGrid;
import com.android.tools.adtui.workbench.StartFilteringListener;
import com.android.tools.idea.uibuilder.actions.ComponentHelpAction;
import com.android.tools.idea.common.analytics.NlUsageTrackerManager;
import com.android.tools.idea.uibuilder.model.DnDTransferComponent;
import com.android.tools.idea.uibuilder.model.DnDTransferItem;
import com.android.tools.idea.uibuilder.model.ItemTransferable;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.google.wireless.android.sdk.stats.LayoutEditorEvent;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SpeedSearchComparator;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.event.*;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

import static com.android.tools.adtui.splitter.SplitterUtil.setMinimumWidth;
import static com.android.tools.idea.uibuilder.palette.TreeCategoryProvider.ALL;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public class NlPaletteTreeGrid extends JPanel implements Disposable {
  static final String PALETTE_CATEGORY_WIDTH = "palette.category.width";
  static final int DEFAULT_CATEGORY_WIDTH = 100;
  private static final int VERTICAL_SCROLLING_UNIT_INCREMENT = 20;
  private static final int VERTICAL_SCROLLING_BLOCK_INCREMENT = 40;

  private final Project myProject;
  private final DependencyManager myDependencyManager;
  private final Runnable myCloseAutoHideCallback;
  private final JList<Palette.Group> myCategoryList;
  private final TreeGrid<Palette.Item> myTree;
  private final MyFilter myFilter;
  private final IconPreviewFactory myIconPreviewFactory;
  private final ComponentsSplitter mySplitter;
  private PaletteMode myMode;
  private SelectionListener myListener;
  private NlDesignSurface mySurface;
  private Palette myPalette;
  private StartFilteringListener myStartFilteringCallback;

  public NlPaletteTreeGrid(@NotNull Project project,
                           @NotNull PaletteMode initialMode,
                           @NotNull DependencyManager dependencyManager,
                           @NotNull Runnable closeAutoHideCallback,
                           @Nullable NlDesignSurface designSurface,
                           @NotNull IconPreviewFactory iconFactory) {
    myProject = project;
    myDependencyManager = dependencyManager;
    myCloseAutoHideCallback = closeAutoHideCallback;
    mySurface = designSurface;
    myMode = initialMode;
    myIconPreviewFactory = iconFactory;
    myTree = createItemTreeGrid(project);
    myTree.addListSelectionListener(event -> fireSelectionChanged(myTree.getSelectedElement()));
    myTree.addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(@NotNull KeyEvent event) {
        handleKeyEvent(event);
      }
    });

    //noinspection unchecked
    myCategoryList = new JBList();
    myCategoryList.setBackground(UIUtil.getPanelBackground());
    myCategoryList.setForeground(UIManager.getColor("Panel.foreground"));
    myCategoryList.addListSelectionListener(this::categorySelectionChanged);
    myCategoryList.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
    myCategoryList.setCellRenderer(new MyCategoryCellRenderer());
    myFilter = new MyFilter();

    JScrollPane categoryPane = ScrollPaneFactory.createScrollPane(myCategoryList, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
    categoryPane.setBorder(BorderFactory.createEmptyBorder());

    JScrollPane paletteScrollPane = ScrollPaneFactory.createScrollPane(myTree, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
    paletteScrollPane.setFocusable(false);
    paletteScrollPane.setBorder(BorderFactory.createEmptyBorder());
    paletteScrollPane.getVerticalScrollBar().setUnitIncrement(VERTICAL_SCROLLING_UNIT_INCREMENT);
    paletteScrollPane.getVerticalScrollBar().setBlockIncrement(VERTICAL_SCROLLING_BLOCK_INCREMENT);

    // Use a ComponentSplitter instead of a Splitter here to avoid a fat splitter size.
    mySplitter = new ComponentsSplitter(false, true);
    mySplitter.setFirstComponent(categoryPane);
    mySplitter.setInnerComponent(paletteScrollPane);
    mySplitter.setHonorComponentsMinimumSize(true);
    mySplitter.setFirstSize(JBUI.scale(getInitialCategoryWidth()));
    mySplitter.setFocusCycleRoot(false);
    myCategoryList.addComponentListener(createCategoryWidthUpdater());
    setMinimumWidth(categoryPane, JBUI.scale(20));
    setMinimumWidth(paletteScrollPane, JBUI.scale(20));
    Disposer.register(this, mySplitter);

    setLayout(new BorderLayout());
    add(mySplitter, BorderLayout.CENTER);
    setFocusTraversalPolicyProvider(true);
  }

  private static TreeGrid<Palette.Item> createItemTreeGrid(@NotNull Project project) {
    TreeGrid<Palette.Item> grid = new TreeGrid<>();
    grid.setName("itemTreeGrid");
    ComponentHelpAction help = new ComponentHelpAction(project, () -> {
      Palette.Item item = grid.getSelectedElement();
      return item != null ? item.getTagName() : null;
    });
    help.registerCustomShortcutSet(KeyEvent.VK_F1, InputEvent.SHIFT_MASK, grid);
    return grid;
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
    int border = JBUI.scale(mode.getBorder());
    int orientation = JList.HORIZONTAL_WRAP;
    switch (mode) {
      case ICON_AND_NAME:
        orientation = JList.VERTICAL;
        break;
      case LARGE_ICONS:
        fixedCellWidth = fixedCellHeight = border + JBUI.scale(24) + border;
        break;
      case SMALL_ICONS:
        fixedCellWidth = fixedCellHeight = border + JBUI.scale(16) + border;
        break;
    }
    myTree.setFixedCellWidth(fixedCellWidth);
    myTree.setFixedCellHeight(fixedCellHeight);
    myTree.setLayoutOrientation(orientation);
    //noinspection unchecked
    myTree.setCellRenderer(new MyCellRenderer(myDependencyManager, mode));
  }

  public void setStartFiltering(@NotNull StartFilteringListener listener) {
    myStartFilteringCallback = listener;
  }

  public void populateUiModel(@NotNull Palette palette, @NotNull NlDesignSurface designSurface) {
    mySurface = designSurface;
    myPalette = palette;
    myCategoryList.setModel(new TreeCategoryProvider(palette));
    if (myCategoryList.getSelectedValue() == null) {
      myCategoryList.setSelectedValue(ALL, true);
    }
    updateTreeModel();
  }

  private void updateTreeModel() {
    AbstractTreeStructure provider = myCategoryList.getModel().getSize() > 1 && myFilter.getPattern().isEmpty()
                                     ? new TreeProvider(myProject, myPalette)
                                     : new SingleListTreeProvider(myProject, myPalette);
    myTree.setModel(provider);
    myTree.setVisibleSection(myCategoryList.getSelectedValue());
    myTree.setTransferHandler(new MyItemTransferHandler(mySurface, myDependencyManager, this::getSelectedItem, myIconPreviewFactory));
    setMode(myMode);
  }

  public void setFilter(@NotNull String pattern) {
    String oldPattern = myFilter.getPattern();
    myFilter.setPattern(pattern);
    if (pattern.isEmpty()) {
      if (!oldPattern.isEmpty()) {
        updateTreeModel();
        myTree.setVisibleSection(myCategoryList.getSelectedValue());
      }
      myTree.setFilter(null);
    }
    else {
      if (oldPattern.isEmpty()) {
        updateTreeModel();
        myTree.setVisibleSection(ALL.getName());
      }
      myTree.setFilter(myFilter);
      myTree.selectIfUnique();
    }
  }

  @NotNull
  public String getFilter() {
    return myFilter.getPattern();
  }

  private void categorySelectionChanged(@Nullable @SuppressWarnings("unused") ListSelectionEvent event) {
    if (myFilter.getPattern().isEmpty()) {
      myTree.setVisibleSection(myCategoryList.getSelectedValue());
      Palette.Item selected = myTree.getSelectedVisibleElement();
      if (selected == null) {
        myTree.getFocusRecipient();  // Will select the first item in a visible list
      }
    }
  }

  public void setSelectionListener(@NotNull SelectionListener listener) {
    myListener = listener;
  }

  public void fireSelectionChanged(@Nullable Palette.Item item) {
    if (myListener != null) {
      myListener.selectionChanged(item);
    }
  }

  public void handleKeyEvent(@NotNull KeyEvent event) {
    if (myStartFilteringCallback != null) {
      myStartFilteringCallback.startFiltering(event.getKeyChar());
    }
  }

  @Nullable
  public Palette.Item getSelectedItem() {
    return myTree.getSelectedElement();
  }

  @NotNull
  private ComponentListener createCategoryWidthUpdater() {
    return new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent event) {
        PropertiesComponent.getInstance()
          .setValue(PALETTE_CATEGORY_WIDTH, String.valueOf(AdtUiUtils.unscale(mySplitter.getFirstSize())));
      }
    };
  }

  private static int getInitialCategoryWidth() {
    try {
      return Integer.parseInt(PropertiesComponent.getInstance().getValue(PALETTE_CATEGORY_WIDTH, String.valueOf(DEFAULT_CATEGORY_WIDTH)));
    }
    catch (NumberFormatException unused) {
      return DEFAULT_CATEGORY_WIDTH;
    }
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

  @TestOnly
  @NotNull
  public ComponentsSplitter getSplitter() {
    return mySplitter;
  }

  @Override
  public void dispose() {
  }

  private class MyItemTransferHandler extends ItemTransferHandler {

    public MyItemTransferHandler(@NotNull DesignSurface designSurface,
                                 @NotNull DependencyManager dependencyManager,
                                 @NotNull Supplier<Palette.Item> itemSupplier,
                                 @NotNull IconPreviewFactory iconPreviewFactory) {
      super(designSurface, dependencyManager, itemSupplier, iconPreviewFactory);
    }

    @Override
    protected Transferable createTransferable(@NotNull JComponent component) {
      Transferable transferable = super.createTransferable(component);
      myCloseAutoHideCallback.run();
      return transferable;
    }

    @Override
    protected void exportDone(@NotNull JComponent source, @Nullable Transferable data, int action) {
      if (action != DnDConstants.ACTION_NONE && data != null) {
        DnDTransferComponent component = getDndComponent(data);
        if (component != null) {
          NlUsageTrackerManager.getInstance(mySurface)
            .logDropFromPalette(component.getTag(), component.getRepresentation(), myMode, getGroupName(), myTree.getFilterMatchCount());
        }
      }
    }

    @NotNull
    private String getGroupName() {
      Palette.Group group = myCategoryList.getSelectedValue();
      return group != null ? group.getName() : "";
    }

    @Nullable
    private DnDTransferComponent getDndComponent(@NotNull Transferable data) {
      try {
        DnDTransferItem item = (DnDTransferItem)data.getTransferData(ItemTransferable.DESIGNER_FLAVOR);
        if (item != null) {
          List<DnDTransferComponent> components = item.getComponents();
          if (components.size() == 1) {
            return components.get(0);
          }
        }
      }
      catch (UnsupportedFlavorException | IOException ex) {
        Logger.getInstance(NlPaletteTreeGrid.class).warn("Could not un-serialize a transferable", ex);
      }
      return null;
    }
  }

  private static class MyCategoryCellRenderer extends ColoredListCellRenderer<Palette.Group> {
    @Override
    protected void customizeCellRenderer(@NotNull JList list, @NotNull Palette.Group group, int index, boolean selected, boolean hasFocus) {
      if (selected) {
        // Use the tree colors.
        // This makes the designer tools look consistent with the component tree.
        setBackground(UIUtil.getTreeSelectionBackground(hasFocus));
        mySelectionForeground = UIUtil.getTreeForeground(true, hasFocus);
      }
      append(group.getName());
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
      if (selected) {
        // Use the tree colors.
        // This makes the designer tools look consistent with the component tree.
        setBackground(UIUtil.getTreeSelectionBackground(hasFocus));
        mySelectionForeground = UIUtil.getTreeForeground(true, hasFocus);
      }
      switch (myMode) {
        case ICON_AND_NAME:
          setIcon(myDependencyManager.createItemIcon(item, list));
          append(item.getTitle());
          break;

        case LARGE_ICONS:
          setIcon(myDependencyManager.createLargeItemIcon(item, list));
          setToolTipText(item.getTitle());
          break;

        case SMALL_ICONS:
          setIcon(myDependencyManager.createItemIcon(item, list));
          setToolTipText(item.getTitle());
          break;
      }
    }
  }

  private static class MyFilter implements Condition<Palette.Item> {
    private final SpeedSearchComparator myComparator;
    private String myPattern;

    public MyFilter() {
      myComparator = new SpeedSearchComparator(false);
      myPattern = "";
    }

    public void setPattern(@NotNull String filter) {
      myPattern = filter;
    }

    @NotNull
    public String getPattern() {
      return myPattern;
    }

    @Override
    public boolean value(@NotNull Palette.Item item) {
      return myComparator.matchingFragments(myPattern, item.getTitle()) != null;
    }
  }
}
