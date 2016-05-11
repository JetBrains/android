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
package com.android.tools.idea.uibuilder.handlers.menu;

import com.android.tools.idea.uibuilder.api.DragType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.model.NlComponent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

final class GroupDragHandler extends GroupDragHandlerBase {
  GroupDragHandler(@NotNull ViewEditor editor,
                   @NotNull ViewGroupHandler handler,
                   @NotNull NlComponent group,
                   @NotNull List<NlComponent> items,
                   @NotNull DragType type) {
    super(editor, handler, group, items, type);
    myItems = group.getChildren();
  }
}
