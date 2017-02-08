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
package com.android.tools.idea.uibuilder.handlers.menu;

import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.NlComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

final class ActionBar {
  private final List<NlComponent> myItems;
  private final List<NlComponent> myOverflowItems;

  @AndroidCoordinate
  private final Rectangle myItemBounds;
  @AndroidCoordinate
  private final Rectangle myOverflowItemBounds;

  ActionBar(@NotNull NlComponent group) {
    myItems = new ArrayList<>();
    myOverflowItems = new ArrayList<>();

    addToItemsOrOverflowItems(group);

    // noinspection SuspiciousNameCombination
    myItems.sort((item1, item2) -> Integer.compare(item1.x, item2.x));

    // noinspection SuspiciousNameCombination
    myOverflowItems.sort((item1, item2) -> Integer.compare(item1.y, item2.y));

    myItemBounds = getBounds(myItems);
    myOverflowItemBounds = getBounds(myOverflowItems);
  }

  private void addToItemsOrOverflowItems(@NotNull NlComponent group) {
    group.getChildren().stream()
      .filter(item -> item.w != -1 && item.h != -1)
      .forEach(item -> {
        if (item.viewInfo == null) {
          // item corresponds to a group element
          addToItemsOrOverflowItems(item);
          return;
        }

        switch (item.viewInfo.getViewType()) {
          case ACTION_BAR_MENU:
            myItems.add(item);
            break;
          case ACTION_BAR_OVERFLOW_MENU:
            myOverflowItems.add(item);
            break;
          default:
            break;
        }
      });
  }

  @AndroidCoordinate
  @Nullable
  private static Rectangle getBounds(@NotNull List<NlComponent> items) {
    if (items.isEmpty()) {
      return null;
    }

    NlComponent firstItem = items.get(0);
    Rectangle bounds = new Rectangle(firstItem.x, firstItem.y, firstItem.w, firstItem.h);
    items.subList(1, items.size()).forEach(item -> bounds.add(new Rectangle(item.x, item.y, item.w, item.h)));

    return bounds;
  }

  @NotNull
  List<NlComponent> getItems() {
    return myItems;
  }

  @NotNull
  List<NlComponent> getOverflowItems() {
    return myOverflowItems;
  }

  @AndroidCoordinate
  @Nullable
  Rectangle getItemBounds() {
    return myItemBounds;
  }

  @AndroidCoordinate
  @Nullable
  Rectangle getOverflowItemBounds() {
    return myOverflowItemBounds;
  }

  boolean contains(@AndroidCoordinate int x, @AndroidCoordinate int y) {
    return myItemBounds.contains(x, y) || myOverflowItemBounds.contains(x, y);
  }
}
