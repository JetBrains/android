/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.palette2;

import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.splitter.ComponentsSplitter;
import com.android.tools.adtui.workbench.ToolContent;
import com.android.tools.idea.common.analytics.NlUsageTrackerManager;
import com.android.tools.idea.common.model.NlLayoutType;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.model.DnDTransferComponent;
import com.android.tools.idea.uibuilder.model.DnDTransferItem;
import com.android.tools.idea.uibuilder.model.ItemTransferable;
import com.android.tools.idea.uibuilder.palette.NlPaletteTreeGrid;
import com.android.tools.idea.uibuilder.palette.Palette;
import com.android.tools.idea.uibuilder.palette.PaletteMode;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.utils.Pair;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

/**
 * Top level Palette UI.
 */
public class PalettePanel extends JPanel implements Disposable, DataProvider, ToolContent<DesignSurface> {
  private static final String PALETTE_CATEGORY_WIDTH = "palette.category.width";
  private static final int DEFAULT_CATEGORY_WIDTH = 100;
  private static final int DOWNLOAD_WIDTH = 16;
  private static final int VERTICAL_SCROLLING_UNIT_INCREMENT = 50;
  private static final int VERTICAL_SCROLLING_BLOCK_INCREMENT = 25;

  private final DependencyManager myDependencyManager;
  private final DataModel myDataModel;
  private final CopyProvider myCopyProvider;
  private final CategoryList myCategoryList;
  private final ItemList myItemList;

  private DesignSurface myDesignSurface;
  private NlLayoutType myLayoutType;
  private Runnable myCloseAutoHideCallback;

  public PalettePanel(@NotNull Project project) {
    this(new DependencyManager(project));
  }

  @VisibleForTesting
  PalettePanel(@NotNull DependencyManager dependencyManager) {
    super(new BorderLayout());
    myDependencyManager = dependencyManager;
    myDependencyManager.registerDependencyUpdates(this, this);
    myDataModel = new DataModel(myDependencyManager);
    myCopyProvider = new CopyProviderImpl();

    myCategoryList = new CategoryList();
    myItemList = new ItemList(myDependencyManager);

    myCategoryList.setBackground(UIUtil.getPanelBackground());
    myCategoryList.setForeground(UIManager.getColor("Panel.foreground"));

    // Use a ComponentSplitter instead of a Splitter here to avoid a fat splitter size.
    ComponentsSplitter splitter = new ComponentsSplitter(false, true);
    Disposer.register(this, splitter);
    splitter.setFirstComponent(createScrollPane(myCategoryList));
    splitter.setInnerComponent(createScrollPane(myItemList));
    splitter.setHonorComponentsMinimumSize(true);
    splitter.setFirstSize(JBUI.scale(getInitialCategoryWidth()));
    splitter.setFocusCycleRoot(false);

    add(splitter, BorderLayout.CENTER);

    myCategoryList.addListSelectionListener(event -> categorySelectionChanged());
    myCategoryList.setModel(myDataModel.getCategoryListModel());

    PreviewProvider provider = new PreviewProvider(() -> myDesignSurface, myDependencyManager);
    Disposer.register(this, provider);
    myItemList.setModel(myDataModel.getItemListModel());
    myItemList.setTransferHandler(new ItemTransferHandler(provider, myItemList::getSelectedValue));
    if (!GraphicsEnvironment.isHeadless()) {
      myItemList.setDragEnabled(true);
    }
    myItemList.addMouseListener(createDependencyAdditionOnMouseClick());

    myLayoutType = NlLayoutType.UNKNOWN;
  }

  @NotNull
  private static JScrollPane createScrollPane(@NotNull JComponent component) {
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(component, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.getVerticalScrollBar().setUnitIncrement(VERTICAL_SCROLLING_UNIT_INCREMENT);
    scrollPane.getVerticalScrollBar().setBlockIncrement(VERTICAL_SCROLLING_BLOCK_INCREMENT);
    scrollPane.setBorder(BorderFactory.createEmptyBorder());
    return scrollPane;
  }

  @NotNull
  private MouseListener createDependencyAdditionOnMouseClick() {
    return new MouseAdapter() {
      // We really should handle mouseClick instead of mouseReleased events.
      // However on Mac with "Show Scroll bars: When scrolling" is set we are not receiving mouseClick events.
      // The mouseReleased events however are received every time we want to recognize the mouse click.
      @Override
      public void mouseReleased(@NotNull MouseEvent event) {
        if (event.getX() < myItemList.getWidth() - DOWNLOAD_WIDTH || event.getX() >= myItemList.getWidth()) {
          // Ignore mouse clicks that are outside the download button
          return;
        }
        int index = myItemList.locationToIndex(event.getPoint());
        Palette.Item item = myItemList.getModel().getElementAt(index);
        myDependencyManager.ensureLibraryIsIncluded(item);
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

  private void categorySelectionChanged() {
    Palette.Group group = myCategoryList.getSelectedValue();
    if (group == null) {
      myCategoryList.setSelectedIndex(0);
      return;
    }
    myDataModel.categorySelectionChanged(group);
    myItemList.setSelectedIndex(0);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return this;
  }

  @NotNull
  @Override
  public JComponent getFocusedComponent() {
    return myCategoryList;
  }

  @NotNull
  @VisibleForTesting
  ItemList getItemList() {
    return myItemList;
  }

  @Override
  public void requestFocus() {
    myCategoryList.requestFocus();
  }

  @Override
  public void setCloseAutoHideWindow(@NotNull Runnable runnable) {
    myCloseAutoHideCallback = runnable;
  }

  @Override
  public boolean supportsFiltering() {
    return true;
  }

  @Override
  public void setFilter(@NotNull String filter) {
    myDataModel.setFilterPattern(filter);
    categorySelectionChanged();
  }

  @Override
  public void setToolContext(@Nullable DesignSurface designSurface) {
    assert designSurface == null || designSurface instanceof NlDesignSurface;
    Module module = getModule(designSurface);
    if (designSurface != null && module != null && myLayoutType != designSurface.getLayoutType()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      assert facet != null;
      myLayoutType = designSurface.getLayoutType();
      myDataModel.setLayoutType(facet, myLayoutType);
      myCategoryList.setSelectedIndex(0);
    }
    myDesignSurface = designSurface;
  }

  @Nullable
  private static Module getModule(@Nullable DesignSurface designSurface) {
    Configuration configuration =
      designSurface != null && designSurface.getLayoutType().isSupportedByDesigner() ? designSurface.getConfiguration() : null;
    return configuration != null ? configuration.getModule() : null;
  }

  @Override
  public void dispose() {
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    return PlatformDataKeys.COPY_PROVIDER.is(dataId) ? myCopyProvider : null;
  }

  private class CopyProviderImpl implements CopyProvider {

    @Override
    public void performCopy(@NotNull DataContext dataContext) {
      Palette.Item item = myItemList.getSelectedValue();
      if (item != null && !myDependencyManager.needsLibraryLoad(item)) {
        DnDTransferComponent component = new DnDTransferComponent(item.getTagName(), item.getXml(), 0, 0);
        CopyPasteManager.getInstance().setContents(new ItemTransferable(new DnDTransferItem(component)));
      }
    }

    @Override
    public boolean isCopyEnabled(@NotNull DataContext dataContext) {
      Palette.Item item = myItemList.getSelectedValue();
      return item != null && !myDependencyManager.needsLibraryLoad(item);
    }

    @Override
    public boolean isCopyVisible(@NotNull DataContext dataContext) {
      return true;
    }
  }

  private class ItemTransferHandler extends TransferHandler {
    private final PreviewProvider myPreviewProvider;
    private final Supplier<Palette.Item> myItemSupplier;

    private ItemTransferHandler(@NotNull PreviewProvider provider,
                                @NotNull Supplier<Palette.Item> itemSupplier) {
      myPreviewProvider = provider;
      myItemSupplier = itemSupplier;
    }

    @Override
    public int getSourceActions(@NotNull JComponent component) {
      return DnDConstants.ACTION_COPY_OR_MOVE;
    }

    @Override
    @Nullable
    protected Transferable createTransferable(@NotNull JComponent component) {
      Palette.Item item = myItemSupplier.get();
      if (item == null) {
        return null;
      }

      PreviewProvider.ImageAndDimension imageAndSize = myPreviewProvider.createPreview(component, item);
      BufferedImage image = imageAndSize.image;
      Dimension size = imageAndSize.dimension;
      setDragImage(image);
      setDragImageOffset(new Point(-image.getWidth() / 2, -image.getHeight() / 2));
      DnDTransferComponent dndComponent = new DnDTransferComponent(item.getTagName(), item.getXml(), size.width, size.height);
      Transferable transferable = new ItemTransferable(new DnDTransferItem(dndComponent));

      myCloseAutoHideCallback.run();
      return transferable;
    }

    @Override
    protected void exportDone(@NotNull JComponent source, @Nullable Transferable data, int action) {
      if (action == DnDConstants.ACTION_NONE || data == null) {
        return;
      }
      DnDTransferComponent component = getDndComponent(data);
      if (component == null) {
        return;
      }
      NlUsageTrackerManager.getInstance(myDesignSurface).logDropFromPalette(
        component.getTag(), component.getRepresentation(), PaletteMode.ICON_AND_NAME, getGroupName(), myDataModel.getMatchCount());
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
}
