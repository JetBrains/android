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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

public class CircularArrayListTest {

  private CircularArrayList<Integer> mList;

  @Before
  public void setUp() throws Exception {
    mList = new CircularArrayList<>(3);
    mList.add(0);
    mList.add(1);
  }

  @Test
  public void testGetAndAdd() throws Exception {
    assertThat(mList.get(0)).isEqualTo(0);
    assertThat(mList.get(1)).isEqualTo(1);

    mList.add(2);
    mList.add(3);

    assertThat(mList.get(0)).isEqualTo(1);
    assertThat(mList.get(1)).isEqualTo(2);
    assertThat(mList.get(2)).isEqualTo(3);
  }

  @Test
  public void testGetIndexOutOfBounds() {
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
  }

  @Test
  public void testSize() throws Exception {
    assertThat(mList.size()).isEqualTo(2);
    mList.add(2);
    assertThat(mList.size()).isEqualTo(3);

    mList.add(3);
    assertThat(mList.size()).isEqualTo(3);
  }

  @Test
  public void testClear() throws Exception {
    assertThat(mList.size()).isEqualTo(2);

    mList.clear();

    assertThat(mList).isEmpty();
  }
}