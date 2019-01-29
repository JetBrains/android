/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.idea.uibuilder.scout;

/**
 * Represents connections between constrained widgets
 */
public class Connection {

  public final static int PARENT_CONNECTION = -1;
  public final static int NO_CONNECTION = -2;

  private ConstrainedWidget myOriginWidget;
  private int myDestWidget;
  private ScoutWidget myDestRect;
  private Direction myOriginAnchor;
  private Direction myDestAnchor;
  private int myMargin;
  private double myDistanceX;
  private double myDistanceY;
  private double myCost;

  /**
   * Constructor for a Connection
   *
   * @param destWidget   index of destination widget within the constraint set
   * @param originAnchor the source direction of the connection
   * @param destAnchor   the destination direction of the connection
   * @param destRect     the ScoutWidget object that represents the destination ScoutWidget
   */
  public Connection(int destWidget, Direction originAnchor, Direction destAnchor, ScoutWidget destRect) {
    myOriginAnchor = originAnchor;
    myDestRect = destRect;
    myDestWidget = destWidget;
    myDestAnchor = destAnchor;
    myMargin = 0;
  }

  public String toString() {
    if (myDestWidget == PARENT_CONNECTION) {
      return "P";
    }
    if (myDestWidget == NO_CONNECTION) {
      return "NULL";
    }
    String result = Integer.toString(myDestWidget);
    result += myDestAnchor.toString();
    return result;
  }

  /**
   * Comparison method for sorting purposes, based on the cost of the connection
   */
  public int compareTo(Connection other) {
    int val = myCost < other.getCost() ? 1 : myCost == other.getCost() ? 0 : -1;
    return val;
  }

  public void setMargin(int margin) {
    myMargin = margin;
  }

  public int getMargin() {
    return myMargin;
  }

  public int getAbsoluteMargin() {
    return Math.abs(myMargin);
  }

  public void setOriginWidget(ConstrainedWidget widget) {
    myOriginWidget = widget;
  }

  public Direction originDirection() {
    return myDestAnchor;
  }

  public ConstrainedWidget getOriginWidget() {
    return myOriginWidget;
  }

  public int destWidget() {
    return myDestWidget;
  }

  public Direction destDirection() {
    return myDestAnchor;
  }

  public void setDistanceX(double distanceX) {
    myDistanceX = distanceX;
  }

  public void setDistanceY(double distanceY) {
    myDistanceY = distanceY;
  }

  public double calculateCost() {
    myCost = (myDistanceX * myDistanceX) + (myDistanceY * myDistanceY);
    myCost += (myMargin * myMargin);
    if (isParentConnection()) {
      myCost += myMargin * myMargin;
    }
    return myCost;
  }

  public double getCost() {
    return myCost;
  }

  public boolean isConnected() {
    return !(myDestWidget == NO_CONNECTION);
  }

  public boolean isParentConnection() {
    return myDestWidget == PARENT_CONNECTION;
  }

  /**
   * Creates a display list string to render in tests
   *
   * @return String
   */
  public String getDisplayString() {
    String res =
      String.format("%dx%dx%dx%d,%d",
                    (int)myDestRect.getX(),
                    (int)myDestRect.getY(),
                    myDestRect.getWidthInt(),
                    myDestRect.getHeightInt(),
                    equivalentDirection(myDestAnchor));
    return res;
  }

  /**
   * Translates a Direction value into the integer required to build a display
   * string
   *
   * @param Direction anchor
   * @return int
   */
  int equivalentDirection(Direction anchor) {
    switch (anchor) {
      case TOP:
        return 2;
      case BOTTOM:
        return 3;
      case RIGHT:
        return 1;
      case LEFT:
        return 0;
      case BASELINE:
        return 0;
    }
    return 0;
  }
}
