/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.sketchImporter;

import com.android.tools.idea.resourceExplorer.sketchImporter.logic.StringPoint;
import org.junit.Test;

import java.awt.*;
import java.util.ArrayList;

import static com.android.tools.idea.resourceExplorer.sketchImporter.logic.CoordinateUtils.calculateStringFrameCorners;
import static org.junit.Assert.assertEquals;

public class CoordinateUtilsTest {

  @Test
  public void calculateStringFrameCornersTest() {
    ArrayList<StringPoint> corners = new ArrayList<>();

    corners.add(new StringPoint("1.0", "5.0"));
    corners.add(new StringPoint("21.0", "5.0"));
    corners.add(new StringPoint("21.0", "15.0"));
    corners.add(new StringPoint("1.0", "15.0"));

    Rectangle.Double shapeGroupFrame = new Rectangle.Double(1, 5, 20, 10);
    Rectangle.Double frame = new Rectangle.Double(0, 0, 20, 10);

    ArrayList<StringPoint> resultCorners = calculateStringFrameCorners(frame, shapeGroupFrame);

    for (int i = 0; i < resultCorners.size(); i++) {
      assertEquals(corners.get(i).getX(), resultCorners.get(i).getX());
      assertEquals(corners.get(i).getY(), resultCorners.get(i).getY());
    }
  }
}
