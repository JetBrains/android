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
package com.android.tools.idea.monitor.memory;

import com.android.tools.idea.monitor.memory.CircularArrayList;
import junit.framework.TestCase;

public class CircularArrayListTest extends TestCase {

  private CircularArrayList<Integer> myList;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myList = new CircularArrayList<Integer>(3);
    myList.add(0);
    myList.add(1);
  }

  public void testGetAndAdd() throws Exception {
    assertEquals(0, myList.get(0).intValue());
    assertEquals(1, myList.get(1).intValue());

    try {
      myList.get(2);
      fail("IndexOutOfBoundsException expected");
    }
    catch (IndexOutOfBoundsException e) {
      // Expected.
    }

    myList.add(2);
    myList.add(3);

    assertEquals(1, myList.get(0).intValue());
    assertEquals(2, myList.get(1).intValue());
    assertEquals(3, myList.get(2).intValue());
  }

  public void testSize() throws Exception {
    assertEquals(2, myList.size());
    myList.add(2);
    assertEquals(3, myList.size());

    myList.add(3);
    assertEquals(3, myList.size());
  }

  public void testClear() throws Exception {
    assertEquals(2, myList.size());

    myList.clear();

    assertEquals(0, myList.size());
  }
}