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
import com.android.tools.idea.uibuilder.handlers.HandlerTestFactory;
import com.android.tools.idea.uibuilder.model.NlComponent;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public final class ActionBarTest {
  @Test
  public void addToItemsOrOverflowItemsItemWidthAndHeightAreNegativeOne() {
    NlComponent item = HandlerTestFactory.newNlComponent("item");
    item.setBounds(0, 0, -1, -1);

    NlComponent menu = HandlerTestFactory.newNlComponent("menu");
    menu.addChild(item);

    ActionBar actionBar = new ActionBar(menu);

    assertEquals(Collections.emptyList(), actionBar.getItems());
    assertEquals(Collections.emptyList(), actionBar.getOverflowItems());
  }

  @Test
  public void addToItemsOrOverflowItemsItemIsGroup() {
    NlComponent item = HandlerTestFactory.newNlComponent("item");
    item.viewInfo = MenuTestFactory.mockViewInfo(ViewType.ACTION_BAR_MENU);

    NlComponent group = HandlerTestFactory.newNlComponent("group");
    group.addChild(item);

    NlComponent menu = HandlerTestFactory.newNlComponent("menu");
    menu.addChild(group);

    ActionBar actionBar = new ActionBar(menu);

    assertEquals(Collections.singletonList(item), actionBar.getItems());
    assertEquals(Collections.emptyList(), actionBar.getOverflowItems());
  }

  @Test
  public void addToItemsOrOverflowItemsItemViewTypeIsActionBarMenu() {
    NlComponent item = HandlerTestFactory.newNlComponent("item");
    item.viewInfo = MenuTestFactory.mockViewInfo(ViewType.ACTION_BAR_MENU);

    NlComponent menu = HandlerTestFactory.newNlComponent("menu");
    menu.addChild(item);

    ActionBar actionBar = new ActionBar(menu);

    assertEquals(Collections.singletonList(item), actionBar.getItems());
    assertEquals(Collections.emptyList(), actionBar.getOverflowItems());
  }

  @Test
  public void addToItemsOrOverflowItemsItemViewTypeIsActionBarOverflowMenu() {
    NlComponent item = HandlerTestFactory.newNlComponent("item");
    item.viewInfo = MenuTestFactory.mockViewInfo(ViewType.ACTION_BAR_OVERFLOW_MENU);

    NlComponent menu = HandlerTestFactory.newNlComponent("menu");
    menu.addChild(item);

    ActionBar actionBar = new ActionBar(menu);

    assertEquals(Collections.emptyList(), actionBar.getItems());
    assertEquals(Collections.singletonList(item), actionBar.getOverflowItems());
  }
}
