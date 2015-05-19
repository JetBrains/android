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

import junit.framework.TestCase;

import java.awt.*;

public class InsetsTest extends TestCase {
  public void test() {
    Insets insets = new Insets(1, 2, 3, 4);
    assertEquals(1, insets.left);
    assertEquals(2, insets.top);
    assertEquals(3, insets.right);
    assertEquals(4, insets.bottom);

    assertEquals(4, insets.width());
    assertEquals(6, insets.height());

    assertTrue(new Insets(1, 2, 3, 4).equals(new Insets(1, 2, 3, 4)));

    assertTrue(Insets.NONE.isEmpty());
    assertTrue(new Insets(0, 0, 0, 0).isEmpty());
    assertFalse(new Insets(1, 0, 0, 0).isEmpty());
    assertFalse(new Insets(0, 1, 0, 0).isEmpty());
    assertFalse(new Insets(0, 0, 1, 0).isEmpty());
    assertFalse(new Insets(0, 0, 0, 1).isEmpty());

    Rectangle bounds = new Rectangle(10, 10, 10, 10);
    new Insets(1, 2, 3, 4).subtractFrom(bounds);
    assertEquals(new Rectangle(9, 8, 14, 16), bounds);

    bounds = new Rectangle(10, 10, 10, 10);
    new Insets(1, 2, 3, 4).addTo(bounds);
    assertEquals(new Rectangle(11, 12, 6, 4), bounds);
  }
}