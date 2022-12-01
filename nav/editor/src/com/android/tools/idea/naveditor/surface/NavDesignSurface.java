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
package com.android.tools.idea.naveditor.surface;

import static com.android.SdkConstants.ATTR_GRAPH;
import static com.android.tools.idea.projectsystem.ProjectSystemSyncUtil.PROJECT_SYSTEM_SYNC_TOPIC;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.ACTIVATE_CLASS;
import static com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.ACTIVATE_INCLUDE;
import static com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.ACTIVATE_LAYOUT;
import static com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.ACTIVATE_NESTED;
import static com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.OPEN_FILE;

import com.android.SdkConstants;
import com.android.annotations.concurrency.UiThread;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.adtui.actions.ZoomType;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.AndroidStudioKotlinPluginUtils;
import com.android.tools.idea.common.editor.DesignerEditorPanel;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.DnDTransferComponent;
import com.android.tools.idea.common.model.DnDTransferItem;
import com.android.tools.idea.common.model.ItemTransferable;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.LerpDouble;
import com.android.tools.idea.common.scene.LerpPoint;
import com.android.tools.idea.common.scene.LerpValue;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.common.surface.SinglePositionableContentLayoutManager;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.configurations.ConfigurationStateManager;
import com.android.tools.idea.naveditor.analytics.NavUsageTracker;
import com.android.tools.idea.naveditor.editor.NavActionManager;
import com.android.tools.idea.naveditor.model.ActionType;
import com.android.tools.idea.naveditor.model.NavComponentHelperKt;
import com.android.tools.idea.naveditor.model.NavCoordinate;
import com.android.tools.idea.naveditor.scene.NavActionHelperKt;
import com.android.tools.idea.naveditor.scene.NavSceneManager;
import com.android.tools.idea.naveditor.scene.NavSceneManagerKt;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.rendering.parsers.TagSnapshot;
import com.android.tools.idea.util.DependencyManagementUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.reference.SoftReference;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Point2D;
import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.refactoring.MigrateToAndroidxUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * {@link DesignSurface} for the navigation editor.
 */
public class NavDesignSurface extends DesignSurface<NavSceneManager> {
  private static final int SCROLL_DURATION_MS = 300;
  private static final Object CONNECTION_CLIENT_PROPERTY_KEY = new Object();
  private static final String FAILED_DEPENDENCY = "Failed to add navigation dependency";
  private static final String FAILED_DEPENDENCY_TITLE = "Failed to Add Dependency";

  private NlComponent myCurrentNavigation;
  @VisibleForTesting
  AtomicReference<Future<?>> myScheduleRef = new AtomicReference<>();
  private DesignerEditorPanel myEditorPanel;

  private static final WeakHashMap<AndroidFacet, SoftReference<ConfigurationManager>> ourConfigurationManagers = new WeakHashMap<>();

  private static final List<GradleCoordinate> NAVIGATION_DEPENDENCIES = ImmutableList.of(
    GoogleMavenArtifactId.NAVIGATION_FRAGMENT.getCoordinate("+"),
    GoogleMavenArtifactId.NAVIGATION_UI.getCoordinate("+"));

  private static final List<GradleCoordinate> NAVIGATION_DEPENDENCIES_KTX = ImmutableList.of(
    GoogleMavenArtifactId.NAVIGATION_FRAGMENT_KTX.getCoordinate("+"),
    GoogleMavenArtifactId.NAVIGATION_UI_KTX.getCoordinate("+"));

  private static final List<GradleCoordinate> ANDROIDX_NAVIGATION_DEPENDENCIES = ImmutableList.of(
    GoogleMavenArtifactId.ANDROIDX_NAVIGATION_FRAGMENT.getCoordinate("+"),
    GoogleMavenArtifactId.ANDROIDX_NAVIGATION_UI.getCoordinate("+"));

  private static final List<GradleCoordinate> ANDROIDX_NAVIGATION_DEPENDENCIES_KTX = ImmutableList.of(
    GoogleMavenArtifactId.ANDROIDX_NAVIGATION_FRAGMENT_KTX.getCoordinate("+"),
    GoogleMavenArtifactId.ANDROIDX_NAVIGATION_UI_KTX.getCoordinate("+"));

  @TestOnly
  public NavDesignSurface(@NotNull Project project, @NotNull Disposable parentDisposable) {
    this(project, null, parentDisposable);
  }

  /**
   * {@code editorPanel} should only be null in tests
   */
  public NavDesignSurface(@NotNull Project project, @Nullable DesignerEditorPanel editorPanel, @NotNull Disposable parentDisposable) {
    super(project, parentDisposable, surface -> new NavActionManager((NavDesignSurface)surface), NavInteractionHandler::new,
          (surface) -> new SinglePositionableContentLayoutManager(),
          (surface) -> new NavDesignSurfaceActionHandler((NavDesignSurface)surface),
          ZoomControlsPolicy.VISIBLE);
    // TODO: add nav-specific issues
    // getIssueModel().addIssueProvider(new NavIssueProvider(project));
    myEditorPanel = editorPanel;

    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        removeComponentListener(this);
        requestRender();
      }
    });

    getSelectionModel().addListener((unused, selection) -> updateCurrentNavigation(selection));
  }

  @NotNull
  @Override
  public CompletableFuture<Void> requestRender() {
    // TODO: According to the documentation of this function -- "Invalidates all models and request a render of the layout. This will
    //  re-inflate the NlModel ...", we should implement NavSceneManager#requestLayoutAndRender() and call it because
    //  SceneManager#requestRender() doesn't re-inflate the NlModel.
    SceneManager manager = Iterables.getFirst(getSceneManagers(), null);
    return manager != null ? manager.requestRenderAsync() : CompletableFuture.completedFuture(null);
  }

  @Override
  public void dispose() {
    Future<?> future = getScheduleRef().get();
    if (future != null) {
      future.cancel(false);
    }
    getScheduleRef().set(null);
    super.dispose();
  }


  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (PlatformDataKeys.CONTEXT_MENU_POINT.is(dataId)) {
      NlComponent selection = getSelectionModel().getPrimary();
      if (selection != null && NavComponentHelperKt.isAction(selection)) {
        Scene scene = getScene();
        if (scene != null) {
          SceneComponent sceneComponent = scene.getSceneComponent(selection);
          if (sceneComponent != null) {
            Point2D.Float p2d = NavActionHelperKt.getAnyPoint(sceneComponent, SceneContext.get(getFocusedSceneView()));
            if (p2d != null) {
              return new Point((int)p2d.x, (int)p2d.y);
            }
          }
        }
      }
    }
    return super.getData(dataId);
  }

  @NotNull
  @Override
  public CompletableFuture<Void> forceUserRequestedRefresh() {
    return forceRefresh();
  }

  @Override
  public @NotNull CompletableFuture<Void> forceRefresh() {
    // Ignored for nav editor
    return CompletableFuture.completedFuture(null);
  }

  @NotNull
  @Override
  protected NavSceneManager createSceneManager(@NotNull NlModel model) {
    return new NavSceneManager(model, this);
  }

  @Nullable
  @Override
  public NavSceneManager getSceneManager() {
    return (NavSceneManager)super.getSceneManager();
  }

  /**
   * Before we can set the model we need to ensure that the {@link NavigationSchema} has been created.
   * Try to create it, adding the nav library dependency if necessary.
   */
  @Override
  public CompletableFuture<?> goingToSetModel(NlModel model) {
    // So it's cached in the future
    model.getConfiguration().getResourceResolver();

    AndroidFacet facet = model.getFacet();
    CompletableFuture<?> result = new CompletableFuture<>();
    Application application = ApplicationManager.getApplication();
    application.executeOnPooledThread(() -> {
      // First, try to create the schema. It should work if our project depends on the nav library.
      if (tryToCreateSchema(facet)) {
        result.complete(null);
      }
      // If it didn't work, it's probably because the nav library isn't included. Prompt for it to be added.
      else if (requestAddDependency(facet)) {
        ListenableFuture<?> syncResult = ProjectSystemUtil.getSyncManager(getProject())
          .syncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED);
        // When sync is done, try to create the schema again.
        Futures.addCallback(syncResult, new FutureCallback<Object>() {
          @Override
          public void onSuccess(@Nullable Object unused) {
            application.executeOnPooledThread(() -> {
              if (!tryToCreateSchema(facet)) {
                showFailToAddMessage(result, model);
              }
              else {
                result.complete(null);
              }
            });
          }

          @Override
          public void onFailure(@Nullable Throwable t) {
            showFailToAddMessage(result, model);
          }
        }, MoreExecutors.directExecutor());
      }
      else {
        showFailToAddMessage(result, model);
      }
    });
    return result;
  }

  @Override
  public CompletableFuture<Void> setModel(@Nullable NlModel model) {
    CompletableFuture<Void> future = super.setModel(model);
    NavUsageTracker.Companion.getInstance(model)
      .createEvent(OPEN_FILE)
      .withNavigationContents()
      .log();
    return future;
  }

  private void showFailToAddMessage(@NotNull CompletableFuture<?> result, @NotNull NlModel model) {
    if (myEditorPanel != null) {
      ProjectSystemSyncManager.SyncResultListener syncFailedListener = new ProjectSystemSyncManager.SyncResultListener() {
        @Override
        public void syncEnded(@NotNull ProjectSystemSyncManager.SyncResult result) {
          ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (tryToCreateSchema(model.getFacet())) {
              myEditorPanel.initNeleModel();
              ((MessageBusConnection)myEditorPanel.getClientProperty(CONNECTION_CLIENT_PROPERTY_KEY)).disconnect();
              myEditorPanel.putClientProperty(CONNECTION_CLIENT_PROPERTY_KEY, null);
            }
          });
        }
      };
      MessageBusConnection connection = getProject().getMessageBus().connect(this);
      myEditorPanel.putClientProperty(CONNECTION_CLIENT_PROPERTY_KEY, connection);
      connection.subscribe(PROJECT_SYSTEM_SYNC_TOPIC, syncFailedListener);
    }
    ApplicationManager.getApplication().invokeLater(() -> onFailedToAddDependency());
    result.completeExceptionally(new Exception(FAILED_DEPENDENCY));
  }

  private void onFailedToAddDependency() {
    Messages.showErrorDialog(getProject(), FAILED_DEPENDENCY, FAILED_DEPENDENCY_TITLE);
    if (myEditorPanel != null) {
      myEditorPanel.getWorkBench().loadingStopped(FAILED_DEPENDENCY);
    }
  }

  private boolean requestAddDependency(@NotNull AndroidFacet facet) {
    List<GradleCoordinate> dependencies = getDependencies(facet.getModule());

    AtomicBoolean didAdd = new AtomicBoolean(false);
    ApplicationManager.getApplication().invokeAndWait(
      () -> didAdd.set(DependencyManagementUtil.addDependenciesWithUiConfirmation(
        facet.getModule(), dependencies, true, false).isEmpty()));
    return didAdd.get();
  }

  @NotNull
  public static List<GradleCoordinate> getDependencies(@NotNull Module module) {
    boolean isKotlin = AndroidStudioKotlinPluginUtils.hasKotlinFacet(module);

    if (MigrateToAndroidxUtil.isAndroidx(module.getProject())) {
      return isKotlin ? ANDROIDX_NAVIGATION_DEPENDENCIES_KTX : ANDROIDX_NAVIGATION_DEPENDENCIES;
    }

    return isKotlin ? NAVIGATION_DEPENDENCIES_KTX : NAVIGATION_DEPENDENCIES;
  }

  private static boolean tryToCreateSchema(@NotNull AndroidFacet facet) {
    Module module = facet.getModule();
    return DumbService.getInstance(module.getProject()).runReadActionInSmartMode(() -> {
      try {
        NavigationSchema.createIfNecessary(module);
        return true;
      }
      catch (ClassNotFoundException e) {
        return false;
      }
    });
  }

  @Override
  public void activate() {
    super.activate();
    NlModel model = getModel();
    if (model != null) {
      Module module = model.getModule();
      try {
        NavigationSchema.createIfNecessary(module);
      }
      catch (ClassNotFoundException e) {
        // We don't have a schema at all, no need to try to update.
        return;
      }

      NavigationSchema schema = NavigationSchema.get(module);
      if (!schema.quickValidate()) {
        if (myEditorPanel == null) {
          return;
        }
        myEditorPanel.getWorkBench().showLoading("Refreshing Navigators...");
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          try {
            schema.rebuildSchema().get();
            ApplicationManager.getApplication().invokeLater(() -> myEditorPanel.getWorkBench().hideLoading());
          }
          catch (Exception e) {
            ApplicationManager.getApplication().invokeLater(
              () -> myEditorPanel.getWorkBench().loadingStopped("Error refreshing Navigators"));
          }
        });
      }
      else {
        schema.rebuildSchema();
      }
    }
  }

  @Override
  @NotNull
  public ItemTransferable getSelectionAsTransferable() {
    NlModel model = getModel();

    ImmutableList<DnDTransferComponent> components =
      getSelectionModel().getSelection().stream()
        .map(component -> new DnDTransferComponent(component.getTagName(), component.getTagDeprecated().getText(), 0, 0))
        .collect(toImmutableList());
    return new ItemTransferable(new DnDTransferItem(model != null ? model.getId() : 0, components));
  }

  @NotNull
  public NlComponent getCurrentNavigation() {
    if (!validateCurrentNavigation()) {
      refreshRoot();
    }
    return myCurrentNavigation;
  }

  private Boolean validateCurrentNavigation() {
    NlComponent current = myCurrentNavigation;
    if (current == null || current.getModel() != getModel()) {
      return false;
    }

    while (current.getParent() != null) {
      NlComponent parent = current.getParent();
      if (!parent.getChildren().contains(current)) {
        return false;
      }

      current = parent;
    }

    List<NlComponent> components = getModel().getComponents();
    assert (components.size() == 1);

    return (current == components.get(0));
  }

  public void setCurrentNavigation(@NotNull NlComponent currentNavigation) {
    myCurrentNavigation = currentNavigation;
    //noinspection ConstantConditions  If the model is not null (which it must be if we're here), the sceneManager will also not be null.
    getSceneManager().update();
    getSceneManager().layout(false);
    zoomToFit();
    currentNavigation.getModel().notifyModified(NlModel.ChangeType.UPDATE_HIERARCHY);
    repaint();
  }

  @Override
  protected Dimension getScrollToVisibleOffset() {
    return new Dimension(0, 0);
  }

  @NavCoordinate
  @Override
  @NotNull
  protected Dimension getPreferredContentSize(int availableWidth, int availableHeight) {
    SceneView view = getFocusedSceneView();
    if (view == null) {
      return new Dimension(0, 0);
    }

    SceneComponent root = view.getScene().getRoot();
    if (root == null) {
      return new Dimension(0, 0);
    }

    @NavCoordinate Rectangle boundingBox = NavSceneManagerKt.getBoundingBox(root);
    return boundingBox.getSize();
  }

  @Override
  public boolean isLayoutDisabled() {
    return false;
  }

  @Override
  public boolean setScale(double scale, int x, int y) {
    SceneView view = getFocusedSceneView();
    if (view == null) {
      // There is no scene view. Nothing we can do.
      return false;
    }

    Point oldViewPosition = getScrollPosition();
    if (x < 0 || y < 0) {
      x = oldViewPosition.x + getViewport().getViewportComponent().getWidth() / 2;
      y = oldViewPosition.y + getViewport().getViewportComponent().getHeight() / 2;
    }

    @AndroidDpCoordinate int androidX = Coordinates.getAndroidXDip(view, x);
    @AndroidDpCoordinate int androidY = Coordinates.getAndroidYDip(view, y);

    boolean ret = super.setScale(scale, x, y);

    @SwingCoordinate int shiftedX = Coordinates.getSwingXDip(view, androidX);
    @SwingCoordinate int shiftedY = Coordinates.getSwingYDip(view, androidY);
    getViewport().setViewPosition(new Point(oldViewPosition.x + shiftedX - x, oldViewPosition.y + shiftedY - y));

    return ret;
  }

  @Override
  protected boolean isKeepingScaleWhenReopen() {
    // TODO: Keeping same scale for Navigation Editor and remove this function from DesignSurface
    // Navigation Editors calls zoom-to-fit automatically when NlModel is set. Some zoom-to-fit functions is called when editor is empty,
    // which makes the scale value become 0%. We don't want to keep this 0% scale value here.
    // To resolve this issue, the zoomToFit() function should be removed from NavSceneManager.requestRender().
    return false;
  }

  @Override
  protected double getMinScale() {
    return isEmpty() ? 1.0 : 0.1;
  }

  @Override
  protected double getMaxScale() {
    return isEmpty() ? 1.0 : 3.0;
  }

  @Override
  public boolean canZoomToFit() {
    if (isEmpty()) {
      return false;
    }

    Double fitScale = getFitScale();
    Double scale = getScale();

    return Math.abs(fitScale - scale) > SCALING_THRESHOLD;
  }

  @Override
  public double getFitScale() {
    return Math.min(super.getFitScale(), 1.0);
  }

  private boolean isEmpty() {
    NavSceneManager sceneManager = getSceneManager();
    return sceneManager == null || sceneManager.isEmpty();
  }

  @Override
  public void notifyComponentActivate(@NotNull NlComponent component) {
    if (myCurrentNavigation == component) {
      return;
    }
    String id;
    NavEditorEventType metricsEventType;

    if (NavComponentHelperKt.isNavigation(component)) {
      if (NavComponentHelperKt.isInclude(component)) {
        id = component.getAttribute(SdkConstants.AUTO_URI, ATTR_GRAPH);
        metricsEventType = ACTIVATE_INCLUDE;
        if (id == null) {
          // includes are always supposed to have a graph specified, but if not, give up.
          return;
        }
      }
      else {
        setCurrentNavigation(component);
        NavUsageTracker.Companion.getInstance(getModel()).createEvent(ACTIVATE_NESTED).log();
        return;
      }
    }
    else {
      id = component.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT);
      metricsEventType = ACTIVATE_LAYOUT;
    }
    if (id != null) {
      Configuration configuration = Iterables.getOnlyElement(getConfigurations(), null);
      ResourceResolver resolver = configuration != null ? configuration.getResourceResolver() : null;
      ResourceValue value = resolver != null ? resolver.findResValue(id, false) : null;
      String fileName = value != null ? value.getValue() : null;
      if (fileName != null) {
        File file = new File(fileName);
        if (file.exists()) {
          VirtualFile virtualFile = VfsUtil.findFileByIoFile(file, false);
          if (virtualFile != null) {
            FileEditorManager.getInstance(getProject()).openFile(virtualFile, true);
            NavUsageTracker.Companion.getInstance(getModel()).createEvent(metricsEventType).log();
            return;
          }
        }
      }
    }

    String className = component.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
    if (className != null) {
      PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass(className, GlobalSearchScope.allScope(getProject()));
      if (psiClass != null) {
        PsiFile file = psiClass.getContainingFile();
        if (file != null) {
          VirtualFile virtualFile = file.getVirtualFile();
          if (virtualFile != null) {
            FileEditorManager.getInstance(getProject()).openFile(virtualFile, true);
            NavUsageTracker.Companion.getInstance(getModel()).createEvent(ACTIVATE_CLASS).log();
            return;
          }
        }
      }
    }
    super.notifyComponentActivate(component);
  }

  @UiThread
  @Override
  public boolean zoom(@NotNull ZoomType type, @SwingCoordinate int x, @SwingCoordinate int y) {
    // track user triggered change
    getAnalyticsManager().trackZoom(type);
    boolean scaled = super.zoom(type, x, y);
    boolean isFitZoom = type == ZoomType.FIT;

    if (scaled || isFitZoom) {
      // The padding around the nav editor is calculated when NavSceneManager.requestLayout is called. If we have changed the scale
      // or we will re-center the area, we need to re-calculate the bounding box.
      NavSceneManager sceneManager = getSceneManager();

      if (sceneManager != null) {
        sceneManager.layout(false);
        // If the Scene size has changed, we might need to resize the viewport dimensions. Ask the scroll panel to revalidate.
        validateScrollArea();
      }
    }

    if (isFitZoom) {
      // The navigation design surface differs from the other design surfaces in that there are
      // still scroll bars visible after doing a zoom to fit. As a result we need to explicitly
      // center the viewport.
      Dimension visibleSize = getExtentSize();
      Dimension size = getViewSize();

      setScrollPosition((size.width - visibleSize.width) / 2, (size.height - visibleSize.height) / 2);
    }
    return scaled;
  }

  @Override
  public void scrollToCenter(@NotNull List<NlComponent> list) {
    Scene scene = getScene();
    SceneView view = getFocusedSceneView();
    if (list.isEmpty() || scene == null || view == null) {
      return;
    }

    @NavCoordinate Rectangle selectionBounds =
      NavSceneManagerKt.getBoundingBox(list.stream().map(nlComponent -> scene.getSceneComponent(nlComponent))
                                         .filter(sceneComponent -> sceneComponent != null)
                                         .collect(Collectors.toList()));
    @SwingCoordinate Dimension swingViewportSize = getExtentSize();

    @SwingCoordinate int swingStartCenterXInViewport =
      Coordinates.getSwingX(view, (int)selectionBounds.getCenterX()) - getScrollPosition().x;
    @SwingCoordinate int swingStartCenterYInViewport =
      Coordinates.getSwingY(view, (int)selectionBounds.getCenterY()) - getScrollPosition().y;

    @SwingCoordinate Point start = new Point(swingStartCenterXInViewport, swingStartCenterYInViewport);
    @SwingCoordinate Point end = new Point(swingViewportSize.width / 2, swingViewportSize.height / 2);
    @SwingCoordinate LerpPoint lerpPoint = new LerpPoint(start, end, getScrollDurationMs());
    LerpValue zoomLerp = new LerpDouble(view.getScale(), Math.min(getFitScale(selectionBounds.getSize()), 1.0),
                                        getScrollDurationMs());

    if (getScheduleRef().get() != null) {
      getScheduleRef().get().cancel(false);
    }

    Runnable action = () -> UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      long time = System.currentTimeMillis();
      @SwingCoordinate Point pointSwingValue = lerpPoint.getValue(time);
      @SwingCoordinate int targetSwingX = Coordinates.getSwingX(view, (int)selectionBounds.getCenterX());
      @SwingCoordinate int targetSwingY = Coordinates.getSwingY(view, (int)selectionBounds.getCenterY());

      setScrollPosition(targetSwingX - pointSwingValue.x, targetSwingY - pointSwingValue.y);
      setScale((double)zoomLerp.getValue(time), targetSwingX, targetSwingY);
      if (lerpPoint.isComplete(time)) {
        getScheduleRef().get().cancel(false);
        getScheduleRef().set(null);
      }
    });

    getScheduleRef().set(AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(action, 0, 10, TimeUnit.MILLISECONDS));
  }

  @VisibleForTesting
  @NotNull
  AtomicReference<Future<?>> getScheduleRef() {
    return myScheduleRef;
  }

  @VisibleForTesting
  int getScrollDurationMs() {
    return SCROLL_DURATION_MS;
  }

  /**
   * Sometimes the model gets regenerated and we need to update the current view root component. This tries to do that as best as possible.
   */
  public void refreshRoot() {
    NlModel model = getModel();
    if (model == null) {
      return;
    }
    NlComponent match = model.getComponents().get(0);
    if (myCurrentNavigation != null) {
      boolean includingParent = false;
      TagSnapshot currentSnapshot = myCurrentNavigation.getSnapshot();
      NlComponent currentParent = myCurrentNavigation.getParent();
      for (NlComponent component : (Iterable<NlComponent>)model.flattenComponents()::iterator) {
        if (!NavComponentHelperKt.isNavigation(component)) {
          continue;
        }
        if (component == myCurrentNavigation) {
          // The old component still exists, so don't change anything
          return;
        }
        TagSnapshot componentSnapshot = component.getSnapshot();
        if (currentSnapshot != null && currentSnapshot == componentSnapshot) {
          // This corresponds exactly to the old component, and is surely the best we can do.
          match = component;
          break;
        }
        // We might not have found the best match yet, keep looking
        if (!includingParent) {
          if (Objects.equals(component.getId(), myCurrentNavigation.getId())) {
            match = component;
            NlComponent componentParent = component.getParent();
            if ((componentParent == null) != (currentParent == null)) {
              continue;
            }
            if (componentParent == null || Objects.equals(componentParent.getId(), currentParent.getId())) {
              // Both the component ids and the parent ids match, so this is a pretty good match.
              includingParent = true;
            }
          }
        }
      }
    }

    if (myCurrentNavigation != match) {
      myCurrentNavigation = match;
      getSelectionModel().setSelection((ImmutableList.of(myCurrentNavigation)));
    }
  }

  @NotNull
  @Override
  public ConfigurationManager getConfigurationManager(@NotNull AndroidFacet facet) {
    SoftReference<ConfigurationManager> ref = ourConfigurationManagers.get(facet);
    ConfigurationManager result = null;
    if (ref != null) {
      result = ref.get();
    }
    if (result == null) {
      result = new MyConfigurationManager(facet.getModule());
      ourConfigurationManagers.put(facet, new SoftReference<>(result));
    }
    return result;
  }

  @Override
  protected boolean getSupportPinchAndZoom() {
    // TODO: Enable pinch and zoom for navigation editor
    return false;
  }

  /**
   * Returns all the components under the current navigation
   * that are selectable in the design surface
   * Contains:
   * Current root navigation
   * Global actions under current root
   * Destinations under current root
   * Actions under the above destinations that point to a visible destination
   *
   * @return the list of destinations
   */
  @NotNull
  @Override
  public List<NlComponent> getSelectableComponents() {
    NlComponent root = getCurrentNavigation();
    return root.flatten().filter(component ->
                                   component == root ||
                                   (NavComponentHelperKt.isDestination(component) && component.getParent() == root) ||
                                   (NavComponentHelperKt.isAction(component) &&
                                    (component.getParent() == root ||
                                     (component.getParent() != null && component.getParent().getParent() == root) ||
                                     NavComponentHelperKt.getActionType(component, root) == ActionType.EXIT_DESTINATION))
    ).collect(Collectors.toList());
  }

  /*
   * If none of the newly selected item are visible, update the current navigation so that the first one is visible.
   */
  private void updateCurrentNavigation(@NotNull List<NlComponent> selection) {
    if (selection.isEmpty()) {
      return;
    }

    if (getSelectableComponents().stream().anyMatch(selection::contains)) {
      return;
    }

    NlComponent selected = selection.get(0);

    NlComponent next = selected.getParent();
    if (next == null) {
      next = selected;
    }

    while (next != null && !NavComponentHelperKt.isNavigation(next)) {
      next = next.getParent();
    }

    if (next != null) {
      setCurrentNavigation(next);
    }
  }

  private static class MyConfigurationManager extends ConfigurationManager {
    MyConfigurationManager(@NotNull Module module) {
      super(module);
    }

    @Override
    public ConfigurationStateManager getStateManager() {
      // Nav editor doesn't want persistent configuration state.
      return new ConfigurationStateManager();
    }
  }
}
