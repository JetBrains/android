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

import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.TAG_MENU;

import com.android.tools.idea.common.api.DragType;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.command.NlWriteCommandActionUtil;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.uibuilder.api.DragHandler;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.actions.ViewAction;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  public boolean acceptsChild(@NotNull NlComponent layout,
                              @NotNull NlComponent newChild) {
    return !TAG_MENU.equals(newChild.getTagName());
  }

  @Override
  public void onChildInserted(@NotNull NlComponent parent,
                              @NotNull NlComponent newChild,
                              @NotNull InsertType type) {
    if (SearchItemHandler.handles(newChild)) {
      SearchItemHandler.onChildInserted(newChild);
    }
  }

  @Override
  public boolean onCreate(@Nullable NlComponent parent,
                          @NotNull NlComponent newChild,
                          @NotNull InsertType type) {
    if (type == InsertType.CREATE) {
      NlWriteCommandActionUtil.run(newChild, "Create Menu", () -> {
        newChild.removeAndroidAttribute(ATTR_LAYOUT_WIDTH);
        newChild.removeAndroidAttribute(ATTR_LAYOUT_HEIGHT);
      });
    }
    return true;
  }

  @Override
  public void addToolbarActions(@NotNull List<ViewAction> actions) {
    // The default behaviour of a ViewHandler is to add the "Expand horizontally" and "Expand vertically" actions.
    // This does not make sense for state lists, so instead no action is added to the toolbar
  }
}
