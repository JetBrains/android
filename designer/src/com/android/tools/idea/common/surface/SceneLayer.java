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

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.scene.Display;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * Basic display layer for Scene
 */
public class SceneLayer extends Layer {
  private final DesignSurface<?> myDesignSurface;
  private final SceneView mySceneView;
  private final Dimension myScreenViewSize = new Dimension();
  private final Rectangle mySizeRectangle = new Rectangle();
  private final boolean myShowAlways;
  private final Display myDisplay = new Display();
  private boolean myShowOnHover = false;
  private boolean myAlwaysShowSelection;
  private boolean myTemporaryShow = false;

  /**
   * Filter that affects the value of {@link myShowOnHover}. Such variable
   * will only be true when {@link mySceneView} is being hovered, and it's
   * not filtered out by this filter.
   */
  private Function<SceneView, Boolean> myShowOnHoverFilter = sceneView -> true;

  /**
   * Default constructor
   *
   * @param view the current ScreenView
   */
  public SceneLayer(@NotNull DesignSurface<?> surface, @NotNull SceneView view, boolean showAlways) {
    myDesignSurface = surface;
    mySceneView = view;
    myShowAlways = showAlways;
  }

  /**
   * Paint function for the layer. Delegate the painting of the Scene to a Display instance.
   *
   * @param g2 the graphics context
   */
  @Override
  public void paint(@NotNull Graphics2D g2) {
    SceneContext sceneContext = SceneContext.get(mySceneView);
    if (!myTemporaryShow && !myShowOnHover && !myShowAlways && !myAlwaysShowSelection) {
      return;
    }
    if (!myShowAlways && getSceneView().getSurface() instanceof NlDesignSurface) {
      NlDesignSurface designSurface = (NlDesignSurface) getSceneView().getSurface();
      if (designSurface.isRenderingSynchronously() && !designSurface.isInAnimationScrubbing()) {
        return;
      }
    }
    sceneContext.setShowOnlySelection(!myTemporaryShow && !myShowOnHover && myAlwaysShowSelection);
    Graphics2D g = (Graphics2D)g2.create();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      mySceneView.getScaledContentSize(myScreenViewSize);

      Rectangle clipBounds = g.getClipBounds();
      mySizeRectangle.setBounds(mySceneView.getX(), mySceneView.getY(), myScreenViewSize.width, myScreenViewSize.height);
      Rectangle2D.intersect(mySizeRectangle, clipBounds, mySizeRectangle);
      if (mySizeRectangle.isEmpty()) {
        return;
      }

      sceneContext.setRenderableBounds(clipBounds);

      if (myShowAlways) {
        paintBackground(g, sceneContext);
      }

      // When screen rotation feature is enabled, we want to hide the relevant drawings.
      DesignSurface<?> sufrace = mySceneView.getSurface();
      if (sufrace instanceof NlDesignSurface) {
        NlDesignSurface nlSurface = (NlDesignSurface)sufrace;
        float degree = nlSurface.getRotateSurfaceDegree();
        if (!Float.isNaN(degree)) {
          return;
        }
      }
      // Draw the components
      myDisplay.draw(sceneContext, g, mySceneView.getScene());
    }
    finally {
      g.dispose();
    }
  }

  private void paintBackground(@NotNull Graphics2D g, @NotNull SceneContext sceneContext) {
    Color backgroundColor = sceneContext.getColorSet().getBackground();
    if (backgroundColor == null) {
      return;
    }

    Shape shape = mySceneView.getScreenShape();
    if (shape == null) {
      g.setColor(backgroundColor);
      g.fillRect(mySizeRectangle.x, mySizeRectangle.y, mySizeRectangle.width, mySizeRectangle.height);
    }
    else {
      g.setColor(backgroundColor.darker());
      g.fillRect(mySizeRectangle.x, mySizeRectangle.y, mySizeRectangle.width, mySizeRectangle.height);
      Shape clip = g.getClip();
      g.setColor(backgroundColor);
      g.setClip(shape);
      g.fillRect(mySizeRectangle.x, mySizeRectangle.y, mySizeRectangle.width, mySizeRectangle.height);
      g.setClip(clip);
    }
  }

  public boolean isShowOnHover() {
    return myShowOnHover;
  }

  public void setShowOnHover(boolean value) {
    myShowOnHover = value;
  }

  public void setShowOnHoverFilter(Function<SceneView, Boolean> filter) {
    myShowOnHoverFilter = filter;
  }

  public SceneView getSceneView() {
    return mySceneView;
  }

  @Override
  public void onHover(@SwingCoordinate int x, @SwingCoordinate int y) {
    boolean show = getSceneView() == myDesignSurface.getSceneViewAt(x, y) && myShowOnHoverFilter.apply(getSceneView());
    if (isShowOnHover() != show) {
      setShowOnHover(show);
      myDesignSurface.repaint();
    }
  }

  public void setAlwaysShowSelection(boolean alwaysShowSelection) {
    myAlwaysShowSelection = alwaysShowSelection;
  }

  public void setTemporaryShow(boolean temporaryShow) {
    myTemporaryShow = temporaryShow;
    myDisplay.reLayout();
  }
}
