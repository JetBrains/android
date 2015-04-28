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
package com.android.tools.idea.editors.theme;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.editors.theme.SeparatedList.group;

public class SeparatedListTest extends TestCase {
  public void testSeparatedListEmpty() {
    SeparatedList list = new SeparatedList("separator");

    assertEquals(0, list.size());
  }

  public void testSeparatedListSingle() {
    List<Integer> intList = Arrays.asList(1, 2, 3);

    SeparatedList list = new SeparatedList(0, group(intList));

    assertEquals(intList.size(), list.size());
    for (int i = 0; i < intList.size(); i++) {
      assertEquals(intList.get(i), list.get(i));
    }
  }

  public void testSeparatedListDouble() {
    List<Integer> intList1 = Arrays.asList(1, 2, 3);
    List<Integer> intList2 = Arrays.asList(4, 5, 6);

    SeparatedList list = new SeparatedList(0, group(intList1), group(intList2));

    assertEquals(intList1.size() + intList2.size() + 1, list.size());
    for (int i = 0; i < intList1.size(); i++) {
      assertEquals(intList1.get(i), list.get(i));
    }

    assertEquals(0, list.get(intList1.size()));

    for (int i = 0; i < intList2.size(); i++) {
      assertEquals(intList2.get(i), list.get(intList1.size() + 1 + i));
    }
  }

  public void testSeparatedListWithEmpty() {
    List<Integer> intList = Arrays.asList(1, 2, 3);

    SeparatedList list = new SeparatedList(0, group(Collections.emptyList()), group(intList));

    assertEquals(list.size(), intList.size());
    for (int i = 0; i < intList.size(); i++) {
      assertEquals(intList.get(i), list.get(i));
    }
  }
}
