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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ResizeHandler;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.SegmentType;

import static com.android.SdkConstants.*;

/**
 * Handler for the {@code <TableLayout>} widget
 */
public class TableLayoutHandler extends LinearLayoutHandler {
  @Override
  protected boolean isVertical(@NotNull NlComponent component) {
    // Tables are always vertical
    return true;
  }

  @Override
  public boolean acceptsChild(@NotNull NlComponent layout, @NotNull NlComponent newChild) {
    // Only table rows are allowed as direct children of the table
    return TABLE_ROW.equals(newChild.getTagName());
  }

  @Override
  public void onChildInserted(@NotNull NlComponent parent, @NotNull NlComponent child, @NotNull InsertType insertType) {
    // Overridden to inhibit the setting of layout_width/layout_height since
    // it should always be match_parent
    child.setAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT);
    child.setAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, VALUE_MATCH_PARENT);
  }

  @Override
  public boolean onCreate(@NotNull ViewEditor editor,
                          @Nullable NlComponent parent,
                          @NotNull NlComponent node,
                          @NotNull InsertType insertType) {
    if (insertType.isCreate()) {
      // Start the table with 4 rows
      for (int i = 0; i < 4; i++) {
        node.createChild(editor, FQCN_TABLE_ROW, null, InsertType.VIEW_HANDLER);
      }
    }

    return true;
  }

  @Nullable
  @Override
  public ResizeHandler createResizeHandler(@NotNull ViewEditor editor,
                                           @NotNull NlComponent component,
                                           @Nullable SegmentType horizontalEdgeType,
                                           @Nullable SegmentType verticalEdgeType) {
    // Children of a table layout cannot set their widths (it is controlled by column
    // settings on the table). They can set their heights (though for TableRow, the
    // height is always wrap_content).
    if (horizontalEdgeType == null) { // Widths are edited by vertical edges.
      // The user is not editing a vertical height so don't allow resizing at all
      return null;
    }
    if (TABLE_ROW.equals(component.getTagName())) {
      // TableRows are always WRAP_CONTENT
      return null;
    }
    return super.createResizeHandler(editor, component, horizontalEdgeType, verticalEdgeType);
  }
}
