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
import android.view.ViewGroup;
import android.view.ViewParent;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.command.NlWriteCommandActionUtil;
import com.android.tools.idea.uibuilder.api.ScrollHandler;
import com.android.tools.idea.uibuilder.api.ScrollViewScrollHandler;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.common.model.NlComponent;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

import static com.android.SdkConstants.*;

public class NestedScrollViewHandler extends ScrollViewHandler {
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_CONTEXT,
      ATTR_SHOW_IN,
      ATTR_FILL_VIEWPORT,
      ATTR_CLIP_TO_PADDING);
  }

  @Override
  public boolean onCreate(@Nullable NlComponent parent,
                          @NotNull NlComponent newChild,
                          @NotNull InsertType type) {
    if (!super.onCreate(parent, newChild, type)) {
      return false;
    }

    if (type == InsertType.CREATE) {
      NlWriteCommandActionUtil.run(newChild, "Setting fill_viewport", () -> {
        newChild.setAndroidAttribute(ATTR_FILL_VIEWPORT, "true");
      });
    }
    return true;
  }

  @Nullable
  @Override
  public ScrollHandler createScrollHandler(@NotNull ViewEditor editor, @NotNull NlComponent component) {
    ViewGroup viewGroup =  ScrollViewHandler.getViewGroupFromComponent(component);
    if (viewGroup == null) {
      return null;
    }

    // NestedScrollView is a NestedScrollingChild so if the parent is an instance of NestedScrollingParent,
    // then delegate the scrolling to it.
    ViewParent parent = viewGroup.getParent();
    if (parent instanceof ViewGroup) {
      boolean nestedScrollingParent = Arrays.stream(parent.getClass().getInterfaces())
        .map(Class::getName)
        .anyMatch("android.support.v4.view.NestedScrollingParent"::equals);
      if (nestedScrollingParent) {
        viewGroup = (ViewGroup)parent;
      }
    }

    int maxScrollableHeight = ScrollViewScrollHandler.getMaxScrollable(viewGroup, ViewGroup::getHeight, View::getMeasuredHeight);

    if (maxScrollableHeight > 0) {
      // There is something to scroll
      return ScrollViewScrollHandler
        .createHandler(viewGroup, component, maxScrollableHeight, 10,
                       ScrollViewScrollHandler.Orientation.VERTICAL);
    }

    return null;
  }
}
