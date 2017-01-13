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

import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.splitter.ComponentsSplitter;
import com.android.tools.adtui.treegrid.TreeGrid;
import com.android.tools.idea.uibuilder.analytics.NlUsageTrackerManager;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.google.common.collect.ImmutableMap;
import com.google.wireless.android.sdk.stats.LayoutEditorEvent;
import com.intellij.ide.actions.ExternalJavaDocAction;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
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
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Map;
import java.util.function.Supplier;

import static com.android.SdkConstants.*;
import static com.android.tools.adtui.splitter.SplitterUtil.setMinimumWidth;
import static com.android.tools.idea.uibuilder.palette.TreeCategoryProvider.ALL;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public class NlPaletteTreeGrid extends JPanel implements Disposable {
  private static final int VERTICAL_SCROLLING_UNIT_INCREMENT = 20;
  private static final int VERTICAL_SCROLLING_BLOCK_INCREMENT = 40;
  @VisibleForTesting
  static final String COMPONENT_HELP = "componentHelp";

  private final Project myProject;
  private final DependencyManager myDependencyManager;
  private final Runnable myCloseAutoHideCallback;
  private final JList<Palette.Group> myCategoryList;
  private final TreeGrid<Palette.Item> myTree;
  private final MyFilter myFilter;
  private final IconPreviewFactory myIconPreviewFactory;
  private PaletteMode myMode;
  private SelectionListener myListener;
  private NlDesignSurface mySurface;
  private Palette myPalette;

  public NlPaletteTreeGrid(@NotNull Project project,
                           @NotNull DependencyManager dependencyManager,
                           @NotNull Runnable closeAutoHideCallback,
                           @Nullable NlDesignSurface designSurface,
                           @NotNull IconPreviewFactory iconFactory) {
    this(project, dependencyManager, closeAutoHideCallback, designSurface, iconFactory, null);
  }

  @VisibleForTesting
  NlPaletteTreeGrid(@NotNull Project project,
                    @NotNull DependencyManager dependencyManager,
                    @NotNull Runnable closeAutoHideCallback,
                    @Nullable NlDesignSurface designSurface,
                    @NotNull IconPreviewFactory iconFactory,
                    @Nullable JavaDocViewer javaDocViewer) {
    myProject = project;
    myDependencyManager = dependencyManager;
    myCloseAutoHideCallback = closeAutoHideCallback;
    mySurface = designSurface;
    myMode = PaletteMode.ICON_AND_NAME;
    myIconPreviewFactory = iconFactory;
    myTree = createItemTreeGrid(project, javaDocViewer);

    //noinspection unchecked
    myCategoryList = new JBList();
    myCategoryList.setBackground(UIUtil.getPanelBackground());
    myCategoryList.setForeground(UIManager.getColor("Panel.foreground"));
    myCategoryList.addListSelectionListener(this::categorySelectionChanged);
    myCategoryList.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
    myFilter = new MyFilter(myCategoryList);

    JScrollPane categoryPane = ScrollPaneFactory.createScrollPane(myCategoryList, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
    categoryPane.setBorder(BorderFactory.createEmptyBorder());

    JScrollPane paletteScrollPane = ScrollPaneFactory.createScrollPane(myTree, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
    paletteScrollPane.setBorder(BorderFactory.createEmptyBorder());
    paletteScrollPane.getVerticalScrollBar().setUnitIncrement(VERTICAL_SCROLLING_UNIT_INCREMENT);
    paletteScrollPane.getVerticalScrollBar().setBlockIncrement(VERTICAL_SCROLLING_BLOCK_INCREMENT);

    // Use a ComponentSplitter instead of a Splitter here to avoid a fat splitter size.
    ComponentsSplitter palette = new ComponentsSplitter(false, true);
    palette.setFirstComponent(categoryPane);
    palette.setInnerComponent(paletteScrollPane);
    palette.setHonorComponentsMinimumSize(true);
    palette.setFirstSize(JBUI.scale(100));
    setMinimumWidth(categoryPane, 20);
    setMinimumWidth(paletteScrollPane, 20);
    Disposer.register(this, palette);

    setLayout(new BorderLayout());
    add(palette, BorderLayout.CENTER);
    setFocusCycleRoot(true);
    setFocusTraversalPolicy(new MyFocusTraversalPolicy(myCategoryList, myTree));
  }

  private static TreeGrid<Palette.Item> createItemTreeGrid(@NotNull Project project, @Nullable JavaDocViewer javaDocViewer) {
    TreeGrid<Palette.Item> grid = new TreeGrid<>();
    grid.setName("itemTreeGrid");
    grid.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_F1, InputEvent.SHIFT_MASK), COMPONENT_HELP);
    grid.getActionMap().put(COMPONENT_HELP, new ComponentHelpAction(project, grid, javaDocViewer));
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

  public void populateUiModel(@NotNull Palette palette, @NotNull NlDesignSurface designSurface) {
    mySurface = designSurface;
    myPalette = palette;
    myCategoryList.setModel(new TreeCategoryProvider(palette));
    updateTreeModel();
  }

  private void updateTreeModel() {
    AbstractTreeStructure provider = myCategoryList.getModel().getSize() > 1 && myFilter.getPattern().isEmpty()
                                     ? new TreeProvider(myProject, myPalette)
                                     : new SingleListTreeProvider(myProject, myPalette);
    myTree.setModel(provider);
    myTree.addListSelectionListener(event -> fireSelectionChanged(myTree.getSelectedElement()));
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
      }
      myTree.setFilter(null);
    }
    else {
      if (oldPattern.isEmpty()) {
        updateTreeModel();
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
    myTree.setVisibleSection(myCategoryList.getSelectedValue());
    if (!myFilter.getPattern().isEmpty()) {
      setFilter(myFilter.getPattern());
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

  @Nullable
  public Palette.Item getSelectedItem() {
    return myTree.getSelectedElement();
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
          setToolTipText(item.getTitle());
          break;

        case SMALL_ICONS:
          setIcon(myDependencyManager.createItemIcon(item, list));
          setToolTipText(item.getTitle());
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

  private static class MyFilter implements Condition<Palette.Item> {
    private final SpeedSearchComparator myComparator;
    private final JList<Palette.Group> myCategoryList;
    private String myPattern;

    public MyFilter(@NotNull JList<Palette.Group> categoryList) {
      myCategoryList = categoryList;
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
      Palette.Group group = myCategoryList.getSelectedValue();
      if (group != null && group != ALL && group != item.getParent()) {
        return false;
      }
      return myComparator.matchingFragments(myPattern, item.getTitle()) != null;
    }
  }

  private static class ComponentHelpAction extends AbstractAction {
    private final Project myProject;
    private final TreeGrid<Palette.Item> myTree;
    private final JavaDocViewer myJavaDocViewer;

    private ComponentHelpAction(@NotNull Project project, @NotNull TreeGrid<Palette.Item> tree, @Nullable JavaDocViewer javaDocViewer) {
      myProject = project;
      myTree = tree;
      myJavaDocViewer = javaDocViewer != null ? javaDocViewer : new JavaDocViewer(project, tree);
    }

    @Override
    public void actionPerformed(@Nullable ActionEvent event) {
      Palette.Item item = myTree.getSelectedElement();
      if (item == null) {
        return;
      }
      PsiClass psiClass = findClassOfTagName(item.getTagName());
      if (psiClass == null) {
        return;
      }
      myJavaDocViewer.showExternalJavaDoc(psiClass);
    }

    @Nullable
    private PsiClass findClassOfTagName(@NotNull String tagName) {
      if (tagName.indexOf('.') != -1) {
        return findClassByClassName(tagName);
      }
      PsiClass psiClass = findClassByClassName(ANDROID_WIDGET_PREFIX + tagName);
      if (psiClass != null) {
        return psiClass;
      }
      psiClass = findClassByClassName(ANDROID_VIEW_PKG + tagName);
      if (psiClass != null) {
        return psiClass;
      }
      return findClassByClassName(ANDROID_WEBKIT_PKG + tagName);
    }

    @Nullable
    private PsiClass findClassByClassName(@NotNull String fqcn) {
      JavaPsiFacade javaFacade = JavaPsiFacade.getInstance(myProject);
      return javaFacade.findClass(fqcn, GlobalSearchScope.allScope(myProject));
    }
  }

  static class JavaDocViewer {
    private final Project myProject;
    private final Component myTree;

    private JavaDocViewer(@NotNull Project project, @NotNull Component tree) {
      myProject = project;
      myTree = tree;
    }

    void showExternalJavaDoc(@NotNull PsiClass psiClass) {
      Map<String, Object> data = ImmutableMap.of(CommonDataKeys.PROJECT.getName(), myProject,
                                                 PlatformDataKeys.CONTEXT_COMPONENT.getName(), myTree);
      DataContext context = SimpleDataContext.getSimpleContext(data, null);
      ExternalJavaDocAction.showExternalJavadoc(psiClass, null, null, context);
    }
  }
}
