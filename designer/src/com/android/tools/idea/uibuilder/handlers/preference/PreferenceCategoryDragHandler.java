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

import com.android.tools.idea.uibuilder.api.DragType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.model.NlComponent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

final class PreferenceCategoryDragHandler extends PreferenceGroupDragHandler {
  PreferenceCategoryDragHandler(@NotNull ViewEditor editor,
                                @NotNull ViewGroupHandler handler,
                                @NotNull NlComponent preferenceGroup,
                                @NotNull List<NlComponent> preferences,
                                @NotNull DragType type) {
    super(editor, handler, preferenceGroup, preferences, type);
  }

  @Override
  void drawDropPreviewLine(@NotNull NlGraphics graphics) {
    int count = myPreferenceGroup.getChildCount();
    graphics.useStyle(NlDrawingStyle.DROP_PREVIEW);

    if (count == 0) {
      drawBottom(graphics, myPreferenceGroup);
    }
    else if (myInsertIndex == -1) {
      drawBottom(graphics, getChild(count - 1));
    }
    else {
      drawTop(graphics, getChild(myInsertIndex));
    }
  }

  @Override
  void drawDropRecipientLines(@NotNull NlGraphics graphics) {
    int count = myPreferenceGroup.getChildCount();
    NlComponent preference = count == 0 ? myPreferenceGroup : getChild(count - 1);

    graphics.useStyle(NlDrawingStyle.DROP_RECIPIENT);

    drawTop(graphics, myPreferenceGroup);
    graphics.drawLine(myPreferenceGroup.x, myPreferenceGroup.y, myPreferenceGroup.x, preference.y + preference.h);

    graphics.drawLine(myPreferenceGroup.x + myPreferenceGroup.w, myPreferenceGroup.y,
                      myPreferenceGroup.x + myPreferenceGroup.w, preference.y + preference.h);

    if (!((count == 0 && myInsertIndex == 0) || myInsertIndex == -1)) {
      drawBottom(graphics, preference);
    }
  }

  @Override
  void drawDropZoneLines(@NotNull NlGraphics graphics) {
    int count = myPreferenceGroup.getChildCount();

    if (count == 0) {
      return;
    }

    graphics.useStyle(NlDrawingStyle.DROP_ZONE);

    for (int i = 0; i < count; i++) {
      if (i == myInsertIndex) {
        continue;
      }

      drawTop(graphics, getChild(i));
    }
  }
}
