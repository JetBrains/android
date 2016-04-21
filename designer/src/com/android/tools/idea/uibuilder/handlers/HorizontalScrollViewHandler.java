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

import com.android.tools.idea.uibuilder.api.actions.ViewAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.handlers.ScrollViewHandler.OneChildDragHandler;
import com.android.tools.idea.uibuilder.model.NlComponent;

import java.util.List;

import static com.android.SdkConstants.*;

/** Handler for the {@code <HorizontalScrollView>} widget */
public class HorizontalScrollViewHandler extends ViewGroupHandler {
  @Override
  public void onChildInserted(@NotNull NlComponent parent, @NotNull NlComponent child,
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
      NlComponent linear = node.createChild(editor, FQCN_LINEAR_LAYOUT, null, InsertType.VIEW_HANDLER);
      linear.setAttribute(ANDROID_URI, ATTR_ORIENTATION, VALUE_VERTICAL);
    }

    return true;
  }

  @Nullable
  @Override
  public DragHandler createDragHandler(@NotNull ViewEditor editor,
                                       @NotNull NlComponent layout,
                                       @NotNull List<NlComponent> components,
                                       @NotNull DragType type) {
    return new OneChildDragHandler(editor, this, layout, components, type);
  }

  @Override
  public void addViewActions(@NotNull List<ViewAction> actions) {
    actions.add(new ScrollViewHandler.ToggleRenderModeAction());
  }
}
