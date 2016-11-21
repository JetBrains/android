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

import com.android.tools.idea.uibuilder.handlers.scene.Display;
import com.android.tools.idea.uibuilder.handlers.scene.Scene;
import com.android.tools.idea.uibuilder.model.NlModel;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * Basic display layer for Scene
 */
public class SceneLayer extends Layer {
  private final ScreenView myScreenView;
  private Dimension myScreenViewSize = new Dimension();
  private Rectangle mySizeRectangle = new Rectangle();
  private Display myDisplay = new Display();
  private Scene myScene = null;

  /**
   * Default constructor
   *
   * @param view the current ScreenView
   */
  public SceneLayer(ScreenView view) {
    myScreenView = view;
  }

  /**
   * Paint function for the layer. Delegate the painting of the Scene to a Display instance.
   *
   * @param g the graphics context
   */
  @Override
  public void paint(@NotNull Graphics2D g) {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    myScreenView.getSize(myScreenViewSize);

    mySizeRectangle.setBounds(myScreenView.getX(), myScreenView.getY(), myScreenViewSize.width, myScreenViewSize.height);
    Rectangle2D.intersect(mySizeRectangle, g.getClipBounds(), mySizeRectangle);
    if (mySizeRectangle.isEmpty()) {
      return;
    }

    // Draw the components
    NlModel model = myScreenView.getModel();
    if (model.getComponents().size() == 0) {
      return;
    }
    if (myScene == null) {
      Scene scene = Scene.createScene(model);
      myScene = scene;
    }
    else {
      myScene.updateFrom(model);
    }
    myDisplay.draw(myScreenView, g, myScene);
  }
}
