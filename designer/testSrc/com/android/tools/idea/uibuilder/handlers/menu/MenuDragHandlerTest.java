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
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public final class MenuDragHandlerTest {
  @Test
  public void update() {
    NlComponent menu = newNlComponent(366, 162, 392, 288);

    menu.addChild(newNlComponent(366, 162, 392, 96));
    menu.addChild(newNlComponent(366, 258, 392, 96));
    menu.addChild(newNlComponent(366, 354, 392, 96));

    DragHandler handler = newMenuDragHandler(menu);
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
  public void updateEmptyGroupInFront() {
    NlComponent menu = newNlComponent(366, 162, 392, 192);

    menu.addChild(newNlComponent(0, 0, -1, -1));
    menu.addChild(newNlComponent(366, 162, 392, 96));
    menu.addChild(newNlComponent(366, 258, 392, 96));

    DragHandler handler = newMenuDragHandler(menu);
    int y = 90;

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
  public void updateEmptyGroupInMiddle() {
    NlComponent menu = newNlComponent(366, 162, 392, 192);

    menu.addChild(newNlComponent(366, 162, 392, 96));
    menu.addChild(newNlComponent(0, 0, -1, -1));
    menu.addChild(newNlComponent(366, 258, 392, 96));

    DragHandler handler = newMenuDragHandler(menu);
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
  public void updateEmptyGroupInBack() {
    NlComponent menu = newNlComponent(366, 162, 392, 192);

    menu.addChild(newNlComponent(366, 162, 392, 96));
    menu.addChild(newNlComponent(366, 258, 392, 96));
    menu.addChild(newNlComponent(0, 0, -1, -1));

    DragHandler handler = newMenuDragHandler(menu);
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
  }

  @NotNull
  private static NlComponent newNlComponent(int x, int y, int width, int height) {
    NlComponent component = new NlComponent(Mockito.mock(NlModel.class), Mockito.mock(XmlTag.class));
    component.setBounds(x, y, width, height);

    return component;
  }

  @NotNull
  private static DragHandler newMenuDragHandler(@NotNull NlComponent menu) {
    return new MenuDragHandler(Mockito.mock(ViewEditor.class), new ViewGroupHandler(), menu, Collections.emptyList(), DragType.CREATE);
  }
}
