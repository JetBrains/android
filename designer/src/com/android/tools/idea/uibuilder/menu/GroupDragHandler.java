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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.common.command.NlWriteCommandAction;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.model.NlModelHelperKt;
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
  private final List<NlComponent> myItems;
  private final ActionBar myActionBar;

  private SceneComponent myActiveItem;

  GroupDragHandler(@NotNull ViewEditor editor,
                   @NotNull ViewGroupHandler handler,
                   @NotNull SceneComponent group,
                   @NotNull List<NlComponent> items,
                   @NotNull DragType type) {
    super(editor, handler, group, items, type);
    assert !items.isEmpty();

    myGroup = group;
    myItems = items;
    myActionBar = new ActionBar(group);
  }

  @Override
  public void commit(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers, @NotNull InsertType insertType) {
    int insertIndex = getInsertIndex();

    if (!canInsertComponents(insertIndex, insertType)) {
      return;
    }

    NlWriteCommandAction.run(myItems.get(0), "menu item addition", () -> {
      updateOrderInCategoryAttributes();
      updateShowAsActionAttribute();
      insertComponents(insertIndex, insertType);
    });
  }

  private void updateOrderInCategoryAttributes() {
    // TODO Handle more than one item
    if (myActiveItem == null || myActiveItem == layout.getSceneComponent(myItems.get(0))) {
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
    myItems.get(0).setAndroidAttribute(ATTR_ORDER_IN_CATEGORY, Integer.toString(order));
  }

  private void updateOverflowGroupOrderInCategoryAttributes(int order) {
    if (lastY >= myActiveItem.getCenterY()) {
      order++;
    }

    incrementOrderInCategoryAttributes(createOrderToItemMultimap(myActionBar.getOverflowItems()), order);

    // TODO Handle more than one item
    myItems.get(0).setAndroidAttribute(ATTR_ORDER_IN_CATEGORY, Integer.toString(order));
  }

  private static void incrementOrderInCategoryAttributes(@NotNull Multimap<Integer, SceneComponent> orderToItemMultimap, int order) {
    Collection<SceneComponent> items = orderToItemMultimap.get(order);

    if (!items.isEmpty()) {
      items.forEach(item -> item.getNlComponent().setAndroidAttribute(ATTR_ORDER_IN_CATEGORY, Integer.toString(order + 1)));
      incrementOrderInCategoryAttributes(orderToItemMultimap, order + 1);
    }
  }

  @NotNull
  private Multimap<Integer, SceneComponent> createOrderToItemMultimap(@NotNull Iterable<SceneComponent> group) {
    SceneComponent draggedItem = layout.getSceneComponent(myItems.get(0));

    Multimap<Integer, SceneComponent> orderToItemMultimap = ArrayListMultimap.create();

    for (SceneComponent item : group) {
      if (item == draggedItem) {
        continue;
      }

      Integer order = getOrderInCategory(item.getNlComponent());

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
    return NlModelHelperKt.isModuleDependency(editor.getModel(), APPCOMPAT_LIB_ARTIFACT) ? AUTO_URI : ANDROID_URI;
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
    List<SceneComponent> items = myActionBar.getItems();
    SceneComponent component;
    if (lastX < items.get(0).getDrawX()) {
      component = items.get(0);
    }
    else {
      Optional<SceneComponent> activeItem = items.stream()
        .filter(item -> item.containsX(lastX))
        .findFirst();

      component = activeItem.orElse(items.get(items.size() - 1));
    }
    myActiveItem = component;
  }

  private void updateUsingOverflowGroup() {
    List<SceneComponent> overflowItems = myActionBar.getOverflowItems();
    SceneComponent component;

    if (lastY < overflowItems.get(0).getDrawY()) {
      component = overflowItems.get(0);
    }
    else {
      Optional<SceneComponent> activeItem = overflowItems.stream()
        .filter(item -> item.containsY(lastY))
        .findFirst();

      component = activeItem.orElse(overflowItems.get(overflowItems.size() - 1));
    }
    myActiveItem = component;
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
    SceneComponent activeItem = myActiveItem;

    if (lastX < activeItem.getCenterX()) {
      graphics.drawLeft(myActiveItem.getNlComponent());
    }
    else {
      graphics.drawRight(myActiveItem.getNlComponent());
    }
  }

  private void drawActionBarGroupDropRecipientLines(@NotNull NlGraphics graphics) {
    @AndroidDpCoordinate Rectangle itemBounds = myActionBar.getItemBounds();
    assert itemBounds != null;

    List<SceneComponent> items = myActionBar.getItems();

    graphics.useStyle(NlDrawingStyle.DROP_RECIPIENT);
    graphics.drawTopDp(itemBounds);

    if (lastX >= items.get(0).getCenterX()) {
      graphics.drawLeftDp(itemBounds);
    }

    if (lastX < items.get(items.size() - 1).getCenterX()) {
      graphics.drawRightDp(itemBounds);
    }

    graphics.drawBottomDp(itemBounds);
  }

  private void drawActionBarGroupDropZoneLines(@NotNull NlGraphics graphics) {
    List<SceneComponent> items = myActionBar.getItems();
    @AndroidDpCoordinate int midpointX = myActiveItem.getCenterX();

    graphics.useStyle(NlDrawingStyle.DROP_ZONE);

    for (int i = 1, size = items.size(); i < size; i++) {
      SceneComponent item = items.get(i);

      if (myActiveItem == items.get(i - 1)) {
        if (lastX < midpointX) {
          graphics.drawLeft(item.getNlComponent());
        }
      }
      else if (myActiveItem == item) {
        if (lastX >= midpointX) {
          graphics.drawLeft(item.getNlComponent());
        }
      }
      else {
        graphics.drawLeft(item.getNlComponent());
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
    List<SceneComponent> overflowItems = myActionBar.getOverflowItems();

    @AndroidDpCoordinate Rectangle overflowItemBounds = myActionBar.getOverflowItemBounds();
    assert overflowItemBounds != null;

    graphics.useStyle(NlDrawingStyle.DROP_RECIPIENT);

    if (lastY >= overflowItems.get(0).getCenterX()) {
      graphics.drawTopDp(overflowItemBounds);
    }

    graphics.drawLeftDp(overflowItemBounds);
    graphics.drawRightDp(overflowItemBounds);

    if (lastY < overflowItems.get(overflowItems.size() - 1).getCenterY()) {
      graphics.drawBottomDp(overflowItemBounds);
    }
  }

  private void drawOverflowGroupDropZoneLines(@NotNull NlGraphics graphics) {
    List<SceneComponent> overflowItems = myActionBar.getOverflowItems();
    @AndroidDpCoordinate int midpointY = myActiveItem.getCenterY();

    graphics.useStyle(NlDrawingStyle.DROP_ZONE);

    for (int i = 1, size = overflowItems.size(); i < size; i++) {
      SceneComponent item = overflowItems.get(i);

      if (myActiveItem == overflowItems.get(i - 1)) {
        if (lastY < midpointY) {
          graphics.drawTop(item.getNlComponent());
        }
      }
      else if (myActiveItem == item) {
        if (lastY >= midpointY) {
          graphics.drawTop(item.getNlComponent());
        }
      }
      else {
        graphics.drawTop(item.getNlComponent());
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
    @AndroidDpCoordinate Rectangle itemBounds = myActionBar.getItemBounds();
    assert itemBounds != null;

    if (lastX < itemBounds.x) {
      return 0;
    }
    else if (lastX >= itemBounds.x + itemBounds.width) {
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
    @AndroidDpCoordinate Rectangle overflowItemBounds = myActionBar.getOverflowItemBounds();
    assert overflowItemBounds != null;

    if (lastY < overflowItemBounds.y) {
      return 0;
    }
    else if (lastY >= overflowItemBounds.y + overflowItemBounds.height) {
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
    @AndroidDpCoordinate Rectangle itemBounds = myActionBar.getItemBounds();
    return itemBounds != null && lastY < itemBounds.y + itemBounds.height;
  }

  private boolean isOverflowGroupActive() {
    return myActionBar.getOverflowItemBounds() != null;
  }
}
