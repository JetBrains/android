/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.adtui;

import junit.framework.TestCase;

public class CircularArrayListTest extends TestCase {

  private CircularArrayList<Integer> mList;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mList = new CircularArrayList<Integer>(3);
    mList.add(0);
    mList.add(1);
  }

  public void testGetAndAdd() throws Exception {
    assertEquals(0, mList.get(0).intValue());
    assertEquals(1, mList.get(1).intValue());

    try {
      mList.get(-1);
      fail("IndexOutOfBoundsException expected");
    }
    catch (IndexOutOfBoundsException e) {
      // Expected.
    }

    try {
      mList.get(2);
      fail("IndexOutOfBoundsException expected");
    }
    catch (IndexOutOfBoundsException e) {
      // Expected.
    }

    mList.add(2);
    mList.add(3);

    assertEquals(1, mList.get(0).intValue());
    assertEquals(2, mList.get(1).intValue());
    assertEquals(3, mList.get(2).intValue());
  }

  public void testSize() throws Exception {
    assertEquals(2, mList.size());
    mList.add(2);
    assertEquals(3, mList.size());

    mList.add(3);
    assertEquals(3, mList.size());
  }

  public void testClear() throws Exception {
    assertEquals(2, mList.size());

    mList.clear();

    assertEquals(0, mList.size());
  }
}