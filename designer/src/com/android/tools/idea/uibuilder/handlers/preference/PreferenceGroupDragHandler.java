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

import com.android.SdkConstants.PreferenceTags;
import com.android.tools.idea.uibuilder.api.DragHandler;
import com.android.tools.idea.uibuilder.api.DragType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.handlers.Utils;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.NlComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

abstract class PreferenceGroupDragHandler extends DragHandler {
  final NlComponent myPreferenceGroup;
  int myInsertIndex;

  PreferenceGroupDragHandler(@NotNull ViewEditor editor,
                             @NotNull ViewGroupHandler handler,
                             @NotNull NlComponent preferenceGroup,
                             @NotNull List<NlComponent> preferences,
                             @NotNull DragType type) {
    super(editor, handler, preferenceGroup, preferences, type);
    myPreferenceGroup = preferenceGroup;
  }

  @Override
  public final void commit(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers) {
  }

  @Nullable
  @Override
  public final String update(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers) {
    String message = super.update(x, y, modifiers);

    if (message != null) {
      return message;
    }

    int count = myPreferenceGroup.getChildCount();

    if (count == 0) {
      myInsertIndex = 0;
      return null;
    }

    NlComponent lastPreference = getChild(count - 1);
    int lastPreferenceMidpoint = getMidpoint(lastPreference);

    if (Utils.contains(lastPreference.y, lastPreferenceMidpoint, y)) {
      myInsertIndex = count - 1;
      return null;
    }
    else if (y >= lastPreferenceMidpoint) {
      myInsertIndex = -1;
      return null;
    }

    for (int i = 0; i < count - 1; i++) {
      NlComponent preference = getChild(i);
      NlComponent nextPreference = getChild(i + 1);
      int midpoint = getMidpoint(preference, nextPreference);

      if (Utils.contains(preference.y, midpoint, y)) {
        myInsertIndex = i;
        break;
      }
      else if (Utils.contains(midpoint, nextPreference.y, y)) {
        myInsertIndex = i + 1;
        break;
      }
    }

    return null;
  }

  @NotNull
  final NlComponent getChild(int i) {
    NlComponent child = myPreferenceGroup.getChild(i);
    assert child != null;

    return child;
  }

  private static int getMidpoint(@NotNull NlComponent preference) {
    return preference.y + getHeight(preference) / 2;
  }

  private static int getMidpoint(@NotNull NlComponent preference1, @NotNull NlComponent preference2) {
    return (preference1.y + preference2.y) / 2;
  }

  @Override
  public final void paint(@NotNull NlGraphics graphics) {
    drawDropPreviewLine(graphics);
    drawDropRecipientLines(graphics);
    drawDropZoneLines(graphics);
  }

  abstract void drawDropPreviewLine(@NotNull NlGraphics graphics);

  abstract void drawDropRecipientLines(@NotNull NlGraphics graphics);

  abstract void drawDropZoneLines(@NotNull NlGraphics graphics);

  static void drawBottom(@NotNull NlGraphics graphics, @NotNull NlComponent preference) {
    int height = getHeight(preference);
    graphics.drawLine(preference.x, preference.y + height, preference.x + preference.w, preference.y + height);
  }

  static int getHeight(@NotNull NlComponent preference) {
    int height = preference.h;

    if (preference.getTagName().equals(PreferenceTags.PREFERENCE_CATEGORY)) {
      for (NlComponent child : preference.getChildren()) {
        height += child.h;
      }
    }

    return height;
  }

  @Override
  public final int getInsertIndex() {
    return myInsertIndex;
  }
}
