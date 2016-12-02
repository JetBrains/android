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
package com.android.tools.idea.uibuilder.scene;

import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Display a layout Scene
 */
public class Display {
  private long mTime;
  private DisplayList myDisplayList = new DisplayList();

  public void draw(@NotNull SceneTransform sceneTransform, @NotNull Graphics2D g, @NotNull Scene scene) {
    mTime = System.currentTimeMillis();

    myDisplayList.clear();
    boolean needsRepaint = scene.paint(myDisplayList, mTime, sceneTransform);
    if (ConstraintLayoutHandler.USE_SCENE_INTERACTION) {
      draw(sceneTransform, g, myDisplayList);
    }
    if (needsRepaint) {
      DesignSurface designSurface = sceneTransform.getSurface();
      if (designSurface != null) {
        designSurface.repaint();
      }
    }
  }

  public void draw(@NotNull SceneTransform sceneTransform, @NotNull Graphics2D g, @NotNull DisplayList list) {
    list.paint(g, sceneTransform);
  }

}
