/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.model;

import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class NlSelectionModel extends SelectionModel {
  private Map<NlComponent, SelectionHandles> myHandles;

  @Override
  public void setSelection(@NotNull List<NlComponent> components, @Nullable NlComponent primary) {
    super.setSelection(components, primary);
    myHandles = null;
  }

  @Override
  public void clear() {
    super.clear();
    myHandles = null;
  }

  @Nullable
  public SelectionHandle findHandle(@AndroidDpCoordinate int x,
                                    @AndroidDpCoordinate int y,
                                    @AndroidDpCoordinate int maxDistance,
                                    @NotNull DesignSurface surface) {
    if (myHandles == null) {
      return null;
    }

    for (SelectionHandles handles : myHandles.values()) {
      SelectionHandle handle = handles.findHandle(x, y, maxDistance, surface);
      if (handle != null) {
        return handle;
      }
    }

    return null;
  }

  @NotNull
  public SelectionHandles getHandles(@NotNull NlComponent component) {
    if (myHandles == null) {
      myHandles = Maps.newHashMap();
    }
    SelectionHandles handles = myHandles.get(component);
    if (handles == null) {
      handles = new SelectionHandles(component);
      myHandles.put(component, handles);
    }
    return handles;
  }


}
