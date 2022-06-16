/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

import com.android.annotations.concurrency.AnyThread;
import com.android.annotations.concurrency.UiThread;
import com.android.tools.adtui.common.AdtSecondaryPanel;
import com.android.tools.adtui.workbench.ToolContent;
import com.android.tools.adtui.workbench.ToolWindowCallback;
import com.android.tools.idea.common.api.DragType;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.model.DnDTransferComponent;
import com.android.tools.idea.common.model.DnDTransferItem;
import com.android.tools.idea.common.model.ItemTransferable;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.UtilsKt;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.actions.ComponentHelpAction;
import com.android.tools.idea.uibuilder.analytics.NlUsageTracker;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.type.LayoutEditorFileType;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Top level Palette UI.
 */
@UiThread
public class PalettePanel extends AdtSecondaryPanel implements Disposable, DataProvider, ToolContent<DesignSurface<?>> {
  private static final int DOWNLOAD_WIDTH = 16;
  private static final int VERTICAL_SCROLLING_UNIT_INCREMENT = 50;
  private static final int VERTICAL_SCROLLING_BLOCK_INCREMENT = 25;

  private final Project myProject;
  private final DependencyManager myDependencyManager;
  private final DataModel myDataModel;
  private final CopyProvider myCopyProvider;
  private final CategoryList myCategoryList;
  private final JScrollPane myCategoryScrollPane;
  private final ItemList myItemList;
  private final AddToDesignAction myAddToDesignAction;
  private final FavoriteAction myFavoriteAction;
  private final ComponentHelpAction myAndroidDocAction;
  private final MaterialDocAction myMaterialDocAction;
  private final ActionGroup myActionGroup;
  private final KeyListener myFilterKeyListener;

  @NotNull private WeakReference<DesignSurface<?>> myDesignSurface = new WeakReference<>(null);
  private LayoutEditorFileType myLayoutType;
  private ToolWindowCallback myToolWindow;
  private Palette.Group myLastSelectedGroup;

  public PalettePanel(@NotNull Project project, @NotNull Disposable parentDisposable) {
    this(project, new DependencyManager(project), parentDisposable);
  }

  @VisibleForTesting
  PalettePanel(@NotNull Project project, @NotNull DependencyManager dependencyManager, @NotNull Disposable parentDisposable) {
    super(new BorderLayout());
    Disposer.register(parentDisposable, this);
    myProject = project;
    myDependencyManager = dependencyManager;
    myDataModel = new DataModel(this, myDependencyManager);
    myDependencyManager.addDependencyChangeListener(() -> repaint());
    myCopyProvider = new CopyProviderImpl();
    Disposer.register(this, dependencyManager);

    myCategoryList = new CategoryList();
    myItemList = new ItemList(myDependencyManager);
    myAddToDesignAction = new AddToDesignAction();
    myFavoriteAction = new FavoriteAction();
    myAndroidDocAction = new ComponentHelpAction(project, this::getSelectedTagName);
    myMaterialDocAction = new MaterialDocAction();
    myActionGroup = createPopupActionGroup();

    myCategoryScrollPane = createScrollPane(myCategoryList);
    add(myCategoryScrollPane, BorderLayout.WEST);
    add(createScrollPane(myItemList), BorderLayout.CENTER);

    myFilterKeyListener = createFilterKeyListener();
    KeyListener keyListener = createKeyListener();

    myCategoryList.addListSelectionListener(event -> categorySelectionChanged());
    myCategoryList.setModel(myDataModel.getCategoryListModel());
    myCategoryList.addKeyListener(keyListener);
    myCategoryList.setBorder(JBUI.Borders.customLine(JBColor.border(), 0, 0, 0, 1));

    PreviewProvider provider = new PreviewProvider(() -> myDesignSurface.get(), myDependencyManager);
    myItemList.setModel(myDataModel.getItemListModel());
    myItemList.setTransferHandler(new ItemTransferHandler(provider, myItemList::getSelectedValue));
    if (!GraphicsEnvironment.isHeadless()) {
      myItemList.setDragEnabled(true);
    }
    myItemList.addMouseListener(createItemListMouseListener());
    myItemList.addKeyListener(keyListener);
    registerKeyboardActions();

    myLastSelectedGroup = DataModel.COMMON;
  }

  @NotNull
  @TestOnly
  AnAction getAddToDesignAction() {
    //noinspection ReturnOfInnerClass
    return myAddToDesignAction;
  }

  @NotNull
  @TestOnly
  AnAction getAndroidDocAction() {
    return myAndroidDocAction;
  }

  @NotNull
  @TestOnly
  AnAction getMaterialDocAction() {
    //noinspection ReturnOfInnerClass
    return myMaterialDocAction;
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
  private MouseListener createItemListMouseListener() {
    return new MouseAdapter() {
      @Override
      public void mousePressed(@NotNull MouseEvent event) {
        if (event.isPopupTrigger()) {
          showPopupMenu(event);
        }
      }

      // We really should handle mouseClick instead of mouseReleased events.
      // However on Mac with "Show Scroll bars: When scrolling" is set we are not receiving mouseClick events.
      // The mouseReleased events however are received every time we want to recognize the mouse click.
      @Override
      public void mouseReleased(@NotNull MouseEvent event) {
        if (event.isPopupTrigger()) {
          showPopupMenu(event);
        }
        else if (SwingUtilities.isLeftMouseButton(event) && !event.isControlDown()) {
          mouseClick(event);
        }
      }

      private void mouseClick(@NotNull MouseEvent event) {
        // b/111124139 On Windows the scrollbar width is included in myItemList.getWidth().
        // Use getCellBounds() instead if possible.
        Rectangle rect = myItemList.getCellBounds(0, 0);
        int width = rect != null ? rect.width : myItemList.getWidth();
        if (event.getX() < width - JBUI.scale(DOWNLOAD_WIDTH) || event.getX() >= myItemList.getWidth()) {
          // Ignore mouse clicks that are outside the download button
          return;
        }
        int index = myItemList.locationToIndex(event.getPoint());
        Palette.Item item = myItemList.getModel().getElementAt(index);
        myDependencyManager.ensureLibraryIsIncluded(item);
      }

      private void showPopupMenu(@NotNull MouseEvent event) {
        if (myItemList.isEmpty()) {
          return;
        }
        myItemList.setSelectedIndex(myItemList.locationToIndex(event.getPoint()));
        DataContext dataContext = DataManager.getInstance().getDataContext(myItemList);
        JBPopupFactory.getInstance()
          .createActionGroupPopup(null, myActionGroup, dataContext, null, true)
          .show(new RelativePoint(event));
      }
    };
  }

  @NotNull
  private KeyListener createKeyListener() {
    return new KeyAdapter() {
      @Override
      public void keyTyped(@NotNull KeyEvent event) {
        if (Character.isAlphabetic(event.getKeyChar())) {
          startFiltering(String.valueOf(event.getKeyChar()));
        }
      }
    };
  }

  private void registerKeyboardActions() {
    myItemList.registerKeyboardAction(event -> keyboardActionPerformed(event, myAddToDesignAction),
                                      KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                                      JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    myItemList.registerKeyboardAction(event -> keyboardActionPerformed(event, myAndroidDocAction),
                                      KeyStroke.getKeyStroke(KeyEvent.VK_F1, InputEvent.SHIFT_DOWN_MASK),
                                      JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  private void startFiltering(@NotNull String initialSearchString) {
    if (myToolWindow != null) {
      myToolWindow.startFiltering(initialSearchString);
    }
  }

  private void keyboardActionPerformed(@NotNull ActionEvent event, @NotNull AnAction action) {
    DataContext dataContext = DataManager.getInstance().getDataContext(this);
    InputEvent inputEvent = event.getSource() instanceof InputEvent ? (InputEvent)event.getSource() : null;
    action.actionPerformed(AnActionEvent.createFromAnAction(action, inputEvent, ActionPlaces.TOOLWINDOW_POPUP, dataContext));
  }

  @NotNull
  private ActionGroup createPopupActionGroup() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(myAddToDesignAction);
    group.add(myFavoriteAction);
    group.addSeparator();
    group.add(myAndroidDocAction);
    group.add(myMaterialDocAction);
    return group;
  }

  @Nullable
  private String getSelectedTagName() {
    Palette.Item item = myItemList.getSelectedValue();
    return item != null ? item.getTagName() : null;
  }

  private void categorySelectionChanged() {
    Palette.Group newSelection = myCategoryList.getSelectedValue();
    if (newSelection == null) {
      myLastSelectedGroup = DataModel.COMMON;
      myCategoryList.setSelectedIndex(0);
      return;
    }
    myDataModel.categorySelectionChanged(newSelection);
    myLastSelectedGroup = newSelection;
    myItemList.setSelectedIndex(0);
    myItemList.setEmptyText(generateEmptyText(newSelection));
  }

  @NotNull
  private static Pair<String, String> generateEmptyText(@NotNull Palette.Group group) {
    if (group == DataModel.COMMON) {
      return Pair.create("No favorites", "Right click to add");
    }
    else if (group == DataModel.RESULTS) {
      return Pair.create("No matches found", "");
    }
    else {
      return Pair.create("Empty group", "");
    }
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
  @AnyThread
  public CategoryList getCategoryList() {
    return myCategoryList;
  }

  @NotNull
  @VisibleForTesting
  @AnyThread
  public ItemList getItemList() {
    return myItemList;
  }

  @Override
  public void requestFocus() {
    myCategoryList.requestFocus();
  }

  @Override
  public void registerCallbacks(@NotNull ToolWindowCallback toolWindow) {
    myToolWindow = toolWindow;
  }

  @Override
  public boolean supportsFiltering() {
    return true;
  }

  @Override
  public void setFilter(@NotNull String filter) {
    myDataModel.setFilterPattern(filter);
    Palette.Group newSelection = myDataModel.getCategoryListModel().contains(myLastSelectedGroup) ? myLastSelectedGroup : null;
    myCategoryList.clearSelection();
    myCategoryList.setSelectedValue(newSelection, true);
  }

  @Override
  @NotNull
  public KeyListener getFilterKeyListener() {
    return myFilterKeyListener;
  }

  @NotNull
  private KeyListener createFilterKeyListener() {
    return new KeyAdapter() {
      @Override
      public void keyPressed(@NotNull KeyEvent event) {
        if (myDataModel.hasFilterPattern() && event.getKeyCode() == KeyEvent.VK_ENTER && event.getModifiers() == 0 &&
            myItemList.getModel().getSize() == 1) {
          myItemList.requestFocus();
        }
      }
    };
  }

  @NotNull
  CompletableFuture<Void> setToolContextAsyncImpl(@Nullable DesignSurface<?> designSurface) {
    assert designSurface == null || designSurface instanceof NlDesignSurface;
    Module module = getModule(designSurface);
    CompletableFuture<Void> result;
    if (designSurface != null && module != null && myLayoutType != designSurface.getLayoutType()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      assert facet != null;
      assert designSurface.getLayoutType() instanceof LayoutEditorFileType;
      myLayoutType = (LayoutEditorFileType)designSurface.getLayoutType();
      result = myDataModel.setLayoutTypeAsync(facet, myLayoutType)
        .thenRunAsync(() -> {
          if (myDataModel.getCategoryListModel().hasExplicitGroups()) {
            setCategoryListVisible(true);
            myCategoryList.setSelectedIndex(0);
          }
          else {
            setCategoryListVisible(false);
            myDataModel.categorySelectionChanged(DataModel.COMMON);
            myItemList.setSelectedIndex(0);
          }
        }, EdtExecutorService.getInstance());
    }
    else {
      result = CompletableFuture.completedFuture(null);
    }
    myDesignSurface = new WeakReference<>(designSurface);
    return result;
  }

  @Override
  public void setToolContext(@Nullable DesignSurface<?> designSurface) {
    setToolContextAsyncImpl(designSurface);
  }

  private void setCategoryListVisible(boolean visible) {
    myCategoryScrollPane.setVisible(visible);
  }

  @Nullable
  private static Module getModule(@Nullable DesignSurface<?> designSurface) {
    Configuration configuration =
      designSurface != null && designSurface.getLayoutType().isEditable() ? designSurface.getConfiguration() : null;
    return configuration != null ? configuration.getModule() : null;
  }

  @Override
  public void dispose() {
  }

  @Nullable
  @Override
  public Object getData(@NotNull @NonNls String dataId) {
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

    @NotNull
    @Override
    public ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
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
      DumbService dumbService = DumbService.getInstance(myProject);
      if (dumbService.isDumb()) {
        dumbService.showDumbModeNotification("Dragging from the Palette is not available while indices are updating.");
        return null;
      }

      PreviewProvider.ImageAndDimension imageAndSize = myPreviewProvider.createPreview(component, item);
      BufferedImage image = imageAndSize.getImage();
      Dimension size = imageAndSize.getDimension();
      setDragImage(image);
      if (SystemInfo.isWindows) {
        // Windows uses opposite conventions for computing offset
        setDragImageOffset(new Point(image.getWidth() / 2, image.getHeight() / 2));
      }
      else {
        setDragImageOffset(new Point(-image.getWidth() / 2, -image.getHeight() / 2));
      }
      DnDTransferComponent dndComponent = new DnDTransferComponent(item.getTagName(), item.getXml(), size.width, size.height);
      Transferable transferable = new ItemTransferable(new DnDTransferItem(dndComponent));

      if (myToolWindow != null) {
        myToolWindow.autoHide();
      }
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
      if (myToolWindow != null) {
        myToolWindow.stopFiltering();
      }
      NlUsageTracker.getInstance(myDesignSurface.get()).logDropFromPalette(
        component.getTag(), component.getRepresentation(), getGroupName(), myDataModel.getMatchCount());
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
        Logger.getInstance(PalettePanel.class).warn("Could not un-serialize a transferable", ex);
      }
      return null;
    }
  }

  private class AddToDesignAction extends AnAction {

    private AddToDesignAction() {
      super("Add to Design");
      setShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      addComponentToModel(false /* checkOnly */);
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      event.getPresentation().setEnabled(addComponentToModel(true /* checkOnly */));
    }

    private boolean addComponentToModel(boolean checkOnly) {
      Palette.Item item = myItemList.getSelectedValue();
      if (item == null) {
        return false;
      }
      DesignSurface<?> surface = myDesignSurface.get();
      if (surface == null) {
        return false;
      }
      NlModel model = surface.getModel();
      if (model == null) {
        return false;
      }
      List<NlComponent> roots = model.getComponents();
      if (roots.isEmpty()) {
        return false;
      }
      SceneView sceneView = surface.getFocusedSceneView();
      if (sceneView == null) {
        return false;
      }
      DnDTransferComponent dndComponent = new DnDTransferComponent(item.getTagName(), item.getXml(), 0, 0);
      DnDTransferItem dndItem = new DnDTransferItem(dndComponent);
      InsertType insertType = model.determineInsertType(DragType.COPY, dndItem, checkOnly /* preview */);

      List<NlComponent> toAdd = model.createComponents(dndItem, insertType);

      NlComponent root = roots.get(0);
      if (!model.canAddComponents(toAdd, root, null, checkOnly)) {
        return false;
      }
      if (!checkOnly) {
        UtilsKt.addComponentsAndSelectedIfCreated(model,
                                                  toAdd,
                                                  root,
                                                  null,
                                                  insertType,
                                                  sceneView.getSurface().getSelectionModel());
        surface.getSelectionModel().setSelection(toAdd);
        surface.getLayeredPane().requestFocus();
      }
      return true;
    }
  }

  private class FavoriteAction extends ToggleAction {

    private FavoriteAction() {
      super("Favorite");
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent event) {
      Palette.Item item = myItemList.getSelectedValue();
      return item != null && myDataModel.isFavoriteItem(item);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent event, boolean state) {
      Palette.Item item = myItemList.getSelectedValue();
      if (item != null) {
        if (state) {
          myDataModel.addFavoriteItem(item);
        }
        else {
          myDataModel.removeFavoriteItem(item);
        }
      }
    }
  }

  private class MaterialDocAction extends AnAction {
    private static final String MATERIAL_DEFAULT_REFERENCE = "https://material.io/guidelines/material-design/introduction.html";

    private MaterialDocAction() {
      super("Material Guidelines");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      String reference = getReference();
      if (!reference.isEmpty()) {
        BrowserUtil.browse(reference);
      }
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      event.getPresentation().setEnabled(!getReference().isEmpty());
    }

    private String getReference() {
      Palette.Item item = myItemList.getSelectedValue();
      if (item == null) {
        return "";
      }
      String reference = item.getMaterialReference();
      if (reference == null) {
        reference = MATERIAL_DEFAULT_REFERENCE;
      }
      return StringUtil.notNullize(reference);
    }
  }
}
