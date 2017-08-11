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
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * A SceneView for <a href="https://developer.android.com/reference/android/support/design/widget/NavigationView.html">NavigationView</a>
 * menus.
 */
public final class NavigationViewSceneView extends ScreenView {
  public static final String SHOW_IN_ATTRIBUTE_VALUE = "navigation_view";

  public NavigationViewSceneView(@NotNull NlDesignSurface surface, @NotNull NlModel model) {
    super(surface, ScreenViewType.NORMAL, model);
  }

  /**
   * Returns the size of the NavigationView view object. The sizes of these SceneViews usually match the size of the device in the
   * configuration.
   */
  @NotNull
  @Override
  public Dimension getPreferredSize(@Nullable Dimension size) {
    if (size == null) {
      size = new Dimension();
    }

    RenderResult result = getResult();

    if (result == null) {
      return size;
    }

    List<ViewInfo> views = result.getRootViews();

    if (views.isEmpty()) {
      return size;
    }

    Object view = views.get(0).getViewObject();

    try {
      size.setSize(getWidth(view), getHeight(view));
      return size;
    }
    catch (ReflectiveOperationException exception) {
      throw new RuntimeException(exception);
    }
  }

  private static int getWidth(@NotNull Object view) throws ReflectiveOperationException {
    return (int)View.class.getDeclaredMethod("getWidth").invoke(view);
  }

  private static int getHeight(@NotNull Object view) throws ReflectiveOperationException {
    return (int)View.class.getDeclaredMethod("getHeight").invoke(view);
  }

  @NotNull
  @Override
  public Shape getScreenShape() {
    Dimension size = getSize();
    return new Rectangle(getX(), getY(), size.width, size.height);
  }
}
