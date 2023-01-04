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
package com.android.tools.idea.common.surface;

import static com.android.tools.adtui.PannableKt.PANNABLE_KEY;
import static com.android.tools.adtui.ZoomableKt.ZOOMABLE_KEY;
import static com.android.tools.idea.actions.DesignerDataKeys.DESIGN_SURFACE;

import com.android.annotations.VisibleForTesting;
import com.android.annotations.concurrency.GuardedBy;
import com.android.annotations.concurrency.UiThread;
import com.android.tools.adtui.Zoomable;
import com.android.tools.adtui.actions.ZoomType;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.editor.PanZoomListener;
import com.android.tools.idea.common.analytics.DesignerAnalyticsManager;
import com.android.tools.idea.common.editor.ActionManager;
import com.android.tools.idea.common.error.IssueListener;
import com.android.tools.idea.common.error.IssueModel;
import com.android.tools.idea.common.error.IssuePanel;
import com.android.tools.idea.common.error.LintIssueProvider;
import com.android.tools.idea.common.lint.LintAnnotationsModel;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.DefaultSelectionModel;
import com.android.tools.idea.common.model.ItemTransferable;
import com.android.tools.idea.common.model.ModelListener;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SecondarySelectionModel;
import com.android.tools.idea.common.model.SelectionListener;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.surface.layout.DesignSurfaceViewport;
import com.android.tools.idea.common.surface.layout.MatchParentLayoutManager;
import com.android.tools.idea.common.surface.layout.NonScrollableDesignSurfaceViewport;
import com.android.tools.idea.common.surface.layout.ScrollableDesignSurfaceViewport;
import com.android.tools.idea.common.type.DefaultDesignerFileType;
import com.android.tools.idea.common.type.DesignerEditorFileType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.android.tools.idea.uibuilder.surface.layout.PositionableContent;
import com.android.tools.idea.uibuilder.surface.layout.PositionableContentLayoutManager;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.Magnificator;
import com.intellij.ui.components.ZoomableViewport;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.OverlayLayout;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.AndroidEditorSettings;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * A generic design surface for use in a graphical editor.
 */
public abstract class DesignSurface<T extends SceneManager> extends EditorDesignSurface
  implements Disposable, InteractableScenesSurface, Zoomable, ZoomableViewport {
  /**
   * Alignment for the {@link SceneView} when its size is less than the minimum size.
   * If the size of the {@link SceneView} is less than the minimum, this enum describes how to align the content within
   * the rectangle formed by the minimum size.
   */
  public enum SceneViewAlignment {
    /**
     * Align content to the left within the minimum size bounds.
     */
    LEFT(LEFT_ALIGNMENT),
    /**
     * Align content to the right within the minimum size bounds.
     */
    RIGHT(RIGHT_ALIGNMENT),
    /**
     * Center contents within the minimum size bounds.
     */
    CENTER(CENTER_ALIGNMENT);

    /**
     * The Swing alignment value equivalent to this alignment setting. See
     * {@link java.awt.Component#LEFT_ALIGNMENT}, {@link java.awt.Component#RIGHT_ALIGNMENT}
     * and {@link java.awt.Component#CENTER_ALIGNMENT}.
     */
    private final float mySwingAlignmentXValue;

    SceneViewAlignment(float swingValue) {
      mySwingAlignmentXValue = swingValue;
    }
  }

  /**
   * Determines the visibility of the zoom controls in this surface.
   */
  public enum ZoomControlsPolicy {
    /** The zoom controls will always be visible. */
    VISIBLE,
    /** The zoom controls will never be visible. */
    HIDDEN,
    /** The zoom controls will only be visible when the mouse is over the surface. */
    AUTO_HIDE
  }

  /**
   * If the difference between old and new scaling values is less than threshold, the scaling will be ignored.
   */
  @SurfaceZoomLevel
  protected static final double SCALING_THRESHOLD = 0.005;

  /**
   * Filter got {@link #getModels()} to avoid returning disposed elements
   **/
  private static final Predicate<NlModel> FILTER_DISPOSED_MODELS = input -> input != null && !input.getModule().isDisposed();
  /**
   * Filter got {@link #getSceneManagers()} ()} to avoid returning disposed elements
   **/
  private final Predicate<T> FILTER_DISPOSED_SCENE_MANAGERS =
    input -> input != null && FILTER_DISPOSED_MODELS.apply(input.getModel());

  private static final Integer LAYER_PROGRESS = JLayeredPane.POPUP_LAYER + 10;
  private static final Integer LAYER_MOUSE_CLICK = LAYER_PROGRESS + 10;

  private final Project myProject;

  @SurfaceScale private double myScale = 1;
  /**
   * The scale level when magnification started. This is used as a standard when the new scale level is evaluated.
   */
  @SurfaceScale private double myMagnificationStartedScale;

  /**
   * {@link JScrollPane} contained in this surface when zooming is enabled.
   */
  @Nullable private final JScrollPane myScrollPane;
  /**
   * Component that wraps the displayed content. If this is a scrollable surface, that will be the Scroll Pane.
   * Otherwise, it will be the ScreenViewPanel container.
   */
  @NotNull private final JComponent myContentContainerPane;
  @NotNull private final DesignSurfaceViewport myViewport;
  @NotNull private final JLayeredPane myLayeredPane;
  @NotNull private final SceneViewPanel mySceneViewPanel;
  @NotNull private final MouseClickDisplayPanel myMouseClickDisplayPanel;
  @VisibleForTesting
  private final GuiInputHandler myGuiInputHandler;
  private final Object myListenersLock = new Object();
  @GuardedBy("myListenersLock")
  protected final ArrayList<DesignSurfaceListener> myListeners = new ArrayList<>();
  @GuardedBy("myListenersLock")
  @NotNull private ArrayList<PanZoomListener> myZoomListeners = new ArrayList<>();
  private final ActionManager<? extends DesignSurface<T>> myActionManager;
  @NotNull private WeakReference<FileEditor> myFileEditorDelegate = new WeakReference<>(null);
  private final ReentrantReadWriteLock myModelToSceneManagersLock = new ReentrantReadWriteLock();
  @GuardedBy("myModelToSceneManagersLock")
  private final LinkedHashMap<NlModel, T> myModelToSceneManagers = new LinkedHashMap<>();

  private final SelectionModel mySelectionModel;
  private final ModelListener myModelListener = new ModelListener() {
    @Override
    public void modelDerivedDataChanged(@NotNull NlModel model) {
      updateNotifications();
    }

    @Override
    public void modelChanged(@NotNull NlModel model) {
      updateNotifications();
    }

    @Override
    public void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {
      repaint();
    }
  };

  @NotNull
  private final List<CompletableFuture<Void>> myRenderFutures = new ArrayList<>();

  protected final IssueModel myIssueModel;
  private final IssuePanel myIssuePanel;
  private final Object myErrorQueueLock = new Object();
  private MergingUpdateQueue myErrorQueue;
  private boolean myIsActive = false;
  private LintIssueProvider myLintIssueProvider;

  /**
   * Responsible for converting this surface state and send it for tracking (if logging is enabled).
   */
  @NotNull
  private final DesignerAnalyticsManager myAnalyticsManager;

  @SurfaceScale protected final double myMaxFitIntoScale;

  /**
   * When surface is opened at first time, it zoom-to-fit the content to make the previews fit the initial window size.
   * After that it leave user to control the zoom. This flag indicates if the initial zoom-to-fit is done or not.
   */
  private boolean myIsInitialZoomLevelDetermined = false;

  private final Timer myRepaintTimer = new Timer(15, (actionEvent) -> {
    repaint();
  });

  @NotNull
  private final Function<DesignSurface<T>, DesignSurfaceActionHandler> myActionHandlerProvider;

  /**
   * See {@link ZoomControlsPolicy}.
   */
  @NotNull
  private final ZoomControlsPolicy myZoomControlsPolicy;

  @NotNull
  private final AWTEventListener myOnHoverListener;

  @NotNull
  private final IssueListener myIssueListener;

  public DesignSurface(
    @NotNull Project project,
    @NotNull Disposable parentDisposable,
    @NotNull Function<DesignSurface<T>, ActionManager<? extends DesignSurface<T>>> actionManagerProvider,
    @NotNull Function<DesignSurface<T>, InteractionHandler> interactionProviderCreator,
    @NotNull Function<DesignSurface<T>, PositionableContentLayoutManager> positionableLayoutManagerProvider,
    @NotNull Function<DesignSurface<T>, DesignSurfaceActionHandler> designSurfaceActionHandlerProvider,
    @NotNull ZoomControlsPolicy zoomControlsPolicy) {
    this(project, parentDisposable, actionManagerProvider, interactionProviderCreator,
         positionableLayoutManagerProvider, designSurfaceActionHandlerProvider, new DefaultSelectionModel(), zoomControlsPolicy, Double.MAX_VALUE);
  }

  public DesignSurface(
    @NotNull Project project,
    @NotNull Disposable parentDisposable,
    @NotNull Function<DesignSurface<T>, ActionManager<? extends DesignSurface<T>>> actionManagerProvider,
    @NotNull Function<DesignSurface<T>, InteractionHandler> interactionProviderCreator,
    @NotNull Function<DesignSurface<T>, PositionableContentLayoutManager> positionableLayoutManagerProvider,
    @NotNull Function<DesignSurface<T>, DesignSurfaceActionHandler> actionHandlerProvider,
    @NotNull SelectionModel selectionModel,
    @NotNull ZoomControlsPolicy zoomControlsPolicy,
    double maxFitIntoZoomLevel) {
    super(new BorderLayout());

    Disposer.register(parentDisposable, this);
    myProject = project;
    mySelectionModel = selectionModel;
    myZoomControlsPolicy = zoomControlsPolicy;
    myIssueModel = new IssueModel(this, myProject);

    boolean hasZoomControls = myZoomControlsPolicy != ZoomControlsPolicy.HIDDEN;

    setOpaque(true);
    setFocusable(false);

    myAnalyticsManager = new DesignerAnalyticsManager(this);

    myActionHandlerProvider = actionHandlerProvider;

    // TODO: handle the case when selection are from different NlModels.
    // Manager can be null if the selected component is not part of NlModel. For example, a temporarily NlMode.
    // In that case we don't change focused SceneView.
    SelectionListener selectionListener = (model, selection) -> {
      if (getFocusedSceneView() != null) {
        notifySelectionListeners(selection);
      }
      else {
        notifySelectionListeners(Collections.emptyList());
      }
    };
    mySelectionModel.addListener(selectionListener);

    myProgressPanel = new MyProgressPanel();
    myProgressPanel.setName("Layout Editor Progress Panel");

    mySceneViewPanel = new SceneViewPanel(
      this::getSceneViews,
      () -> getGuiInputHandler().getLayers(),
      positionableLayoutManagerProvider.apply(this));
    mySceneViewPanel.setBackground(getBackground());

    if (hasZoomControls) {
      myScrollPane = DesignSurfaceScrollPane.createDefaultScrollPane(mySceneViewPanel, getBackground(), this::notifyPanningChanged);
    }
    else {
      myScrollPane = null;
    }
    myMouseClickDisplayPanel = new MouseClickDisplayPanel(this);

    // Setup the layers for the DesignSurface
    // If the surface is scrollable, we use four layers:
    //
    //  1. ScrollPane layer: Layer that contains the ScreenViews and does all the rendering, including the interaction layers.
    //  2. Progress layer: Displays the progress icon while a rendering is happening
    //  3. Mouse click display layer: It allows displaying clicks on the surface with a translucent bubble
    //  4. Zoom controls layer: Used to display the zoom controls of the surface
    //
    //  (4) sits at the top of the stack so is the first one to receive events like clicks.
    //
    // If the surface is NOT scrollable, the zoom controls will not be added and the scroll pane will be replaced
    // by the actual content.
    myLayeredPane = new JLayeredPane();
    myLayeredPane.setFocusable(true);
    if (myScrollPane != null) {
      myLayeredPane.setLayout(new MatchParentLayoutManager());
      myLayeredPane.add(myScrollPane, JLayeredPane.POPUP_LAYER);
      myContentContainerPane = myScrollPane;
      myViewport = new ScrollableDesignSurfaceViewport(myScrollPane.getViewport());
      myScrollPane.addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          // Relayout the PositionableContents when visible size (e.g. window size) of ScrollPane is changed.
          revalidateScrollArea();
        }
      });
    }
    else {
      myLayeredPane.setLayout(new OverlayLayout(myLayeredPane));
      mySceneViewPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
      myLayeredPane.add(mySceneViewPanel, JLayeredPane.POPUP_LAYER);
      myContentContainerPane = mySceneViewPanel;
      myViewport = new NonScrollableDesignSurfaceViewport(this);
    }
    myLayeredPane.add(myProgressPanel, LAYER_PROGRESS);
    myLayeredPane.add(myMouseClickDisplayPanel, LAYER_MOUSE_CLICK);

    myIssueListener = new DesignSurfaceIssueListenerImpl(this);
    myIssuePanel = new IssuePanel(myIssueModel, myIssueListener);

    add(myLayeredPane);

    // TODO: Do this as part of the layout/validate operation instead
    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent componentEvent) {
        if (componentEvent.getID() == ComponentEvent.COMPONENT_RESIZED) {
          if (!myIsInitialZoomLevelDetermined && isShowing() && getWidth() > 0 && getHeight() > 0) {
            // Set previous scale when DesignSurface becomes visible at first time.
            NlModel model = Iterables.getFirst(getModels(), null);
            if (model == null) {
              // No model is attached, ignore the setup of initial zoom level.
              return;
            }

            if (!restorePreviousScale(model)) {
              zoomToFit();
            }
            // The default size is defined, enable the flag.
            myIsInitialZoomLevelDetermined = true;
          }
          // We rebuilt the scene to make sure all SceneComponents are placed at right positions.
          getSceneManagers().forEach(manager -> {
            Scene scene = manager.getScene();
            scene.needsRebuildList();
          });
          repaint();
        }
      }
    });

    Interactable interactable = new SurfaceInteractable(this);
    myGuiInputHandler = new GuiInputHandler(this, interactable, interactionProviderCreator.apply(this));
    myGuiInputHandler.startListening();
    //noinspection AbstractMethodCallInConstructor
    myActionManager = actionManagerProvider.apply(this);
    myActionManager.registerActionsShortcuts(myLayeredPane);

    if (hasZoomControls) {
      JPanel zoomControlsLayerPane = new JPanel();
      zoomControlsLayerPane.setBorder(JBUI.Borders.empty(UIUtil.getScrollBarWidth()));
      zoomControlsLayerPane.setOpaque(false);
      zoomControlsLayerPane.setLayout(new BorderLayout());
      zoomControlsLayerPane.setFocusable(false);

      myLayeredPane.add(zoomControlsLayerPane, JLayeredPane.DRAG_LAYER);
      zoomControlsLayerPane.add(myActionManager.getDesignSurfaceToolbar(), BorderLayout.EAST);
      if (myZoomControlsPolicy == ZoomControlsPolicy.AUTO_HIDE) {
        myOnHoverListener = DesignSurfaceHelper.createZoomControlAutoHiddenListener(this, zoomControlsLayerPane);
        zoomControlsLayerPane.setVisible(false);
        Toolkit.getDefaultToolkit().addAWTEventListener(myOnHoverListener, AWTEvent.MOUSE_EVENT_MASK);
      }
      else {
        myOnHoverListener = event -> {};
      }
    }
    else {
      myOnHoverListener = event -> {};
    }

    // Sets the maximum zoom level allowed for ZoomType#FIT.
    myMaxFitIntoScale = maxFitIntoZoomLevel / getScreenScalingFactor();
  }

  @NotNull
  protected DesignSurfaceViewport getViewport() {
    return myViewport;
  }

  /**
   * When true, the surface will autoscroll when the mouse gets near the edges. See {@link JScrollPane#setAutoscrolls(boolean)}
   */
  protected void setSurfaceAutoscrolls(boolean enabled) {
    if (myScrollPane != null) {
      myScrollPane.setAutoscrolls(enabled);
    }
  }

  @SurfaceScreenScalingFactor
  @Override
  public double getScreenScalingFactor() {
    return 1d;
  }

  @NotNull
  protected abstract T createSceneManager(@NotNull NlModel model);

  /**
   * When not null, returns a {@link JPanel} to be rendered next to the primary panel of the editor.
   */
  public JPanel getAccessoryPanel() {
    return null;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public DesignerEditorFileType getLayoutType() {
    NlModel model = getModel();
    return model == null ? DefaultDesignerFileType.INSTANCE : model.getType();
  }

  @NotNull
  public ActionManager getActionManager() {
    return myActionManager;
  }

  // Allow a test to override myActionHandlerProvider when the surface is a mockito mock
  @NotNull
  public Function<DesignSurface<T>, DesignSurfaceActionHandler> getActionHandlerProvider() {
    return myActionHandlerProvider;
  }

  @NotNull
  public SelectionModel getSelectionModel() {
    return mySelectionModel;
  }

  @NotNull
  public SecondarySelectionModel getSecondarySelectionModel() {
    return mySelectionModel;
  }

  @NotNull
  public abstract ItemTransferable getSelectionAsTransferable();

  /**
   * Returns whether render error panels should be rendered when {@link SceneView}s in this surface have render errors.
   */
  public boolean shouldRenderErrorsPanel() {
    return false;
  }

  /**
   * @return the primary (first) {@link NlModel} if exist. null otherwise.
   * @see #getModels()
   * @deprecated The surface can contain multiple models. Use {@link #getModels() instead}.
   */
  @Deprecated
  @Nullable
  public NlModel getModel() {
    return Iterables.getFirst(getModels(), null);
  }

  /**
   * @return the list of added {@link NlModel}s.
   * @see #getModel()
   */
  @NotNull
  public ImmutableList<NlModel> getModels() {
    myModelToSceneManagersLock.readLock().lock();
    try {
      return ImmutableList.copyOf(Sets.filter(myModelToSceneManagers.keySet(), FILTER_DISPOSED_MODELS));
    }
    finally {
      myModelToSceneManagersLock.readLock().unlock();
    }
  }

  /**
   * Returns the list of all the {@link SceneManager} part of this surface
   */
  @VisibleForTesting(visibility = VisibleForTesting.Visibility.PROTECTED)
  @NotNull
  public ImmutableList<T> getSceneManagers() {
    myModelToSceneManagersLock.readLock().lock();
    try {
      return ImmutableList.copyOf(Collections2.filter(myModelToSceneManagers.values(), FILTER_DISPOSED_SCENE_MANAGERS));
    }
    finally {
      myModelToSceneManagersLock.readLock().unlock();
    }
  }

  /**
   * Add an {@link NlModel} to DesignSurface and return the created {@link SceneManager}.
   * If it is added before then it just returns the associated {@link SceneManager} which created before. The {@link NlModel} will be moved
   * to the last position which might affect rendering.
   *
   * @param model the added {@link NlModel}
   * @see #addAndRenderModel(NlModel)
   */
  @NotNull
  private T addModel(@NotNull NlModel model) {
    T manager = getSceneManager(model);
    if (manager != null) {
      // No need to add same model twice. We just move it to the bottom of the model list since order is important.
      myModelToSceneManagersLock.writeLock().lock();
      try {
        T managerToMove = myModelToSceneManagers.remove(model);
        if (managerToMove != null) {
          myModelToSceneManagers.put(model, managerToMove);
        }
      }
      finally {
        myModelToSceneManagersLock.writeLock().unlock();
      }
      return manager;
    }

    model.addListener(myModelListener);
    manager = createSceneManager(model);
    myModelToSceneManagersLock.writeLock().lock();
    try {
      myModelToSceneManagers.put(model, manager);
    }
    finally {
      myModelToSceneManagersLock.writeLock().unlock();
    }

    if (myIsActive) {
      manager.activate(this);
    }
    return manager;
  }

  /**
   * Gets a copy of {@code myListeners} under a lock. Use this method instead of accessing the listeners directly.
   */
  @NotNull
  private ImmutableList<DesignSurfaceListener> getListeners() {
    synchronized (myListenersLock) {
      return ImmutableList.copyOf(myListeners);
    }
  }

  /**
   * Gets a copy of {@code myZoomListeners} under a lock. Use this method instead of accessing the listeners directly.
   */
  @NotNull
  private ImmutableList<PanZoomListener> getZoomListeners() {
    synchronized (myListenersLock) {
      return ImmutableList.copyOf(myZoomListeners);
    }
  }

  /**
   * Add an {@link NlModel} to DesignSurface and refreshes the rendering of the model. If the model was already part of the surface, it will
   * be moved to the bottom of the list and a refresh will be triggered.
   * The scene views are updated before starting to render and the callback
   * {@link DesignSurfaceListener#modelChanged(DesignSurface, NlModel)} is triggered after rendering.
   * The method returns a {@link CompletableFuture} that will complete when the render of the new model has finished.
   * <br/><br/>
   * Note that the order of the addition might be important for the rendering order. {@link PositionableContentLayoutManager} will receive
   * the models in the order they are added.
   *
   * @param model the added {@link NlModel}
   * @see #addModel(NlModel)
   */
  @NotNull
  public final CompletableFuture<Void> addAndRenderModel(@NotNull NlModel model) {
    T modelSceneManager = addModel(model);
    // Mark the scene view panel as invalid to force the scene views to be updated
    mySceneViewPanel.invalidate();

    // We probably do not need to request a render for all models but it is currently the
    // only point subclasses can override to disable the layoutlib render behaviour.
    return modelSceneManager.requestRenderAsync()
      .whenCompleteAsync((result, ex) -> {
        reactivateGuiInputHandler();

        revalidateScrollArea();

        // TODO(b/147225165): The tasks depends on model inflating callback should be moved to ModelListener#modelDerivedDataChanged.
        for (DesignSurfaceListener listener : getListeners()) {
          listener.modelChanged(this, model);
        }
      }, EdtExecutorService.getInstance());
  }

  /**
   * Add an {@link NlModel} to DesignSurface and return the created {@link SceneManager}.
   * If it is added before then it just returns the associated {@link SceneManager} which created before.
   * In this function, the scene views are not updated and {@link DesignSurfaceListener#modelChanged(DesignSurface, NlModel)}
   * callback is triggered immediately.
   * In the opposite, {@link #addAndRenderModel(NlModel)} updates the scene views and triggers
   * {@link DesignSurfaceListener#modelChanged(DesignSurface, NlModel)} when render is completed.
   * <p>
   * <br/><br/>
   * Note that the order of the addition might be important for the rendering order. {@link PositionableContentLayoutManager} will receive
   * the models in the order they are added.
   * <p>
   * TODO(b/147225165): Remove #addAndRenderModel function and rename this function as #addModel
   *
   * @param model the added {@link NlModel}
   * @see #addModel(NlModel)
   * @see #addAndRenderModel(NlModel)
   */
  @NotNull
  public final T addModelWithoutRender(@NotNull NlModel model) {
    T manager = addModel(model);

    EdtExecutorService.getInstance().execute(() -> {
      for (DesignSurfaceListener listener : getListeners()) {
        // TODO: The listeners have the expectation of the call happening in the EDT. We need
        //       to address that.
        listener.modelChanged(this, model);
      }
    });
    reactivateGuiInputHandler();
    return manager;
  }

  /**
   * Remove an {@link NlModel} from DesignSurface. If it had not been added before then nothing happens.
   *
   * @param model the {@link NlModel} to remove
   * @returns true if the model existed and was removed
   */
  private boolean removeModelImpl(@NotNull NlModel model) {
    SceneManager manager;
    myModelToSceneManagersLock.writeLock().lock();
    try {
      manager = myModelToSceneManagers.remove(model);
    }
    finally {
      myModelToSceneManagersLock.writeLock().unlock();
    }

    // Mark the scene view panel as invalid to force the scene views to be updated
    mySceneViewPanel.removeSceneViewForModel(model);

    if (manager == null) {
      return false;
    }

    model.deactivate(this);

    model.removeListener(myModelListener);

    Disposer.dispose(manager);
    UIUtil.invokeLaterIfNeeded(() -> revalidateScrollArea());
    return true;
  }

  /**
   * Remove an {@link NlModel} from DesignSurface. If it isn't added before then nothing happens.
   *
   * @param model the {@link NlModel} to remove
   */
  public void removeModel(@NotNull NlModel model) {
    if (!removeModelImpl(model)) {
      return;
    }

    reactivateGuiInputHandler();
  }

  /**
   * Sets the current {@link NlModel} to DesignSurface.
   *
   * @see #addAndRenderModel(NlModel)
   * @see #removeModel(NlModel)
   */
  public CompletableFuture<Void> setModel(@Nullable NlModel model) {
    NlModel oldModel = getModel();
    if (model == oldModel) {
      return CompletableFuture.completedFuture(null);
    }

    if (oldModel != null) {
      removeModelImpl(oldModel);
    }

    if (model == null) {
      return CompletableFuture.completedFuture(null);
    }

    addModel(model);
    // Mark the scene view panel as invalid to force the scene views to be updated
    mySceneViewPanel.invalidate();

    return requestRender()
      .whenCompleteAsync((result, ex) -> {
        reactivateGuiInputHandler();
        if (!restorePreviousScale(model)) {
          zoomToFit();
        }
        revalidateScrollArea();

        // TODO: The listeners have the expectation of the call happening in the EDT. We need
        //       to address that.
        for (DesignSurfaceListener listener : getListeners()) {
          listener.modelChanged(this, model);
        }
      }, EdtExecutorService.getInstance());
  }

  /**
   * Update the status of {@link GuiInputHandler}. It will start or stop listening depending on the current layout type.
   */
  private void reactivateGuiInputHandler() {
    if (isEditable()) {
      myGuiInputHandler.startListening();
    }
    else {
      myGuiInputHandler.stopListening();
    }
  }

  @Override
  public void dispose() {
    synchronized (myListenersLock) {
      myListeners.clear();
      myZoomListeners.clear();
    }
    myGuiInputHandler.stopListening();
    Toolkit.getDefaultToolkit().removeAWTEventListener(myOnHoverListener);
    synchronized (myRenderFutures) {
      for (CompletableFuture<Void> future : myRenderFutures) {
        try {
          future.cancel(true);
        }
        catch (CancellationException ignored) {
        }
      }
      myRenderFutures.clear();
    }
    if (myRepaintTimer.isRunning()) {
      myRepaintTimer.stop();
    }
    getModels().forEach(this::removeModelImpl);
  }

  /**
   * Re-layouts the ScreenViews contained in this design surface immediately.
   */
  @UiThread
  public void validateScrollArea() {
    // Mark both the sceneview panel and the scroll pane as invalid to force a relayout.
    mySceneViewPanel.invalidate();
    myContentContainerPane.invalidate();
    // Validate the scroll pane immediately and layout components.
    myContentContainerPane.validate();
    mySceneViewPanel.repaint();
  }

  /**
   * Asks the ScreenViews for a re-layouts the ScreenViews contained in this design surface. The re-layout will not happen immediately in
   * this call.
   */
  @UiThread
  public void revalidateScrollArea() {
    // Mark the scene view panel as invalid to force a revalidation when the scroll pane is revalidated.
    mySceneViewPanel.invalidate();
    // Schedule a layout for later.
    myContentContainerPane.revalidate();
    // Also schedule a repaint.
    mySceneViewPanel.repaint();
  }

  public JComponent getPreferredFocusedComponent() {
    return getInteractionPane();
  }

  /**
   * Call this to generate repaints
   */
  public void needsRepaint() {
    if (!myRepaintTimer.isRunning()) {
      myRepaintTimer.setRepeats(false);
      myRepaintTimer.start();
    }
  }

  @Override
  @Nullable
  public SceneView getFocusedSceneView() {
    ImmutableList<T> managers = getSceneManagers();
    if (managers.size() == 1) {
      // Always return primary SceneView In single-model mode,
      SceneManager manager = getSceneManager();
      assert manager != null;
      return Iterables.getFirst(manager.getSceneViews(), null);
    }
    List<NlComponent> selection = mySelectionModel.getSelection();
    if (!selection.isEmpty()) {
      NlComponent primary = selection.get(0);
      SceneManager manager = getSceneManager(primary.getModel());
      if (manager != null) {
        return Iterables.getFirst(manager.getSceneViews(), null);
      }
    }
    return null;
  }

  /**
   * Returns the list of SceneViews attached to this surface
   */
  @NotNull
  protected ImmutableCollection<SceneView> getSceneViews() {
    return getSceneManagers().stream()
      .flatMap(sceneManager -> sceneManager.getSceneViews().stream())
      .collect(ImmutableList.toImmutableList());
  }

  @Override
  public void onHover(@SwingCoordinate int x, @SwingCoordinate int y) {
    for (SceneView sceneView : getSceneViews()) {
      sceneView.onHover(x, y);
    }
  }

  /**
   * Gives us a chance to change layers behaviour upon drag and drop interaction starting
   * <p>
   * TODO(b/142953949): move this function into {@link com.android.tools.idea.uibuilder.surface.DragDropInteraction}
   */
  public void startDragDropInteraction() {
    for (SceneView sceneView : getSceneViews()) {
      sceneView.onDragStart();
    }
    repaint();
  }

  /**
   * Gives us a chance to change layers behaviour upon drag and drop interaction ending
   * <p>
   * TODO(b/142953949): move this function into {@link com.android.tools.idea.uibuilder.surface.DragDropInteraction}
   */
  public void stopDragDropInteraction() {
    for (SceneView sceneView : getSceneViews()) {
      sceneView.onDragEnd();
    }
    repaint();
  }

  /**
   * Execute a zoom on the content. See {@link ZoomType} for the different type of zoom available.
   *
   * @see #zoom(ZoomType, int, int)
   */
  @UiThread
  @Override
  final public boolean zoom(@NotNull ZoomType type) {
    return zoom(type, -1, -1);
  }

  @Nullable
  @Override
  public Magnificator getMagnificator() {
    if (!getSupportPinchAndZoom()) {
      return null;
    }

    return (scale, at) -> null;
  }

  @Override
  public void magnificationStarted(Point at) {
    myMagnificationStartedScale = getScale();
  }

  @Override
  public void magnificationFinished(double magnification) {
  }

  @Override
  public void magnify(double magnification) {
    if (Double.compare(magnification, 0) == 0) {
      return;
    }

    Point mouse;
    if (!GraphicsEnvironment.isHeadless()) {
      PointerInfo pointerInfo = MouseInfo.getPointerInfo();
      if (pointerInfo == null) {
        return;
      }
      mouse = pointerInfo.getLocation();
      SwingUtilities.convertPointFromScreen(mouse, getViewport().getViewportComponent());
    }
    else {
      // In headless mode we assume the scale point is at the center.
      mouse = new Point(getWidth() / 2, getHeight() / 2);
    }
    double sensitivity = AndroidEditorSettings.getInstance().getGlobalState().getMagnifySensitivity();
    @SurfaceScale double newScale = myMagnificationStartedScale + magnification * sensitivity;
    setScale(newScale, mouse.x, mouse.y);
  }

  @Override
  public void setPanning(boolean isPanning) {
    myGuiInputHandler.setPanning(isPanning);
  }

  @Override
  @UiThread
  public boolean zoom(@NotNull ZoomType type, @SwingCoordinate int x, @SwingCoordinate int y) {
    // track user triggered change
    getAnalyticsManager().trackZoom(type);
    SceneView view = getFocusedSceneView();
    if (type == ZoomType.IN && (x < 0 || y < 0)
        && view != null && !getSelectionModel().isEmpty()) {
      Scene scene = getScene();
      if (scene != null) {
        SceneComponent component = scene.getSceneComponent(getSelectionModel().getPrimary());
        if (component != null) {
          x = Coordinates.getSwingXDip(view, component.getCenterX());
          y = Coordinates.getSwingYDip(view, component.getCenterY());
        }
      }
    }
    boolean scaled;
    switch (type) {
      case IN: {
        @SurfaceZoomLevel double currentScale = myScale * getScreenScalingFactor();
        int current = (int)(Math.round(currentScale * 100));
        @SurfaceScale double scale = (ZoomType.zoomIn(current) / 100.0) / getScreenScalingFactor();
        scaled = setScale(scale, x, y);
        break;
      }
      case OUT: {
        @SurfaceZoomLevel double currentScale = myScale * getScreenScalingFactor();
        int current = (int)(currentScale * 100);
        @SurfaceScale double scale = (ZoomType.zoomOut(current) / 100.0) / getScreenScalingFactor();
        scaled = setScale(scale, x, y);
        break;
      }
      case ACTUAL:
        scaled = setScale(1d / getScreenScalingFactor());
        break;
      case FIT:
        scaled = setScale(getFitScale());
        break;
      default:
        throw new UnsupportedOperationException("Not yet implemented: " + type);
    }

    return scaled;
  }

  /**
   * Measure the scale size which can fit the SceneViews into the scrollable area.
   * This function doesn't consider the legal scale range, which can be get by {@link #getMaxScale()} and {@link #getMinScale()}.
   *
   * @return The scale to make the content fit the design surface
   */
  @SurfaceScale
  abstract public double getFitScale();

  @SwingCoordinate
  protected abstract Dimension getScrollToVisibleOffset();

  @UiThread
  final public boolean zoomToFit() {
    return zoom(ZoomType.FIT, -1, -1);
  }

  @Override
  @SurfaceScale
  public double getScale() {
    return myScale;
  }

  @Override
  public boolean isPanning() {
    return myGuiInputHandler.isPanning();
  }

  @Override
  public boolean isPannable() {
    return true;
  }

  @Override
  public boolean canZoomIn() {
    double currentScale = getScale();
    double maxScale = getMaxScale();
    return currentScale < maxScale && !isScaleSame(currentScale, maxScale);
  }

  @Override
  public boolean canZoomOut() {
    double minScale = getMinScale();
    double currentScale = getScale();
    return minScale < currentScale && !isScaleSame(minScale, currentScale);
  }

  @Override
  public abstract boolean canZoomToFit();

  @Override
  public boolean canZoomToActual() {
    double currentScale = getScale();
    return (currentScale > 1 && canZoomOut()) || (currentScale < 1 && canZoomIn());
  }

  /**
   * Scroll to the center of a list of given components. Usually the center of the area containing these elements.
   */
  public abstract void scrollToCenter(@NotNull List<NlComponent> list);

  /**
   * Ensures that the given model is visible in the surface by scrolling to it if needed.
   * If the {@link SceneView} is partially visible and {@code forceScroll} is set to {@code false}, no scroll will happen.
   */
  public final void scrollToVisible(@NotNull SceneView sceneView, boolean forceScroll) {
    Rectangle rectangle = mySceneViewPanel.findSceneViewRectangle(sceneView);
    if (rectangle != null && (forceScroll || !getViewport().getViewRect().intersects(rectangle))) {
      Dimension offset = getScrollToVisibleOffset();
      setScrollPosition(rectangle.x - offset.width, rectangle.y - offset.height);
    }
  }

  /**
   * Ensures that the given model is visible in the surface by scrolling to it if needed.
   * If the {@link NlModel} is partially visible and {@code forceScroll} is set to {@code false}, no scroll will happen.
   */
  public final void scrollToVisible(@NotNull NlModel model, boolean forceScroll) {
    getSceneViews().stream().filter(sceneView -> sceneView.getSceneManager().getModel() == model).findFirst()
      .ifPresent(sceneView -> scrollToVisible(sceneView, forceScroll));
  }

  public void setScrollPosition(@SwingCoordinate int x, @SwingCoordinate int y) {
    setScrollPosition(new Point(x, y));
  }

  /**
   * Sets the offset for the scroll viewer to the specified x and y values
   * The offset will never be less than zero, and never greater that the
   * maximum value allowed by the sizes of the underlying view and the extent.
   * If the zoom factor is large enough that a scroll bars isn't visible,
   * the position will be set to zero.
   */
  @Override
  public void setScrollPosition(@SwingCoordinate Point p) {
    p.setLocation(Math.max(0, p.x), Math.max(0, p.y));

    Dimension extent = getExtentSize();
    Dimension view = getViewSize();

    int minX = Math.min(p.x, view.width - extent.width);
    int minY = Math.min(p.y, view.height - extent.height);

    p.setLocation(minX, minY);

    getViewport().setViewPosition(p);
  }

  @Override
  @SwingCoordinate
  public Point getScrollPosition() {
    return getViewport().getViewPosition();
  }

  /**
   * Returns the size of the surface scroll viewport.
   */
  @NotNull
  @SwingCoordinate
  public Dimension getExtentSize() {
    return getViewport().getExtentSize();
  }

  /**
   * Returns the size of the surface containing the ScreenViews.
   */
  @NotNull
  @SwingCoordinate
  public Dimension getViewSize() {
    return getViewport().getViewSize();
  }

  /**
   * Set the scale factor used to multiply the content size.
   *
   * @param scale The scale factor. Can be any value but it will be capped between -1 and 10
   *              (value below 0 means zoom to fit)
   * @return True if the scaling was changed, false if this was a noop.
   */
  public boolean setScale(double scale) {
    return setScale(scale, -1, -1);
  }

  /**
   * <p>
   * Set the scale factor used to multiply the content size and try to
   * position the viewport such that its center is the closest possible
   * to the provided x and y coordinate in the Viewport's view coordinate system
   * ({@link JViewport#getView()}).
   * </p><p>
   * If x OR y are negative, the scale will be centered toward the center the viewport.
   * </p>
   *
   * @param scale The scale factor. Can be any value but it will be capped between -1 and 10
   *              (value below 0 means zoom to fit)
   *              This value doesn't consider DPI.
   * @param x     The X coordinate to center the scale to (in the Viewport's view coordinate system)
   * @param y     The Y coordinate to center the scale to (in the Viewport's view coordinate system)
   * @return True if the scaling was changed, false if this was a noop.
   */
  @VisibleForTesting(visibility = VisibleForTesting.Visibility.PROTECTED)
  public boolean setScale(@SurfaceScale double scale, @SwingCoordinate int x, @SwingCoordinate int y) {
    @SurfaceScale final double newScale = Math.min(Math.max(scale, getMinScale()), getMaxScale());
    if (isScaleSame(myScale, newScale)) {
      return false;
    }

    double previousScale = myScale;
    myScale = newScale;
    NlModel model = Iterables.getFirst(getModels(), null);
    if (model != null) {
      storeCurrentScale(model);
    }

    revalidateScrollArea();
    notifyScaleChanged(previousScale, myScale);
    return true;
  }

  /**
   * If the differences of two scales are smaller than tolerance, they are considered as the same scale.
   */
  private boolean isScaleSame(@SurfaceScale double scaleA, @SurfaceScale double scaleB) {
    double tolerance = SCALING_THRESHOLD / getScreenScalingFactor();
    return Math.abs(scaleA - scaleB) < tolerance;
  }

  protected boolean isKeepingScaleWhenReopen() {
    return true;
  }

  /**
   * Save the current zoom level from the file of the given {@link NlModel}.
   */
  private void storeCurrentScale(@NotNull NlModel model) {
    if (!isKeepingScaleWhenReopen()) {
      return;
    }
    SurfaceState state = DesignSurfaceSettings.getInstance(model.getProject()).getSurfaceState();
    state.saveFileScale(myProject, model.getVirtualFile(), myScale);
  }

  /**
   * Load the saved zoom level from the file of the given {@link NlModel}.
   * Return true if the previous zoom level is restored, false otherwise.
   */
  private boolean restorePreviousScale(@NotNull NlModel model) {
    if (!isKeepingScaleWhenReopen()) {
      return false;
    }
    SurfaceState state = DesignSurfaceSettings.getInstance(model.getProject()).getSurfaceState();
    Double previousScale = state.loadFileScale(myProject, model.getVirtualFile());
    if (previousScale != null) {
      setScale(previousScale);
      return true;
    }
    else {
      return false;
    }
  }

  /**
   * The minimum scale we'll allow.
   */
  @SurfaceScale
  protected double getMinScale() {
    return 0;
  }

  /**
   * The maximum scale we'll allow.
   */
  @SurfaceScale
  protected double getMaxScale() {
    return 1;
  }

  private void notifyScaleChanged(double previousScale, double newScale) {
    for (PanZoomListener myZoomListener : getZoomListeners()) {
      myZoomListener.zoomChanged(previousScale, newScale);
    }
  }

  private void notifyPanningChanged(AdjustmentEvent adjustmentEvent) {
    for (PanZoomListener myZoomListener : getZoomListeners()) {
      myZoomListener.panningChanged(adjustmentEvent);
    }
  }

  @NotNull
  public JComponent getLayeredPane() {
    return myLayeredPane;
  }

  @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
  @NotNull
  public JComponent getInteractionPane() {
    return mySceneViewPanel;
  }

  @NotNull
  public DesignerAnalyticsManager getAnalyticsManager() {
    return myAnalyticsManager;
  }

  @Nullable
  public LayoutScannerControl getLayoutScannerControl() {
    return null;
  }

  protected void notifySelectionListeners(@NotNull List<NlComponent> newSelection) {
    List<DesignSurfaceListener> listeners = Lists.newArrayList(myListeners);
    for (DesignSurfaceListener listener : listeners) {
      listener.componentSelectionChanged(this, newSelection);
    }
  }

  /**
   * @param x the x coordinate of the double click converted to pixels in the Android coordinate system
   * @param y the y coordinate of the double click converted to pixels in the Android coordinate system
   */
  public void notifyComponentActivate(@NotNull NlComponent component, @AndroidCoordinate int x, @AndroidCoordinate int y) {
    notifyComponentActivate(component);
  }

  public void notifyComponentActivate(@NotNull NlComponent component) {
    activatePreferredEditor(component);
  }

  protected void activatePreferredEditor(@NotNull NlComponent component) {
    for (DesignSurfaceListener listener : getListeners()) {
      if (listener.activatePreferredEditor(this, component)) {
        break;
      }
    }
  }

  public void addListener(@NotNull DesignSurfaceListener listener) {
    synchronized (myListenersLock) {
      myListeners.remove(listener); // ensure single registration
      myListeners.add(listener);
    }
  }

  public void removeListener(@NotNull DesignSurfaceListener listener) {
    synchronized (myListenersLock) {
      myListeners.remove(listener);
    }
  }

  public void addPanZoomListener(PanZoomListener listener) {
    synchronized (myListenersLock) {
      myZoomListeners.remove(listener);
      myZoomListeners.add(listener);
    }
  }

  public void removePanZoomListener(PanZoomListener listener) {
    synchronized (myListenersLock) {
      myZoomListeners.remove(listener);
    }
  }

  /**
   * The editor has been activated
   */
  public void activate() {
    if (Disposer.isDisposed(this)) {
      // Prevent activating a disposed surface.
      return;
    }

    if (!myIsActive) {
      for (SceneManager manager : getSceneManagers()) {
        manager.activate(this);
      }
      if (myZoomControlsPolicy == ZoomControlsPolicy.AUTO_HIDE) {
        Toolkit.getDefaultToolkit().addAWTEventListener(myOnHoverListener, AWTEvent.MOUSE_EVENT_MASK);
      }
    }
    myIsActive = true;
    myIssueModel.activate();
  }

  public void deactivateIssueModel() {
    myIssueModel.deactivate();
  }

  public void deactivate() {
    if (myIsActive) {
      Toolkit.getDefaultToolkit().removeAWTEventListener(myOnHoverListener);
      for (SceneManager manager : getSceneManagers()) {
        manager.deactivate(this);
      }
    }
    myIsActive = false;
    myIssueModel.deactivate();

    myGuiInputHandler.cancelInteraction();
  }

  /**
   * Sets the file editor to which actions like undo/redo will be delegated. This is only needed if this DesignSurface is not a child
   * of a {@link FileEditor}.
   * <p>
   * The surface will only keep a {@link WeakReference} to the editor.
   */
  public void setFileEditorDelegate(@Nullable FileEditor fileEditor) {
    myFileEditorDelegate = new WeakReference<>(fileEditor);
  }

  @Nullable
  public FileEditor getFileEditorDelegate() {
    return myFileEditorDelegate.get();
  }

  @Override
  @Deprecated
  @Nullable
  public SceneView getSceneViewAtOrPrimary(@SwingCoordinate int x, @SwingCoordinate int y) {
    SceneView view = getSceneViewAt(x, y);
    if (view == null) {
      // TODO: For keeping the behaviour as before in multi-model case, we return primary SceneView when there is no hovered SceneView.
      SceneManager manager = getSceneManager();
      if (manager != null) {
        view = manager.getSceneView();
      }
    }
    return view;
  }

  @Override
  @Nullable
  public SceneView getSceneViewAt(@SwingCoordinate int x, @SwingCoordinate int y) {
    Collection<SceneView> sceneViews = getSceneViews();
    Dimension scaledSize = new Dimension();
    for (SceneView view : sceneViews) {
      view.getScaledContentSize(scaledSize);
      if (view.getX() <= x &&
          x <= (view.getX() + scaledSize.width) &&
          view.getY() <= y &&
          y <= (view.getY() + scaledSize.height)) {
        return view;
      }
    }
    return null;
  }

  /**
   * Returns the {@link SceneView} under the mouse cursor if the mouse is within the coordinates of this surface or null
   * otherwise.
   */
  @Nullable
  public SceneView getSceneViewAtMousePosition() {
    Point mouseLocation = !GraphicsEnvironment.isHeadless() ? MouseInfo.getPointerInfo().getLocation() : null;
    if (mouseLocation == null || contains(mouseLocation) || !isVisible() || !isEnabled()) {
      return null;
    }

    SwingUtilities.convertPointFromScreen(mouseLocation, mySceneViewPanel);
    return getSceneViewAt(mouseLocation.x, mouseLocation.y);
  }

  @Override
  @Deprecated
  @Nullable
  public Scene getScene() {
    SceneManager sceneManager = getSceneManager();
    return sceneManager != null ? sceneManager.getScene() : null;
  }

  /**
   * @see #getSceneManager(NlModel)
   * @deprecated Use {@link #getSceneManager(NlModel)} or {@link #getSceneManagers} instead.
   * Using this method will cause the code not to correctly support multiple previews.
   */
  @Nullable
  public T getSceneManager() {
    NlModel model = getModel();
    return model != null ? getSceneManager(model) : null;
  }

  /**
   * @return The {@link SceneManager} associated to the given {@link NlModel}.
   */
  @Nullable
  public T getSceneManager(@NotNull NlModel model) {
    if (model.getModule().isDisposed()) {
      return null;
    }

    myModelToSceneManagersLock.readLock().lock();
    try {
      return myModelToSceneManagers.get(model);
    }
    finally {
      myModelToSceneManagersLock.readLock().unlock();
    }
  }

  /**
   * This is called before {@link #setModel(NlModel)}. After the returned future completes, we'll wait for smart mode and then invoke
   * {@link #setModel(NlModel)}. If a {@code DesignSurface} needs to do any extra work before the model is set it should be done here.
   */
  public CompletableFuture<?> goingToSetModel(NlModel model) {
    return CompletableFuture.completedFuture(null);
  }

  @NotNull
  public GuiInputHandler getGuiInputHandler() {
    return myGuiInputHandler;
  }

  protected boolean getSupportPinchAndZoom() {
    return true;
  }

  /**
   * @return true if the content is editable (e.g. move position or drag-and-drop), false otherwise.
   */
  public boolean isEditable() {
    return getLayoutType().isEditable();
  }

  /**
   * Returns all the {@link PositionableContent} in this surface.
   */
  @NotNull
  protected Collection<PositionableContent> getPositionableContent() {
    return mySceneViewPanel.getPositionableContent();
  }

  private final Set<ProgressIndicator> myProgressIndicators = new HashSet<>();

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private final MyProgressPanel myProgressPanel;

  public void registerIndicator(@NotNull ProgressIndicator indicator) {
    if (myProject.isDisposed() || Disposer.isDisposed(this)) {
      return;
    }

    synchronized (myProgressIndicators) {
      if (myProgressIndicators.add(indicator)) {
        myProgressPanel.showProgressIcon();
      }
    }
  }

  public void unregisterIndicator(@NotNull ProgressIndicator indicator) {
    synchronized (myProgressIndicators) {
      myProgressIndicators.remove(indicator);

      if (myProgressIndicators.isEmpty()) {
        myProgressPanel.hideProgressIcon();
      }
    }
  }

  protected boolean useSmallProgressIcon() {
    return true;
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
        setSmallIcon(useSmallProgressIcon());
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
      setBackground(JBColor.RED); // make this null instead?

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
          Disposer.register(DesignSurface.this, mySmallProgressIcon);
        }
        return mySmallProgressIcon;
      }
      else {
        if (myLargeProgressIcon == null) {
          myLargeProgressIcon = new AsyncProcessIcon.Big("Android layout rendering");
          Disposer.register(DesignSurface.this, myLargeProgressIcon);
        }
        return myLargeProgressIcon;
      }
    }
  }

  /**
   * Invalidates all models and request a render of the layout. This will re-inflate the {@link NlModel}s and render them sequentially.
   * The result {@link CompletableFuture} will notify when all the renderings have completed.
   */
  @NotNull
  public CompletableFuture<Void> requestRender() {
    ImmutableList<T> managers = getSceneManagers();
    if (managers.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }
    return requestSequentialRender(manager -> manager.requestLayoutAndRenderAsync(false));
  }

  /**
   * Schedule the render requests sequentially for all {@link SceneManager}s in this {@link DesignSurface}.
   *
   * @param renderRequest The requested rendering to be scheduled. This gives the caller a chance to choose the preferred rendering request.
   * @return A callback which is triggered when the scheduled rendering are completed.
   */
  @NotNull
  protected CompletableFuture<Void> requestSequentialRender(@NotNull Function<T, CompletableFuture<Void>> renderRequest) {
    CompletableFuture<Void> callback = new CompletableFuture<>();
    synchronized (myRenderFutures) {
      if (!myRenderFutures.isEmpty()) {
        // TODO: This may make the rendered previews not match the last status of NlModel if the modifications happen during rendering.
        //       Similar case happens in LayoutlibSceneManager#requestRender function, both need to be fixed.
        myRenderFutures.add(callback);
        return callback;
      }
      else {
        myRenderFutures.add(callback);
      }
    }

    // Cascading the CompletableFuture to make them executing sequentially.
    CompletableFuture<Void> renderFuture = CompletableFuture.completedFuture(null);
    for (T manager : getSceneManagers()) {
      renderFuture = renderFuture.thenCompose(it -> {
        CompletableFuture<Void> future = renderRequest.apply(manager);
        invalidate();
        return future;
      });
    }
    renderFuture.thenRun(() -> {
      synchronized (myRenderFutures) {
        myRenderFutures.forEach(future -> future.complete(null));
        myRenderFutures.clear();
      }
      updateNotifications();
    });

    return callback;
  }

  /**
   * Returns true if this surface is currently refreshing.
   */
  public final boolean isRefreshing() {
    synchronized (myRenderFutures) {
      return !myRenderFutures.isEmpty();
    }
  }

  /**
   * Converts a given point that is in view coordinates to viewport coordinates.
   */
  @TestOnly
  @NotNull
  public Point getCoordinatesOnViewport(@NotNull Point viewCoordinates) {
    return SwingUtilities.convertPoint(mySceneViewPanel, viewCoordinates.x, viewCoordinates.y, getViewport().getViewportComponent());
  }

  @TestOnly
  public void setScrollViewSizeAndValidate(@SwingCoordinate int width, @SwingCoordinate int height) {
    if (myScrollPane != null) {
      myScrollPane.setSize(width, height);
      myScrollPane.doLayout();
      UIUtil.invokeAndWaitIfNeeded((Runnable)this::validateScrollArea);
    }
  }

  /**
   * Sets the tooltip for the design surface
   */
  public void setDesignToolTip(@Nullable String text) {
    mySceneViewPanel.setToolTipText(text);
  }

  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (DESIGN_SURFACE.is(dataId) || ZOOMABLE_KEY.is(dataId) || PANNABLE_KEY.is(dataId) || GuiInputHandler.CURSOR_RECEIVER.is(dataId)) {
      return this;
    }
    if (PlatformCoreDataKeys.FILE_EDITOR.is(dataId)) {
      return myFileEditorDelegate.get();
    }
    else if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId) ||
             PlatformDataKeys.CUT_PROVIDER.is(dataId) ||
             PlatformDataKeys.COPY_PROVIDER.is(dataId) ||
             PlatformDataKeys.PASTE_PROVIDER.is(dataId)) {
      return getActionHandlerProvider().apply(this);
    }
    else if (PlatformDataKeys.CONTEXT_MENU_POINT.is(dataId)) {
      SceneView view = getFocusedSceneView();
      NlComponent selection = getSelectionModel().getPrimary();
      Scene scene = getScene();
      if (view == null || scene == null || selection == null) {
        return null;
      }
      SceneComponent sceneComponent = scene.getSceneComponent(selection);
      if (sceneComponent == null) {
        return null;
      }
      return new Point(Coordinates.getSwingXDip(view, sceneComponent.getCenterX()),
                       Coordinates.getSwingYDip(view, sceneComponent.getCenterY()));
    }
    else if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      SceneView view = getFocusedSceneView();
      if (view != null) {
        SelectionModel selectionModel = view.getSelectionModel();
        NlComponent primary = selectionModel.getPrimary();
        if (primary != null) {
          return primary.getTagDeprecated();
        }
      }
    }
    else if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
      SceneView view = getFocusedSceneView();
      if (view != null) {
        SelectionModel selectionModel = view.getSelectionModel();
        List<NlComponent> selection = selectionModel.getSelection();
        List<XmlTag> list = Lists.newArrayListWithCapacity(selection.size());
        for (NlComponent component : selection) {
          list.add(component.getTagDeprecated());
        }
        return list.toArray(XmlTag.EMPTY);
      }
    }
    else {
      NlModel model = getModel();
      if (PlatformCoreDataKeys.MODULE.is(dataId) && model != null) {
        return model.getModule();
      }
    }

    return null;
  }

  /**
   * Returns true we shouldn't currently try to relayout our content (e.g. if some other operations is in progress).
   */
  public abstract boolean isLayoutDisabled();

  @NotNull
  @Override
  public ImmutableCollection<Configuration> getConfigurations() {
    return getModels().stream()
      .map(NlModel::getConfiguration)
      .collect(ImmutableList.toImmutableList());
  }

  @NotNull
  public IssueModel getIssueModel() {
    return myIssueModel;
  }

  public void setLintAnnotationsModel(@NotNull LintAnnotationsModel model) {
    if (myLintIssueProvider != null) {
      myLintIssueProvider.setLintAnnotationsModel(model);
    }
    else {
      myLintIssueProvider = new LintIssueProvider(model);
      getIssueModel().addIssueProvider(myLintIssueProvider);
    }
  }

  @NotNull
  public IssuePanel getIssuePanel() {
    return myIssuePanel;
  }

  @NotNull
  protected MergingUpdateQueue getErrorQueue() {
    synchronized (myErrorQueueLock) {
      if (myErrorQueue == null) {
        myErrorQueue = new MergingUpdateQueue("android.error.computation", 200, true, null, this, null,
                                              Alarm.ThreadToUse.POOLED_THREAD);
      }
      return myErrorQueue;
    }
  }

  @NotNull
  public ConfigurationManager getConfigurationManager(@NotNull AndroidFacet facet) {
    return ConfigurationManager.getOrCreateInstance(facet);
  }

  @Override
  public void updateUI() {
    super.updateUI();
    //noinspection FieldAccessNotGuarded We are only accessing the reference so we do not need to guard the access
    if (myModelToSceneManagers != null) {
      // updateUI() is called in the parent constructor, at that time all class member in this class has not initialized.
      for (SceneManager manager : getSceneManagers()) {
        manager.getSceneViews().forEach(SceneView::updateUI);
      }
    }
  }

  /**
   * Returns all the selectable components in the design surface
   *
   * @return the list of components
   */
  @NotNull
  abstract public List<NlComponent> getSelectableComponents();

  /**
   * Enables the mouse click display. If enabled, the clicks of the user are displayed in the surface.
   */
  public void enableMouseClickDisplay() {
    myMouseClickDisplayPanel.setEnabled(true);
  }

  /**
   * Disables the mouse click display.
   */
  public void disableMouseClickDisplay() {
    myMouseClickDisplayPanel.setEnabled(false);
  }

  @Override
  public void setBackground(Color bg) {
    super.setBackground(bg);

    // setBackground is called before the class initialization is complete so we do the null checking to prevent calling mySceneViewPanel
    // before the constructor has completed. At that point mySceneViewPanel might still be null.
    //noinspection ConstantConditions
    if (mySceneViewPanel != null) {
      mySceneViewPanel.setBackground(bg);
    }
  }

  @NotNull
  public final PositionableContentLayoutManager getSceneViewLayoutManager() {
    return (PositionableContentLayoutManager)mySceneViewPanel.getLayout();
  }

  /**
   * Sets the {@link SceneViewAlignment} for the {@link SceneView}s. This only applies to {@link SceneView}s when the
   * content size is less than the minimum size allowed. See {@link SceneViewPanel}.
   */
  public final void setSceneViewAlignment(@NotNull SceneViewAlignment sceneViewAlignment) {
    mySceneViewPanel.setSceneViewAlignment(sceneViewAlignment.mySwingAlignmentXValue);
  }

  /**
   * Updates the notifications panel associated to this {@link DesignSurface}.
   */
  protected void updateNotifications() {
    FileEditor fileEditor = myFileEditorDelegate.get();
    VirtualFile file = fileEditor != null ? fileEditor.getFile() : null;
    if (file == null) return;
    UIUtil.invokeLaterIfNeeded(() -> EditorNotifications.getInstance(myProject).updateNotifications(file));
  }

  @NotNull
  public IssueListener getIssueListener() {
    return myIssueListener;
  }
}
