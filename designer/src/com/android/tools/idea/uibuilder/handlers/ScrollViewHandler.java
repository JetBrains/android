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

import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.api.actions.ToggleViewAction;
import com.android.tools.idea.uibuilder.api.actions.ViewAction;
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.google.common.collect.ImmutableList;
import icons.AndroidDesignerIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;

/** Handler for the {@code <ScrollView>} widget */
public class ScrollViewHandler extends ViewGroupHandler {
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_SCROLLBAR_STYLE,
      ATTR_STYLE,
      ATTR_FILL_VIEWPORT,
      ATTR_CLIP_TO_PADDING);
  }

  @Override
  public void onChildInserted(@NotNull NlComponent parent, @NotNull NlComponent child,
                              @NotNull InsertType insertType) {
    child.setAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT);
    child.setAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
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

  @Nullable
  @Override
  public ScrollHandler createScrollHandler(@NotNull ViewEditor editor, @NotNull NlComponent component) {
    int maxScrollableHeight = 0;
    Map<NlComponent, Dimension> childrenMeasure = editor.measureChildren(component, null);
    if (childrenMeasure != null && !childrenMeasure.isEmpty()) {
      for (Dimension dimension : childrenMeasure.values()) {
        maxScrollableHeight += dimension.height;
      }

      // Substract the viewport form the scrollable size
      maxScrollableHeight -=  component.h;

      if (maxScrollableHeight > 0) {
        // There is something to scroll
        return new ScrollHandler(component, maxScrollableHeight, 10);
      }
    }

    return null;
  }

  static class OneChildDragHandler extends DragHandler {
    public OneChildDragHandler(@NotNull ViewEditor editor,
                               @NotNull ViewGroupHandler handler,
                               @NotNull NlComponent layout,
                               @NotNull List<NlComponent> components,
                               @NotNull DragType type) {
      super(editor, handler, layout, components, type);
    }

    @Nullable
    @Override
    public String update(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers) {
      super.update(x, y, modifiers);

      if (layout.getChildCount() > 0 || components.size() > 1) {
        return "Layout only allows 1 child";
      }

      return null;
    }

    @Override
    public void commit(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers) {
    }

    @Override
    public void paint(@NotNull NlGraphics graphics) {
      if (layout.getChildCount() == 0) {
        graphics.useStyle(NlDrawingStyle.DROP_RECIPIENT);
        graphics.drawRect(layout.x, layout.y, layout.w, layout.h);
      }
    }
  }

  @Override
  public void addToolbarActions(@NotNull List<ViewAction> actions) {
    actions.add(new ToggleRenderModeAction());
  }

  static class ToggleRenderModeAction extends ToggleViewAction {
    public ToggleRenderModeAction() {
      super(AndroidDesignerIcons.ViewportRender, AndroidDesignerIcons.NormalRender, "Toggle Viewport Render Mode", null);
    }

    @Override
    public boolean isSelected(@NotNull ViewEditor editor,
                              @NotNull ViewHandler handler,
                              @NotNull NlComponent parent,
                              @NotNull List<NlComponent> selectedChildren) {
      return NlModel.isRenderViewPort();
    }

    @Override
    public void setSelected(@NotNull ViewEditor editor,
                            @NotNull ViewHandler handler,
                            @NotNull NlComponent parent,
                            @NotNull List<NlComponent> selectedChildren,
                            boolean selected) {
      NlModel.setRenderViewPort(selected);
      parent.getModel().requestRender();
    }
  }
}
