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
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.naveditor.editor.NavActionManager;
import com.android.tools.idea.naveditor.property.inspector.NavInspectorProviders;
import com.android.tools.idea.naveditor.scene.NavSceneManager;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneInteraction;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.Interaction;
import com.android.tools.idea.common.surface.SceneLayer;
import com.android.tools.idea.common.surface.SceneView;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.JBColor;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;

import static com.android.annotations.VisibleForTesting.Visibility;

/**
 * {@link DesignSurface} for the navigation editor.
 */
public class NavDesignSurface extends DesignSurface {
  private NavView myNavView;
  private NavigationSchema mySchema;
  private NlComponent myCurrentNavigation;
  private double myOldScale = 0;

  public NavDesignSurface(@NotNull Project project, @NotNull Disposable parentDisposable) {
    super(project, parentDisposable);
    zoomActual();

    // If we've previously offset the view or expanded the scrollable area size beyond the root, we want to undo that offset as we scroll
    // back toward the content instead of actually scrolling.
    getScrollPane().getHorizontalScrollBar().addAdjustmentListener(event -> {
      if (myNavView != null && myNavView.getX() > 0) {
        JViewport viewport = getScrollPane().getViewport();
        int viewX = (int)viewport.getViewPosition().getX();
        myNavView.setLocation(myNavView.getX() - viewX, myNavView.getY());
        viewport.setViewPosition(new Point(0, (int)viewport.getViewPosition().getY()));
        if (getScene() != null) {
          getScene().needsRebuildList();
        }
      }
      updateScrolledAreaSize();
    });
    getScrollPane().getVerticalScrollBar().addAdjustmentListener(event -> {
      if (myNavView != null && myNavView.getY() > 0) {
        JViewport viewport = getScrollPane().getViewport();
        int viewY = (int)viewport.getViewPosition().getY();
        myNavView.setLocation(myNavView.getX(), myNavView.getY() - viewY);
        viewport.setViewPosition(new Point((int)viewport.getViewPosition().getX(), 0));
        if (getScene() != null) {
          getScene().needsRebuildList();
        }
        updateScrolledAreaSize();
      }
    });
  }

  @NotNull
  public NavigationSchema getSchema() {
    // TODO: simplify this logic if possible:
    if (mySchema == null) {
      NlModel model = getModel();
      assert model != null;  // TODO: make sure this cannot happen
      mySchema = NavigationSchema.getOrCreateSchema(model.getFacet());
    }
    return mySchema;
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

  @NotNull
  @Override
  public NavInspectorProviders getInspectorProviders(@NotNull NlPropertiesManager propertiesManager, @NotNull Disposable parentDisposable) {
    return new NavInspectorProviders(propertiesManager, parentDisposable);
  }

  @Override
  protected void layoutContent() {
    requestRender();
  }

  @NotNull
  @Override
  protected JBColor getBackgroundColor() {
    return JBColor.white;
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
    currentNavigation.getModel().notifyModified(NlModel.ChangeType.UPDATE_HIERARCHY);
    repaint();
  }

  @Override
  protected void doCreateSceneViews() {
    NlModel model = getModel();
    if (model == null && myNavView == null) {
      return;
    }
    myNavView = null;
    if (model != null) {
      myNavView = new NavView(this, model);
      setLayers(ImmutableList.of(new SceneLayer(this, myNavView, true)));

      getLayeredPane().setPreferredSize(myNavView.getPreferredSize());

      layoutContent();
    }
    setShowIssuePanel(false);
  }

  @Nullable
  @Override
  public Dimension getScrolledAreaSize() {
    Dimension content = getContentSize(null);
    SceneView view = getCurrentSceneView();

    if (myOldScale != myScale || view == null) {
      // If we've changed scale, just fit to the content.
      myOldScale = myScale;
      return content;
    }

    // Otherwise, ensure that the content stays where it is in the view.
    Dimension viewExtents = getScrollPane().getViewport().getExtentSize();
    Point viewPosition = getScrollPosition();

    return new Dimension((int)Math.max(viewPosition.getX() + viewExtents.getWidth(),
                                       getCurrentSceneView().getX() + content.getWidth()),
                         (int)Math.max(viewPosition.getY() + viewExtents.getHeight(),
                                       getCurrentSceneView().getY() + content.getHeight()));
  }

  @Nullable
  @Override
  public SceneView getCurrentSceneView() {
    return myNavView;
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
    return new Dimension(20, 20);
  }

  @Override
  @NotNull
  protected Dimension getPreferredContentSize(int availableWidth, int availableHeight) {
    if (getCurrentSceneView() == null) {
      return new Dimension(availableWidth, availableHeight);
    }
    return getCurrentSceneView().getPreferredSize();
  }

  @Override
  public boolean isLayoutDisabled() {
    return false;
  }

  @Override
  protected int getContentOriginX() {
    return 0;
  }

  @Override
  protected int getContentOriginY() {
    return 0;
  }

  @Override
  public void notifyComponentActivate(@NotNull NlComponent component) {
    if (getSchema().getDestinationType(component.getTagName()) == NavigationSchema.DestinationType.NAVIGATION) {
       setCurrentNavigation(component);
    }
    else {
      String layout = component.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT);
      if (layout != null) {
        Configuration configuration = getConfiguration();
        ResourceResolver resolver = configuration != null ? configuration.getResourceResolver() : null;
        ResourceValue value = resolver != null ? resolver.findResValue(layout, false) : null;
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
}
