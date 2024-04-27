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

import com.android.tools.idea.common.api.DragType;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.sdklib.AndroidDpCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

final class PreferenceCategoryDragHandler extends PreferenceGroupDragHandler {
  PreferenceCategoryDragHandler(@NotNull ViewEditor editor,
                                @NotNull ViewGroupHandler handler,
                                @NotNull SceneComponent category,
                                @NotNull List<NlComponent> preferences,
                                @NotNull DragType type) {
    super(editor, handler, category, preferences, type);
  }

  @Nullable
  @Override
  public String update(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, int modifiers, @NotNull SceneContext sceneContext) {
    String message = super.update(x, y, modifiers, sceneContext);

    if (message != null) {
      return message;
    }
    else if (myGroup.getChildren().isEmpty()) {
      return null;
    }
    else {
      updateActivePreference();
      return null;
    }
  }

  @Override
  void drawDropPreviewLine(@NotNull NlGraphics graphics) {
    if (myGroup.getChildren().isEmpty()) {
      graphics.useStyle(NlDrawingStyle.DROP_PREVIEW);
      graphics.drawBottomDp(getBounds(myGroup));
    }
    else {
      super.drawDropPreviewLine(graphics);
    }
  }

  @Override
  void drawDropRecipientLines(@NotNull NlGraphics graphics) {
    graphics.useStyle(NlDrawingStyle.DROP_RECIPIENT);
    Rectangle bounds = getBounds(myGroup);

    graphics.drawTopDp(bounds);
    graphics.drawLeftDp(bounds);
    graphics.drawRightDp(bounds);

    List<SceneComponent> preferences = myGroup.getChildren();

    if (!preferences.isEmpty() && lastY < getMidpointY(preferences.get(preferences.size() - 1))) {
      graphics.drawBottomDp(bounds);
    }
  }

  @Override
  void drawDropZoneLines(@NotNull NlGraphics graphics) {
    drawDropZoneLines(graphics, 0);
  }
}
