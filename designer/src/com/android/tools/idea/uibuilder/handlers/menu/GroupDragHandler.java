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

import com.android.tools.idea.uibuilder.api.DragHandler;
import com.android.tools.idea.uibuilder.api.DragType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.primitives.Ints;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.android.SdkConstants.*;

/**
 * "Group" is used generically to refer to a menu or group element. Menus can contain item or group elements and group elements can only
 * contain items.
 */
final class GroupDragHandler extends DragHandler {
  private final NlComponent myGroup;
  private final List<NlComponent> myItems;

  private final List<NlComponent> myActionBarGroup;
  private final List<NlComponent> myOverflowGroup;

  private final Rectangle myActionBarGroupBounds;
  private final Rectangle myOverflowGroupBounds;

  private NlComponent myActiveItem;

  GroupDragHandler(@NotNull ViewEditor editor,
                   @NotNull ViewGroupHandler handler,
                   @NotNull NlComponent group,
                   @NotNull List<NlComponent> items,
                   @NotNull DragType type) {
    super(editor, handler, group, items, type);

    myGroup = group;
    myItems = items;

    myActionBarGroup = new ArrayList<>();
    myOverflowGroup = new ArrayList<>();
    addToActionBarOrOverflowGroups(group);

    // noinspection SuspiciousNameCombination
    myActionBarGroup.sort((item1, item2) -> Integer.compare(item1.x, item2.x));

    // noinspection SuspiciousNameCombination
    myOverflowGroup.sort((item1, item2) -> Integer.compare(item1.y, item2.y));

    myActionBarGroupBounds = getBounds(myActionBarGroup);
    myOverflowGroupBounds = getBounds(myOverflowGroup);
  }

  private void addToActionBarOrOverflowGroups(@NotNull NlComponent group) {
    for (NlComponent item : group.getChildren()) {
      if (item.w == -1 || item.h == -1) {
        continue;
      }

      if (item.viewInfo == null) {
        // item corresponds to a group element
        addToActionBarOrOverflowGroups(item);
        continue;
      }

      switch (item.viewInfo.getViewType()) {
        case ACTION_BAR_MENU:
          myActionBarGroup.add(item);
          break;
        case ACTION_BAR_OVERFLOW_MENU:
          myOverflowGroup.add(item);
          break;
        default:
          break;
      }
    }
  }

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

  @Override
  public void commit(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers) {
    updateOrderInCategoryAttributes();
    updateShowAsActionAttribute();
  }

  private void updateOrderInCategoryAttributes() {
    // TODO Handle more than one item
    if (myActiveItem == null || myActiveItem == myItems.get(0)) {
      return;
    }

    Integer order = getOrderInCategory(myActiveItem);

    if (order == null) {
      return;
    }

    if (isActionBarGroupActive()) {
      updateActionBarGroupOrderInCategoryAttributes(order);
    }
    else {
      updateOverflowGroupOrderInCategoryAttributes(order);
    }
  }

  private void updateActionBarGroupOrderInCategoryAttributes(int order) {
    if (lastX >= myActiveItem.getMidpointX()) {
      order++;
    }

    incrementOrderInCategoryAttributes(createOrderToItemMultimap(myActionBarGroup), order);

    // TODO Handle more than one item
    myItems.get(0).setAndroidAttribute(ATTR_ORDER_IN_CATEGORY, Integer.toString(order));
  }

  private void updateOverflowGroupOrderInCategoryAttributes(int order) {
    if (lastY >= myActiveItem.getMidpointY()) {
      order++;
    }

    incrementOrderInCategoryAttributes(createOrderToItemMultimap(myOverflowGroup), order);

    // TODO Handle more than one item
    myItems.get(0).setAndroidAttribute(ATTR_ORDER_IN_CATEGORY, Integer.toString(order));
  }

  private static void incrementOrderInCategoryAttributes(@NotNull Multimap<Integer, NlComponent> orderToItemMultimap, int order) {
    Collection<NlComponent> items = orderToItemMultimap.get(order);

    if (!items.isEmpty()) {
      items.forEach(item -> item.setAndroidAttribute(ATTR_ORDER_IN_CATEGORY, Integer.toString(order + 1)));
      incrementOrderInCategoryAttributes(orderToItemMultimap, order + 1);
    }
  }

  @NotNull
  private Multimap<Integer, NlComponent> createOrderToItemMultimap(@NotNull Iterable<NlComponent> group) {
    Object draggedItem = myItems.get(0);
    Multimap<Integer, NlComponent> orderToItemMultimap = ArrayListMultimap.create();

    for (NlComponent item : group) {
      if (item == draggedItem) {
        continue;
      }

      Integer order = getOrderInCategory(item);

      if (order != null) {
        orderToItemMultimap.put(order, item);
      }
    }

    return orderToItemMultimap;
  }

  @Nullable
  private static Integer getOrderInCategory(@NotNull NlComponent item) {
    String order = item.getAndroidAttribute(ATTR_ORDER_IN_CATEGORY);
    return order == null ? null : Ints.tryParse(order);
  }

  private void updateShowAsActionAttribute() {
    if (isActionBarGroupActive()) {
      // TODO Handle more than one item
      myItems.get(0).setAttribute(getNamespace(), ATTR_SHOW_AS_ACTION, VALUE_ALWAYS);
    }
    else {
      myItems.get(0).removeAttribute(getNamespace(), ATTR_SHOW_AS_ACTION);
    }
  }

  private String getNamespace() {
    return editor.isModuleDependency(APPCOMPAT_LIB_ARTIFACT) ? AUTO_URI : ANDROID_URI;
  }

  @Nullable
  @Override
  public String update(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers) {
    String message = super.update(x, y, modifiers);

    if (message != null) {
      myActiveItem = null;
      return message;
    }

    if (isActionBarGroupActive()) {
      updateUsingActionBarGroup();
    }
    else if (isOverflowGroupActive()) {
      updateUsingOverflowGroup();
    }
    else {
      myActiveItem = null;
    }

    return null;
  }

  private void updateUsingActionBarGroup() {
    if (lastX < myActionBarGroup.get(0).x) {
      myActiveItem = myActionBarGroup.get(0);
    }
    else {
      Optional<NlComponent> activeItem = myActionBarGroup.stream()
        .filter(item -> item.containsX(lastX))
        .findFirst();

      myActiveItem = activeItem.orElse(myActionBarGroup.get(myActionBarGroup.size() - 1));
    }
  }

  private void updateUsingOverflowGroup() {
    if (lastY < myOverflowGroup.get(0).y) {
      myActiveItem = myOverflowGroup.get(0);
    }
    else {
      Optional<NlComponent> activeItem = myOverflowGroup.stream()
        .filter(item -> item.containsY(lastY))
        .findFirst();

      myActiveItem = activeItem.orElse(myOverflowGroup.get(myOverflowGroup.size() - 1));
    }
  }

  @Override
  public void paint(@NotNull NlGraphics graphics) {
    if (isActionBarGroupActive()) {
      drawActionBarGroupDropPreviewLine(graphics);
      drawActionBarGroupDropRecipientLines(graphics);
      drawActionBarGroupDropZoneLines(graphics);
    }
    else if (isOverflowGroupActive()) {
      drawOverflowGroupDropPreviewLine(graphics);
      drawOverflowGroupDropRecipientLines(graphics);
      drawOverflowGroupDropZoneLines(graphics);
    }
  }

  private void drawActionBarGroupDropPreviewLine(@NotNull NlGraphics graphics) {
    graphics.useStyle(NlDrawingStyle.DROP_PREVIEW);

    if (lastX < myActiveItem.getMidpointX()) {
      graphics.drawLeft(myActiveItem);
    }
    else {
      graphics.drawRight(myActiveItem);
    }
  }

  private void drawActionBarGroupDropRecipientLines(@NotNull NlGraphics graphics) {
    graphics.useStyle(NlDrawingStyle.DROP_RECIPIENT);
    graphics.drawTop(myActionBarGroupBounds);

    if (lastX >= myActionBarGroup.get(0).getMidpointX()) {
      graphics.drawLeft(myActionBarGroupBounds);
    }

    if (lastX < myActionBarGroup.get(myActionBarGroup.size() - 1).getMidpointX()) {
      graphics.drawRight(myActionBarGroupBounds);
    }

    graphics.drawBottom(myActionBarGroupBounds);
  }

  private void drawActionBarGroupDropZoneLines(@NotNull NlGraphics graphics) {
    int midpointX = myActiveItem.getMidpointX();
    graphics.useStyle(NlDrawingStyle.DROP_ZONE);

    for (int i = 1, size = myActionBarGroup.size(); i < size; i++) {
      NlComponent item = myActionBarGroup.get(i);

      if (myActiveItem == myActionBarGroup.get(i - 1)) {
        if (lastX < midpointX) {
          graphics.drawLeft(item);
        }
      }
      else if (myActiveItem == item) {
        if (lastX >= midpointX) {
          graphics.drawLeft(item);
        }
      }
      else {
        graphics.drawLeft(item);
      }
    }
  }

  private void drawOverflowGroupDropPreviewLine(@NotNull NlGraphics graphics) {
    graphics.useStyle(NlDrawingStyle.DROP_PREVIEW);

    if (lastY < myActiveItem.getMidpointY()) {
      graphics.drawTop(myActiveItem);
    }
    else {
      graphics.drawBottom(myActiveItem);
    }
  }

  private void drawOverflowGroupDropRecipientLines(@NotNull NlGraphics graphics) {
    graphics.useStyle(NlDrawingStyle.DROP_RECIPIENT);

    if (lastY >= myOverflowGroup.get(0).getMidpointY()) {
      graphics.drawTop(myOverflowGroupBounds);
    }

    graphics.drawLeft(myOverflowGroupBounds);
    graphics.drawRight(myOverflowGroupBounds);

    if (lastY < myOverflowGroup.get(myOverflowGroup.size() - 1).getMidpointY()) {
      graphics.drawBottom(myOverflowGroupBounds);
    }
  }

  private void drawOverflowGroupDropZoneLines(@NotNull NlGraphics graphics) {
    int midpointY = myActiveItem.getMidpointY();
    graphics.useStyle(NlDrawingStyle.DROP_ZONE);

    for (int i = 1, size = myOverflowGroup.size(); i < size; i++) {
      NlComponent item = myOverflowGroup.get(i);

      if (myActiveItem == myOverflowGroup.get(i - 1)) {
        if (lastY < midpointY) {
          graphics.drawTop(item);
        }
      }
      else if (myActiveItem == item) {
        if (lastY >= midpointY) {
          graphics.drawTop(item);
        }
      }
      else {
        graphics.drawTop(item);
      }
    }
  }

  @Override
  public int getInsertIndex() {
    if (isActionBarGroupActive()) {
      return getInsertIndexUsingActionBarGroup();
    }
    else if (isOverflowGroupActive()) {
      return getInsertIndexUsingOverflowGroup();
    }
    else {
      return -1;
    }
  }

  private int getInsertIndexUsingActionBarGroup() {
    if (lastX < myActionBarGroupBounds.x) {
      return 0;
    }
    else if (lastX >= myActionBarGroupBounds.x + myActionBarGroupBounds.width) {
      return -1;
    }

    int i = myGroup.getChildren().indexOf(myActiveItem);
    assert i != -1;

    if (lastX >= myActiveItem.getMidpointX()) {
      i++;
    }

    return i == myGroup.getChildCount() ? -1 : i;
  }

  private int getInsertIndexUsingOverflowGroup() {
    if (lastY < myOverflowGroupBounds.y) {
      return 0;
    }
    else if (lastY >= myOverflowGroupBounds.y + myOverflowGroupBounds.height) {
      return -1;
    }

    int i = myGroup.getChildren().indexOf(myActiveItem);
    assert i != -1;

    if (lastY >= myActiveItem.getMidpointY()) {
      i++;
    }

    return i == myGroup.getChildCount() ? -1 : i;
  }

  private boolean isActionBarGroupActive() {
    return myActionBarGroupBounds != null && lastY < myActionBarGroupBounds.y + myActionBarGroupBounds.height;
  }

  private boolean isOverflowGroupActive() {
    return myOverflowGroupBounds != null;
  }
}
