/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.model;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.LayoutTestUtilities;
import junit.framework.TestCase;

import java.awt.*;

import static org.mockito.Mockito.mock;

public class SelectionHandleTest extends TestCase {
  public void test() {
    NlComponent component = LayoutTestUtilities.createMockComponent();
    NlComponentHelperKt.setX(component, 100);
    NlComponentHelperKt.setY(component, 110);
    NlComponentHelperKt.setW(component, 500);
    NlComponentHelperKt.setH(component, 400);

    SelectionHandle handle = new SelectionHandle(component, SelectionHandle.Position.TOP_LEFT);
    assertTrue(handle.contains(100, 110, 2));
    assertFalse(handle.contains(200, 200, 2));
    assertEquals(100, handle.getCenterX());
    assertEquals(110, handle.getCenterY());
    assertEquals(Cursor.NW_RESIZE_CURSOR, handle.getAwtCursorType());

    handle = new SelectionHandle(component, SelectionHandle.Position.TOP_MIDDLE);
    assertTrue(handle.contains(100 + 500 / 2, 110, 2));
    assertFalse(handle.contains(100 + 500 / 2, 110 + 5, 2));
    assertFalse(handle.contains(100 + 500 / 2 + 5, 110, 2));
    assertFalse(handle.contains(100, 110, 2));
    assertEquals(100 + 500 / 2, handle.getCenterX());
    assertEquals(110, handle.getCenterY());
    assertEquals(Cursor.N_RESIZE_CURSOR, handle.getAwtCursorType());

    handle = new SelectionHandle(component, SelectionHandle.Position.TOP_RIGHT);
    assertTrue(handle.contains(100 + 500, 110, 2));
    handle = new SelectionHandle(component, SelectionHandle.Position.RIGHT_MIDDLE);
    assertTrue(handle.contains(100 + 500, 110 + 400 / 2, 2));
    handle = new SelectionHandle(component, SelectionHandle.Position.BOTTOM_RIGHT);
    assertTrue(handle.contains(100 + 500, 110 + 400, 2));
    handle = new SelectionHandle(component, SelectionHandle.Position.BOTTOM_MIDDLE);
    assertTrue(handle.contains(100 + 500 / 2, 110 + 400, 2));
    handle = new SelectionHandle(component, SelectionHandle.Position.BOTTOM_LEFT);
    assertTrue(handle.contains(100, 110 + 400, 2));
    handle = new SelectionHandle(component, SelectionHandle.Position.LEFT_MIDDLE);
    assertTrue(handle.contains(100, 110 + 400 / 2, 2));
  }
}