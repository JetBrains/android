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
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.*;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.Interaction;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.common.surface.ZoomType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.naveditor.editor.NavActionManager;
import com.android.tools.idea.naveditor.model.NavCoordinate;
import com.android.tools.idea.naveditor.scene.NavSceneManager;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
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
import com.intellij.ui.JBColor;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.android.SdkConstants.TAG_INCLUDE;
import static com.android.annotations.VisibleForTesting.Visibility;

/**
 * {@link DesignSurface} for the navigation editor.
 */
public class NavDesignSurface extends DesignSurface {
  private static final int SCROLL_DURATION_MS = 300;

  private NavigationSchema mySchema;
  private NlComponent myCurrentNavigation;
  @VisibleForTesting
  AtomicReference<Future<?>> myScheduleRef = new AtomicReference<>();

  public NavDesignSurface(@NotNull Project project, @NotNull Disposable parentDisposable) {
    super(project, parentDisposable);
    setBackground(JBColor.white);
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

  @NotNull
  public NavigationSchema getSchema() {
    // TODO: simplify this logic if possible:
    if (mySchema == null) {
      NlModel model = getModel();
      assert model != null;  // TODO: make sure this cannot happen
      mySchema = NavigationSchema.get(model.getFacet());
    }
    return mySchema;
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
    AndroidFacet facet = model.getFacet();
    CompletableFuture<?> result = new CompletableFuture<>();
    Application application = ApplicationManager.getApplication();
    Project project = model.getProject();
    application.executeOnPooledThread(() -> {
      // First, try to create the schema. It should work if our project depends on the nav library.
      if (tryToCreateSchema(facet)) {
        result.complete(null);
      }
      // If it didn't work, it's probably because the nav library isn't included. Prompt for it to be added.
      else if (requestAddDependency(facet)) {
        ListenableFuture<?> syncResult = ProjectSystemUtil.getSyncManager(project)
          .syncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED, true);
        // When sync is done, try to create the schema again.
        Futures.addCallback(syncResult, new FutureCallback<Object>() {
          @Override
          public void onSuccess(@Nullable Object unused) {
            application.executeOnPooledThread(() -> {
              if (!tryToCreateSchema(facet)) {
                showFailToAddMessage(project);
              }
              result.complete(null);
            });
          }

          @Override
          public void onFailure(@Nullable Throwable t) {
            showFailToAddMessage(project);
            result.complete(null);
          }
        });
      }
      else {
        showFailToAddMessage(project);
        result.complete(null);
      }
    });
    return result;
  }

  private static void showFailToAddMessage(@NotNull Project project) {
    ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(
      project, "Failed to add navigation library dependency", "Failed to Add Dependency"));
  }

  private static boolean requestAddDependency(@NotNull AndroidFacet facet) {
    AtomicBoolean didAdd = new AtomicBoolean(false);
    ApplicationManager.getApplication().invokeAndWait(
      () -> didAdd.set(DependencyManagementUtil.addDependencies(
        facet.getModule(), ImmutableList.of(GoogleMavenArtifactId.NAVIGATION), true, false, true).isEmpty()));
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
      if (getModel() != null) {
        myCurrentNavigation = getModel().getComponents().get(0);
      }
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
    return 0.1;
  }

  @Override
  public void notifyComponentActivate(@NotNull NlComponent component) {
    String tagName = component.getTagName();
    String id;
    if (getSchema().getDestinationType(tagName) == NavigationSchema.DestinationType.NAVIGATION) {
      if (tagName.equals(TAG_INCLUDE)) {
        id = component.getAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_GRAPH);
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
      NavSceneManager.getBoundingBox(list.stream().map(nlComponent -> scene.getSceneComponent(nlComponent)).collect(Collectors.toList()));
    @SwingCoordinate Dimension swingViewportSize = getScrollPane().getViewport().getExtentSize();

    @SwingCoordinate int swingStartCenterXInViewport =
      Coordinates.getSwingX(view, (int)selectionBounds.getCenterX()) - getScrollPosition().x;
    @SwingCoordinate int swingStartCenterYInViewport =
      Coordinates.getSwingY(view, (int)selectionBounds.getCenterY()) - getScrollPosition().y;

    @SwingCoordinate LerpValue xLerp = new LerpValue(swingStartCenterXInViewport, swingViewportSize.width / 2, getScrollDurationMs());
    @SwingCoordinate LerpValue yLerp = new LerpValue(swingStartCenterYInViewport, swingViewportSize.height / 2, getScrollDurationMs());
    LerpValue zoomLerp = new LerpValue((int)(view.getScale() * 100), (int)(getFitScale(selectionBounds.getSize(), true) * 100),
                                       getScrollDurationMs());

    if (getScheduleRef().get() != null) {
      getScheduleRef().get().cancel(false);
    }

    Runnable action = () -> {
      UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
        long time = System.currentTimeMillis();
        @SwingCoordinate int xSwingValue = xLerp.getValue(time);
        @SwingCoordinate int ySwingValue = yLerp.getValue(time);
        @SwingCoordinate int targetSwingX = Coordinates.getSwingX(view, (int)selectionBounds.getCenterX());
        @SwingCoordinate int targetSwingY = Coordinates.getSwingY(view, (int)selectionBounds.getCenterY());

        setScrollPosition(targetSwingX - xSwingValue, targetSwingY - ySwingValue);
        setScale(zoomLerp.getValue(time) / 100., targetSwingX, targetSwingY);
        if (xSwingValue == xLerp.getEnd() && ySwingValue == yLerp.getEnd()) {
          getScheduleRef().get().cancel(false);
          getScheduleRef().set(null);
        }
      });
    };

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
}