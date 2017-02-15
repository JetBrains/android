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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.primitives.Ints;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.android.SdkConstants.*;

/**
 * "Group" is used generically to refer to a menu or group element. Menus can contain item or group elements and group elements can only
 * contain items.
 */
final class GroupDragHandler extends DragHandler {
  private final SceneComponent myGroup;
  private final List<SceneComponent> myItems;
  private final ActionBar myActionBar;

  private SceneComponent myActiveItem;

  GroupDragHandler(@NotNull ViewEditor editor,
                   @NotNull ViewGroupHandler handler,
                   @NotNull SceneComponent group,
                   @NotNull List<SceneComponent> items,
                   @NotNull DragType type) {
    super(editor, handler, group, items, type);
    assert !items.isEmpty();

    myGroup = group;
    myItems = items;
    myActionBar = new ActionBar(group.getNlComponent());
  }

  @Override
  public void commit(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers, @NotNull InsertType insertType) {
    updateOrderInCategoryAttributes();
    updateShowAsActionAttribute();
    insertComponents(getInsertIndex(), insertType);
  }

  private void updateOrderInCategoryAttributes() {
    // TODO Handle more than one item
    if (myActiveItem == null || myActiveItem == myItems.get(0)) {
      return;
    }

    Integer order = getOrderInCategory(myActiveItem.getNlComponent());

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
    if (lastX >= myActiveItem.getCenterX()) {
      order++;
    }

    incrementOrderInCategoryAttributes(createOrderToItemMultimap(myActionBar.getItems()), order);

    // TODO Handle more than one item
    myItems.get(0).getNlComponent().setAndroidAttribute(ATTR_ORDER_IN_CATEGORY, Integer.toString(order));
  }

  private void updateOverflowGroupOrderInCategoryAttributes(int order) {
    if (lastY >= myActiveItem.getCenterY()) {
      order++;
    }

    incrementOrderInCategoryAttributes(createOrderToItemMultimap(myActionBar.getOverflowItems()), order);

    // TODO Handle more than one item
    myItems.get(0).getNlComponent().setAndroidAttribute(ATTR_ORDER_IN_CATEGORY, Integer.toString(order));
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
      myItems.get(0).getNlComponent().setAttribute(getNamespace(), ATTR_SHOW_AS_ACTION, VALUE_ALWAYS);
    }
    else {
      myItems.get(0).getNlComponent().removeAttribute(getNamespace(), ATTR_SHOW_AS_ACTION);
    }
  }

  private String getNamespace() {
    return editor.getModel().isModuleDependency(APPCOMPAT_LIB_ARTIFACT) ? AUTO_URI : ANDROID_URI;
  }

  @Nullable
  @Override
  public String update(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, int modifiers) {
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
    List<NlComponent> items = myActionBar.getItems();
    NlComponent nlComponent;
    if (editor.dpToPx(lastX) < items.get(0).x) {
      nlComponent = items.get(0);
    }
    else {
      Optional<NlComponent> activeItem = items.stream()
        .filter(item -> item.containsX(editor.dpToPx(lastX)))
        .findFirst();

      nlComponent = activeItem.orElse(items.get(items.size() - 1));
    }
    myActiveItem = myGroup.getScene().getSceneComponent(nlComponent);
  }

  private void updateUsingOverflowGroup() {
    List<NlComponent> overflowItems = myActionBar.getOverflowItems();
    NlComponent nlComponent;

    if (editor.dpToPx(lastY) < overflowItems.get(0).y) {
      nlComponent = overflowItems.get(0);
    }
    else {
      Optional<NlComponent> activeItem = overflowItems.stream()
        .filter(item -> item.containsY(editor.dpToPx(lastY)))
        .findFirst();

      nlComponent = activeItem.orElse(overflowItems.get(overflowItems.size() - 1));
    }
    myActiveItem = myGroup.getScene().getSceneComponent(nlComponent);
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

    if (lastX < myActiveItem.getCenterX()) {
      graphics.drawLeft(myActiveItem.getNlComponent());
    }
    else {
      graphics.drawRight(myActiveItem.getNlComponent());
    }
  }

  private void drawActionBarGroupDropRecipientLines(@NotNull NlGraphics graphics) {
    @AndroidCoordinate Rectangle itemBounds = myActionBar.getItemBounds();
    assert itemBounds != null;

    List<NlComponent> items = myActionBar.getItems();

    graphics.useStyle(NlDrawingStyle.DROP_RECIPIENT);
    graphics.drawTop(itemBounds);

    if (editor.dpToPx(lastX) >= items.get(0).getMidpointX()) {
      graphics.drawLeft(itemBounds);
    }

    if (editor.dpToPx(lastX) < items.get(items.size() - 1).getMidpointX()) {
      graphics.drawRight(itemBounds);
    }

    graphics.drawBottom(itemBounds);
  }

  private void drawActionBarGroupDropZoneLines(@NotNull NlGraphics graphics) {
    List<NlComponent> items = myActionBar.getItems();
    @AndroidDpCoordinate int midpointX = myActiveItem.getCenterX();

    graphics.useStyle(NlDrawingStyle.DROP_ZONE);

    for (int i = 1, size = items.size(); i < size; i++) {
      NlComponent item = items.get(i);

      if (myActiveItem.getNlComponent() == items.get(i - 1)) {
        if (lastX < midpointX) {
          graphics.drawLeft(item);
        }
      }
      else if (myActiveItem.getNlComponent() == item) {
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

    if (lastY < myActiveItem.getCenterY()) {
      graphics.drawTop(myActiveItem.getNlComponent());
    }
    else {
      graphics.drawBottom(myActiveItem.getNlComponent());
    }
  }

  private void drawOverflowGroupDropRecipientLines(@NotNull NlGraphics graphics) {
    List<NlComponent> overflowItems = myActionBar.getOverflowItems();

    @AndroidCoordinate Rectangle overflowItemBounds = myActionBar.getOverflowItemBounds();
    assert overflowItemBounds != null;

    graphics.useStyle(NlDrawingStyle.DROP_RECIPIENT);

    if (editor.dpToPx(lastY) >= overflowItems.get(0).getMidpointY()) {
      graphics.drawTop(overflowItemBounds);
    }

    graphics.drawLeft(overflowItemBounds);
    graphics.drawRight(overflowItemBounds);

    if (editor.dpToPx(lastY) < overflowItems.get(overflowItems.size() - 1).getMidpointY()) {
      graphics.drawBottom(overflowItemBounds);
    }
  }

  private void drawOverflowGroupDropZoneLines(@NotNull NlGraphics graphics) {
    List<NlComponent> overflowItems = myActionBar.getOverflowItems();
    @AndroidDpCoordinate int midpointY = myActiveItem.getCenterY();

    graphics.useStyle(NlDrawingStyle.DROP_ZONE);

    for (int i = 1, size = overflowItems.size(); i < size; i++) {
      NlComponent item = overflowItems.get(i);

      if (myActiveItem.getNlComponent() == overflowItems.get(i - 1)) {
        if (lastY < midpointY) {
          graphics.drawTop(item);
        }
      }
      else if (myActiveItem.getNlComponent() == item) {
        if (lastY >= midpointY) {
          graphics.drawTop(item);
        }
      }
      else {
        graphics.drawTop(item);
      }
    }
  }

  @VisibleForTesting
  int getInsertIndex() {
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
    @AndroidCoordinate Rectangle itemBounds = myActionBar.getItemBounds();
    assert itemBounds != null;

    if (editor.dpToPx(lastX) < itemBounds.x) {
      return 0;
    }
    else if (editor.dpToPx(lastX) >= itemBounds.x + itemBounds.width) {
      return -1;
    }

    int i = myGroup.getChildren().indexOf(myActiveItem);
    assert i != -1;

    if (lastX >= myActiveItem.getCenterX()) {
      i++;
    }

    return i == myGroup.getChildCount() ? -1 : i;
  }

  private int getInsertIndexUsingOverflowGroup() {
    @AndroidCoordinate Rectangle overflowItemBounds = myActionBar.getOverflowItemBounds();
    assert overflowItemBounds != null;

    if (editor.dpToPx(lastY) < overflowItemBounds.y) {
      return 0;
    }
    else if (editor.dpToPx(lastY) >= overflowItemBounds.y + overflowItemBounds.height) {
      return -1;
    }

    int i = myGroup.getChildren().indexOf(myActiveItem);
    assert i != -1;

    if (lastY >= myActiveItem.getCenterY()) {
      i++;
    }

    return i == myGroup.getChildCount() ? -1 : i;
  }

  private boolean isActionBarGroupActive() {
    @AndroidCoordinate Rectangle itemBounds = myActionBar.getItemBounds();
    return itemBounds != null && editor.dpToPx(lastY) < itemBounds.y + itemBounds.height;
  }

  private boolean isOverflowGroupActive() {
    return myActionBar.getOverflowItemBounds() != null;
  }
}
