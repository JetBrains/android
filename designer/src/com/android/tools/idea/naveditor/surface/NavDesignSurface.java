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

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.editor.NlEditorPanel;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.scene.*;
import com.android.tools.idea.common.surface.*;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.configurations.ConfigurationStateManager;
import com.android.tools.idea.naveditor.editor.NavActionManager;
import com.android.tools.idea.naveditor.model.NavComponentHelperKt;
import com.android.tools.idea.naveditor.model.NavCoordinate;
import com.android.tools.idea.naveditor.scene.NavSceneManager;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.rendering.parsers.TagSnapshot;
import com.android.tools.idea.util.DependencyManagementUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
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
import com.intellij.ui.JBColor;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
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

import static com.android.SdkConstants.ATTR_GRAPH;
import static com.android.annotations.VisibleForTesting.Visibility;
import static com.android.tools.idea.projectsystem.ProjectSystemSyncUtil.PROJECT_SYSTEM_SYNC_TOPIC;

/**
 * {@link DesignSurface} for the navigation editor.
 */
public class NavDesignSurface extends DesignSurface {
  private static final int SCROLL_DURATION_MS = 300;
  private static final Object CONNECTION_CLIENT_PROPERTY_KEY = new Object();

  private NlComponent myCurrentNavigation;
  @VisibleForTesting
  AtomicReference<Future<?>> myScheduleRef = new AtomicReference<>();
  private final NlEditorPanel myEditorPanel;

  private static final WeakHashMap<AndroidFacet, SoftReference<ConfigurationManager>> ourConfigurationManagers = new WeakHashMap<>();

  @TestOnly
  public NavDesignSurface(@NotNull Project project, @NotNull Disposable parentDisposable) {
    this(project, null, parentDisposable);
  }

  /**
   * {@code editorPanel} should only be null in tests
   */
  public NavDesignSurface(@NotNull Project project, @Nullable NlEditorPanel editorPanel, @NotNull Disposable parentDisposable) {
    super(project, new SelectionModel(), parentDisposable);
    setBackground(JBColor.white);

    // TODO: add nav-specific issues
    // getIssueModel().addIssueProvider(new NavIssueProvider(project));
    myEditorPanel = editorPanel;
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
  public float getSceneScalingFactor() {
    return 1f;
  }

  @Override
  public void forceUserRequestedRefresh() {
  }

  @NotNull
  @Override
  protected NavActionManager createActionManager() {
    return new NavActionManager(this);
  }

  @NotNull
  @Override
  protected SceneManager createSceneManager(@NotNull NlModel model) {
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
          .syncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED, true);
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
        });
      }
      else {
        showFailToAddMessage(result, model);
      }
    });
    return result;
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
    ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(
      getProject(), "Failed to add navigation library dependency", "Failed to Add Dependency"));
    result.completeExceptionally(new Exception("Failed to add nav library dependency"));
  }

  private static boolean requestAddDependency(@NotNull AndroidFacet facet) {
    AtomicBoolean didAdd = new AtomicBoolean(false);
    ApplicationManager.getApplication().invokeAndWait(
      () -> didAdd.set(DependencyManagementUtil.addDependencies(
        // TODO: check for and add androidx dependency when it's released
        facet.getModule(), ImmutableList.of(GoogleMavenArtifactId.NAVIGATION_FRAGMENT.getCoordinate("+")), true, false).isEmpty()));
    return didAdd.get();
  }

  private static boolean tryToCreateSchema(@NotNull AndroidFacet facet) {
    return DumbService.getInstance(facet.getModule().getProject()).runReadActionInSmartMode(() -> {
      try {
        NavigationSchema.createIfNecessary(facet);
        return true;
      }
      catch (ClassNotFoundException e) {
        return false;
      }
    });
  }

  @Override
  protected void layoutContent() {
    requestRender();
  }

  @NotNull
  public NlComponent getCurrentNavigation() {
    if (myCurrentNavigation == null || myCurrentNavigation.getModel() != getModel()) {
      refreshRoot();
    }
    return myCurrentNavigation;
  }

  public void setCurrentNavigation(@NotNull NlComponent currentNavigation) {
    myCurrentNavigation = currentNavigation;
    //noinspection ConstantConditions  If the model is not null (which it must be if we're here), the sceneManager will also not be null.
    getSceneManager().update();
    getSelectionModel().clear();
    getSceneManager().layout(false);
    zoomToFit();
    currentNavigation.getModel().notifyModified(NlModel.ChangeType.UPDATE_HIERARCHY);
    repaint();
  }

  @Nullable
  @Override
  public Dimension getScrolledAreaSize() {
    return getContentSize(null);
  }

  @NotNull
  @Override
  public Dimension getContentSize(@Nullable Dimension dimension) {
    SceneView view = getCurrentSceneView();
    if (view == null) {
      Dimension dim = dimension == null ? new Dimension() : dimension;
      dim.setSize(0, 0);
      return dim;
    }
    return view.getSize(dimension);
  }

  @Override
  protected Dimension getDefaultOffset() {
    return new Dimension(0, 0);
  }

  @NavCoordinate
  @Override
  @NotNull
  protected Dimension getPreferredContentSize(int availableWidth, int availableHeight) {
    SceneView view = getCurrentSceneView();
    if (view == null) {
      return new Dimension(0, 0);
    }

    SceneComponent root = view.getScene().getRoot();
    if (root == null) {
      return new Dimension(0, 0);
    }

    @NavCoordinate Rectangle boundingBox = NavSceneManager.getBoundingBox(root);
    return boundingBox.getSize();
  }

  @Override
  public boolean isLayoutDisabled() {
    return false;
  }

  @Override
  public int getContentOriginX() {
    return 0;
  }

  @Override
  public int getContentOriginY() {
    return 0;
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
    return !isEmpty();
  }

  @Override
  protected double getFitScale(boolean fitInto) {
    return Math.min(super.getFitScale(fitInto), 1.0);
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
    if (NavComponentHelperKt.isNavigation(component)) {
      if (NavComponentHelperKt.isInclude(component)) {
        id = component.getAttribute(SdkConstants.AUTO_URI, ATTR_GRAPH);
        if (id == null) {
          // includes are always supposed to have a graph specified, but if not, give up.
          return;
        }
      }
      else {
        setCurrentNavigation(component);
        return;
      }
    }
    else {
      id = component.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT);
    }
    if (id != null) {
      Configuration configuration = getConfiguration();
      ResourceResolver resolver = configuration != null ? configuration.getResourceResolver() : null;
      ResourceValue value = resolver != null ? resolver.findResValue(id, false) : null;
      String fileName = value != null ? value.getValue() : null;
      if (fileName != null) {
        File file = new File(fileName);
        if (file.exists()) {
          VirtualFile virtualFile = VfsUtil.findFileByIoFile(file, false);
          if (virtualFile != null) {
            FileEditorManager.getInstance(getProject()).openFile(virtualFile, true);
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
            return;
          }
        }
      }
    }
    super.notifyComponentActivate(component);
  }

  @VisibleForTesting(visibility = Visibility.PROTECTED)
  @Nullable
  @Override
  public Interaction doCreateInteractionOnClick(int mouseX, int mouseY, @NotNull SceneView view) {
    return new SceneInteraction(view);
  }

  @Nullable
  @Override
  public Interaction createInteractionOnDrag(@NotNull SceneComponent draggedSceneComponent, @Nullable SceneComponent primary) {
    return null;
  }

  @Override
  public void zoom(@NotNull ZoomType type, @SwingCoordinate int x, @SwingCoordinate int y) {
    super.zoom(type, x, y);

    if (type == ZoomType.FIT || type == ZoomType.FIT_INTO) {
      // The navigation design surface differs from the other design surfaces in that there are
      // still scroll bars visible after doing a zoom to fit. As a result we need to explicitly
      // center the viewport.
      JViewport viewport = getScrollPane().getViewport();

      Rectangle bounds = viewport.getViewRect();
      Dimension size = viewport.getViewSize();

      viewport.setViewPosition(new Point((size.width - bounds.width) / 2, (size.height - bounds.height) / 2));
    }
  }

  @NotNull
  @SwingCoordinate
  public Dimension getExtentSize() {
    return getScrollPane().getViewport().getExtentSize();
  }

  public void scrollToCenter(@NotNull List<NlComponent> list) {
    Scene scene = getScene();
    SceneView view = getCurrentSceneView();
    if (list.isEmpty() || scene == null || view == null) {
      return;
    }

    @NavCoordinate Rectangle selectionBounds =
      NavSceneManager.getBoundingBox(list.stream().map(nlComponent -> scene.getSceneComponent(nlComponent))
                                         .filter(sceneComponent -> sceneComponent != null)
                                         .collect(Collectors.toList()));
    @SwingCoordinate Dimension swingViewportSize = getScrollPane().getViewport().getExtentSize();

    @SwingCoordinate int swingStartCenterXInViewport =
      Coordinates.getSwingX(view, (int)selectionBounds.getCenterX()) - getScrollPosition().x;
    @SwingCoordinate int swingStartCenterYInViewport =
      Coordinates.getSwingY(view, (int)selectionBounds.getCenterY()) - getScrollPosition().y;

    @SwingCoordinate Point start = new Point(swingStartCenterXInViewport, swingStartCenterYInViewport);
    @SwingCoordinate Point end = new Point(swingViewportSize.width / 2, swingViewportSize.height / 2);
    @SwingCoordinate LerpPoint lerpPoint = new LerpPoint(start, end, getScrollDurationMs());
    LerpValue zoomLerp = new LerpDouble(view.getScale(), getFitScale(selectionBounds.getSize(), true),
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
    myCurrentNavigation = match;
    zoomToFit();
  }

  @NotNull
  @Override
  protected DesignSurfaceActionHandler createActionHandler() {
    return new NavDesignSurfaceActionHandler(this);
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
      result = new ConfigurationManager(facet.getModule()) {
        @Override
        public ConfigurationStateManager getStateManager() {
          // Nav editor doesn't want persistent configuration state
          return new ConfigurationStateManager();
        }
      };
      ourConfigurationManagers.put(facet, new SoftReference<>(result));
    }
    return result;
  }

  @Override
  protected boolean getSupportPinchAndZoom() {
    // TODO: Enable pinch and zoom for navigation editor
    return false;
  }
}
