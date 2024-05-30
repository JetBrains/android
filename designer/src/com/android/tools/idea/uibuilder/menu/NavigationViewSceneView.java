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
package com.android.tools.idea.uibuilder.menu;

import android.view.View;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.idea.uibuilder.surface.sizepolicy.ContentSizePolicy;
import com.android.tools.rendering.RenderResult;
import java.awt.Dimension;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Utilities for building a SceneView for <a href="https://developer.android.com/reference/android/support/design/widget/NavigationView.html">NavigationView</a>
 * menus.
 */
public final class NavigationViewSceneView {
  public static final String SHOW_IN_ATTRIBUTE_VALUE = "navigation_view";

  /**
   * Returns the size of the NavigationView view object. The sizes of these SceneViews usually match the size of the device in the
   * configuration.
   */
  public static final ContentSizePolicy CONTENT_SIZE_POLICY = new ContentSizePolicy() {
    @Override
    public void measure(@NotNull ScreenView screenView, @NotNull Dimension outDimension) {
      RenderResult result = screenView.getResult();

      if (result == null) {
        return;
      }

      List<ViewInfo> views = result.getRootViews();

      if (views.isEmpty()) {
        return;
      }

      Object view = views.get(0).getViewObject();

      try {
        outDimension.setSize(getWidth(view), getHeight(view));
      }
      catch (ReflectiveOperationException exception) {
        throw new RuntimeException(exception);
      }
    }
  };

  private static int getWidth(@NotNull Object view) throws ReflectiveOperationException {
    return (int)View.class.getDeclaredMethod("getWidth").invoke(view);
  }

  private static int getHeight(@NotNull Object view) throws ReflectiveOperationException {
    return (int)View.class.getDeclaredMethod("getHeight").invoke(view);
  }

  private NavigationViewSceneView() {}
}
