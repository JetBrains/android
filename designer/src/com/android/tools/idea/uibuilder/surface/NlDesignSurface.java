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
package com.android.tools.idea.uibuilder.surface;

import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.editor.ActionManager;
import com.android.tools.idea.common.model.*;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.surface.*;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.rendering.RenderErrorModelFactory;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel;
import com.android.tools.idea.uibuilder.adaptiveicon.ShapeMenuAction;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.editor.NlActionManager;
import com.android.tools.idea.uibuilder.menu.NavigationViewSceneView;
import com.android.tools.idea.uibuilder.mockup.editor.MockupEditor;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.android.SdkConstants.ATTR_SHOW_IN;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.annotations.VisibleForTesting.Visibility;
import static com.android.tools.idea.uibuilder.graphics.NlConstants.*;

/**
 * The {@link DesignSurface} for the layout editor, which contains the full background, rulers, one
 * or more device renderings, etc
 */
public class NlDesignSurface extends DesignSurface {

  @NotNull private static SceneMode
    ourDefaultSceneMode = SceneMode.Companion.loadPreferredMode();

  @NotNull private SceneMode mySceneMode = ourDefaultSceneMode;
  @Nullable private SceneView mySecondarySceneView;
  @SwingCoordinate private int myScreenX = RULER_SIZE_PX + DEFAULT_SCREEN_OFFSET_X;
  @SwingCoordinate private int myScreenY = RULER_SIZE_PX + DEFAULT_SCREEN_OFFSET_Y;
  private boolean myIsCanvasResizing = false;
  private boolean myStackVertically;
  private boolean myMockupVisible;
  private MockupEditor myMockupEditor;
  private boolean myCentered;
  @Nullable private SceneView myPrimarySceneView;
  private final boolean myInPreview;
  private WeakReference<PanZoomPanel> myPanZoomPanel = new WeakReference<>(null);
  private ShapeMenuAction.AdaptiveIconShape myAdaptiveIconShape = ShapeMenuAction.AdaptiveIconShape.getDefaultShape();

  public NlDesignSurface(@NotNull Project project, boolean inPreview, @NotNull Disposable parentDisposable) {
    super(project, parentDisposable);
    myInPreview = inPreview;
  }

  public boolean isPreviewSurface() {
    return myInPreview;
  }

  /**
   * Tells this surface to resize mode. While on resizing mode, the views won't be auto positioned.
   * This can be disabled to avoid moving the screens around when the user is resizing the canvas. See {@link CanvasResizeInteraction}
   *
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

  @Override
  public boolean isLayoutDisabled() {
    return myIsCanvasResizing;
  }

  @Override
  public void activate() {
    super.activate();
    showPanZoomPanelIfRequired();
  }

  @NotNull
  public SceneMode getSceneMode() {
    return mySceneMode;
  }

  public void setScreenMode(@NotNull SceneMode sceneMode, boolean setAsDefault) {
    if (setAsDefault) {
      if (ourDefaultSceneMode != sceneMode) {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourDefaultSceneMode = sceneMode;

        SceneMode.Companion.savePreferredMode(sceneMode);
      }
    }

    if (sceneMode != mySceneMode) {
      // If we're going from 1 screens to 2 or back from 2 to 1, must adjust the zoom
      // to-fit the screen(s) in the surface
      boolean adjustZoom = sceneMode == SceneMode.BOTH || mySceneMode == SceneMode.BOTH;
      mySceneMode = sceneMode;

      createSceneViews();
      if (myPrimarySceneView != null) {
        if (adjustZoom) {
          zoomToFit();
        }
      }

      repaint();
    }
  }

  public boolean isDeviceFrameVisible() {
    return myDeviceFrames;
  }

  @NotNull
  @Override
  protected SceneManager createSceneManager(@NotNull NlModel model) {
    return new LayoutlibSceneManager(model, this);
  }

  @Nullable
  @Override
  public LayoutlibSceneManager getSceneManager() {
    return (LayoutlibSceneManager)super.getSceneManager();
  }

  private void setLayers() {
    ImmutableList.Builder<Layer> builder = ImmutableList.builder();
    assert myPrimarySceneView != null;
    builder.addAll(myPrimarySceneView.getLayers());
    if (mySecondarySceneView != null) {
      builder.addAll(mySecondarySceneView.getLayers());
    }
    setLayers(builder.build());
  }

  /**
   * Set the ConstraintsLayer and SceneLayer layers to paint,
   * even if they are set to paint only on mouse hover
   *
   * @param value if true, force painting
   */
  public void forceLayersPaint(boolean value) {
    for (Layer layer : getLayers()) {
      if (layer instanceof ConstraintsLayer) {
        ConstraintsLayer constraintsLayer = (ConstraintsLayer)layer;
        constraintsLayer.setTemporaryShow(value);
        repaint();
      }
      if (layer instanceof SceneLayer) {
        SceneLayer sceneLayer = (SceneLayer)layer;
        sceneLayer.setTemporaryShow(value);
        repaint();
      }
    }
  }

  @Nullable
  @Override
  public SceneView getCurrentSceneView() {
    return myPrimarySceneView;
  }

  @Override
  @Nullable
  public SceneView getSceneView(@SwingCoordinate int x, @SwingCoordinate int y) {
    // Currently only a single screen view active in the canvas.
    if (mySecondarySceneView != null && x >= mySecondarySceneView.getX() && y >= mySecondarySceneView.getY()) {
      return mySecondarySceneView;
    }
    return myPrimarySceneView;
  }

  /**
   * Return the ScreenView under the given position
   *
   * @return the ScreenView, or null if we are not above one.
   */
  @Nullable
  @Override
  public SceneView getHoverSceneView(@SwingCoordinate int x, @SwingCoordinate int y) {
    if (mySecondarySceneView != null
        && x >= mySecondarySceneView.getX() && x <= mySecondarySceneView.getX() + mySecondarySceneView.getSize().width
        && y >= mySecondarySceneView.getY() && y <= mySecondarySceneView.getY() + mySecondarySceneView.getSize().height) {
      return mySecondarySceneView;
    }
    if (myPrimarySceneView != null
        && x >= myPrimarySceneView.getX() && x <= myPrimarySceneView.getX() + myPrimarySceneView.getSize().width
        && y >= myPrimarySceneView.getY() && y <= myPrimarySceneView.getY() + myPrimarySceneView.getSize().height) {
      return myPrimarySceneView;
    }
    return null;
  }

  @Nullable
  public SceneView getSecondarySceneView() {
    return mySecondarySceneView;
  }

  @Override
  public Dimension getScrolledAreaSize() {
    if (myPrimarySceneView == null) {
      return null;
    }
    Dimension size = myPrimarySceneView.getSize();
    // TODO: Account for the size of the blueprint screen too? I should figure out if I can automatically make it jump
    // to the side or below based on the form factor and the available size
    Dimension dimension = new Dimension(size.width + 2 * DEFAULT_SCREEN_OFFSET_X,
                                        size.height + 2 * DEFAULT_SCREEN_OFFSET_Y);
    if (mySceneMode == SceneMode.BOTH) {
      if (isStackVertically()) {
        dimension.setSize(dimension.getWidth(),
                          dimension.getHeight() + size.height + SCREEN_DELTA);
      }
      else {
        dimension.setSize(dimension.getWidth() + size.width + SCREEN_DELTA,
                          dimension.getHeight());
      }
    }
    return dimension;
  }

  public void setAdaptiveIconShape(@NotNull ShapeMenuAction.AdaptiveIconShape adaptiveIconShape) {
    myAdaptiveIconShape = adaptiveIconShape;
  }

  @NotNull
  public ShapeMenuAction.AdaptiveIconShape getAdaptiveIconShape() {
    return myAdaptiveIconShape;
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

  public void setCentered(boolean centered) {
    myCentered = centered;
  }

  @NotNull
  @Override
  protected NlActionManager createActionManager() {
    return new NlActionManager(this);
  }

  @NotNull
  @Override
  public NlActionManager getActionManager() {
    return (NlActionManager)super.getActionManager();
  }

  @Override
  protected void layoutContent() {
    if (myPrimarySceneView == null) {
      return;
    }
    Dimension screenViewSize = myPrimarySceneView.getSize();

    // Position primary screen
    int availableWidth = myScrollPane.getWidth();
    int availableHeight = myScrollPane.getHeight();
    myStackVertically = isVerticalScreenConfig(availableWidth, availableHeight, screenViewSize);

    // If we are resizing the canvas, do not relocate the primary screen
    if (!myIsCanvasResizing) {
      if (myCentered && availableWidth > 10 && availableHeight > 10) {
        int requiredWidth = screenViewSize.width;
        if (mySceneMode == SceneMode.BOTH && !myStackVertically) {
          requiredWidth += SCREEN_DELTA;
          requiredWidth += screenViewSize.width;
        }
        myScreenX = Math.max((availableWidth - requiredWidth) / 2, RULER_SIZE_PX + DEFAULT_SCREEN_OFFSET_X);

        int requiredHeight = screenViewSize.height;
        if (mySceneMode == SceneMode.BOTH && myStackVertically) {
          requiredHeight += SCREEN_DELTA;
          requiredHeight += screenViewSize.height;
        }
        myScreenY = Math.max((availableHeight - requiredHeight) / 2, RULER_SIZE_PX + DEFAULT_SCREEN_OFFSET_Y);
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
    myPrimarySceneView.setLocation(myScreenX, myScreenY);

    // Position blueprint view
    if (mySecondarySceneView != null) {

      if (myStackVertically) {
        // top/bottom stacking
        mySecondarySceneView.setLocation(myScreenX, myScreenY + screenViewSize.height + SCREEN_DELTA);
      }
      else {
        // left/right ordering
        mySecondarySceneView.setLocation(myScreenX + screenViewSize.width + SCREEN_DELTA, myScreenY);
      }
    }
    if (myPrimarySceneView != null) {
      Scene scene = myPrimarySceneView.getScene();
      scene.needsRebuildList();
    }
    if (mySecondarySceneView != null) {
      Scene scene = mySecondarySceneView.getScene();
      scene.needsRebuildList();
    }
  }

  @Override
  @SwingCoordinate
  protected int getContentOriginX() {
    return myScreenX;
  }

  @Override
  @SwingCoordinate
  protected int getContentOriginY() {
    return myScreenY;
  }

  public boolean isStackVertically() {
    return myStackVertically;
  }

  @Override
  protected void doCreateSceneViews() {
    myPrimarySceneView = null;
    mySecondarySceneView = null;

    if (myModel == null) {
      return;
    }

    NlLayoutType type = myModel.getType();

    if (type.equals(NlLayoutType.MENU)) {
      doCreateSceneViewsForMenu();
      return;
    }

    if (type.equals(NlLayoutType.PREFERENCE_SCREEN)) {
      mySceneMode = SceneMode.SCREEN_ONLY;
    }

    myPrimarySceneView = mySceneMode.createPrimarySceneView(this, myModel);
    mySecondarySceneView = mySceneMode.createSecondarySceneView(this, myModel);
    if (mySecondarySceneView != null) {
      mySecondarySceneView.setLocation(myScreenX + myPrimarySceneView.getPreferredSize().width + 10, myScreenY);
    }

    updateErrorDisplay();
    getLayeredPane().setPreferredSize(myPrimarySceneView.getPreferredSize());

    setLayers();
    layoutContent();
  }

  private void doCreateSceneViewsForMenu() {
    mySceneMode = SceneMode.SCREEN_ONLY;
    XmlTag tag = myModel.getFile().getRootTag();

    // TODO See if there's a better way to trigger the NavigationViewSceneView. Perhaps examine the view objects?
    if (tag != null && Objects.equals(tag.getAttributeValue(ATTR_SHOW_IN, TOOLS_URI), NavigationViewSceneView.SHOW_IN_ATTRIBUTE_VALUE)) {
      myPrimarySceneView = new NavigationViewSceneView(this, myModel);
    }
    else {
      myPrimarySceneView = new ScreenView(this, myModel);
    }

    setLayers(myPrimarySceneView.getLayers());
    updateErrorDisplay();
    getLayeredPane().setPreferredSize(myPrimarySceneView.getPreferredSize());
    layoutContent();
  }

  @Override
  @NotNull
  public Dimension getContentSize(@Nullable Dimension dimension) {
    if (dimension == null) {
      dimension = new Dimension();
    }
    if (mySceneMode == SceneMode.BOTH
        && myPrimarySceneView != null && mySecondarySceneView != null) {
      if (isStackVertically()) {
        dimension.setSize(
          myPrimarySceneView.getSize().getWidth(),
          myPrimarySceneView.getSize().getHeight() + mySecondarySceneView.getSize().getHeight()
        );
      }
      else {
        dimension.setSize(
          myPrimarySceneView.getSize().getWidth() + mySecondarySceneView.getSize().getWidth(),
          myPrimarySceneView.getSize().getHeight()
        );
      }
    }
    else if (getCurrentSceneView() != null) {
      dimension.setSize(
        getCurrentSceneView().getSize().getWidth(),
        getCurrentSceneView().getSize().getHeight());
    }
    return dimension;
  }

  @Override
  protected Dimension getDefaultOffset() {
    return new Dimension(2 * DEFAULT_SCREEN_OFFSET_X + RULER_SIZE_PX, 2 * DEFAULT_SCREEN_OFFSET_Y + RULER_SIZE_PX);
  }

  @SwingCoordinate
  @NotNull
  @Override
  protected Dimension getPreferredContentSize(int availableWidth, int availableHeight) {
    assert myPrimarySceneView != null;
    Dimension preferredSize = myPrimarySceneView.getPreferredSize();

    int requiredWidth = preferredSize.width;
    int requiredHeight = preferredSize.height;
    if (mySceneMode == SceneMode.BOTH) {
      if (isVerticalScreenConfig(availableWidth, availableHeight, preferredSize)) {
        requiredHeight *= 2;
        requiredHeight += SCREEN_DELTA;
      }
      else {
        requiredWidth *= 2;
        requiredWidth += SCREEN_DELTA;
      }
    }

    return new Dimension(requiredWidth, requiredHeight);
  }

  @Override
  public void notifyComponentActivate(@NotNull NlComponent component) {
    ViewHandler handler = NlComponentHelperKt.getViewHandler(component);
    ViewEditor editor = getViewEditor();

    if (handler != null && editor != null) {
      handler.onActivateInComponentTree(editor, component);
    }

    super.notifyComponentActivate(component);
  }

  @Override
  public void notifyComponentActivate(@NotNull NlComponent component, int x, int y) {
    ViewHandler handler = NlComponentHelperKt.getViewHandler(component);
    ViewEditor editor = getViewEditor();

    if (handler != null && editor != null) {
      handler.onActivateInDesignSurface(editor, component, x, y);
    }
    super.notifyComponentActivate(component, x, y);
  }

  public void setMockupVisible(boolean mockupVisible) {
    myMockupVisible = mockupVisible;
    repaint();
  }

  public boolean isMockupVisible() {
    return myMockupVisible;
  }

  public void setMockupEditor(@Nullable MockupEditor mockupEditor) {
    myMockupEditor = mockupEditor;
  }

  @Nullable
  public MockupEditor getMockupEditor() {
    return myMockupEditor;
  }

  private void setPanZoomPanel(@Nullable PanZoomPanel panZoomPanel) {
    myPanZoomPanel = new WeakReference<>(panZoomPanel);
  }

  @Nullable
  public PanZoomPanel getPanZoomPanel() {
    return myPanZoomPanel.get();
  }

  /**
   * Shows the {@link PanZoomPanel} if the {@link PropertiesComponent} {@link PanZoomPanel#PROP_OPEN} is true
   */
  private void showPanZoomPanelIfRequired() {
    if (PanZoomPanel.isPropertyComponentOpen()) {
      setPanZoomPanelVisible(true);
    }
  }

  /**
   * If show is true, displays the {@link PanZoomPanel}.
   *
   * If the {@link DesignSurface} is not shows yet, it register a callback that will show the {@link PanZoomPanel}
   * once the {@link DesignSurface} is visible, otherwise it shows it directly.
   */
  public void setPanZoomPanelVisible(boolean show) {
    PanZoomPanel panel = myPanZoomPanel.get();
    if (show) {
      if (panel == null) {
        panel = new PanZoomPanel(this);
      }
      setPanZoomPanel(panel);
      if (isShowing()) {
        panel.showPopup();
      }
      else {
        PanZoomPanel finalPanel = panel;
        ComponentAdapter adapter = new ComponentAdapter() {
          @Override
          public void componentShown(ComponentEvent e) {
            finalPanel.showPopup();
            removeComponentListener(this);
          }
        };
        addComponentListener(adapter);
      }
    }
    else if (panel != null) {
      panel.closePopup();
    }
  }


  /**
   * Notifies the design surface that the given screen view (which must be showing in this design surface)
   * has been rendered (possibly with errors)
   */
  public void updateErrorDisplay() {
    assert ApplicationManager.getApplication().isDispatchThread() ||
           !ApplicationManager.getApplication().isReadAccessAllowed() : "Do not hold read lock when calling updateErrorDisplay!";

    getErrorQueue().cancelAllUpdates();
    getErrorQueue().queue(new Update("errors") {
      @Override
      public void run() {
        // Look up *current* result; a newer one could be available
        LayoutlibSceneManager sceneManager = getSceneManager();
        RenderResult result = sceneManager != null ? sceneManager.getRenderResult() : null;
        if (result == null) {
          return;
        }

        BuildMode gradleBuildMode = BuildSettings.getInstance(getProject()).getBuildMode();
        RenderErrorModel model = gradleBuildMode != null && result.getLogger().hasErrors()
                                 ? RenderErrorModel.STILL_BUILDING_ERROR_MODEL
                                 : RenderErrorModelFactory
                                   .createErrorModel(result, DataManager.getInstance().getDataContext(getIssuePanel()));
        getIssueModel().setRenderErrorModel(model);
      }

      @Override
      public boolean canEat(Update update) {
        return true;
      }
    });
  }

  @Override
  protected void modelRendered() {
    if (getCurrentSceneView() != null) {
      updateErrorDisplay();
    }
    super.modelRendered();
  }

  @Override
  protected boolean useSmallProgressIcon() {
    return getCurrentSceneView() != null && getSceneManager().getRenderResult() != null;
  }

  @Override
  protected double getMinScale() {
    return Math.min(getFitScale(false), 1);
  }

  @Override
  protected double getMaxScale() {
    return 10;
  }

  @VisibleForTesting(visibility = Visibility.PROTECTED)
  @Nullable
  @Override
  public Interaction doCreateInteractionOnClick(@SwingCoordinate int mouseX, @SwingCoordinate int mouseY, @NotNull SceneView view) {
    ScreenView screenView = (ScreenView)view;
    Dimension size = screenView.getSize();
    Rectangle resizeZone =
      new Rectangle(view.getX() + size.width, screenView.getY() + size.height, RESIZING_HOVERING_SIZE, RESIZING_HOVERING_SIZE);
    if (resizeZone.contains(mouseX, mouseY)) {
      return new CanvasResizeInteraction(this);
    }

    SelectionModel selectionModel = screenView.getSelectionModel();
    NlComponent component = Coordinates.findComponent(screenView, mouseX, mouseY);
    if (component == null) {
      // If we cannot find an element where we clicked, try to use the first element currently selected
      // (if any) to find the view group handler that may want to handle the mousePressed()
      // This allows us to correctly handle elements out of the bounds of the screen view.
      if (!selectionModel.isEmpty()) {
        component = selectionModel.getPrimary();
      }
      else {
        return null;
      }
    }
    // Check if we have a ViewGroupHandler that might want
    // to handle the entire interaction
    ViewGroupHandler viewGroupHandler = component != null ? NlComponentHelperKt.getViewGroupHandler(component) : null;
    if (viewGroupHandler == null) {
      return null;
    }

    Interaction interaction = null;

    // Give a chance to the current selection's parent handler
    if (!selectionModel.isEmpty()) {
      NlComponent primary = screenView.getSelectionModel().getPrimary();
      NlComponent parent = primary != null ? primary.getParent() : null;
      if (parent != null) {
        int ax = Coordinates.getAndroidX(screenView, mouseX);
        int ay = Coordinates.getAndroidY(screenView, mouseY);
        if (NlComponentHelperKt.containsX(primary, ax) && NlComponentHelperKt.containsY(primary, ay)) {
          ViewGroupHandler handler = NlComponentHelperKt.getViewGroupHandler(parent);
          if (handler != null) {
            interaction = handler.createInteraction(screenView, primary);
          }
        }
      }
    }

    if (interaction == null) {
      interaction = viewGroupHandler.createInteraction(screenView, component);
    }
    return interaction;
  }

  @Override
  @Nullable
  public Interaction createInteractionOnDrag(@NotNull SceneComponent draggedSceneComponent, @Nullable SceneComponent primary) {
    List<NlComponent> dragged;
    NlComponent primaryNlComponent = primary != null ? primary.getNlComponent() : null;
    // Dragging over a non-root component: move the set of components (if the component dragged over is
    // part of the selection, drag them all, otherwise drag just this component)
    if (getSelectionModel().isSelected(draggedSceneComponent.getNlComponent())) {
      dragged = Lists.newArrayList();

      // Make sure the primary is the first element
      if (primary != null) {
        if (primary.getParent() == null) {
          primaryNlComponent = null;
        }
        else {
          dragged.add(primaryNlComponent);
        }
      }

      for (NlComponent selected : getSelectionModel().getSelection()) {
        if (!selected.isRoot() && selected != primaryNlComponent) {
          dragged.add(selected);
        }
      }
    }
    else {
      dragged = Collections.singletonList(primaryNlComponent);
    }
    return new DragDropInteraction(this, dragged);
  }
}
