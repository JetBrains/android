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
package com.android.tools.idea.uibuilder.surface;

import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.ddms.screenshot.DeviceArtPainter;
import com.android.tools.idea.rendering.RenderErrorPanel;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.uibuilder.editor.NlActionManager;
import com.android.tools.idea.uibuilder.editor.NlEditorPanel;
import com.android.tools.idea.uibuilder.editor.NlPreviewForm;
import com.android.tools.idea.uibuilder.handlers.constraint.WidgetNavigatorPanel;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.palette.ScalableDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView.ScreenViewType;
import com.google.common.collect.Lists;
import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.Magnificator;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.ButtonlessScrollBarUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.uibuilder.graphics.NlConstants.*;

/**
 * The design surface in the layout editor, which contains the full background, rulers, one
 * or more device renderings, etc
 */
public class DesignSurface extends JPanel implements Disposable, ScalableDesignSurface {
  public static final boolean SIZE_ERROR_PANEL_DYNAMICALLY = true;
  private static final Integer LAYER_PROGRESS = JLayeredPane.POPUP_LAYER + 100;

  private final Project myProject;
  private final DesignerEditorPanelFacade myDesigner;
  private boolean myRenderHasProblems;
  private boolean myStackVertically;

  private boolean myIsCanvasResizing = false;

  public enum ScreenMode {
    SCREEN_ONLY(ScreenViewType.NORMAL),
    BLUEPRINT_ONLY(ScreenViewType.BLUEPRINT),
    BOTH(ScreenViewType.NORMAL);

    private final ScreenViewType myScreenViewType;

    private ScreenMode(@NotNull ScreenViewType screenViewType) {
      myScreenViewType = screenViewType;
    }

    @NotNull
    public ScreenMode next() {
      ScreenMode[] values = values();
      return values[(ordinal() + 1) % values.length];
    }

    @NotNull
    private ScreenViewType getScreenViewType() {
      return myScreenViewType;
    }

    private static final String SCREEN_MODE_PROPERTY = "NlScreenMode";

    @NotNull
    public static ScreenMode loadDefault() {
      String modeName = PropertiesComponent.getInstance().getValue(SCREEN_MODE_PROPERTY);
      for (ScreenMode mode : values()) {
        if (mode.name().equals(modeName)) {
          return mode;
        }
      }

      return ScreenMode.BOTH;
    }

    public static void saveDefault(@NotNull ScreenMode mode) {
      PropertiesComponent.getInstance().setValue(SCREEN_MODE_PROPERTY, mode.name());
    }
  }

  @NotNull private static ScreenMode ourDefaultScreenMode = ScreenMode.loadDefault();

  @NotNull private ScreenMode myScreenMode = ourDefaultScreenMode;
  @Nullable private ScreenView myScreenView;
  @Nullable private ScreenView myBlueprintView;
  @SwingCoordinate private int myScreenX = RULER_SIZE_PX + DEFAULT_SCREEN_OFFSET_X;
  @SwingCoordinate private int myScreenY = RULER_SIZE_PX + DEFAULT_SCREEN_OFFSET_Y;

  private double myScale = 1;
  @NotNull private final JScrollPane myScrollPane;
  private final MyLayeredPane myLayeredPane;
  private boolean myDeviceFrames = false;
  private final List<Layer> myLayers = Lists.newArrayList();
  private final InteractionManager myInteractionManager;
  private final GlassPane myGlassPane;
  private final RenderErrorPanel myErrorPanel;
  private List<DesignSurfaceListener> myListeners;
  private List<PanZoomListener> myZoomListeners;
  private boolean myCentered;
  private final NlActionManager myActionManager = new NlActionManager(this);

  public DesignSurface(@NotNull Project project, @NotNull DesignerEditorPanelFacade designer) {
    super(new BorderLayout());
    myProject = project;
    myDesigner = designer;

    setOpaque(true);
    setFocusable(true);
    setRequestFocusEnabled(true);
    setBackground(UIUtil.TRANSPARENT_COLOR);

    myInteractionManager = new InteractionManager(this);

    myLayeredPane = new MyLayeredPane();
    myLayeredPane.setBounds(0, 0, 100, 100);
    myGlassPane = new GlassPane();
    myLayeredPane.add(myGlassPane, JLayeredPane.DRAG_LAYER);

    myProgressPanel = new MyProgressPanel();
    myProgressPanel.setName("Layout Editor Progress Panel");
    myLayeredPane.add(myProgressPanel, LAYER_PROGRESS);


    myScrollPane = new MyScrollPane();
    myScrollPane.setViewportView(myLayeredPane);
    myScrollPane.setBorder(null);
    myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
    myScrollPane.getHorizontalScrollBar().addAdjustmentListener(this::notifyPanningChanged);
    myScrollPane.getVerticalScrollBar().addAdjustmentListener(this::notifyPanningChanged);

    add(myScrollPane, BorderLayout.CENTER);

    myErrorPanel = new RenderErrorPanel();
    myErrorPanel.setName("Layout Editor Error Panel");
    myErrorPanel.setVisible(false);
    myLayeredPane.add(myErrorPanel, JLayeredPane.POPUP_LAYER);

    // TODO: Do this as part of the layout/validate operation instead
    addComponentListener(new ComponentListener() {
      @Override
      public void componentResized(ComponentEvent componentEvent) {
        updateScrolledAreaSize();
        if (isShowing() && getWidth() > 0 && getHeight() > 0) {
          zoomToFit();
        }
      }

      @Override
      public void componentMoved(ComponentEvent componentEvent) {
      }

      @Override
      public void componentShown(ComponentEvent componentEvent) {
      }

      @Override
      public void componentHidden(ComponentEvent componentEvent) {
      }
    });

    myInteractionManager.registerListeners();
    myActionManager.registerActions(myLayeredPane);

    Disposer.register(project, this);
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public boolean isPreviewSurface() {
    return myDesigner instanceof NlPreviewForm;
  }

  @NotNull
  @Override
  public NlLayoutType getLayoutType() {
    XmlFile file;
    if (myDesigner instanceof NlEditorPanel) {
      file = ((NlEditorPanel)myDesigner).getFile();
    }
    else if (myDesigner instanceof NlPreviewForm) {
      // TODO Will this ever happen?
      file = ((NlPreviewForm)myDesigner).getFile();
      assert file != null;
    }
    else {
      throw new IllegalStateException(myDesigner.toString());
    }
    return NlLayoutType.typeOf(file);
  }

  @Override
  public void minimizePaletteOnPreview() {
    if (isPreviewSurface()) {
      ApplicationManager.getApplication().invokeLater(((NlPreviewForm)myDesigner)::minimizePalette);
    }
  }

  @NotNull
  public NlActionManager getActionManager() {
    return myActionManager;
  }

  public void setCentered(boolean centered) {
    myCentered = centered;
  }

  /**
   * Tells this surface to resize mode. While on resizing mode, the views won't be auto positioned.
   * This can be disabled to avoid moving the screens around when the user is resizing the canvas. See {@link CanvasResizeInteraction}
   * @param isResizing true to enable the resize mode
   */
  public void setResizeMode(boolean isResizing) {
    myIsCanvasResizing = isResizing;
  }

  /**
   * Returns whether this surface is currently in resize mode or not. See {@link #setResizeMode(boolean)}
   */
  public boolean isCanvasResizing() {
    return myIsCanvasResizing;
  }

  @NotNull
  public ScreenMode getScreenMode() {
    return myScreenMode;
  }

  public void setScreenMode(@NotNull ScreenMode screenMode, boolean setAsDefault) {
    if (setAsDefault) {
      if (ourDefaultScreenMode != screenMode) {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourDefaultScreenMode = screenMode;

        ScreenMode.saveDefault(screenMode);
      }
    }

    if (screenMode != myScreenMode) {
      // If we're going from 1 screens to 2 or back from 2 to 1, must adjust the zoom
      // to-fit the screen(s) in the surface
      boolean adjustZoom = screenMode == ScreenMode.BOTH || myScreenMode == ScreenMode.BOTH;
      myScreenMode = screenMode;

      if (myScreenView != null) {
        NlModel model = myScreenView.getModel();
        setModel(null);
        setModel(model);
        if (adjustZoom) {
          zoomToFit();
        }
      }
    }
  }

  public void setModel(@Nullable NlModel model) {
    if (model == null && myScreenView == null) {
      return;
    }

    List<NlComponent> selectionBefore = Collections.emptyList();
    List<NlComponent> selectionAfter = Collections.emptyList();

    ScreenView previousScreenView = myScreenView;
    if (myScreenView != null) {
      myScreenView.getModel().removeListener(myModelListener);

      SelectionModel selectionModel = myScreenView.getSelectionModel();
      selectionBefore = selectionModel.getSelection();
      selectionModel.removeListener(mySelectionListener);
      myScreenView = null;
    }

    myLayers.clear();
    if (model != null) {
      myScreenView = new ScreenView(this, ScreenView.ScreenViewType.NORMAL, model);
      myScreenView.getModel().addListener(myModelListener);

      // If the model has already rendered, there may be errors to display,
      // so update the error panel to reflect that.
      updateErrorDisplay(myScreenView.getResult());
      myLayeredPane.setPreferredSize(myScreenView.getPreferredSize());

      NlLayoutType layoutType = myScreenView.getModel().getType();

      if (layoutType.equals(NlLayoutType.MENU) || layoutType.equals(NlLayoutType.PREFERENCE_SCREEN)) {
        myScreenMode = ScreenMode.SCREEN_ONLY;
      }

      myScreenView.setType(myScreenMode.getScreenViewType());

      addLayers(model);
      positionScreens();


      SelectionModel selectionModel = model.getSelectionModel();
      selectionModel.addListener(mySelectionListener);
      selectionAfter = selectionModel.getSelection();
    }
    else {
      myScreenView = null;
      myBlueprintView = null;
    }
    repaint();

    if (!selectionBefore.equals(selectionAfter)) {
      notifySelectionListeners(selectionAfter);
    }
    notifyScreenViewChanged();
  }

  private void addLayers(@NotNull NlModel model) {
    switch (myScreenMode) {
      case SCREEN_ONLY:
        addScreenLayers();
        break;
      case BLUEPRINT_ONLY:
        addBlueprintLayers(myScreenView);
        break;
      case BOTH:
        myBlueprintView = new ScreenView(this, ScreenViewType.BLUEPRINT, model);
        myBlueprintView.setLocation(myScreenX + myScreenView.getPreferredSize().width + 10, myScreenY);

        addScreenLayers();
        addBlueprintLayers(myBlueprintView);

        break;
      default:
        assert false : myScreenMode;
    }
  }

  private void addScreenLayers() {
    myLayers.add(new ScreenViewLayer(myScreenView));
    myLayers.add(new SelectionLayer(myScreenView));

    if (myScreenView.getModel().getType().isLayout()) {
      myLayers.add(new ConstraintsLayer(this, myScreenView, true));
    }

    myLayers.add(new WarningLayer(myScreenView));
  }

  private void addBlueprintLayers(@NotNull ScreenView view) {
    myLayers.add(new BlueprintLayer(view));
    myLayers.add(new SelectionLayer(view));
    myLayers.add(new ConstraintsLayer(this, view, false));
  }

  @Override
  public void dispose() {
  }

  private void updateScrolledAreaSize() {
    if (myScreenView == null) {
      return;
    }
    Dimension size = myScreenView.getSize();
    if (size != null) {
      // TODO: Account for the size of the blueprint screen too? I should figure out if I can automatically make it jump
      // to the side or below based on the form factor and the available size
      Dimension dimension = new Dimension(size.width + 2 * DEFAULT_SCREEN_OFFSET_X,
                                          size.height + 2 * DEFAULT_SCREEN_OFFSET_Y);
      myLayeredPane.setBounds(0, 0, dimension.width, dimension.height);
      myLayeredPane.setPreferredSize(dimension);
      myScrollPane.revalidate();
      myProgressPanel.setBounds(myScreenX, myScreenY, size.width, size.height);
    }
    else {
      myProgressPanel.setBounds(0, 0, getWidth(), getHeight());
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return myGlassPane;
  }

  @Override
  protected void paintChildren(Graphics graphics) {
    super.paintChildren(graphics);

    if (isFocusOwner()) {
      graphics.setColor(UIUtil.getFocusedBoundsColor());
      graphics.drawRect(getX(), getY(), getWidth() - 1, getHeight() - 1);
    }
  }

  @Nullable
  @Override
  public ScreenView getCurrentScreenView() {
    return myScreenView;
  }

  @Nullable
  public ScreenView getScreenView(@SwingCoordinate int x, @SwingCoordinate int y) {
    // Currently only a single screen view active in the canvas.
    if (myBlueprintView != null && x >= myBlueprintView.getX() && y >= myBlueprintView.getY()) {
      return myBlueprintView;
    }
    return myScreenView;
  }

  /**
   * Return the ScreenView under the given position
   *
   * @param x
   * @param y
   * @return the ScreenView, or null if we are not above one.
   */
  @Nullable
  private ScreenView getHoverScreenView(@SwingCoordinate int x, @SwingCoordinate int y) {
    if (myBlueprintView != null
        && x >= myBlueprintView.getX() && x <= myBlueprintView.getX() + myBlueprintView.getSize().width
        && y >= myBlueprintView.getY() && y <= myBlueprintView.getY() + myBlueprintView.getSize().height) {
      return myBlueprintView;
    }
    if (myScreenView != null
        && x >= myScreenView.getX() && x <= myScreenView.getX() + myScreenView.getSize().width
        && y >= myScreenView.getY() && y <= myScreenView.getY() + myScreenView.getSize().height) {
      return myScreenView;
    }
    return null;
  }

  /**
   * Gives us a chance to change layers behaviour upon drag and drop interaction starting
   */
  public void startDragDropInteraction() {
    for (Layer layer : myLayers) {
      if (layer instanceof ConstraintsLayer) {
        ConstraintsLayer constraintsLayer = (ConstraintsLayer)layer;
        if (constraintsLayer.isShowOnHover() != true) {
          constraintsLayer.setShowOnHover(true);
          repaint();
        }
      }
    }
  }

  /**
   * Gives us a chance to change layers behaviour upon drag and drop interaction ending
   */
  public void stopDragDropInteraction() {
    for (Layer layer : myLayers) {
      if (layer instanceof ConstraintsLayer) {
        ConstraintsLayer constraintsLayer = (ConstraintsLayer)layer;
        if (constraintsLayer.isShowOnHover() != false) {
          constraintsLayer.setShowOnHover(false);
          repaint();
        }
      }
    }
  }

  @Nullable
  public ScreenView getBlueprintView() {
    return myBlueprintView;
  }

  /**
   * @param dimension the Dimension object to reuse to avoid reallocation
   * @return The total size of all the ScreenViews in the DesignSurface
   */
  @NotNull
  public Dimension getContentSize(@Nullable Dimension dimension) {
    if (dimension == null) {
      dimension = new Dimension();
    }
    if (myScreenMode == ScreenMode.BOTH
        && myScreenView != null && myBlueprintView != null) {
      if (isStackVertically()) {
        dimension.setSize(
          myScreenView.getSize().getWidth(),
          myScreenView.getSize().getHeight() + myBlueprintView.getSize().getHeight()
        );
      }
      else {
        dimension.setSize(
          myScreenView.getSize().getWidth() + myBlueprintView.getSize().getWidth(),
          myScreenView.getSize().getHeight()
        );
      }
    }
    else if (getCurrentScreenView() != null) {
      dimension.setSize(
        getCurrentScreenView().getSize().getWidth(),
        getCurrentScreenView().getSize().getHeight());
    }
    return dimension;
  }

  public void hover(@SwingCoordinate int x, @SwingCoordinate int y) {
    // For constraint layer, set show on hover if they are above their screenview
    ScreenView current = getHoverScreenView(x, y);
    for (Layer layer : myLayers) {
      if (layer instanceof ConstraintsLayer) {
        ConstraintsLayer constraintsLayer = (ConstraintsLayer)layer;
        boolean show = false;
        if (constraintsLayer.getScreenView() == current) {
          show = true;
        }
        if (constraintsLayer.isShowOnHover() != show) {
          constraintsLayer.setShowOnHover(show);
          repaint();
        }
      }
    }

    if (myErrorPanel.isVisible() && myRenderHasProblems) {
      // don't show any warnings on hover if there is already some errors that are being displayed
      // TODO: we should really move this logic into the error panel itself
      return;
    }

    // Currently, we use the hover action only to check whether we need to show a warning.
    for (Layer layer : myLayers) {
      String tooltip = layer.getTooltip(x, y);
      if (tooltip != null) {
        myErrorPanel.showWarning(tooltip);
        if (!myErrorPanel.isVisible()) {
          myErrorPanel.setVisible(true);
          revalidate();
        }
        else {
          repaint();
        }
        break;
      }
    }
  }

  public void resetHover() {
    if (myRenderHasProblems) {
      return;
    }
    // if we were showing some warnings, then close it.
    // TODO: similar to hover() method above, this logic of warning/error should be inside the error panel itself
    myErrorPanel.setVisible(false);
  }

  public void zoom(@NotNull ZoomType type) {
    switch (type) {
      case IN: {
        int current = (int)(myScale * 100);
        double scale = ZoomType.zoomIn(current) / 100.0;
        setScale(scale);
        repaint();
        break;
      }
      case OUT: {
        int current = (int)(myScale * 100);
        double scale = ZoomType.zoomOut(current) / 100.0;
        setScale(scale);
        repaint();
        break;
      }
      case ACTUAL:
        if (SystemInfo.isMac && UIUtil.isRetina()) {
          setScale(0.5);
        }
        else {
          setScale(1);
        }
        repaint();
        break;
      case FIT:
      case FIT_INTO:
        if (myScreenView == null) {
          return;
        }

        // Fit to zoom
        int availableWidth = myScrollPane.getWidth();
        int availableHeight = myScrollPane.getHeight();
        Dimension preferredSize = myScreenView.getPreferredSize();
        if (preferredSize != null) {
          int requiredWidth = preferredSize.width;
          int requiredHeight = preferredSize.height;
          availableWidth -= 2 * DEFAULT_SCREEN_OFFSET_X;
          availableHeight -= 2 * DEFAULT_SCREEN_OFFSET_Y;

          if (myScreenMode == ScreenMode.BOTH) {
            if (isVerticalScreenConfig(availableWidth, availableHeight, preferredSize)) {
              requiredHeight *= 2;
              requiredHeight += SCREEN_DELTA;
            }
            else {
              requiredWidth *= 2;
              requiredWidth += SCREEN_DELTA;
            }
          }

          double scaleX = (double)availableWidth / requiredWidth;
          double scaleY = (double)availableHeight / requiredHeight;
          double scale = Math.min(scaleX, scaleY);
          if (type == ZoomType.FIT_INTO) {
            scale = Math.min(1.0, scale);
          }
          setScale(scale);
          repaint();
        }

        break;
      default:
      case SCREEN:
        throw new UnsupportedOperationException("Not yet implemented: " + type);
    }
  }

  public void zoomActual() {
    zoom(ZoomType.ACTUAL);
  }

  public void zoomIn() {
    zoom(ZoomType.IN);
  }

  public void zoomOut() {
    zoom(ZoomType.OUT);
  }

  public void zoomToFit() {
    zoom(ZoomType.FIT);
  }

  /**
   * Returns true if we want to arrange screens vertically instead of horizontally
   */
  private static boolean isVerticalScreenConfig(int availableWidth, int availableHeight, @NotNull Dimension preferredSize) {
    boolean stackVertically = preferredSize.width > preferredSize.height;
    if (availableWidth > 10 && availableHeight > 3 * availableWidth / 2) {
      stackVertically = true;
    }
    return stackVertically;
  }

  @Override
  public double getScale() {
    return myScale;
  }

  @Override
  public Configuration getConfiguration() {
    return myScreenView != null ? myScreenView.getConfiguration() : null;
  }

  public void setConfiguration(@NotNull Configuration configuration) {
    if (myScreenView != null) {
      myScreenView.setConfiguration(configuration);
    }
  }

  public void setScrollPosition(int x, int y) {
    myScrollPane.getHorizontalScrollBar().setValue(x);
    myScrollPane.getVerticalScrollBar().setValue(y);
  }

  private void setScale(double scale) {
    if (Math.abs(scale - 1) < 0.0001) {
      scale = 1;
    }
    else if (scale < 0.01) {
      scale = 0.01;
    }
    else if (scale > 10) {
      scale = 10;
    }
    myScale = scale;
    positionScreens();
    updateScrolledAreaSize();
    notifyScaleChanged();
  }

  private void notifyScaleChanged() {
    if (myZoomListeners != null) {
      for (int i = 0; i < myZoomListeners.size(); i++) {
        myZoomListeners.get(i).zoomChanged(this);
      }
    }
  }

  private void notifyPanningChanged(AdjustmentEvent adjustmentEvent) {
    if (myZoomListeners != null) {
      for (int i = 0; i < myZoomListeners.size(); i++) {
        myZoomListeners.get(i).panningChanged(adjustmentEvent);
      }
    }
  }

  private void positionScreens() {
    if (myScreenView == null) {
      return;
    }
    Dimension screenViewSize = myScreenView.getSize();

    // Position primary screen
    int availableWidth = myScrollPane.getWidth();
    int availableHeight = myScrollPane.getHeight();
    myStackVertically = isVerticalScreenConfig(availableWidth, availableHeight, screenViewSize);

    // If we are resizing the canvas, do not relocate the primary screen
    if (!myIsCanvasResizing) {
      if (myCentered && availableWidth > 10 && availableHeight > 10) {
        int requiredWidth = screenViewSize.width;
        if (myScreenMode == ScreenMode.BOTH && !myStackVertically) {
          requiredWidth += SCREEN_DELTA;
          requiredWidth += screenViewSize.width;
        }
        if (requiredWidth < availableWidth) {
          myScreenX = (availableWidth - requiredWidth) / 2;
        }
        else {
          myScreenX = 0;
        }

        int requiredHeight = screenViewSize.height;
        if (myScreenMode == ScreenMode.BOTH && myStackVertically) {
          requiredHeight += SCREEN_DELTA;
          requiredHeight += screenViewSize.height;
        }
        if (requiredHeight < availableHeight) {
          myScreenY = (availableHeight - requiredHeight) / 2;
        }
        else {
          myScreenY = 0;
        }
      }
      else {
        if (myDeviceFrames) {
          myScreenX = RULER_SIZE_PX + 2 * DEFAULT_SCREEN_OFFSET_X;
          myScreenY = RULER_SIZE_PX + 2 * DEFAULT_SCREEN_OFFSET_Y;
        }
        else {
          myScreenX = RULER_SIZE_PX + DEFAULT_SCREEN_OFFSET_X;
          myScreenY = RULER_SIZE_PX + DEFAULT_SCREEN_OFFSET_Y;
        }
      }
    }
    myScreenView.setLocation(myScreenX, myScreenY);

    // Position blueprint view
    if (myBlueprintView != null) {

      if (myStackVertically) {
        // top/bottom stacking
        myBlueprintView.setLocation(myScreenX, myScreenY + screenViewSize.height + SCREEN_DELTA);
      }
      else {
        // left/right ordering
        myBlueprintView.setLocation(myScreenX + screenViewSize.width + SCREEN_DELTA, myScreenY);
      }
    }
  }

  public boolean isStackVertically() {
    return myStackVertically;
  }

  public void toggleDeviceFrames() {
    myDeviceFrames = !myDeviceFrames;
    positionScreens();
    repaint();
  }

  @NotNull
  public JComponent getLayeredPane() {
    return myLayeredPane;
  }

  private void notifySelectionListeners(@NotNull List<NlComponent> newSelection) {
    if (myListeners != null) {
      List<DesignSurfaceListener> listeners = Lists.newArrayList(myListeners);
      for (DesignSurfaceListener listener : listeners) {
        listener.componentSelectionChanged(this, newSelection);
      }
    }
  }

  private void notifyScreenViewChanged() {
    ScreenView screenView = myScreenView;
    NlModel model = myScreenView != null ? myScreenView.getModel() : null;
    if (myListeners != null) {
      List<DesignSurfaceListener> listeners = Lists.newArrayList(myListeners);
      for (DesignSurfaceListener listener : listeners) {
        listener.modelChanged(this, model);
        listener.screenChanged(this, screenView);
      }
    }
  }

  public void addListener(@NotNull DesignSurfaceListener listener) {
    if (myListeners == null) {
      myListeners = Lists.newArrayList();
    }
    else {
      myListeners.remove(listener); // ensure single registration
    }
    myListeners.add(listener);
  }

  public void removeListener(@NotNull DesignSurfaceListener listener) {
    if (myListeners != null) {
      myListeners.remove(listener);
    }
  }

  private final SelectionListener mySelectionListener = new SelectionListener() {
    @Override
    public void selectionChanged(@NotNull SelectionModel model, @NotNull List<NlComponent> selection) {
      if (myScreenView != null) {
        notifySelectionListeners(selection);
      }
      else {
        notifySelectionListeners(Collections.<NlComponent>emptyList());
      }
    }
  };

  private final ModelListener myModelListener = new ModelListener() {
    @Override
    public void modelChanged(@NotNull NlModel model) {
      model.render();
    }

    @Override
    public void modelRendered(@NotNull NlModel model) {
      if (myScreenView != null) {
        updateErrorDisplay(myScreenView.getResult());
        repaint();
        positionScreens();
      }
    }
  };

  public void addPanZoomListener(PanZoomListener listener) {
    if (myZoomListeners == null) {
      myZoomListeners = Lists.newArrayList();
    }
    else {
      myZoomListeners.remove(listener);
    }
    myZoomListeners.add(listener);
  }

  public void removePanZoomListener(PanZoomListener listener) {
    if(myZoomListeners != null) {
      myZoomListeners.remove(listener);
    }
  }

  /**
   * The editor has been activated
   */
  public void activate() {
    if (myScreenView != null) {
      myScreenView.getModel().activate();
    }
  }

  public void deactivate() {
    if (myScreenView != null) {
      myScreenView.getModel().deactivate();
    }

    myInteractionManager.cancelInteraction();
  }

  private void positionErrorPanel() {
    if (!myErrorPanel.isVisible()) {
      return;
    }
    int height = getHeight();
    int width = getWidth();
    int size;
    if (SIZE_ERROR_PANEL_DYNAMICALLY) { // TODO: Only do this when the error panel is showing
      boolean showingErrors = HighlightSeverity.ERROR.equals(myErrorPanel.getSeverity());
      size = computeErrorPanelHeight(showingErrors, height, myErrorPanel.getPreferredHeight(width) + 16);
    }
    else {
      size = height / 2;
    }

    myErrorPanel.setSize(width, size);
    myErrorPanel.setLocation(RULER_SIZE_PX, height - size);
  }

  private static int computeErrorPanelHeight(boolean showingErrors, int designerHeight, int preferredHeight) {
    int maxSize = designerHeight * 3 / 4; // error panel can take up to 3/4th of the designer
    int minSize = showingErrors ? designerHeight / 4 : 16; // but is at least 1/4th if errors are being shown
    if (preferredHeight < maxSize) {
      return Math.max(preferredHeight, minSize);
    }
    else {
      return maxSize;
    }
  }

  private static class MyScrollPane extends JBScrollPane {
    private MyScrollPane() {
      super(0);
      setOpaque(true);
      setBackground(UIUtil.TRANSPARENT_COLOR);
      setupCorners();
    }

    @NotNull
    @Override
    public JScrollBar createVerticalScrollBar() {
      return new MyScrollBar(Adjustable.VERTICAL);
    }

    @Override
    public JScrollBar createHorizontalScrollBar() {
      return new MyScrollBar(Adjustable.HORIZONTAL);
    }

    @Override
    protected boolean isOverlaidScrollbar(@Nullable JScrollBar scrollbar) {
      ScrollBarUI vsbUI = scrollbar == null ? null : scrollbar.getUI();
      return vsbUI instanceof ButtonlessScrollBarUI && !((ButtonlessScrollBarUI)vsbUI).alwaysShowTrack();
    }
  }

  private static class MyScrollBar extends JBScrollBar implements IdeGlassPane.TopComponent {
    private ScrollBarUI myPersistentUI;

    private MyScrollBar(@JdkConstants.AdjustableOrientation int orientation) {
      super(orientation);
      setOpaque(false);
    }

    @Override
    public boolean canBePreprocessed(MouseEvent e) {
      return JBScrollPane.canBePreprocessed(e, this);
    }

    @Override
    public void setUI(ScrollBarUI ui) {
      if (myPersistentUI == null) myPersistentUI = ui;
      super.setUI(myPersistentUI);
      setOpaque(false);
    }

    @Override
    public int getUnitIncrement(int direction) {
      return 5;
    }

    @Override
    public int getBlockIncrement(int direction) {
      return 1;
    }
  }

  private class MyLayeredPane extends JLayeredPane implements Magnificator, DataProvider {
    public MyLayeredPane() {
      setOpaque(true);
      setBackground(UIUtil.TRANSPARENT_COLOR);

      // Enable pinching to zoom
      putClientProperty(Magnificator.CLIENT_PROPERTY_KEY, this);
    }

    // ---- Implements Magnificator ----

    @Override
    public Point magnify(double scale, Point at) {
      // Handle screen zooming.
      // Note: This only seems to work (be invoked) on Mac with the Apple JDK (1.6) currently
      setScale(scale * myScale);
      DesignSurface.this.repaint();
      return new Point((int)(at.x * scale), (int)(at.y * scale));
    }

    @Override
    protected void paintComponent(@NotNull Graphics graphics) {
      super.paintComponent(graphics);

      Graphics2D g2d = (Graphics2D)graphics;
      // (x,y) coordinates of the top left corner in the view port
      int tlx = myScrollPane.getHorizontalScrollBar().getValue();
      int tly = myScrollPane.getVerticalScrollBar().getValue();

      paintBackground(g2d, tlx, tly);

      if (myScreenView == null) {
        return;
      }

      Composite oldComposite = g2d.getComposite();

      RenderResult result = myScreenView.getResult();
      boolean paintedFrame = false;
      if (myDeviceFrames && result != null && result.getRenderedImage() != null) {
        Configuration configuration = myScreenView.getConfiguration();
        Device device = configuration.getDevice();
        State deviceState = configuration.getDeviceState();
        DeviceArtPainter painter = DeviceArtPainter.getInstance();
        if (device != null && painter.hasDeviceFrame(device) && deviceState != null) {
          paintedFrame = true;
          g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
          painter.paintFrame(g2d, device, deviceState.getOrientation(), true, myScreenX, myScreenY,
                             (int)(myScale * result.getRenderedImage().getHeight()));
        }
      }

      if (paintedFrame) {
        // Only use alpha on the ruler bar if overlaying the device art
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.9f));
      }
      else {
        // Only show bounds dashed lines when there's no device
        paintBoundsRectangle(g2d);
      }

      g2d.setComposite(oldComposite);

      for (Layer layer : myLayers) {
        if (!layer.isHidden()) {
          layer.paint(g2d);
        }
      }

      // Temporary overlays:
      List<Layer> layers = myInteractionManager.getLayers();
      if (layers != null) {
        for (Layer layer : layers) {
          if (!layer.isHidden()) {
            layer.paint(g2d);
          }
        }
      }
    }

    private void paintBackground(@NotNull Graphics2D graphics, int lx, int ly) {
      int width = myScrollPane.getWidth() - RULER_SIZE_PX;
      int height = myScrollPane.getHeight() - RULER_SIZE_PX;
      graphics.setColor(DESIGN_SURFACE_BG);
      graphics.fillRect(RULER_SIZE_PX + lx, RULER_SIZE_PX + ly, width, height);
    }

    private void paintRulers(@NotNull Graphics2D g, int lx, int ly) {
      final Graphics2D graphics = (Graphics2D)g.create();
      try {
        int width = myScrollPane.getWidth();
        int height = myScrollPane.getHeight();

        graphics.setColor(RULER_BG);
        graphics.fillRect(lx, ly, width, RULER_SIZE_PX);
        graphics.fillRect(lx, ly + RULER_SIZE_PX, RULER_SIZE_PX, height - RULER_SIZE_PX);

        graphics.setColor(RULER_TICK_COLOR);

        int x = myScreenX + lx - lx % 100;
        int px2 = x + 10 - 100;
        for (int i = 1; i < 10; i++, px2 += 10) {
          if (px2 < myScreenX + lx - 100) {
            continue;
          }
          graphics.drawLine(px2, ly, px2, ly + RULER_MINOR_TICK_PX);
        }
        // TODO: The rulers need to be updated to track the scale!!!

        for (int px = 0; px < width; px += 100, x += 100) {
          graphics.drawLine(x, ly, x, ly + RULER_MAJOR_TICK_PX);
          px2 = x + 10;
          for (int i = 1; i < 10; i++, px2 += 10) {
            graphics.drawLine(px2, ly, px2, ly + RULER_MINOR_TICK_PX);
          }
        }

        int y = myScreenY + ly - ly % 100;
        int py2 = y + 10 - 100;
        for (int i = 1; i < 10; i++, py2 += 10) {
          if (py2 < myScreenY + ly - 100) {
            continue;
          }
          graphics.drawLine(lx, py2, lx + RULER_MINOR_TICK_PX, py2);
        }
        for (int py = 0; py < height; py += 100, y += 100) {
          graphics.drawLine(lx, y, lx + RULER_MAJOR_TICK_PX, y);
          py2 = y + 10;
          for (int i = 1; i < 10; i++, py2 += 10) {
            graphics.drawLine(lx, py2, lx + RULER_MINOR_TICK_PX, py2);
          }
        }

        graphics.setColor(RULER_TEXT_COLOR);
        graphics.setFont(RULER_TEXT_FONT);
        int xDelta = lx - lx % 100;
        x = myScreenX + 2 + xDelta;
        for (int px = 0; px < width; px += 100, x += 100) {
          graphics.drawString(Integer.toString(px + xDelta), x, ly + RULER_MAJOR_TICK_PX);
        }

        graphics.rotate(-Math.PI / 2);
        int yDelta = ly - ly % 100;
        y = myScreenY - 2 + yDelta;
        for (int py = 0; py < height; py += 100, y += 100) {
          graphics.drawString(Integer.toString(py + yDelta), -y, lx + RULER_MAJOR_TICK_PX);
        }
      }
      finally {
        graphics.dispose();
      }
    }

    private void paintBoundsRectangle(Graphics2D g2d) {
      if (myScreenView == null) {
        return;
      }

      g2d.setColor(BOUNDS_RECT_COLOR);
      int x = myScreenX;
      int y = myScreenY;
      Dimension size = myScreenView.getSize();
      if (size == null) {
        return;
      }

      Stroke prevStroke = g2d.getStroke();
      g2d.setStroke(DASHED_STROKE);

      g2d.drawLine(x - 1, y - BOUNDS_RECT_DELTA, x - 1, y + size.height + BOUNDS_RECT_DELTA);
      g2d.drawLine(x - BOUNDS_RECT_DELTA, y - 1, x + size.width + BOUNDS_RECT_DELTA, y - 1);
      g2d.drawLine(x + size.width, y - BOUNDS_RECT_DELTA, x + size.width, y + size.height + BOUNDS_RECT_DELTA);
      g2d.drawLine(x - BOUNDS_RECT_DELTA, y + size.height, x + size.width + BOUNDS_RECT_DELTA, y + size.height);

      // Draw the canvas resize symbol at the bottom right of the screen view
      // TODO: make those numbers constants, or use an icon
      g2d.setStroke(SOLID_STROKE);
      g2d.drawLine(x + size.width + 4, y + size.height + 4, x + size.width + 9, y + size.height + 4);
      g2d.drawLine(x + size.width + 4, y + size.height + 4, x + size.width + 4, y + size.height + 9);
      g2d.drawLine(x + size.width + 4, y + size.height + 9, x + size.width + 9, y + size.height + 4);

      g2d.drawLine(x + size.width + 3, y + size.height + 13, x + size.width + 13, y + size.height + 13);
      g2d.drawLine(x + size.width + 13, y + size.height + 3, x + size.width + 13, y + size.height + 13);
      g2d.drawLine(x + size.width + 3, y + size.height + 13, x + size.width + 13, y + size.height + 3);

      g2d.setStroke(prevStroke);
    }

    @Override
    protected void paintChildren(@NotNull Graphics graphics) {
      super.paintChildren(graphics); // paints the screen

      // Paint rulers on top of whatever is under the scroll panel

      Graphics2D g2d = (Graphics2D)graphics;
      // (x,y) coordinates of the top left corner in the view port
      int tlx = myScrollPane.getHorizontalScrollBar().getValue();
      int tly = myScrollPane.getVerticalScrollBar().getValue();
      paintRulers(g2d, tlx, tly);
    }

    @Override
    public void doLayout() {
      super.doLayout();
      positionErrorPanel();
    }

    @Nullable
    @Override
    public Object getData(@NonNls String dataId) {
      if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
        if (myScreenView != null) {
          SelectionModel selectionModel = myScreenView.getSelectionModel();
          NlComponent primary = selectionModel.getPrimary();
          if (primary != null) {
            return primary.getTag();
          }
        }
      }
      if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
        if (myScreenView != null) {
          SelectionModel selectionModel = myScreenView.getSelectionModel();
          List<NlComponent> selection = selectionModel.getSelection();
          List<XmlTag> list = Lists.newArrayListWithCapacity(selection.size());
          for (NlComponent component : selection) {
            list.add(component.getTag());
          }
          return list.toArray(new XmlTag[0]);
        }
      }

      return null;
    }
  }

  /**
   * Notifies the design surface that the given screenview (which must be showing in this design surface)
   * has been rendered (possibly with errors)
   */
  public void updateErrorDisplay(@Nullable final RenderResult result) {
    assert ApplicationManager.getApplication().isDispatchThread() ||
           !ApplicationManager.getApplication().isReadAccessAllowed() : "Do not hold read lock when calling updateErrorDisplay!";

    getErrorQueue().cancelAllUpdates();
    myRenderHasProblems = result != null && result.getLogger().hasProblems();
    if (myRenderHasProblems) {
      updateErrors(result);
    }
    else {
      UIUtil.invokeLaterIfNeeded(() -> {
        myErrorPanel.setVisible(false);
        repaint();
      });
    }
  }

  /**
   * When we have render errors for a given result, kick off a background computation
   * of the error panel HTML, which when done will update the UI thread
   */
  private void updateErrors(@Nullable final RenderResult result) {
    assert result != null && result.getLogger().hasProblems();

    getErrorQueue().cancelAllUpdates();
    getErrorQueue().queue(new Update("errors") {
      @Override
      public void run() {
        // Look up *current* result; a newer one could be available
        final RenderResult result = myScreenView != null ? myScreenView.getResult() : null;
        if (result == null) {
          return;
        }
        myErrorPanel.showErrors(result);
        ApplicationManager.getApplication().invokeLater(() -> {
          if (!myErrorPanel.isVisible()) {
            myErrorPanel.setVisible(true);
          }
          revalidate();
          repaint();
        });
      }

      @Override
      public boolean canEat(Update update) {
        return true;
      }
    });
  }

  @NotNull
  private MergingUpdateQueue getErrorQueue() {
    synchronized (myErrorQueueLock) {
      if (myErrorQueue == null) {
        myErrorQueue = new MergingUpdateQueue("android.error.computation", 200, true, null, myProject, null,
                                              Alarm.ThreadToUse.POOLED_THREAD);
      }
      return myErrorQueue;
    }
  }

  private final Object myErrorQueueLock = new Object();
  private MergingUpdateQueue myErrorQueue;

  private static class GlassPane extends JComponent {
    private static final long EVENT_FLAGS = AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK;

    public GlassPane() {
      enableEvents(EVENT_FLAGS);
    }

    @Override
    public void setEnabled(boolean enabled) {
      super.setEnabled(enabled);
      if (enabled) {
        enableEvents(EVENT_FLAGS);
      }
      else {
        disableEvents(EVENT_FLAGS);
      }
    }

    @Override
    protected void processKeyEvent(KeyEvent event) {
      if (!event.isConsumed()) {
        super.processKeyEvent(event);
      }
    }

    @Override
    protected void processMouseEvent(MouseEvent event) {
      if (event.getID() == MouseEvent.MOUSE_PRESSED) {
        requestFocusInWindow();
      }

      super.processMouseEvent(event);
    }
  }

  private final List<ProgressIndicator> myProgressIndicators = new ArrayList<ProgressIndicator>();

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private final MyProgressPanel myProgressPanel;

  public void registerIndicator(@NotNull ProgressIndicator indicator) {
    synchronized (myProgressIndicators) {
      myProgressIndicators.add(indicator);
      myProgressPanel.showProgressIcon();
    }
  }

  public void unregisterIndicator(@NotNull ProgressIndicator indicator) {
    synchronized (myProgressIndicators) {
      myProgressIndicators.remove(indicator);

      if (myProgressIndicators.size() == 0) {
        myProgressPanel.hideProgressIcon();
      }
    }
  }

  /**
   * Panel which displays the progress icon. The progress icon can either be a large icon in the
   * center, when there is no rendering showing, or a small icon in the upper right corner when there
   * is a rendering. This is necessary because even though the progress icon looks good on some
   * renderings, depending on the layout theme colors it is invisible in other cases.
   */
  private class MyProgressPanel extends JPanel {
    private AsyncProcessIcon mySmallProgressIcon;
    private AsyncProcessIcon myLargeProgressIcon;
    private boolean mySmall;
    private boolean myProgressVisible;

    private MyProgressPanel() {
      super(new BorderLayout());
      setOpaque(false);
      setVisible(false);
    }

    /**
     * The "small" icon mode isn't just for the icon size; it's for the layout position too; see {@link #doLayout}
     */
    private void setSmallIcon(boolean small) {
      if (small != mySmall) {
        if (myProgressVisible && getComponentCount() != 0) {
          AsyncProcessIcon oldIcon = getProgressIcon();
          oldIcon.suspend();
        }
        mySmall = true;
        removeAll();
        AsyncProcessIcon icon = getProgressIcon();
        add(icon, BorderLayout.CENTER);
        if (myProgressVisible) {
          icon.setVisible(true);
          icon.resume();
        }
      }
    }

    public void showProgressIcon() {
      if (!myProgressVisible) {
        boolean hasResult = myScreenView != null && myScreenView.getResult() != null;
        setSmallIcon(hasResult);
        myProgressVisible = true;
        setVisible(true);
        AsyncProcessIcon icon = getProgressIcon();
        if (getComponentCount() == 0) { // First time: haven't added icon yet?
          add(getProgressIcon(), BorderLayout.CENTER);
        }
        else {
          icon.setVisible(true);
        }
        icon.resume();
      }
    }

    public void hideProgressIcon() {
      if (myProgressVisible) {
        myProgressVisible = false;
        setVisible(false);
        AsyncProcessIcon icon = getProgressIcon();
        icon.setVisible(false);
        icon.suspend();
      }
    }

    @Override
    public void doLayout() {
      super.doLayout();
      setBackground(Color.RED);

      if (!myProgressVisible) {
        return;
      }

      // Place the progress icon in the center if there's no rendering, and in the
      // upper right corner if there's a rendering. The reason for this is that the icon color
      // will depend on whether we're in a light or dark IDE theme, and depending on the rendering
      // in the layout it will be invisible. For example, in Darcula the icon is white, and if the
      // layout is rendering a white screen, the progress is invisible.
      AsyncProcessIcon icon = getProgressIcon();
      Dimension size = icon.getPreferredSize();
      if (mySmall) {
        icon.setBounds(getWidth() - size.width - 1, 1, size.width, size.height);
      }
      else {
        icon.setBounds(getWidth() / 2 - size.width / 2, getHeight() / 2 - size.height / 2, size.width, size.height);
      }
    }

    @Override
    public Dimension getPreferredSize() {
      return getProgressIcon().getPreferredSize();
    }

    @NotNull
    private AsyncProcessIcon getProgressIcon() {
      return getProgressIcon(mySmall);
    }

    @NotNull
    private AsyncProcessIcon getProgressIcon(boolean small) {
      if (small) {
        if (mySmallProgressIcon == null) {
          mySmallProgressIcon = new AsyncProcessIcon("Android layout rendering");
          Disposer.register(myProject, mySmallProgressIcon);
        }
        return mySmallProgressIcon;
      }
      else {
        if (myLargeProgressIcon == null) {
          myLargeProgressIcon = new AsyncProcessIcon.Big("Android layout rendering");
          Disposer.register(myProject, myLargeProgressIcon);
        }
        return myLargeProgressIcon;
      }
    }
  }

  /**
   * Requests a new render of the layout.
   * @param invalidateModel if true, the model will be invalidated and re-inflated. When false, this will only repaint the current model.
   */
  public void requestRender(boolean invalidateModel) {
    ScreenView screenView = getCurrentScreenView();
    if (screenView != null) {
      if (invalidateModel) {
        // Invalidate the current model and request a render
        screenView.getModel().notifyModified(NlModel.ChangeType.REQUEST_RENDER);
      }
      else {
        screenView.getModel().requestRender();
      }
    }
  }

  @NotNull
  public JScrollPane getScrollPane() {
    return myScrollPane;
  }

  /**
   * Invalidates the current model and request a render of the layout. This will re-inflate the layout and render it.
   */
  public void requestRender() {
    requestRender(true);
  }
}
