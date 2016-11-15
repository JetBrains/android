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
package com.android.tools.sherpa.scout;

import junit.framework.TestCase;

import java.util.Arrays;

/**
 * This test the major methods of the Scout utils
 */
public class UtilsTest extends TestCase {
  public void testMax() {
    int max = Utils.max(new float[]{1, 2, 3, 4, 4, 2, 3});
    assertEquals(max, 3);
  }

  public void testMax2d() {
    float[][] array = new float[32][32];
    int[] result = new int[2];
    for (int i = 0; i < array.length; i++) {
      for (int j = 0; j < array[i].length; j++) {
        array[i][j] = 1 / (1 + (float)Math.hypot(i - 16, j - 16));
      }
    }
    float f = Utils.max(array, result);
    assertEquals(1.f, f);
    assertEquals(16, result[0]);
    assertEquals(16, result[1]);
  }

  public void testTOS() {
    float[] array = new float[3];

    for (int j = 0; j < array.length; j++) {
      array[j] = j;
    }
    String s = Utils.toS(array);
    assertEquals("[0.0     , 1.0     , 2.0    ]", s);
  }

  public void testLeftTrim() {
    String str = Utils.leftTrim("1234567", 4);
    assertEquals("4567", str);
  }

  public void testGaps() {
    int[] start = {50, 30, 40, 20, 10};
    int[] end = {55, 32, 43, 21, 13};
    int gaps = Utils.gaps(start, end);
    assertEquals(5, gaps);
  }

  public void testCells1() {
    int[] start = {50, 30, 40, 20, 10};
    int[] end = {55, 32, 43, 21, 13};
    int[] cells = Utils.cells(start, end);
    assertEquals(Arrays.toString(new int[]{10, 13, 20, 21, 30, 32, 40, 43, 50, 55}), Arrays.toString(cells));
  }

  public void testCells2() {
    int[] start = {50, 30, 40, 20, 10};
    int[] end = {55, 41, 43, 21, 13};
    int[] cells = Utils.cells(start, end);
    assertEquals(Arrays.toString(new int[]{10, 13, 20, 21, 30, 43, 50, 55}), Arrays.toString(cells));
  }

  public void testGetPosition() {
    int[] pos = {50, 55, 30, 36, 40, 45, 10, 20};
    int cell = Utils.getPosition(pos, 47, 48);
    assertEquals(-1, cell);
    cell = Utils.getPosition(pos, 31, 33);
    assertEquals(1, cell);
    cell = Utils.getPosition(pos, 29, 33);
    assertEquals(-1, cell);
  }

  public void testSortUnique() {
    int[] table1 = {50, 55, 30, 34, 40, 30, 10, 20}; // should remove 30
    int[] table2 = Utils.sortUnique(table1);
    assertEquals(Arrays.toString(new int[]{10, 20, 30, 34, 40, 50, 55}), Arrays.toString(table2));
    int[] table3 = {50, 55, 30, 34, 40, 10, 20}; // should remove 30
    table2 = Utils.sortUnique(table1);
    assertEquals(Arrays.toString(new int[]{10, 20, 30, 34, 40, 50, 55}), Arrays.toString(table2));
  }
}
