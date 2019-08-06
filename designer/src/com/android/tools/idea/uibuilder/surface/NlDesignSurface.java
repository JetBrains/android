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

import static com.android.resources.Density.DEFAULT_DENSITY;
import static com.android.tools.idea.uibuilder.graphics.NlConstants.DEFAULT_SCREEN_OFFSET_X;
import static com.android.tools.idea.uibuilder.graphics.NlConstants.DEFAULT_SCREEN_OFFSET_Y;
import static com.android.tools.idea.uibuilder.graphics.NlConstants.RESIZING_HOVERING_SIZE;
import static com.android.tools.idea.uibuilder.graphics.NlConstants.SCREEN_DELTA;

import com.android.sdklib.devices.Device;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.DnDTransferComponent;
import com.android.tools.idea.common.model.DnDTransferItem;
import com.android.tools.idea.common.model.ItemTransferable;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneInteraction;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.DesignSurfaceActionHandler;
import com.android.tools.idea.common.surface.DesignSurfaceListener;
import com.android.tools.idea.common.surface.Interaction;
import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.common.surface.SceneLayer;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.rendering.RenderErrorModelFactory;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel;
import com.android.tools.idea.uibuilder.adaptiveicon.ShapeMenuAction;
import com.android.tools.idea.uibuilder.analytics.NlAnalyticsManager;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.editor.NlActionManager;
import com.android.tools.idea.uibuilder.error.RenderIssueProvider;
import com.android.tools.idea.uibuilder.mockup.editor.MockupEditor;
import com.android.tools.idea.uibuilder.model.NlComponentHelper;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.scene.RenderListener;
import com.android.utils.ImmutableCollectors;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.update.Update;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The {@link DesignSurface} for the layout editor, which contains the full background, rulers, one
 * or more device renderings, etc
 */
public class NlDesignSurface extends DesignSurface implements ViewGroupHandler.AccessoryPanelVisibility {
  public static class Builder {
    private final Project myProject;
    private final Disposable myParentDisposable;
    private boolean myIsPreview = false;
    private BiFunction<NlDesignSurface, NlModel, LayoutlibSceneManager> mySceneManagerProvider =
      NlDesignSurface::defaultSceneManagerProvider;
    private boolean myShowModelName = false;

    private Builder(@NotNull Project project, @NotNull Disposable parentDisposable) {
      myProject = project;
      myParentDisposable = parentDisposable;
    }

    /**
     * Marks the {@link NlDesignSurface} as being in preview mode.
     */
    @NotNull
    public Builder setIsPreview(boolean isPreview) {
      myIsPreview = isPreview;
      return this;
    }

    /**
     * Allows customizing the {@link LayoutlibSceneManager}. Use this method if you need to apply additional settings to it or if you
     * need to completely replace it, for example for tests.
     *
     * @see NlDesignSurface#defaultSceneManagerProvider(NlDesignSurface, NlModel)
     */
    @NotNull
    public Builder setSceneManagerProvider(@NotNull BiFunction<NlDesignSurface, NlModel, LayoutlibSceneManager> sceneManagerProvider) {
      mySceneManagerProvider = sceneManagerProvider;
      return this;
    }

    /**
     * Enables {@link NlDesignSurface} displaying of the model names when present.
     */
    @NotNull
    public Builder showModelNames() {
      myShowModelName = true;
      return this;
    }

    @NotNull
    public NlDesignSurface build() {
      return new NlDesignSurface(myProject, myParentDisposable, myIsPreview, myShowModelName, mySceneManagerProvider);
    }
  }

  @NotNull private SceneMode mySceneMode = SceneMode.Companion.loadPreferredMode();
  @SwingCoordinate private int myScreenX = DEFAULT_SCREEN_OFFSET_X;
  @SwingCoordinate private int myScreenY = DEFAULT_SCREEN_OFFSET_Y;
  private boolean myIsCanvasResizing = false;
  private boolean myShowModelNames = false;
  private boolean myStackVertically;
  private boolean myMockupVisible;
  private MockupEditor myMockupEditor;
  private boolean myCentered;
  private final boolean myIsInPreview;
  private ShapeMenuAction.AdaptiveIconShape myAdaptiveIconShape = ShapeMenuAction.AdaptiveIconShape.getDefaultShape();
  private final RenderListener myRenderListener = this::modelRendered;
  private RenderIssueProvider myRenderIssueProvider;
  private AccessoryPanel myAccessoryPanel = new AccessoryPanel(AccessoryPanel.Type.SOUTH_PANEL, true);
  @NotNull private final NlAnalyticsManager myAnalyticsManager;
  /**
   * Allows customizing the generation of {@link SceneManager}s
   */
  private final BiFunction<NlDesignSurface, NlModel, LayoutlibSceneManager> mySceneManagerProvider;

  private NlDesignSurface(@NotNull Project project,
                          @NotNull Disposable parentDisposable,
                          boolean isInPreview,
                          boolean showModelNames,
                          @NotNull BiFunction<NlDesignSurface, NlModel, LayoutlibSceneManager> sceneManagerProvider) {
    super(project, new SelectionModel(), parentDisposable);
    myAnalyticsManager = new NlAnalyticsManager(this);
    myAccessoryPanel.setSurface(this);
    myIsInPreview = isInPreview;
    myShowModelNames = showModelNames;
    mySceneManagerProvider = sceneManagerProvider;
  }

  /**
   * Default {@link LayoutlibSceneManager} provider.
   */
  @NotNull
  public static LayoutlibSceneManager defaultSceneManagerProvider(@NotNull NlDesignSurface surface, @NotNull NlModel model) {
    return new LayoutlibSceneManager(model, surface);
  }

  @NotNull
  public static Builder builder(@NotNull Project project, @NotNull Disposable parentDisposable) {
    return new Builder(project, parentDisposable);
  }

  @NotNull
  @Override
  protected SceneManager createSceneManager(@NotNull NlModel model) {
    LayoutlibSceneManager manager = mySceneManagerProvider.apply(this, model);
    manager.addRenderListener(myRenderListener);

    return manager;
  }

  @NotNull
  @Override
  public NlAnalyticsManager getAnalyticsManager() {
    return myAnalyticsManager;
  }

  public boolean isPreviewSurface() {
    return myIsInPreview;
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

  public boolean isShowModelNames() {
    return StudioFlags.NELE_DISPLAY_MODEL_NAME.get() && myShowModelNames;
  }

  @NotNull
  public SceneMode getSceneMode() {
    return mySceneMode;
  }

  public void setScreenMode(@NotNull SceneMode sceneMode, boolean setAsDefault) {

    if (setAsDefault) {
      SceneMode.Companion.savePreferredMode(sceneMode);
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
      layoutContent();
      updateScrolledAreaSize();
    }
  }

  /**
   * Builds a new {@link NlDesignSurface} with the default settings
   */
  @NotNull
  public static NlDesignSurface build(@NotNull Project project, @NotNull Disposable parentDisposable) {
    return new Builder(project, parentDisposable).build();
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
    SceneView view = getHoverSceneView(x, y);
    if (view == null) {
      // TODO: For keeping the behaviour as before in multi-model case, we return primary SceneView when there is no hovered SceneView.
      SceneManager manager = getSceneManager();
      if (manager != null) {
        view = manager.getSceneView();
      }
    }
    return view;
  }

  @NotNull
  @Override
  public Rectangle getRenderableBoundsForInvisibleComponents(@NotNull SceneView sceneView, @Nullable Rectangle rectangle) {
    if (rectangle == null) {
      rectangle = new Rectangle();
    }
    List<SceneView> sceneViews = new ArrayList<>();
    Iterator<SceneManager> managerIterator = myModelToSceneManagers.values().iterator();
    // If there is no SceneManager then this function shouldn't be called. (The input argument SceneView is from SceneManager.)
    assert managerIterator.hasNext();
    SceneManager primaryManager = managerIterator.next();
    sceneViews.add(primaryManager.getSceneView());
    if (mySceneMode == SceneMode.BOTH) {
      SceneView secondarySceneView = ((LayoutlibSceneManager)primaryManager).getSecondarySceneView();
      if (secondarySceneView != null) {
        // menu and preference always has only one SceneView even scene mode is both.
        sceneViews.add(secondarySceneView);
      }
    }
    managerIterator.forEachRemaining(it -> sceneViews.add(it.getSceneView()));

    Rectangle viewRect = myScrollPane.getViewport().getViewRect();
    if (sceneViews.size() == 1) {
      rectangle.setBounds(viewRect);
      return rectangle;
    }

    int leftBound = viewRect.x;
    int topBound = viewRect.y;
    int rightBound = viewRect.x + viewRect.width;
    int bottomBound = viewRect.y + viewRect.height;

    int index = sceneViews.indexOf(sceneView);
    assert index != -1;
    int lastSceneViewIndex = sceneViews.size() - 1;
    if (myStackVertically) {
      rectangle.x = leftBound;
      rectangle.width = rightBound - leftBound;

      // Calculate top bound.
      if (index == 0) {
        // The top-most SceneView can render to the top edge of DesignSurface.
        rectangle.y = topBound;
      }
      else {
        // The top side of SceneView shouldn't cover other SceneViews.
        SceneView previousSceneView = sceneViews.get(index - 1);
        int previousBottom = previousSceneView.getY() + previousSceneView.getSize().height;
        rectangle.y = Math.max(topBound, (previousBottom + sceneView.getY() - sceneView.getNameLabelHeight()) / 2);
      }

      // Calculate bottom bound (represent by height).
      if (index == lastSceneViewIndex) {
        // The last SceneView can render to the bottom edge of DesignSurface.
        rectangle.height = bottomBound - rectangle.y;
      }
      else {
        // The bottom side of SceneView shouldn't cover other SceneViews.
        SceneView nextSceneView = sceneViews.get(index + 1);
        int bottom = sceneView.getY() + sceneView.getSize().height;
        rectangle.height = Math.min(bottomBound, (bottom + nextSceneView.getY() - nextSceneView.getNameLabelHeight()) / 2) - rectangle.y;
      }
    }
    else {
      rectangle.y = topBound;
      rectangle.height = bottomBound - topBound;

      // Calculate left bound.
      if (index == 0) {
        // The left-most SceneView can render to the left edge of DesignSurface.
        rectangle.x = leftBound;
      }
      else {
        // The left side of SceneView shouldn't cover other SceneViews.
        SceneView previousSceneView = sceneViews.get(index - 1);
        int previousRight = previousSceneView.getX() + previousSceneView.getSize().width;
        rectangle.x = Math.max(leftBound, (previousRight + sceneView.getX()) / 2);
      }

      // Calculate right bound (represent by width).
      if (index == lastSceneViewIndex) {
        // The last SceneView can render to the right edge of DesignSurface.
        rectangle.width = rightBound - rectangle.x;
      }
      else {
        // The right side of SceneView shouldn't cover other SceneViews.
        SceneView nextSceneView = sceneViews.get(index + 1);
        int right = sceneView.getX() + sceneView.getSize().width;
        rectangle.width = Math.min(rightBound, (right + nextSceneView.getX()) / 2) - rectangle.x;
      }
    }
    return rectangle;
  }

  /**
   * Return the ScreenView under the given position
   *
   * @return the ScreenView, or null if we are not above one.
   */
  @Nullable
  @Override
  public SceneView getHoverSceneView(@SwingCoordinate int x, @SwingCoordinate int y) {
    List<SceneView> sceneViews = getSceneViews();
    for (SceneView view : sceneViews) {
      if (view.getX() <= x && x <= (view.getX() + view.getSize().width) && view.getY() <= y && y <= (view.getY() + view.getSize().height)) {
        return view;
      }
    }
    return null;
  }

  @NotNull
  private ImmutableList<SceneView> getSceneViews() {
    ImmutableList.Builder<SceneView> builder = new ImmutableList.Builder<>();
    for (SceneManager manager : myModelToSceneManagers.values()) {
      SceneView view = manager.getSceneView();
      builder.add(view);
      SceneView secondarySceneView = ((LayoutlibSceneManager)manager).getSecondarySceneView();
      if (secondarySceneView != null) {
        builder.add(secondarySceneView);
      }
    }
    return builder.build();
  }

  @Override
  public Dimension getScrolledAreaSize() {
    int contentWidth = getRequiredWidth();
    int contentHeight = getRequiredHeight();
    if (contentWidth <= 0 || contentHeight <= 0) {
      // There is no scrollable area, return null. This may happen when there is no Model, SceneManager, or SceneView.
      return null;
    }
    return new Dimension(contentWidth + 2 * DEFAULT_SCREEN_OFFSET_X,
                         contentHeight + 2 * DEFAULT_SCREEN_OFFSET_Y);
  }

  public void setAdaptiveIconShape(@NotNull ShapeMenuAction.AdaptiveIconShape adaptiveIconShape) {
    myAdaptiveIconShape = adaptiveIconShape;
  }

  @NotNull
  public ShapeMenuAction.AdaptiveIconShape getAdaptiveIconShape() {
    return myAdaptiveIconShape;
  }

  @NotNull
  @Override
  public AccessoryPanel getAccessoryPanel() {
    return myAccessoryPanel;
  }

  public void showInspectorAccessoryPanel(boolean show) {
    for (DesignSurfaceListener listener : ImmutableList.copyOf(myListeners)) {
      listener.showAccessoryPanel(this, show);
    }
  }

  @Override
  public void show(@NotNull AccessoryPanel.Type type, boolean show) {
    showInspectorAccessoryPanel(show);
  }

  /**
   * Returns true if we want to arrange screens vertically instead of horizontally
   */
  private static boolean isVerticalScreenConfig(@SwingCoordinate int availableWidth,
                                                @SwingCoordinate int availableHeight,
                                                @SwingCoordinate @NotNull Dimension preferredSize) {
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

  @Override
  public float getScreenScalingFactor() {
    return JBUIScale.sysScale(this);
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
    Iterator<SceneManager> iterator = myModelToSceneManagers.values().iterator();
    if (!iterator.hasNext()) {
      return;
    }
    LayoutlibSceneManager primaryManager = (LayoutlibSceneManager)iterator.next();
    Dimension primarySceneViewSize = primaryManager.getSceneView().getSize();

    // Position primary screen
    int availableWidth = myScrollPane.getWidth();
    int availableHeight = myScrollPane.getHeight();
    myStackVertically = isVerticalScreenConfig(availableWidth, availableHeight, primarySceneViewSize);

    // First find the top-left position of primary SceneView.
    if (!myIsCanvasResizing) {
      // If we are resizing the canvas, do not relocate the primary SceneView.
      if (myCentered && availableWidth > 10 && availableHeight > 10) {
        int requiredWidth = getRequiredWidth();
        int requiredHeight = getRequiredHeight();
        myScreenX = Math.max((availableWidth - requiredWidth) / 2, DEFAULT_SCREEN_OFFSET_X);
        myScreenY = Math.max((availableHeight - requiredHeight) / 2, DEFAULT_SCREEN_OFFSET_Y);
      }
      else {
        myScreenX = DEFAULT_SCREEN_OFFSET_X;
        myScreenY = DEFAULT_SCREEN_OFFSET_Y;
      }
    }

    // Calculate the positions of all scenes by using the position of primary SceneView.
    int nextX = myScreenX;
    int nextY = myScreenY;
    for (SceneManager manager : myModelToSceneManagers.values()) {
      SceneView sceneView = manager.getSceneView();
      SceneView secondView = ((LayoutlibSceneManager)manager).getSecondarySceneView();

      if (myStackVertically) {
        // top/bottom stacking
        nextY += sceneView.getNameLabelHeight();
        sceneView.setLocation(nextX, nextY);
        nextY += sceneView.getSize().height + SCREEN_DELTA;
        if (secondView != null) {
          nextY += secondView.getNameLabelHeight();
          secondView.setLocation(nextX, nextY);
          nextY += secondView.getSize().height + SCREEN_DELTA;
        }
      }
      else {
        // left/right ordering
        sceneView.setLocation(nextX, myScreenY);
        nextX += sceneView.getSize().width + SCREEN_DELTA;
        if (secondView != null) {
          secondView.setLocation(nextX, nextY);
          nextX += secondView.getSize().width + SCREEN_DELTA;
        }
      }
    }

    revalidate();
    repaint();
  }

  /**
   * Take the largest width when stacking vertically or combine all of them otherwise. <br>
   * When combining the width, all spaces between different {@link SceneView}s are included.
   *
   * @return the required width to display all {@link SceneView}s.
   */
  private int getRequiredWidth() {
    int requiredWidth = 0;
    if (myStackVertically) {
      for (SceneManager sceneManager : myModelToSceneManagers.values()) {
        SceneView view = sceneManager.getSceneView();
        requiredWidth = Math.max(requiredWidth, view.getSize().width);
        SceneView secondView = ((LayoutlibSceneManager)sceneManager).getSecondarySceneView();
        if (secondView != null) {
          requiredWidth = Math.max(requiredWidth, secondView.getSize().width);
        }
      }
    }
    else {
      for (SceneManager sceneManager : myModelToSceneManagers.values()) {
        SceneView view = sceneManager.getSceneView();
        requiredWidth += view.getSize().width;
        requiredWidth += SCREEN_DELTA;
        SceneView secondView = ((LayoutlibSceneManager)sceneManager).getSecondarySceneView();
        if (secondView != null) {
          requiredWidth += secondView.getSize().width;
          requiredWidth += SCREEN_DELTA;
        }
      }
      // Remove tailed space.
      requiredWidth -= SCREEN_DELTA;
    }
    return Math.max(0, requiredWidth);
  }

  /**
   * Combine all heights when stacking vertically or take the largest one otherwise.<br>
   * When combining the width, all spaces between different {@link SceneView}s are included.
   *
   * @return the required height to display all {@link SceneView}s.
   */
  private int getRequiredHeight() {
    int requiredHeight = 0;
    if (myStackVertically) {
      for (SceneManager sceneManager : myModelToSceneManagers.values()) {
        SceneView view = sceneManager.getSceneView();
        requiredHeight += view.getNameLabelHeight();
        requiredHeight += view.getSize().height;
        requiredHeight += SCREEN_DELTA;
        SceneView secondView = ((LayoutlibSceneManager)sceneManager).getSecondarySceneView();
        if (secondView != null) {
          requiredHeight += secondView.getNameLabelHeight();
          requiredHeight += secondView.getSize().height;
          requiredHeight += SCREEN_DELTA;
        }
      }
      // Remove tailed space.
      requiredHeight -= SCREEN_DELTA;
    }
    else {
      for (SceneManager sceneManager : myModelToSceneManagers.values()) {
        SceneView view = sceneManager.getSceneView();
        requiredHeight = Math.max(requiredHeight, view.getSize().height);
        SceneView secondView = ((LayoutlibSceneManager)sceneManager).getSecondarySceneView();
        if (secondView != null) {
          requiredHeight = Math.max(requiredHeight, secondView.getSize().height);
        }
      }
    }
    return Math.max(0, requiredHeight);
  }

  @Override
  @NotNull
  public ItemTransferable getSelectionAsTransferable() {
    NlModel model = getModel();

    ImmutableList<DnDTransferComponent> components =
      getSelectionModel().getSelection().stream()
        .map(component -> new DnDTransferComponent(component.getTagName(), component.getTagDeprecated().getText(),
                                                   NlComponentHelperKt.getW(component), NlComponentHelperKt.getH(component)))
        .collect(
          ImmutableCollectors.toImmutableList());
    return new ItemTransferable(new DnDTransferItem(model != null ? model.getId() : 0, components));
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

  @Override
  public void onSingleClick(@SwingCoordinate int x, @SwingCoordinate int y) {
    if (isPreviewSurface()) {
      // Highlight the clicked widget but keep focus in DesignSurface.
      // TODO: Remove this after when b/136174865 is implemented, which removes the preview mode.
      onClickPreview(x, y, false);
    }
    else {
      super.onSingleClick(x, y);
    }
  }

  @Override
  public void onDoubleClick(@SwingCoordinate int x, @SwingCoordinate int y) {
    if (isPreviewSurface()) {
      // Navigate the caret to the clicked widget and focus on text editor.
      // TODO: Remove this after when b/136174865 is implemented, which removes the preview mode.
      onClickPreview(x, y, true);
    }
    else {
      super.onDoubleClick(x, y);
    }
  }

  private void onClickPreview(int x, int y, boolean needsFocusEditor) {
    SceneView sceneView = getSceneView(x, y);
    if (sceneView == null) {
      return;
    }
    NlComponent component = Coordinates.findComponent(sceneView, x, y);
    if (component != null) {
      navigateToComponent(component, needsFocusEditor);
    }
  }

  @Override
  @NotNull
  public Dimension getContentSize(@Nullable Dimension dimension) {
    if (dimension == null) {
      dimension = new Dimension();
    }

    int width = getRequiredWidth();
    int height = getRequiredHeight();
    if (width == 0 && height == 0) {
      dimension.setSize(0, 0);
      return dimension;
    }

    dimension.setSize(width, height);
    return dimension;
  }

  @SwingCoordinate
  @Override
  protected Dimension getDefaultOffset() {
    return new Dimension(2 * DEFAULT_SCREEN_OFFSET_X, 2 * DEFAULT_SCREEN_OFFSET_Y);
  }

  @SwingCoordinate
  @NotNull
  @Override
  protected Dimension getPreferredContentSize(@SwingCoordinate int availableWidth, @SwingCoordinate int availableHeight) {
    int requiredWidth = 0;
    int requiredHeight = 0;

    for (SceneManager sceneManager : myModelToSceneManagers.values()) {
      Dimension size = sceneManager.getSceneView().getPreferredSize();
      SceneView secondarySceneView = ((LayoutlibSceneManager)sceneManager).getSecondarySceneView();
      if (myStackVertically) {
        requiredWidth = Math.max(requiredWidth, size.width);

        requiredHeight += size.height;
        requiredHeight += SCREEN_DELTA;
        if (secondarySceneView != null) {
          requiredHeight += size.height;
          requiredHeight += SCREEN_DELTA;
        }
      }
      else {
        requiredWidth += size.width;
        requiredWidth += SCREEN_DELTA;
        if (secondarySceneView != null) {
          requiredWidth += size.width;
          requiredWidth += SCREEN_DELTA;
        }

        requiredHeight = Math.max(requiredHeight, size.height);
      }
    }

    return new Dimension(requiredWidth, requiredHeight);
  }

  @Override
  public CompletableFuture<Void> setModel(@Nullable NlModel model) {
    myAccessoryPanel.setModel(model);
    return super.setModel(model);
  }

  @Override
  public void dispose() {
    myAccessoryPanel.setSurface(null);
    super.dispose();
  }

  @Override
  public void notifyComponentActivate(@NotNull NlComponent component, int x, int y) {
    ViewHandler handler = NlComponentHelperKt.getViewHandler(component);

    if (handler != null) {
      handler.onActivateInDesignSurface(component, getSceneManager().getViewEditor(), x, y);
    }
    super.notifyComponentActivate(component, x, y);
  }

  @NotNull
  @Override
  public Consumer<NlComponent> getComponentRegistrar() {
    return component -> NlComponentHelper.INSTANCE.registerComponent(component);
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

        ReadAction.run(() -> {
          Project project = getProject();
          if (project.isDisposed()) {
            return;
          }

          BuildMode gradleBuildMode = BuildSettings.getInstance(project).getBuildMode();
          RenderErrorModel model = gradleBuildMode != null && result.getLogger().hasErrors()
                                   ? RenderErrorModel.STILL_BUILDING_ERROR_MODEL
                                   : RenderErrorModelFactory
                                     .createErrorModel(NlDesignSurface.this, result,
                                                       DataManager.getInstance().getDataContext(getIssuePanel()));
          if (myRenderIssueProvider != null) {
            getIssueModel().removeIssueProvider(myRenderIssueProvider);
          }
          myRenderIssueProvider = new RenderIssueProvider(model);
          getIssueModel().addIssueProvider(myRenderIssueProvider);
        });
      }

      @Override
      public boolean canEat(Update update) {
        return true;
      }
    });
  }

  private void modelRendered() {
    updateErrorDisplay();
    repaint();
    layoutContent();
  }

  @Override
  public CompletableFuture<Void> forceUserRequestedRefresh() {
    ArrayList<CompletableFuture<Void>> refreshFutures = new ArrayList<>();
    for (SceneManager sceneManager : myModelToSceneManagers.values()) {
      LayoutlibSceneManager layoutlibSceneManager = (LayoutlibSceneManager)sceneManager;
      refreshFutures.add(layoutlibSceneManager.requestUserInitiatedRender());
    }

    return CompletableFuture.allOf(refreshFutures.toArray(new CompletableFuture[refreshFutures.size()]));
  }

  @Override
  protected boolean useSmallProgressIcon() {
    if (getFocusedSceneView() == null) {
      return false;
    }

    LayoutlibSceneManager manager = getSceneManager();
    assert manager != null;

    return manager.getRenderResult() != null;
  }

  @Override
  protected double getMinScale() {
    return Math.max(getFitScale(false), 0.01);
  }

  @Override
  protected double getMaxScale() {
    return 10;
  }

  @Override
  public boolean canZoomToFit() {
    return Math.abs(getScale() - getFitScale(true)) > 0.01;
  }

  @Override
  public void scrollToCenter(@NotNull List<NlComponent> list) {
    Scene scene = getScene();
    SceneView view = getFocusedSceneView();
    if (list.isEmpty() || scene == null || view == null) {
      return;
    }
    @AndroidDpCoordinate Rectangle componentsArea = new Rectangle(0, 0, -1, -1);
    @AndroidDpCoordinate Rectangle componentRect = new Rectangle();
    list.stream().filter(nlComponent -> !nlComponent.isRoot()).forEach(nlComponent -> {
      SceneComponent component = scene.getSceneComponent(nlComponent);
      if (component == null) {
        return;
      }
      component.fillRect(componentRect);

      if (componentsArea.width < 0) {
        componentsArea.setBounds(componentRect);
      }
      else {
        componentsArea.add(componentRect);
      }
    });

    @SwingCoordinate Rectangle areaToCenter = Coordinates.getSwingRectDip(view, componentsArea);
    if (areaToCenter.isEmpty() || getLayeredPane().getVisibleRect().contains(areaToCenter)) {
      // No need to scroll to components if they are all fully visible on the surface.
      return;
    }

    @SwingCoordinate Dimension swingViewportSize = getScrollPane().getViewport().getExtentSize();
    @SwingCoordinate int targetSwingX = (int)areaToCenter.getCenterX();
    @SwingCoordinate int targetSwingY = (int)areaToCenter.getCenterY();
    // Center to position.
    setScrollPosition(targetSwingX - swingViewportSize.width / 2, targetSwingY - swingViewportSize.height / 2);
    double fitScale = getFitScale(areaToCenter.getSize(), false);

    if (getScale() > fitScale) {
      // Scale down to fit selection.
      setScale(fitScale, targetSwingX, targetSwingY);
    }
  }

  @Override
  public boolean isResizeAvailable() {
    Configuration configuration = getConfiguration();
    if (configuration == null) {
      return false;
    }
    Device device = configuration.getDevice();
    if (device == null) {
      return false;
    }

    if (StudioFlags.NELE_SIMPLER_RESIZE.get()) {
      return true;
    }

    return Configuration.CUSTOM_DEVICE_ID.equals(device.getId());
  }

  @Override
  protected void notifySelectionListeners(@NotNull List<NlComponent> newSelection) {
    super.notifySelectionListeners(newSelection);
    scrollToCenter(newSelection);
  }

  @VisibleForTesting
  @Nullable
  @Override
  public Interaction doCreateInteractionOnClick(@SwingCoordinate int mouseX, @SwingCoordinate int mouseY, @NotNull SceneView view) {
    ScreenView screenView = (ScreenView)view;
    Dimension size = screenView.getSize();
    Rectangle resizeZone =
      new Rectangle(view.getX() + size.width, screenView.getY() + size.height, RESIZING_HOVERING_SIZE, RESIZING_HOVERING_SIZE);
    if (resizeZone.contains(mouseX, mouseY) && isResizeAvailable()) {
      Configuration configuration = getConfiguration();
      assert configuration != null;

      if (StudioFlags.NELE_SIMPLER_RESIZE.get()) {
        return new SimplerCanvasResizeInteraction(this, screenView, configuration);
      }
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

    Interaction interaction = null;

    // Give a chance to the current selection's parent handler
    if (!selectionModel.isEmpty()) {
      NlComponent primary = screenView.getSelectionModel().getPrimary();
      NlComponent parent = primary != null ? primary.getParent() : null;
      if (parent != null) {
        ViewGroupHandler handler = NlComponentHelperKt.getViewGroupHandler(parent);
        if (handler != null) {
          interaction = handler.createInteraction(screenView, primary);
        }
      }
    }

    if (interaction == null) {
      // Check if we have a ViewGroupHandler that might want
      // to handle the entire interaction
      ViewGroupHandler viewGroupHandler = component != null ? NlComponentHelperKt.getViewGroupHandler(component) : null;
      if (viewGroupHandler != null) {
        interaction = viewGroupHandler.createInteraction(screenView, component);
      }
    }

    if (interaction == null) {
      interaction = new SceneInteraction(screenView);
    }
    return interaction;
  }

  @Override
  @Nullable
  public Interaction createInteractionOnDrag(@NotNull SceneComponent draggedSceneComponent, @Nullable SceneComponent primary) {
    if (primary == null) {
      primary = draggedSceneComponent;
    }
    List<NlComponent> dragged;
    NlComponent primaryNlComponent = primary.getNlComponent();
    // Dragging over a non-root component: move the set of components (if the component dragged over is
    // part of the selection, drag them all, otherwise drag just this component)
    if (getSelectionModel().isSelected(draggedSceneComponent.getNlComponent())) {
      dragged = Lists.newArrayList();

      // Make sure the primary is the first element
      if (primary.getParent() == null) {
        primaryNlComponent = null;
      }
      else {
        dragged.add(primaryNlComponent);
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

  @NotNull
  @Override
  protected DesignSurfaceActionHandler createActionHandler() {
    return new NlDesignSurfaceActionHandler(this);
  }

  @NotNull
  @Override
  public List<NlComponent> getSelectableComponents() {
    NlModel model = getModel();
    if (model == null) {
      return Collections.emptyList();
    }

    List<NlComponent> roots = model.getComponents();
    if (roots.isEmpty()) {
      return Collections.emptyList();
    }

    NlComponent root = roots.get(0);
    if (root == null) {
      return Collections.emptyList();
    }

    return root.flatten().collect(Collectors.toList());
  }
}
