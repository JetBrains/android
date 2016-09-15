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

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.adtui.visual.treegrid.TreeGrid;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.rendering.ImageUtils;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBImageIcon;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public class NlPaletteTreeGrid extends JPanel implements Disposable {
  private final Project myProject;
  private final Set<String> myMissingLibraries;
  private final JList<Palette.Group> myCategoryList;
  private final TreeGrid<Palette.Item> myTree;
  private final TransferHandler myTransferHandler;
  private DesignSurface myDesignSurface;
  private PaletteMode myMode;
  private Palette myPalette;
  private SelectionListener myListener;

  public NlPaletteTreeGrid(@NotNull Project project) {
    myProject = project;
    myMissingLibraries = new HashSet<>();
    myMode = PaletteMode.ICON_AND_NAME;
    myTree = new TreeGrid<>();
    myTransferHandler = new ItemTransferHandler(this::getSelectedItem, this::getDesignSurface);
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

    registerDependencyUpdates();
  }

  @Override
  public void requestFocus() {
    myTree.requestFocus();
  }

  @Nullable
  public DesignSurface getDesignSurface() {
    return myDesignSurface;
  }

  public void setDesignSurface(@Nullable DesignSurface designSurface) {
    DesignSurface oldDesignSurface = myDesignSurface;
    myDesignSurface = designSurface;
    if (designSurface != null &&
        (oldDesignSurface == null || designSurface.getLayoutType() != oldDesignSurface.getLayoutType()) &&
        designSurface.getLayoutType().isSupportedByDesigner()) {
      NlPaletteModel model = NlPaletteModel.get(myProject);
      myPalette = model.getPalette(myDesignSurface.getLayoutType());
      populateUiModel(myPalette);
      checkForNewMissingDependencies();
      repaint();
    }
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
    myTree.setCellRenderer(new MyCellRenderer(myMissingLibraries, mode));
  }

  @Override
  public void dispose() {
    setDesignSurface(null);
  }

  private void populateUiModel(@NotNull Palette palette) {
    myTree.setTransferHandler(null);
    myTree.setModel(new TreeProvider(myProject, palette));
    myTree.setTransferHandler(myTransferHandler);
    myTree.addMouseListener(createMouseListenerForLoadMissingDependency());
    myTree.addListSelectionListener(event -> fireSelectionChanged(myTree.getSelectedElement()));
    setMode(myMode);
    myCategoryList.setModel(new TreeCategoryProvider(palette));
  }

  @Nullable
  private Module getModule() {
    Configuration configuration =
      myDesignSurface != null && myDesignSurface.getLayoutType().isSupportedByDesigner() ? myDesignSurface.getConfiguration() : null;
    return configuration != null ? configuration.getModule() : null;
  }

  private boolean checkForNewMissingDependencies() {
    Module module = getModule();
    Set<String> missing = Collections.emptySet();
    if (module != null) {
      GradleDependencyManager manager = GradleDependencyManager.getInstance(myProject);
      missing = fromGradleCoordinatesToDependencies(manager.findMissingDependencies(module, myPalette.getGradleCoordinates()));
      if (myMissingLibraries.equals(missing)) {
        return false;
      }
    }
    myMissingLibraries.clear();
    myMissingLibraries.addAll(missing);
    return true;
  }

  public boolean needsLibraryLoad(@NotNull Palette.Item item) {
    return myMissingLibraries.contains(item.getGradleCoordinate());
  }

  @NotNull
  private static Set<String> fromGradleCoordinatesToDependencies(@NotNull Collection<GradleCoordinate> coordinates) {
    return coordinates.stream()
      .map(GradleCoordinate::getId)
      .filter(dependency -> dependency != null)
      .collect(Collectors.toSet());
  }

  @NotNull
  private static List<GradleCoordinate> toGradleCoordinatesFromDependencies(@NotNull Collection<String> dependencies) {
    return dependencies.stream()
      .map(dependency -> GradleCoordinate.parseCoordinateString(dependency + ":+"))
      .filter(coordinate -> coordinate != null)
      .collect(Collectors.toList());
  }

  @NotNull
  protected MouseListener createMouseListenerForLoadMissingDependency() {
    return new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent event) {
        Palette.Item item = getSelectedItem();
        if (item != null && needsLibraryLoad(item)) {
          String coordinate = item.getGradleCoordinate();
          assert coordinate != null;
          Module module = getModule();
          assert module != null;
          GradleDependencyManager manager = GradleDependencyManager.getInstance(myProject);
          if (!manager.ensureLibraryIsIncluded(module, toGradleCoordinatesFromDependencies(Collections.singletonList(coordinate)), null)) {
            clearSelection();
          }
        }
      }
    };
  }

  private void registerDependencyUpdates() {
    GradleSyncState.subscribe(myProject, new GradleSyncListener.Adapter() {
      @Override
      public void syncSucceeded(@NotNull Project project) {
        if (checkForNewMissingDependencies()) {
          repaint();
        }
      }
    }, this);
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

  private static class MyCellRenderer extends ColoredListCellRenderer<Palette.Item> {
    private static final double DOWNLOAD_SCALE = 0.7;
    private static final double DOWNLOAD_RELATIVE_OFFSET = (1.0 - DOWNLOAD_SCALE) / DOWNLOAD_SCALE;

    private final Set<String> myMissingLibraries;
    private final PaletteMode myMode;

    private MyCellRenderer(@NotNull Set<String> missingLibraries, @NotNull PaletteMode mode) {
      myMissingLibraries = missingLibraries;
      myMode = mode;
      int padding = mode.getBorder();
      setIpad(new JBInsets(padding, Math.max(4, padding), padding, padding));
      setBorder(BorderFactory.createEmptyBorder());
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList list, @NotNull Palette.Item item, int index, boolean selected, boolean hasFocus) {
      switch (myMode) {
        case ICON_AND_NAME:
          setIcon(createItemIcon(item, item.getIcon(), list));
          append(item.getTitle());
          break;

        case LARGE_ICONS:
          setIcon(createItemIcon(item, item.getLargeIcon(), list));
          break;

        case SMALL_ICONS:
          setIcon(createItemIcon(item, item.getIcon(), list));
          break;
      }
    }

    @NotNull
    private Icon createItemIcon(@NotNull Palette.Item item, @NotNull Icon icon, @NotNull Component componentContext) {
      if (!myMissingLibraries.contains(item.getGradleCoordinate())) {
        return icon;
      }
      Icon download = AllIcons.Actions.Download;
      BufferedImage image = UIUtil.createImage(icon.getIconWidth(),
                                               icon.getIconHeight(),
                                               BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2 = (Graphics2D)image.getGraphics();
      icon.paintIcon(componentContext, g2, 0, 0);
      int x = icon.getIconWidth() - download.getIconWidth();
      int y = icon.getIconHeight() - download.getIconHeight();
      if (x == 0 && y == 0) {
        g2.scale(DOWNLOAD_SCALE, DOWNLOAD_SCALE);
        x = (int)(icon.getIconWidth() * DOWNLOAD_RELATIVE_OFFSET);
        y = (int)(icon.getIconHeight() * DOWNLOAD_RELATIVE_OFFSET);
      }
      download.paintIcon(componentContext, g2, x, y);
      g2.dispose();
      BufferedImage retina = ImageUtils.convertToRetina(image);
      if (retina != null) {
        image = retina;
      }
      return new JBImageIcon(image);
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
