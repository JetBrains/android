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

import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.SceneView;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.sherpa.drawing.AndroidColorSet;
import com.android.tools.sherpa.drawing.BlueprintColorSet;
import com.android.tools.sherpa.drawing.ColorSet;
import com.intellij.reference.SoftReference;

import java.util.WeakHashMap;

/**
 * This represents the Transform between dp to screen space.
 * There are two ways to create it get() or get(ScreenView)
 */
public class SceneContext {
  ColorSet myColorSet;
  Long myTime;

  private SceneContext() {
    myTime = System.currentTimeMillis();
  }

  public long getTime() {
    return myTime;
  }

  public void setTime(long time) {
    myTime = time;
  }

  /**
   * Used to request Repaint
   */
  public void repaint() {

  }

  public int getSwingX(float x) {
    return (int) x;
  }

  public int getSwingY(float y) {
    return (int) y;
  }

  public int getSwingDimension(float x) {
    return (int) x;
  }

  public ColorSet getColorSet() {
    return myColorSet;
  }

  public double getScale() { return 1; }

  private static SceneContext lazySingleton;

  /**
   * Provide an Identity transform used in testing
   *
   * @return
   */
  public static SceneContext get() {
    if (lazySingleton == null) {
      lazySingleton = new SceneContext();
      lazySingleton.myColorSet = new BlueprintColorSet();
    }
    return lazySingleton;
  }

  /**
   * Get a SceneContext for a SceneView. They are cached and reused using a weakhashmap.
   *
   * @param sceneView
   * @return
   */
  public static SceneContext get(SceneView sceneView) {
    if (cache.containsKey(sceneView)) {
      SoftReference<SceneViewTransform> viewTransformRef =  cache.get(sceneView);
      SceneViewTransform viewTransform = viewTransformRef != null ? viewTransformRef.get() : null;

      if (viewTransform != null) {
        return viewTransform;
      }
    }
    SceneViewTransform sceneViewTransform = new SceneViewTransform(sceneView);
    // TODO(jbakermalone): don't require instanceof
    sceneViewTransform.myColorSet =
      (sceneView instanceof ScreenView && ((ScreenView)sceneView).getScreenViewType() == ScreenView.ScreenViewType.BLUEPRINT) ? new BlueprintColorSet() : new AndroidColorSet();

    cache.put(sceneView, new SoftReference<>(sceneViewTransform));
    return sceneViewTransform;
  }

  private static WeakHashMap<SceneView, SoftReference<SceneViewTransform>> cache = new WeakHashMap<>();

  public DesignSurface getSurface() {
    return null;
  }

  /**
   * The  SceneContext based on a ScreenView
   */
  private static class SceneViewTransform extends SceneContext {
    SceneView mySceneView;

    public SceneViewTransform(SceneView sceneView) {
      mySceneView = sceneView;
    }

    @Override
    public DesignSurface getSurface() {
      return mySceneView.getSurface();
    }

    @Override
    public double getScale() { return mySceneView.getScale(); }

    @Override
    public int getSwingX(float x) {
      return Coordinates.getSwingX(mySceneView, Coordinates.dpToPx(mySceneView, x));
    }

    @Override
    public int getSwingY(float y) {
      return Coordinates.getSwingY(mySceneView, Coordinates.dpToPx(mySceneView, y));
    }

    @Override
    public void repaint() {
      mySceneView.getSurface().repaint();
    }

    @Override
    public int getSwingDimension(float v) {
      return Coordinates.getSwingDimension(mySceneView, Coordinates.dpToPx(mySceneView, v));
    }
  }
}
