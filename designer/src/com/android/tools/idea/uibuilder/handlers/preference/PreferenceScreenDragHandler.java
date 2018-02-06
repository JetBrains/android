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
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.android.SdkConstants.PreferenceTags.PREFERENCE_CATEGORY;

final class PreferenceScreenDragHandler extends PreferenceGroupDragHandler {
  private final SceneComponent myInitialGroup;
  @AndroidDpCoordinate
  private final Map<SceneComponent, Rectangle> myInitialPreferenceToBoundsMap;

  PreferenceScreenDragHandler(@NotNull ViewEditor editor,
                              @NotNull ViewGroupHandler handler,
                              @NotNull SceneComponent group,
                              @NotNull List<NlComponent> preferences,
                              @NotNull DragType type) {
    super(editor, handler, group, preferences, type);

    myInitialGroup = group;
    myInitialPreferenceToBoundsMap = getPreferenceToBoundsMap(group);
  }

  @Nullable
  @Override
  public String update(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, int modifiers) {
    String message = super.update(x, y, modifiers);

    if (message != null) {
      return message;
    }
    else if (myGroup.getChildren().isEmpty()) {
      return null;
    }

    // 1. Preference category bounds do not contain their children
    // 2. Preference category children are separated by the ListView divider height
    //
    // Those two things complicate the drag handler selection logic in DragDropInteraction. If the mouse pointer ends up between two
    // preference category children, a PreferenceScreenDragHandler is selected when a PreferenceCategoryDragHandler should really be doing
    // the painting and insertion index calculation.
    //
    // If a preference category child of the preference screen contains the pointer, the myGroup field is set to the category and this drag
    // handler acts like a PreferenceCategoryDragHandler. If a category no longer contains the pointer, the field is reset to the screen.

    Optional<SceneComponent> category = myInitialPreferenceToBoundsMap.keySet().stream()
      .filter(preference -> preference.getNlComponent().getTagName().equals(PREFERENCE_CATEGORY)
                            && myInitialPreferenceToBoundsMap.get(preference).contains(x, y))
      .findFirst();

    if (category.isPresent()) {
      if (myGroup != category.get()) {
        myGroup = category.get();
        myPreferenceToBoundsMap = getPreferenceToBoundsMap(myGroup);

        layout = myGroup;
      }
    }
    else if (myGroup != myInitialGroup) {
      myGroup = myInitialGroup;
      myPreferenceToBoundsMap = myInitialPreferenceToBoundsMap;

      layout = myInitialGroup;
    }

    updateActivePreference();
    return null;
  }

  @Override
  void drawDropPreviewLine(@NotNull NlGraphics graphics) {
    if (!myGroup.getChildren().isEmpty()) {
      super.drawDropPreviewLine(graphics);
    }
  }

  @Override
  void drawDropRecipientLines(@NotNull NlGraphics graphics) {
    List<SceneComponent> preferences = myGroup.getChildren();

    if (preferences.isEmpty()) {
      return;
    }

    graphics.useStyle(NlDrawingStyle.DROP_RECIPIENT);
    Rectangle bounds = getBounds(myGroup);

    if (lastY >= getMidpointY(preferences.get(0)) || myGroup.getNlComponent().getTagName().equals(PREFERENCE_CATEGORY)) {
      graphics.drawTopDp(bounds);
    }

    graphics.drawLeftDp(bounds);
    graphics.drawRightDp(bounds);

    if (lastY < getMidpointY(preferences.get(preferences.size() - 1))) {
      graphics.drawBottomDp(bounds);
    }
  }

  @Override
  void drawDropZoneLines(@NotNull NlGraphics graphics) {
    // I don't draw drop preview, recipient, and zone lines on top of each other because it looks bad. In a preference screen, the top of
    // the first child is never a drop zone line because it's always either a drop preview line or a recipient line. Hence 1 for the
    // starting index. In a preference category, the top of the first child may be a drop zone line because the top of the preference
    // category title is always a drop recipient line. Hence 0.
    drawDropZoneLines(graphics, myGroup.getNlComponent().getTagName().equals(PREFERENCE_CATEGORY) ? 0 : 1);
  }
}
