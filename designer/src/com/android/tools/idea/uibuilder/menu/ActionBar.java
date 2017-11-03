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

import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.common.scene.SceneComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

final class ActionBar {
  private final List<SceneComponent> myItems;
  private final List<SceneComponent> myOverflowItems;

  @AndroidCoordinate
  private final Rectangle myItemBounds;
  @AndroidCoordinate
  private final Rectangle myOverflowItemBounds;

  ActionBar(@NotNull SceneComponent group) {
    myItems = new ArrayList<>();
    myOverflowItems = new ArrayList<>();

    addToItemsOrOverflowItems(group);

    // noinspection SuspiciousNameCombination
    myItems.sort((item1, item2) -> Integer.compare(item1.getDrawX(), item2.getDrawX()));

    // noinspection SuspiciousNameCombination
    myOverflowItems.sort((item1, item2) -> Integer.compare(item1.getDrawY(), item2.getDrawY()));

    myItemBounds = getBounds(myItems);
    myOverflowItemBounds = getBounds(myOverflowItems);
  }

  private void addToItemsOrOverflowItems(@NotNull SceneComponent group) {
    group.getChildren().stream()
      .filter(item -> item.getDrawWidth() != -1 && item.getDrawHeight() != -1)
      .forEach(item -> {
        if (NlComponentHelperKt.getViewInfo(item.getNlComponent()) == null) {
          // item corresponds to a group element
          addToItemsOrOverflowItems(item);
          return;
        }

        switch (NlComponentHelperKt.getViewInfo(item.getNlComponent()).getViewType()) {
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

  @AndroidDpCoordinate
  @Nullable
  private static Rectangle getBounds(@NotNull List<SceneComponent> items) {
    if (items.isEmpty()) {
      return null;
    }

    SceneComponent firstItem = items.get(0);
    Rectangle bounds = firstItem.fillRect(null);
    Rectangle temp = new Rectangle();
    items.subList(1, items.size()).forEach(item -> bounds.add(item.fillRect(temp)));

    return bounds;
  }

  @NotNull
  List<SceneComponent> getItems() {
    return myItems;
  }

  @NotNull
  List<SceneComponent> getOverflowItems() {
    return myOverflowItems;
  }

  @AndroidDpCoordinate
  @Nullable
  Rectangle getItemBounds() {
    return myItemBounds;
  }

  @AndroidDpCoordinate
  @Nullable
  Rectangle getOverflowItemBounds() {
    return myOverflowItemBounds;
  }

  boolean contains(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    return myItemBounds.contains(x, y) || myOverflowItemBounds.contains(x, y);
  }
}
