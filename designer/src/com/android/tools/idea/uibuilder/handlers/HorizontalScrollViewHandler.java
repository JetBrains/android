/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.view.ViewGroup;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ScrollHandler;
import com.android.tools.idea.uibuilder.api.ScrollViewScrollHandler;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.actions.ViewAction;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Handler for the {@code <HorizontalScrollView>} widget
 */
public class HorizontalScrollViewHandler extends ScrollViewHandler {
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(ATTR_FILL_VIEWPORT);
  }

  @Override
  public void onChildInserted(@NotNull ViewEditor editor,
                              @NotNull NlComponent parent,
                              @NotNull NlComponent child,
                              @NotNull InsertType insertType) {
    child.setAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT);
    child.setAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, VALUE_MATCH_PARENT);
  }

  @Override
  public boolean onCreate(@NotNull ViewEditor editor,
                          @Nullable NlComponent parent,
                          @NotNull NlComponent node,
                          @NotNull InsertType insertType) {
    if (insertType.isCreate()) {
      // Insert a default linear layout (which will in turn be registered as
      // a child of this node and the create child method above will set its
      // fill parent attributes, its id, etc.
      NlComponent linear = NlComponentHelperKt.createChild(node, editor, FQCN_LINEAR_LAYOUT, null, InsertType.VIEW_HANDLER);
      linear.setAttribute(ANDROID_URI, ATTR_ORIENTATION, VALUE_VERTICAL);
    }

    return true;
  }

  @Nullable
  @Override
  public ScrollHandler createScrollHandler(@NotNull ViewEditor editor, @NotNull NlComponent component) {
    ViewGroup viewGroup = getViewGroupFromComponent(component);
    if (viewGroup == null) {
      return null;
    }

    int maxScrollableWidth = getMaxScrollable(viewGroup, ViewGroup::getWidth, View::getMeasuredWidth);

    if (maxScrollableWidth > 0) {
      // There is something to scroll
      return ScrollViewScrollHandler.createHandler(viewGroup, maxScrollableWidth, 10, ScrollViewScrollHandler.Orientation.HORIZONTAL);
    }

    return null;
  }

  @Override
  public void addToolbarActions(@NotNull List<ViewAction> actions) {
    actions.add(new ScrollViewHandler.ToggleRenderModeAction());
  }
}
