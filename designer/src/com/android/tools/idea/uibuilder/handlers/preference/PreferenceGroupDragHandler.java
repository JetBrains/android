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
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.NlComponent;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.android.SdkConstants.PreferenceTags.PREFERENCE_CATEGORY;

abstract class PreferenceGroupDragHandler extends DragHandler {
  NlComponent myGroup;
  Map<NlComponent, Rectangle> myPreferenceToBoundsMap;
  NlComponent myActivePreference;

  private int myDividerHeight;

  PreferenceGroupDragHandler(@NotNull ViewEditor editor,
                             @NotNull ViewGroupHandler handler,
                             @NotNull NlComponent group,
                             @NotNull List<NlComponent> preferences,
                             @NotNull DragType type) {
    super(editor, handler, group, preferences, type);
    myGroup = group;

    initDividerHeight();
    myPreferenceToBoundsMap = getPreferenceToBoundsMap(group);
  }

  private void initDividerHeight() {
    ViewInfo view = ViewInfoUtils.findListView(editor.getRootViews());
    assert view != null;

    myDividerHeight = ((ListView)view.getViewObject()).getDividerHeight();
  }

  @NotNull
  final Map<NlComponent, Rectangle> getPreferenceToBoundsMap(@NotNull NlComponent group) {
    return group.getChildren().stream().collect(Collectors.toMap(Function.identity(), this::getBounds));
  }

  @NotNull
  final Rectangle getBounds(@NotNull NlComponent preference) {
    int height = preference.h + myDividerHeight;

    if (preference.getTagName().equals(PREFERENCE_CATEGORY)) {
      height += preference.getChildren().stream()
        .mapToInt(child -> child.h + myDividerHeight)
        .sum();
    }

    return new Rectangle(preference.x, preference.y, preference.w, height);
  }

  final void updateActivePreference() {
    List<NlComponent> preferences = myGroup.getChildren();

    if (lastY < preferences.get(0).y) {
      myActivePreference = preferences.get(0);
    }
    else {
      Optional<NlComponent> activePreference = myPreferenceToBoundsMap.keySet().stream()
        .filter(preference -> myPreferenceToBoundsMap.get(preference).contains(lastX, lastY))
        .findFirst();

      myActivePreference = activePreference.orElse(preferences.get(preferences.size() - 1));
    }
  }

  @Override
  public final void paint(@NotNull NlGraphics graphics) {
    drawDropPreviewLine(graphics);
    drawDropRecipientLines(graphics);
    drawDropZoneLines(graphics);
  }

  void drawDropPreviewLine(@NotNull NlGraphics graphics) {
    graphics.useStyle(NlDrawingStyle.DROP_PREVIEW);
    Rectangle bounds = myPreferenceToBoundsMap.get(myActivePreference);

    if (lastY < getMidpointY(bounds)) {
      graphics.drawTop(bounds);
    }
    else {
      graphics.drawBottom(bounds);
    }
  }

  abstract void drawDropRecipientLines(@NotNull NlGraphics graphics);

  abstract void drawDropZoneLines(@NotNull NlGraphics graphics);

  final void drawDropZoneLines(@NotNull NlGraphics graphics, int startingIndex) {
    List<NlComponent> preferences = myGroup.getChildren();

    if (preferences.isEmpty()) {
      return;
    }

    graphics.useStyle(NlDrawingStyle.DROP_ZONE);
    int midpointY = getMidpointY(myActivePreference);

    for (int i = startingIndex, size = preferences.size(); i < size; i++) {
      NlComponent preference = preferences.get(i);

      if (i != 0 && myActivePreference == preferences.get(i - 1)) {
        if (lastY < midpointY) {
          graphics.drawTop(preference);
        }
      }
      else if (myActivePreference == preference) {
        if (lastY >= midpointY) {
          graphics.drawTop(preference);
        }
      }
      else {
        graphics.drawTop(preference);
      }
    }
  }

  @Override
  public final void commit(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers, @NotNull InsertType type) {
    List<NlComponent> preferences = myGroup.getChildren();

    if (preferences.isEmpty()) {
      insertComponents(-1, type);
      return;
    }

    int i = preferences.indexOf(myActivePreference);

    if (lastY >= getMidpointY(myActivePreference)) {
      i++;
    }

    insertComponents(i == preferences.size() ? -1 : i, type);
  }

  final int getMidpointY(@NotNull NlComponent preference) {
    return getMidpointY(myPreferenceToBoundsMap.get(preference));
  }

  static int getMidpointY(@NotNull Rectangle bounds) {
    return bounds.y + bounds.height / 2;
  }
}
