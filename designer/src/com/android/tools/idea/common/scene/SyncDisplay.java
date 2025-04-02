/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.common.scene;

import com.android.annotations.concurrency.UiThread;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import java.awt.Graphics2D;
import org.jetbrains.annotations.NotNull;

/**
 * Display a layout Scene
 */
@Deprecated // Use AsyncDisplay instead
class SyncDisplay implements Display {
  private final DisplayList myDisplayList = new DisplayList();
  private long myDisplayListVersion = 0;
  double myScale = 0;

  @Override
  public void relayout() {
    myDisplayListVersion = 0;
  }

  @UiThread
  public void draw(@NotNull SceneView sceneView, @NotNull Graphics2D g) {
    Scene scene = sceneView.getScene();
    SceneContext sceneContext = sceneView.getContext();
    long mTime = System.currentTimeMillis();
    boolean needsRebuild = scene.getDisplayListVersion() > myDisplayListVersion;
    if (sceneContext.getScale() != myScale) {
      myScale = sceneContext.getScale();
      needsRebuild = true;
    }
    needsRebuild |= myDisplayList.getCommands().isEmpty();
    if (needsRebuild) {
      needsRebuild = scene.layout(mTime, sceneContext);
      myDisplayList.clear();
      scene.buildDisplayList(myDisplayList, mTime, sceneContext);
      myDisplayListVersion = scene.getDisplayListVersion();
      if (needsRebuild) {
        scene.needsRebuildList();
      }
    }
    draw(sceneContext, g, myDisplayList);

    if (needsRebuild) {
      DesignSurface<?> designSurface = sceneContext.getSurface();
      if (designSurface != null) {
        designSurface.repaint();
      }
    }
  }

  public void draw(@NotNull SceneContext sceneContext, @NotNull Graphics2D g, @NotNull DisplayList list) {
    sceneContext.setTime(System.currentTimeMillis());
    list.paint(g, sceneContext);
  }

  @Override
  public boolean hasPendingPaints() {
    return false;
  }
}
