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

import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.editor.ActionManager;
import com.android.tools.idea.naveditor.editor.NavActionManager;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.naveditor.scene.NavSceneManager;
import com.android.tools.idea.uibuilder.scene.SceneManager;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.SceneLayer;
import com.android.tools.idea.uibuilder.surface.SceneView;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * {@link DesignSurface} for the navigation editor.
 */
public class NavDesignSurface extends DesignSurface {
  private NavView myNavView;

  public NavDesignSurface(@NotNull Project project) {
    super(project);
    zoomActual();
  }

  @Override
  protected ActionManager createActionManager() {
    return new NavActionManager(this);
  }

  @NotNull
  @Override
  protected SceneManager createSceneManager(@NotNull NlModel model) {
    return new NavSceneManager(model, this);
  }

  @Override
  public NavSceneManager getSceneManager() {
    return (NavSceneManager)super.getSceneManager();
  }

  @Override
  protected void layoutContent() {
    requestRender();
  }

  @Override
  public void repaint() {
    super.repaint();
  }

  @Override
  protected void doCreateSceneViews() {
    NlModel model = getModel();
    if (model == null && myNavView == null) {
      return;
    }
    myNavView = null;
    myLayers.clear();
    if (model != null) {
      myNavView = new NavView(this, model);
      myLayers.add(new SceneLayer(this, myNavView, true));

      getLayeredPane().setPreferredSize(myNavView.getPreferredSize());

      layoutContent();
    }
    setShowErrorPanel(false);
  }

  @Nullable
  @Override
  public Dimension getScrolledAreaSize() {
    return getContentSize(null);
  }

  @Nullable
  @Override
  public SceneView getCurrentSceneView() {
    return myNavView;
  }

  @NotNull
  @Override
  public Dimension getContentSize(@Nullable Dimension dimension) {
    if (dimension == null) {
      dimension = new Dimension();
    }
    if (getSceneManager() == null) {
      dimension.setSize(0, 0);
    }
    else {
      getSceneManager().getContentSize(dimension);
    }
    return dimension;
  }

  @Override
  protected Dimension getDefaultOffset() {
    return new Dimension(20, 20);
  }

  @Override
  protected Dimension getPreferredContentSize(int availableWidth, int availableHeight) {
    return getContentSize(new Dimension());
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
  public Configuration getConfiguration() {
    return null;
  }
}
