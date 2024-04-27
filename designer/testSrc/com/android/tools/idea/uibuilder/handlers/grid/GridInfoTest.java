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
package com.android.tools.idea.uibuilder.handlers.grid;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public final class GridInfoTest {
  @Test
  public void initLineLocations() {
    int[] actual = GridInfo.initLineLocations(718, 496, new int[]{0, 248, 718});
    assertArrayEquals(new int[]{0, 248, 496, 528, 560, 592, 624, 656, 688, 717}, actual);
  }

  @Test
  public void initLineLocationsDifferenceModNewCellSizeEqualsZero() {
    int[] actual = GridInfo.initLineLocations(718, 526, new int[]{0, 248, 718});
    assertArrayEquals(new int[]{0, 248, 526, 558, 590, 622, 654, 686, 717}, actual);
  }

  @Test
  public void getIndex() {
    final int[] lineLocatoin = {100, 248, 248, 248, 248, 248, 248, 248, 248, 520, 767};

    assertEquals(1, GridInfo.getIndex(lineLocatoin, 380, true));
    assertEquals(2, GridInfo.getIndex(lineLocatoin, 600, true));
    assertEquals(8, GridInfo.getIndex(lineLocatoin, 380, false));
    assertEquals(-1, GridInfo.getIndex(lineLocatoin, 50, true));
    assertEquals(-1, GridInfo.getIndex(lineLocatoin, 800, true));
  }
}
