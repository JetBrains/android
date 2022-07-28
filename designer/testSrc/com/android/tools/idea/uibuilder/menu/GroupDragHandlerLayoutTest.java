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
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.api.DragType;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.fixtures.ComponentDescriptor;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlComponentBackend;
import com.android.tools.idea.common.model.NlComponentBackendXml;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.util.XmlTagUtil;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.common.LayoutTestUtilities;
import com.android.tools.idea.uibuilder.NlModelBuilderUtil;
import com.android.tools.idea.uibuilder.scene.SyncLayoutlibSceneManager;
import com.android.tools.idea.uibuilder.api.*;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static com.android.SdkConstants.*;

public final class GroupDragHandlerLayoutTest extends LayoutTestCase {
  public void testCommitConsecutiveOrders() {
    ComponentDescriptor menuDescriptor = component(TAG_MENU)
      .withBounds(576, 58, 192, 96)
      .children(
        item(2, 672),
        item(1, 576));

    NlModel model = model("menu.xml", menuDescriptor).build();
    NlComponent menuComponent = model.getComponents().get(0);
    NlComponent item = LayoutTestUtilities.createMockComponent();
    XmlTag tag = XmlTagUtil.createTag(getProject(), "<" + TAG_ITEM + "/>");
    NlComponentBackend backend = new NlComponentBackendXml(model.getProject(), tag);

    Mockito.when(item.getBackend()).thenReturn(backend);
    Mockito.when(item.getTagName()).thenReturn(TAG_ITEM);
    Mockito.when(item.getModel()).thenReturn(model);

    DragHandler handler = newGroupDragHandler(menuComponent, item);
    handler.update(350, 50, 0, SceneContext.get());
    handler.commit(700, 100, 0, InsertType.MOVE);

    Iterator<NlComponent> i = menuComponent.getChildren().iterator();

    assertEquals("3", i.next().getAndroidAttribute(ATTR_ORDER_IN_CATEGORY));
    assertEquals("1", i.next().getAndroidAttribute(ATTR_ORDER_IN_CATEGORY));

    Mockito.verify(item).setAndroidAttribute(ATTR_ORDER_IN_CATEGORY, "2");
  }

  public void testCommitNonconsecutiveOrders() {
    ComponentDescriptor menuDescriptor = component(TAG_MENU)
      .withBounds(480, 58, 288, 96)
      .children(
        item(30, 672),
        item(20, 576),
        item(10, 480));

    NlModel model = model("menu.xml", menuDescriptor).build();
    NlComponent menuComponent = model.getComponents().get(0);
    NlComponent item = LayoutTestUtilities.createMockComponent();
    XmlTag tag = XmlTagUtil.createTag(getProject(), "<" + TAG_ITEM + "/>");
    NlComponentBackend backend = new NlComponentBackendXml(model.getProject(), tag);

    Mockito.when(item.getBackend()).thenReturn(backend);
    Mockito.when(item.getTagName()).thenReturn(TAG_ITEM);
    Mockito.when(item.getModel()).thenReturn(model);

    DragHandler handler = newGroupDragHandler(menuComponent, item);
    handler.update(300, 50, 0, SceneContext.get());
    handler.commit(600, 100, 0, InsertType.MOVE);

    Iterator<NlComponent> i = menuComponent.getChildren().iterator();

    assertEquals("30", i.next().getAndroidAttribute(ATTR_ORDER_IN_CATEGORY));
    assertEquals("21", i.next().getAndroidAttribute(ATTR_ORDER_IN_CATEGORY));
    assertEquals("10", i.next().getAndroidAttribute(ATTR_ORDER_IN_CATEGORY));

    Mockito.verify(item).setAndroidAttribute(ATTR_ORDER_IN_CATEGORY, "20");
  }

  public void testCommitActiveItemIsSameAsDraggedItem() {
    ComponentDescriptor menuDescriptor = component(TAG_MENU)
      .withBounds(480, 58, 288, 96)
      .children(
        item(3, 672),
        item(2, 576),
        item(1, 480));

    NlComponent menuComponent = model("menu.xml", menuDescriptor).build().getComponents().get(0);

    NlComponent item = menuComponent.getChild(0);
    assert item != null;

    DragHandler handler = newGroupDragHandler(menuComponent, item);
    handler.update(370, 50, 16, SceneContext.get());
    handler.commit(740, 100, 16, InsertType.MOVE);

    Iterator<NlComponent> i = menuComponent.getChildren().iterator();

    assertEquals("3", i.next().getAndroidAttribute(ATTR_ORDER_IN_CATEGORY));
    assertEquals("2", i.next().getAndroidAttribute(ATTR_ORDER_IN_CATEGORY));
    assertEquals("1", i.next().getAndroidAttribute(ATTR_ORDER_IN_CATEGORY));
  }

  @NotNull
  private ComponentDescriptor item(int order, int x) {
    return component(TAG_ITEM)
      .withAttribute(ANDROID_URI, ATTR_ORDER_IN_CATEGORY, Integer.toString(order))
      .withAttribute(AUTO_URI, ATTR_SHOW_AS_ACTION, VALUE_ALWAYS)
      .withBounds(x, 58, 96, 96)
      .viewType(ViewType.ACTION_BAR_MENU);
  }

  @NotNull
  private static DragHandler newGroupDragHandler(@NotNull NlComponent menu, @NotNull NlComponent item) {
    SyncNlModel model = (SyncNlModel)menu.getModel();

    SyncLayoutlibSceneManager manager = NlModelBuilderUtil.getSyncLayoutlibSceneManagerForModel(model);
    manager.setIgnoreRenderRequests(true);
    Scene scene = manager.getScene();
    scene.buildDisplayList(new DisplayList(), 0);

    SceneComponent sceneComponent = scene.getSceneComponent(item);
    if (sceneComponent == null) {
      sceneComponent = manager.createTemporaryComponent(item);
    }
    List<NlComponent> itemAsList = Collections.singletonList(sceneComponent.getNlComponent());
    return new GroupDragHandler(mockViewEditor(model), new ViewGroupHandler(), scene.getSceneComponent(menu), itemAsList, DragType.MOVE);
  }

  @NotNull
  private static ViewEditor mockViewEditor(@NotNull NlModel model) {
    ViewEditor editor = Mockito.mock(ViewEditor.class);

    Mockito.when(editor.canInsertChildren(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyInt())).thenReturn(true);
    Mockito.when(editor.getModel()).thenReturn(model);

    return editor;
  }
}