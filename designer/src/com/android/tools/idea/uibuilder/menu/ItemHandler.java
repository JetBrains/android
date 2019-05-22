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

import com.android.tools.idea.uibuilder.api.DragHandler;
import com.android.tools.idea.common.api.DragType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.SdkConstants.*;

public final class ItemHandler extends MenuHandler {
  static boolean handles(@NotNull NlComponent item) {
    return item.getTagName().equals("item");
  }

  @Nullable
  @Override
  public DragHandler createDragHandler(@NotNull ViewEditor editor,
                                       @NotNull SceneComponent group,
                                       @NotNull List<NlComponent> items,
                                       @NotNull DragType type) {
    return null;
  }

  @Override
  public boolean acceptsChild(@NotNull NlComponent parent, @NotNull NlComponent newChild) {
    return newChild.getTagName().equals(TAG_MENU);
  }

  @NotNull
  @Override
  public String getTitle(@NotNull NlComponent item) {
    return Strings.nullToEmpty(item.getAndroidAttribute(ATTR_TITLE));
  }

  @NotNull
  @Override
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_ID,
      ATTR_TITLE,
      ATTR_ICON,
      ATTR_SHOW_AS_ACTION,
      ATTR_VISIBLE,
      ATTR_ENABLED,
      ATTR_CHECKABLE);
  }
}
