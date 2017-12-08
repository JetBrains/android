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
import com.android.sdklib.devices.Device;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.*;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.surface.*;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.rendering.RenderErrorModelFactory;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel;
import com.android.tools.idea.uibuilder.adaptiveicon.ShapeMenuAction;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.editor.NlActionManager;
import com.android.tools.idea.uibuilder.mockup.editor.MockupEditor;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.google.common.collect.Lists;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.List;

import static com.android.annotations.VisibleForTesting.Visibility;
import static com.android.resources.Density.DEFAULT_DENSITY;
import static com.android.tools.idea.uibuilder.graphics.NlConstants.*;

/**
 * The {@link DesignSurface} for the layout editor, which contains the full background, rulers, one
 * or more device renderings, etc
 */
public class NlDesignSurface extends DesignSurface {

  @NotNull private static SceneMode
    ourDefaultSceneMode = SceneMode.Companion.loadPreferredMode();

  @NotNull private SceneMode mySceneMode = ourDefaultSceneMode;
  @SwingCoordinate private int myScreenX = RULER_SIZE_PX + DEFAULT_SCREEN_OFFSET_X;
  @SwingCoordinate private int myScreenY = RULER_SIZE_PX + DEFAULT_SCREEN_OFFSET_Y;
  private boolean myIsCanvasResizing = false;
  private boolean myStackVertically;
  private boolean myMockupVisible;
  private MockupEditor myMockupEditor;
  private boolean myCentered;
  private final boolean myInPreview;
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
      mySceneMode = sceneMode;

      LayoutlibSceneManager manager = getSceneManager();
      if (manager != null) {
        manager.updateSceneView();
        manager.requestLayoutAndRender(false);
      }
      if (!contentResizeSkipped()) {
        zoomToFit();
      }
      updateScrolledAreaSize();
      repaint();
    }
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

  @Override
  @Nullable
  public SceneView getSceneView(@SwingCoordinate int x, @SwingCoordinate int y) {
    LayoutlibSceneManager manager = getSceneManager();
    SceneView primarySceneView = manager != null ? manager.getSceneView() : null;
    SceneView secondarySceneView = manager != null ? manager.getSecondarySceneView() : null;

    if (secondarySceneView != null && x >= secondarySceneView.getX() && y >= secondarySceneView.getY()) {
      return secondarySceneView;
    }
    return primarySceneView;
  }

  /**
   * Return the ScreenView under the given position
   *
   * @return the ScreenView, or null if we are not above one.
   */
  @Nullable
  @Override
  public SceneView getHoverSceneView(@SwingCoordinate int x, @SwingCoordinate int y) {
    LayoutlibSceneManager manager = getSceneManager();
    SceneView primarySceneView = manager != null ? manager.getSceneView() : null;
    SceneView secondarySceneView = manager != null ? manager.getSecondarySceneView() : null;
    if (secondarySceneView != null
        && x >= secondarySceneView.getX() && x <= secondarySceneView.getX() + secondarySceneView.getSize().width
        && y >= secondarySceneView.getY() && y <= secondarySceneView.getY() + secondarySceneView.getSize().height) {
      return secondarySceneView;
    }
    if (primarySceneView != null
        && x >= primarySceneView.getX() && x <= primarySceneView.getX() + primarySceneView.getSize().width
        && y >= primarySceneView.getY() && y <= primarySceneView.getY() + primarySceneView.getSize().height) {
      return primarySceneView;
    }
    return null;
  }

  @Override
  public Dimension getScrolledAreaSize() {
    SceneManager manager = getSceneManager();
    SceneView primarySceneView = manager != null ? manager.getSceneView() : null;
    if (primarySceneView == null) {
      return null;
    }
    Dimension size = primarySceneView.getSize();
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

  /**
   * In the layout editor, Scene uses {@link AndroidDpCoordinate}s whereas rendering is done in (zoomed and offset)
   * {@link AndroidCoordinate}s. The scaling factor between them is the ratio of the screen density to the standard density (160).
   */
  @Override
  public float getSceneScalingFactor() {
    Configuration configuration = getConfiguration();
    if (configuration != null) {
      return configuration.getDensity().getDpiValue() / (float)DEFAULT_DENSITY;
    }
    return 1f;
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
    LayoutlibSceneManager manager = getSceneManager();
    SceneView primarySceneView = manager != null ? manager.getSceneView() : null;
    if (primarySceneView == null) {
      return;
    }

    Dimension screenViewSize = primarySceneView.getSize();

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
        myScreenX = RULER_SIZE_PX + DEFAULT_SCREEN_OFFSET_X;
        myScreenY = RULER_SIZE_PX + DEFAULT_SCREEN_OFFSET_Y;
      }
    }

    SceneView secondarySceneView = manager.getSecondarySceneView();

    primarySceneView.setLocation(myScreenX, myScreenY);

    // Position blueprint view
    if (secondarySceneView != null) {

      if (myStackVertically) {
        // top/bottom stacking
        secondarySceneView.setLocation(myScreenX, myScreenY + screenViewSize.height + SCREEN_DELTA);
      }
      else {
        // left/right ordering
        secondarySceneView.setLocation(myScreenX + screenViewSize.width + SCREEN_DELTA, myScreenY);
      }
    }

    manager.getScene().needsRebuildList();
    revalidate();
    repaint();
  }

  @Override
  @SwingCoordinate
  public int getContentOriginX() {
    return myScreenX;
  }

  @Override
  @SwingCoordinate
  public int getContentOriginY() {
    return myScreenY;
  }

  public boolean isStackVertically() {
    return myStackVertically;
  }

  @Override
  @NotNull
  public Dimension getContentSize(@Nullable Dimension dimension) {
    if (dimension == null) {
      dimension = new Dimension();
    }
    LayoutlibSceneManager manager = getSceneManager();
    SceneView primarySceneView = manager != null ? manager.getSceneView() : null;
    SceneView secondarySceneView = manager != null ? manager.getSecondarySceneView() : null;
    if (mySceneMode == SceneMode.BOTH
        && primarySceneView != null && secondarySceneView != null) {
      if (isStackVertically()) {
        dimension.setSize(
          primarySceneView.getSize().getWidth(),
          primarySceneView.getSize().getHeight() + secondarySceneView.getSize().getHeight()
        );
      }
      else {
        dimension.setSize(
          primarySceneView.getSize().getWidth() + secondarySceneView.getSize().getWidth(),
          primarySceneView.getSize().getHeight()
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
    SceneManager manager = getSceneManager();
    SceneView primarySceneView = manager != null ? manager.getSceneView() : null;
    if (primarySceneView == null) {
      return JBUI.emptySize();
    }
    Dimension preferredSize = primarySceneView.getPreferredSize();

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
  public void notifyComponentActivate(@NotNull NlComponent component, int x, int y) {
    ViewHandler handler = NlComponentHelperKt.getViewHandler(component);

    if (handler != null) {
      handler.onActivateInDesignSurface(component, x, y);
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

        Project project = getProject();

        if (project.isDisposed()) {
          return;
        }

        BuildMode gradleBuildMode = BuildSettings.getInstance(project).getBuildMode();
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
  public void forceUserRequestedRefresh() {
    LayoutlibSceneManager sceneManager = getSceneManager();
    if (sceneManager != null) {
      sceneManager.requestUserInitatedRender();
    }
  }

  @Override
  protected boolean useSmallProgressIcon() {
    if (getCurrentSceneView() == null) {
      return false;
    }

    LayoutlibSceneManager manager = getSceneManager();
    assert manager != null;

    return manager.getRenderResult() != null;
  }

  @Override
  protected double getMinScale() {
    return Math.min(getFitScale(false), 1);
  }

  @Override
  protected double getMaxScale() {
    return 10;
  }

  public boolean hasCustomDevice() {
    Configuration configuration = getConfiguration();
    if (configuration == null) {
      return false;
    }
    Device device = configuration.getDevice();
    return device != null && Configuration.CUSTOM_DEVICE_ID.equals(device.getId());
  }

  @VisibleForTesting(visibility = Visibility.PROTECTED)
  @Nullable
  @Override
  public Interaction doCreateInteractionOnClick(@SwingCoordinate int mouseX, @SwingCoordinate int mouseY, @NotNull SceneView view) {
    ScreenView screenView = (ScreenView)view;
    Dimension size = screenView.getSize();
    Rectangle resizeZone =
      new Rectangle(view.getX() + size.width, screenView.getY() + size.height, RESIZING_HOVERING_SIZE, RESIZING_HOVERING_SIZE);
    if (resizeZone.contains(mouseX, mouseY) && hasCustomDevice()) {
      Configuration configuration = getConfiguration();
      assert configuration != null;
      return new CanvasResizeInteraction(this, screenView, configuration);
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
