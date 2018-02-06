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
package com.android.tools.idea.uibuilder.menu;

import com.android.tools.idea.common.api.DragType;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.uibuilder.api.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;

public class MenuHandler extends ViewGroupHandler {
  @Nullable
  @Override
  public DragHandler createDragHandler(@NotNull ViewEditor editor,
                                       @NotNull SceneComponent group,
                                       @NotNull List<NlComponent> items,
                                       @NotNull DragType type) {
    return new GroupDragHandler(editor, this, group, items, type);
  }

  @Override
  public void onChildInserted(@NotNull ViewEditor editor,
                              @NotNull NlComponent parent,
                              @NotNull NlComponent newChild,
                              @NotNull InsertType type) {
    if (SearchItemHandler.handles(newChild)) {
      SearchItemHandler.onChildInserted(editor);
    }
  }

  @Override
  public boolean onCreate(@NotNull ViewEditor editor,
                          @Nullable NlComponent parent,
                          @NotNull NlComponent newChild,
                          @NotNull InsertType type) {
    newChild.removeAndroidAttribute(ATTR_LAYOUT_WIDTH);
    newChild.removeAndroidAttribute(ATTR_LAYOUT_HEIGHT);

    return true;
  }
}
