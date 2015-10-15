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

import java.lang.reflect.Field;
import java.util.Formatter;

final class ChildInfo {
  private final int row1;
  private final int column1;
  private final int row2;
  private final int column2;

  ChildInfo(Object rowSpecSpan, Object columnSpecSpan) throws NoSuchFieldException, IllegalAccessException {
    Class<?> interval = rowSpecSpan.getClass();

    Field min = interval.getDeclaredField("min");
    min.setAccessible(true);

    row1 = min.getInt(rowSpecSpan);
    column1 = min.getInt(columnSpecSpan);

    Field max = interval.getDeclaredField("max");
    max.setAccessible(true);

    row2 = max.getInt(rowSpecSpan);
    column2 = max.getInt(columnSpecSpan);
  }

  int getRow1() {
    return row1;
  }

  int getColumn1() {
    return column1;
  }

  int getRow2() {
    return row2;
  }

  int getColumn2() {
    return column2;
  }

  @Override
  public String toString() {
    Formatter formatter = new Formatter();

    formatter.format("row1: %s%n", row1);
    formatter.format("column1: %s%n", column1);
    formatter.format("row2: %s%n", row2);
    formatter.format("column2: %s%n", column2);

    return formatter.toString();
  }
}
