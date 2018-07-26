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
package com.android.tools.idea.resourceExplorer.sketchImporter.logic;

import java.awt.geom.Point2D;

public class StringPoint {

  private String coordX;
  private String coordY;

  public StringPoint(Point2D.Double point) {
    this(Double.toString(point.getX()), Double.toString(point.getY()));
  }

  public StringPoint(String x, String y) {
    coordX = x;
    coordY = y;
  }

  public StringPoint(Double x, Double y) {
    this(Double.toString(x), Double.toString(y));
  }

  public String getX() {
    return coordX;
  }

  public String getY() {
    return coordY;
  }
}
