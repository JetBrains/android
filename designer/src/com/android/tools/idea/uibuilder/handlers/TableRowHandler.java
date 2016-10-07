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

import static com.android.SdkConstants.TABLE_ROW;

/**
 * Handler for the {@code <TableRow>} widget
 */
public class TableRowHandler extends LinearLayoutHandler {

  @Override
  protected boolean isVertical(@NotNull NlComponent component) {
    // Rows are always horizontal
    return false;
  }

  @Override
  public boolean acceptsParent(@NotNull NlComponent layout,
                               @NotNull NlComponent newChild) {
    // Only table rows are allowed as direct children of the table
    return TABLE_ROW.equals(newChild.getTagName());
  }

  @Override
  public void onChildInserted(@NotNull NlComponent parent, @NotNull NlComponent child, @NotNull InsertType insertType) {
    // Overridden to inhibit the setting of layout_width/layout_height since
    // the table row will enforce match_parent and wrap_content for width and height
    // respectively.
  }

  @Nullable
  @Override
  public ResizeHandler createResizeHandler(@NotNull ViewEditor editor,
                                           @NotNull NlComponent component,
                                           @Nullable SegmentType horizontalEdgeType,
                                           @Nullable SegmentType verticalEdgeType) {
    // No resizing in TableRows; the width is *always* match_parent and the height is
    // *always* wrap_content.
    return null;
  }
}
