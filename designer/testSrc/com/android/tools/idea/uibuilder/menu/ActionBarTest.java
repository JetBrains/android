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

import com.android.ide.common.rendering.api.ViewType;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.NlModelBuilderUtil;
import com.android.tools.idea.uibuilder.scene.SyncLayoutlibSceneManager;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.common.scene.SceneComponent;

import java.util.Collections;

public final class ActionBarTest extends LayoutTestCase {
  public void testAddToItemsOrOverflowItemsItemWidthAndHeightAreNegativeOne() {
    SyncNlModel model = model("model.xml", component("menu").unboundedChildren(component("item").viewType(ViewType.ACTION_BAR_MENU))).build();

    SyncLayoutlibSceneManager manager = NlModelBuilderUtil.getSyncLayoutlibSceneManagerForModel(model);
    manager.setIgnoreRenderRequests(true);
    SceneComponent menu = manager.getScene().getRoot();
    SceneComponent item = menu.getChildren().get(0);
    item.setPosition(0, 0);
    item.setSize(-1, -1);

    ActionBar actionBar = new ActionBar(menu);

    assertEquals(Collections.emptyList(), actionBar.getItems());
    assertEquals(Collections.emptyList(), actionBar.getOverflowItems());
  }

  public void testAddToItemsOrOverflowItemsItemIsGroup() {
    SyncNlModel model = model("model.xml",
                          component("menu").unboundedChildren(
                            component("group").unboundedChildren(
                              component("item").viewType(ViewType.ACTION_BAR_MENU)))).build();

    SyncLayoutlibSceneManager manager = NlModelBuilderUtil.getSyncLayoutlibSceneManagerForModel(model);
    manager.setIgnoreRenderRequests(true);
    SceneComponent menu = manager.getScene().getRoot();
    SceneComponent group = menu.getChildren().get(0);
    NlComponentHelperKt.setViewInfo(group.getNlComponent(), null);
    SceneComponent item = group.getChildren().get(0);

    ActionBar actionBar = new ActionBar(menu);

    assertEquals(Collections.singletonList(item), actionBar.getItems());
    assertEquals(Collections.emptyList(), actionBar.getOverflowItems());
  }

  public void testAddToItemsOrOverflowItemsItemViewTypeIsActionBarMenu() {
    SyncNlModel model = model("model.xml", component("menu").unboundedChildren(component("item").viewType(ViewType.ACTION_BAR_MENU))).build();

    SyncLayoutlibSceneManager manager = NlModelBuilderUtil.getSyncLayoutlibSceneManagerForModel(model);
    manager.setIgnoreRenderRequests(true);
    SceneComponent menu = manager.getScene().getRoot();
    SceneComponent item = menu.getChildren().get(0);

    ActionBar actionBar = new ActionBar(menu);

    assertEquals(Collections.singletonList(item), actionBar.getItems());
    assertEquals(Collections.emptyList(), actionBar.getOverflowItems());
  }

  public void testAddToItemsOrOverflowItemsItemViewTypeIsActionBarOverflowMenu() {
    SyncNlModel model = model("model.xml", component("menu").unboundedChildren(component("item").viewType(ViewType.ACTION_BAR_OVERFLOW_MENU))).build();

    SyncLayoutlibSceneManager manager = NlModelBuilderUtil.getSyncLayoutlibSceneManagerForModel(model);
    manager.setIgnoreRenderRequests(true);
    SceneComponent menu = manager.getScene().getRoot();
    SceneComponent item = menu.getChildren().get(0);

    ActionBar actionBar = new ActionBar(menu);

    assertEquals(Collections.emptyList(), actionBar.getItems());
    assertEquals(Collections.singletonList(item), actionBar.getOverflowItems());
  }
}
