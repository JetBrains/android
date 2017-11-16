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
package com.android.tools.idea.uibuilder.handlers;

import android.view.View;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.handlers.frame.FrameLayoutHandler;
import com.android.tools.idea.uibuilder.model.*;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import static com.android.SdkConstants.*;

final class NavigationViewHandler extends FrameLayoutHandler {
  @Override
  public void onActivateInDesignSurface(@NotNull ViewEditor editor,
                                        @NotNull NlComponent component,
                                        @AndroidCoordinate int x,
                                        @AndroidCoordinate int y) {
    ViewInfo viewInfo = NlComponentHelperKt.getViewInfo(component);
    if (viewInfo == null) {
      return;
    }

    View view = getHeaderView(viewInfo.getViewObject(), 0);
    String resource;

    if (view != null && contains(view, x, y)) {
      resource = component.getAttribute(AUTO_URI, ATTR_HEADER_LAYOUT);
    }
    else if (NlComponentHelperKt.contains(component, x, y)) {
      resource = component.getAttribute(AUTO_URI, ATTR_MENU);
    }
    else {
      return;
    }

    if (resource == null) {
      return;
    }

    NlModel model = component.getModel();
    editor.openResource(model.getConfiguration(), resource, model.getVirtualFile());
  }

  private static boolean contains(@NotNull View view, @AndroidCoordinate int x, @AndroidCoordinate int y) {
    int viewX = view.getLeft();
    int viewY = view.getTop();

    return Ranges.contains(viewX, viewX + view.getWidth(), x) && Ranges.contains(viewY, viewY + view.getHeight(), y);
  }

  @Nullable
  private static View getHeaderView(@NotNull Object navigationView, int index) {
    try {
      return (View)navigationView.getClass().getDeclaredMethod("getHeaderView", int.class).invoke(navigationView, index);
    }
    catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
      return null;
    }
  }

  @NotNull
  @Override
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_HEADER_LAYOUT,
      ATTR_MENU,
      ATTR_ITEM_BACKGROUND,
      ATTR_ITEM_ICON_TINT,
      ATTR_ITEM_TEXT_APPEARANCE,
      ATTR_ITEM_TEXT_COLOR,
      ATTR_FITS_SYSTEM_WINDOWS);
  }
}
