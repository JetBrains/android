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
import com.android.tools.idea.uibuilder.surface.ScreenView;

import java.util.WeakHashMap;

/**
 * This represents the Transform between dp to screen space.
 * There are two ways to create it get() or get(ScreenView)
 */
public class SceneTransform {
  private SceneTransform() {
  }

  public int getSwingX(int x) {
    return x;
  }

  public int getSwingY(int y) {
    return y;
  }

  public int getSwingDimension(int x) {
    return x;
  }

  private static SceneTransform lazySingleton;

  /**
   * Provide an Identity transform used in testing
   * @return
   */
  static SceneTransform get() {
    if (lazySingleton == null) {
      lazySingleton = new SceneTransform();
    }
    return lazySingleton;
  }

  /**
   * Get a SceneTransform for a ScreenView. They are cached and reused using a weekhashmap.
   *
   * @param screenView
   * @return
   */
  public static SceneTransform get(ScreenView screenView) {
    if (cache.containsKey(screenView)) {
      return cache.get(screenView);
    }
    ScreenViewTransform screenViewTransform = new ScreenViewTransform(screenView);
    cache.put(screenView, screenViewTransform);
    return screenViewTransform;
  }

  private static WeakHashMap<ScreenView, ScreenViewTransform> cache = new WeakHashMap<>();

  public DesignSurface getSurface() {
    return null;
  }

  /**
   * The  SceneTransform based on a ScreenView
   */
  private static class ScreenViewTransform extends SceneTransform {
    ScreenView myScreenView;

    public ScreenViewTransform(ScreenView screenView) {
      myScreenView = screenView;
    }

    @Override
    public DesignSurface getSurface() {
      return myScreenView.getSurface();
    }

    @Override
    public int getSwingX(int x) {
      return Coordinates.getSwingX(myScreenView, Coordinates.dpToPx(myScreenView, x));
    }

    @Override
    public int getSwingY(int y) {
      return Coordinates.getSwingY(myScreenView, Coordinates.dpToPx(myScreenView, y));
    }

    @Override

    public int getSwingDimension(int v) {
      return Coordinates.getSwingDimension(myScreenView, Coordinates.dpToPx(myScreenView, v));
    }
  }
}
