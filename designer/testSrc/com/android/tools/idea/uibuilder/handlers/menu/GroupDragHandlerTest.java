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

import com.android.ide.common.rendering.api.ViewType;
import com.android.tools.idea.uibuilder.api.DragType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.handlers.HandlerTestFactory;
import com.android.tools.idea.uibuilder.model.NlComponent;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public final class GroupDragHandlerTest {
  @Test
  public void updateUsingActionBarGroup() {
    NlComponent group = newGroup(670, 58, 98, 96);
    group.addChild(newActionBarItem(670, 58, 98, 96));

    NlComponent menu = newMenu(572, 58, 196, 96);
    menu.addChild(newActionBarItem(572, 58, 98, 96));
    menu.addChild(group);

    GroupDragHandler handler = newGroupDragHandler(menu);
    handler.update(793, 0, 0);

    assertEquals(-1, handler.getInsertIndex());
  }

  @Test
  public void updateUsingOverflowGroup() {
    NlComponent menu = newMenu(366, 162, 392, 288);

    menu.addChild(newOverflowItem(366, 162, 392, 96));
    menu.addChild(newOverflowItem(366, 258, 392, 96));
    menu.addChild(newOverflowItem(366, 354, 392, 96));

    GroupDragHandler handler = newGroupDragHandler(menu);
    int y = 90;

    y += 48;
    handler.update(0, y, 0);
    assertEquals(0, handler.getInsertIndex());

    y += 48;
    handler.update(0, y, 0);
    assertEquals(0, handler.getInsertIndex());

    y += 48;
    handler.update(0, y, 0);
    assertEquals(1, handler.getInsertIndex());

    y += 48;
    handler.update(0, y, 0);
    assertEquals(1, handler.getInsertIndex());

    y += 48;
    handler.update(0, y, 0);
    assertEquals(2, handler.getInsertIndex());

    y += 48;
    handler.update(0, y, 0);
    assertEquals(2, handler.getInsertIndex());

    y += 48;
    handler.update(0, y, 0);
    assertEquals(-1, handler.getInsertIndex());

    y += 48;
    handler.update(0, y, 0);
    assertEquals(-1, handler.getInsertIndex());
  }

  @Test
  public void updateUsingOverflowGroupEmptyGroupInFront() {
    NlComponent menu = newMenu(366, 162, 392, 192);

    menu.addChild(newGroup(0, 0, -1, -1));
    menu.addChild(newOverflowItem(366, 162, 392, 96));
    menu.addChild(newOverflowItem(366, 258, 392, 96));

    GroupDragHandler handler = newGroupDragHandler(menu);
    int y = 90;

    y += 48;
    handler.update(0, y, 0);
    assertEquals(0, handler.getInsertIndex());

    y += 48;
    handler.update(0, y, 0);
    assertEquals(1, handler.getInsertIndex());

    y += 48;
    handler.update(0, y, 0);
    assertEquals(2, handler.getInsertIndex());

    y += 48;
    handler.update(0, y, 0);
    assertEquals(2, handler.getInsertIndex());

    y += 48;
    handler.update(0, y, 0);
    assertEquals(-1, handler.getInsertIndex());

    y += 48;
    handler.update(0, y, 0);
    assertEquals(-1, handler.getInsertIndex());
  }

  @Test
  public void updateUsingOverflowGroupEmptyGroupInMiddle() {
    NlComponent menu = newMenu(366, 162, 392, 192);

    menu.addChild(newOverflowItem(366, 162, 392, 96));
    menu.addChild(newGroup(0, 0, -1, -1));
    menu.addChild(newOverflowItem(366, 258, 392, 96));

    GroupDragHandler handler = newGroupDragHandler(menu);
    int y = 90;

    y += 48;
    handler.update(0, y, 0);
    assertEquals(0, handler.getInsertIndex());

    y += 48;
    handler.update(0, y, 0);
    assertEquals(0, handler.getInsertIndex());

    y += 48;
    handler.update(0, y, 0);
    assertEquals(1, handler.getInsertIndex());

    y += 48;
    handler.update(0, y, 0);
    assertEquals(2, handler.getInsertIndex());

    y += 48;
    handler.update(0, y, 0);
    assertEquals(-1, handler.getInsertIndex());

    y += 48;
    handler.update(0, y, 0);
    assertEquals(-1, handler.getInsertIndex());
  }

  @Test
  public void updateUsingOverflowGroupEmptyGroupInBack() {
    NlComponent menu = newMenu(366, 162, 392, 192);

    menu.addChild(newOverflowItem(366, 162, 392, 96));
    menu.addChild(newOverflowItem(366, 258, 392, 96));
    menu.addChild(newGroup(0, 0, -1, -1));

    GroupDragHandler handler = newGroupDragHandler(menu);
    int y = 90;

    y += 48;
    handler.update(0, y, 0);
    assertEquals(0, handler.getInsertIndex());

    y += 48;
    handler.update(0, y, 0);
    assertEquals(0, handler.getInsertIndex());

    y += 48;
    handler.update(0, y, 0);
    assertEquals(1, handler.getInsertIndex());

    y += 48;
    handler.update(0, y, 0);
    assertEquals(1, handler.getInsertIndex());

    y += 48;
    handler.update(0, y, 0);
    assertEquals(2, handler.getInsertIndex());

    y += 48;
    handler.update(0, y, 0);
    assertEquals(-1, handler.getInsertIndex());
  }

  @NotNull
  private static NlComponent newMenu(int x, int y, int width, int height) {
    NlComponent menu = HandlerTestFactory.newNlComponent("menu");
    menu.setBounds(x, y, width, height);

    return menu;
  }

  @NotNull
  @SuppressWarnings("SameParameterValue")
  private static NlComponent newActionBarItem(int x, int y, int width, int height) {
    NlComponent item = HandlerTestFactory.newNlComponent("item");
    item.viewInfo = MenuTestFactory.mockViewInfo(ViewType.ACTION_BAR_MENU);
    item.setBounds(x, y, width, height);

    return item;
  }

  @NotNull
  @SuppressWarnings("SameParameterValue")
  private static NlComponent newOverflowItem(int x, int y, int width, int height) {
    NlComponent item = HandlerTestFactory.newNlComponent("item");
    item.viewInfo = MenuTestFactory.mockViewInfo(ViewType.ACTION_BAR_OVERFLOW_MENU);
    item.setBounds(x, y, width, height);

    return item;
  }

  @NotNull
  private static NlComponent newGroup(int x, int y, int width, int height) {
    NlComponent group = HandlerTestFactory.newNlComponent("group");
    group.setBounds(x, y, width, height);

    return group;
  }

  @NotNull
  private static GroupDragHandler newGroupDragHandler(@NotNull NlComponent group) {
    List<NlComponent> items = Collections.singletonList(Mockito.mock(NlComponent.class));
    return new GroupDragHandler(Mockito.mock(ViewEditor.class), new ViewGroupHandler(), group, items, DragType.CREATE);
  }
}
