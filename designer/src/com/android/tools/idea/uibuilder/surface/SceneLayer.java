/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.idea.uibuilder.model.SwingCoordinate;
import com.android.tools.idea.uibuilder.scene.Display;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Rectangle2D;

import static com.android.tools.idea.uibuilder.graphics.NlConstants.BLUEPRINT_BG_COLOR;

/**
 * Basic display layer for Scene
 */
public class SceneLayer extends Layer {
  private final DesignSurface myDesignSurface;
  private final SceneView mySceneView;
  private final Dimension myScreenViewSize = new Dimension();
  private final Rectangle mySizeRectangle = new Rectangle();
  private final Display myDisplay = new Display();
  private boolean myShowOnHover = false;
  private boolean myShowAlways = true;
  private boolean myAlwaysShowSelection;

  /**
   * Default constructor
   *
   * @param view the current ScreenView
   */
  public SceneLayer(@NotNull DesignSurface surface, @NotNull SceneView view, boolean showAlways) {
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
    if (!myShowOnHover && !myShowAlways && !myAlwaysShowSelection) {
      return;
    }
    sceneContext.setShowOnlySelection(!myShowOnHover && myAlwaysShowSelection);
    Graphics2D g = (Graphics2D)g2.create();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      mySceneView.getSize(myScreenViewSize);

      mySizeRectangle.setBounds(mySceneView.getX(), mySceneView.getY(), myScreenViewSize.width, myScreenViewSize.height);
      Rectangle2D.intersect(mySizeRectangle, g.getClipBounds(), mySizeRectangle);
      if (mySizeRectangle.isEmpty()) {
        return;
      }

      if (myShowAlways) {
        g.setColor(BLUEPRINT_BG_COLOR);
        g.fillRect(mySizeRectangle.x, mySizeRectangle.y, mySizeRectangle.width, mySizeRectangle.height);
      }

      // Draw the components
      myDisplay.draw(sceneContext, g, mySceneView.getScene());
    }
    finally {
      g.dispose();
    }
  }

  public boolean isShowOnHover() {
    return myShowOnHover;
  }

  public void setShowOnHover(boolean value) {
    myShowOnHover = value;
  }

  public SceneView getSceneView() {
    return mySceneView;
  }

  @Override
  public void hover(@SwingCoordinate int x, @SwingCoordinate int y) {
    boolean show = false;
    if (getSceneView() == myDesignSurface.getHoverSceneView(x, y)) {
      show = true;
    }
    if (isShowOnHover() != show) {
      setShowOnHover(show);
      myDesignSurface.repaint();
    }
  }

  public void setAlwaysShowSelection(boolean alwaysShowSelection) {
    myAlwaysShowSelection = alwaysShowSelection;
  }
}
