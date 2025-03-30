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

import static com.android.SdkConstants.ATTR_CHECKABLE_BEHAVIOR;
import static com.android.SdkConstants.ATTR_ENABLED;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_VISIBLE;

import com.android.sdklib.AndroidCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public final class GroupHandler extends MenuHandler {
  @Override
  public boolean acceptsChild(@NotNull SceneComponent parent,
                              @NotNull NlComponent newChild,
                              @AndroidCoordinate int x,
                              @AndroidCoordinate int y) {
    return new ActionBar(parent).contains(Coordinates.pxToDp(newChild.getModel(), x),
                                          Coordinates.pxToDp(newChild.getModel(), y));
  }

  @NotNull
  @Override
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_ID,
      ATTR_CHECKABLE_BEHAVIOR,
      ATTR_VISIBLE,
      ATTR_ENABLED);
  }
}
