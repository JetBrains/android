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
package com.android.tools.idea.uibuilder.handlers.preference;

import android.widget.ListView;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class PreferenceScreenHandler extends ViewGroupHandler {
  @NotNull
  @Override
  public DragHandler createDragHandler(@NotNull ViewEditor editor,
                                       @NotNull SceneComponent preferenceScreen,
                                       @NotNull List<NlComponent> preferences,
                                       @NotNull DragType type) {
    return new PreferenceScreenDragHandler(editor, this, preferenceScreen, preferences, type);
  }

  @Nullable
  @Override
  public ScrollHandler createScrollHandler(@NotNull ViewEditor editor, @NotNull NlComponent preference) {
    if (preference.getParent() != null) {
      // preference is not the root PreferenceScreen
      return null;
    }

    ViewInfo listView = ViewInfoUtils.findListView(editor.getRootViews());

    if (listView == null) {
      return null;
    }

    return new ListViewScrollHandler((ListView)listView.getViewObject());
  }
}
