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
package com.android.tools.idea.uibuilder.handlers;

import com.android.tools.idea.uibuilder.model.Insets;
import com.android.tools.idea.uibuilder.model.NlComponent;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.Arrays;

final class GridInfo {
  private static final int NEW_CELL_SIZE = 32;

  private final NlComponent layout;
  final int[] verticalLineLocations;
  final int[] horizontalLineLocations;

  GridInfo(NlComponent layout) {
    this.layout = layout;

    try {
      Insets padding = layout.getPadding();
      Dimension size = getSize();

      int[] horizontalAxisLocations = getAxisLocations("mHorizontalAxis", "horizontalAxis");
      verticalLineLocations = initLineLocations(layout.w - padding.width(), size.width, horizontalAxisLocations);

      int[] verticalAxisLocations = getAxisLocations("mVerticalAxis", "verticalAxis");
      horizontalLineLocations = initLineLocations(layout.h - padding.height(), size.height, verticalAxisLocations);
    }
    catch (NoSuchFieldException exception) {
      throw new IllegalArgumentException(exception);
    }
    catch (IllegalAccessException exception) {
      throw new IllegalArgumentException(exception);
    }
  }

  private Dimension getSize() {
    Dimension size = new Dimension();

    if (layout.children != null) {
      Insets padding = layout.getPadding();

      for (NlComponent child : layout.children) {
        size.width = Math.max(child.x - layout.x - padding.left + child.w, size.width);
        size.height = Math.max(child.y - layout.y - padding.top + child.h, size.height);
      }
    }

    return size;
  }

  private int[] getAxisLocations(String name1, String name2) throws NoSuchFieldException, IllegalAccessException {
    assert layout.viewInfo != null;
    Object view = layout.viewInfo.getViewObject();

    Field axisField = getDeclaredField(view.getClass(), name1, name2);
    axisField.setAccessible(true);

    Object axis = axisField.get(view);

    Field locationsField = axis.getClass().getDeclaredField("locations");
    locationsField.setAccessible(true);

    return (int[])locationsField.get(axis);
  }

  private static Field getDeclaredField(Class<?> c, String name1, String name2) throws NoSuchFieldException {
    try {
      return c.getDeclaredField(name1);
    }
    catch (NoSuchFieldException exception) {
      return c.getDeclaredField(name2);
    }
  }

  private static int[] initLineLocations(int layoutSize, int gridInfoSize, int[] axisLocations) {
    int difference = layoutSize - gridInfoSize;
    int lineLocationsLength = difference > 0 ? axisLocations.length + difference / NEW_CELL_SIZE : axisLocations.length;

    int startIndex;
    int[] lineLocations = Arrays.copyOf(axisLocations, lineLocationsLength);

    if (axisLocations.length == 0) {
      startIndex = 1;
    }
    else {
      lineLocations[axisLocations.length - 1] = gridInfoSize;
      startIndex = axisLocations.length;
    }

    for (int i = startIndex; i < lineLocationsLength; i++) {
      lineLocations[i] = lineLocations[i - 1] + NEW_CELL_SIZE;
    }

    return lineLocations;
  }
}
