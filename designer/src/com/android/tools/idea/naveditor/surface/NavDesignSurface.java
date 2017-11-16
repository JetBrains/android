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
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneInteraction;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.surface.*;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.naveditor.editor.NavActionManager;
import com.android.tools.idea.naveditor.scene.NavSceneManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;

import static com.android.annotations.VisibleForTesting.Visibility;
import static org.jetbrains.android.dom.navigation.NavigationSchema.TAG_INCLUDE;

/**
 * {@link DesignSurface} for the navigation editor.
 */
public class NavDesignSurface extends DesignSurface {
  private NavigationSchema mySchema;
  private NlComponent myCurrentNavigation;

  public NavDesignSurface(@NotNull Project project, @NotNull Disposable parentDisposable) {
    super(project, parentDisposable);
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

    @AndroidDpCoordinate Rectangle boundingBox = NavSceneManager.getBoundingBox(root);
    return new Dimension(Coordinates.dpToPx(view, boundingBox.width),
                         Coordinates.dpToPx(view, boundingBox.height));
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
  protected double getMaxScale() {
    if (SystemInfo.isMac && UIUtil.isRetina()) {
      return 0.5;
    }
    return 1;
  }

  @Override
  protected double getMinScale() {
    if (SystemInfo.isMac && UIUtil.isRetina()) {
      return 0.05;
    }
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
}
