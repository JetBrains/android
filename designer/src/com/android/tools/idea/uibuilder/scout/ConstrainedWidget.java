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
 * Represents a widget with connections, a constrained widget.
 */
public class ConstrainedWidget {
  private ScoutWidget myScoutWidget;
  public Connection north;
  public Connection south;
  public Connection east;
  public Connection west;
  public Connection baseline;
  private double myCost;
  private double errorCenterFactor = 8;

  /**
   * Constructor for a ConstrainedWidget that only takes the scout widget as parameter,
   * used mostly for representation of guidelines.
   *
   * @param sWidget ScoutWidget represented with the ConstrainedWidget instance
   */
  public ConstrainedWidget(ScoutWidget sWidget) {
    myScoutWidget = sWidget;
  }

  /**
   * Constructor for a ConstrainedWidget that takes all connections and widget as parameters.
   * Sets the cost of this constrained widget as the summation of the cost of its connections.
   *
   * @param north   connection object for the north anchor (idem for south, east, west and baseline)
   * @param sWidget ScoutWidget that is represented with this ConstrainedWidget
   */
  public ConstrainedWidget(
    Connection north,
    Connection south,
    Connection east,
    Connection west,
    Connection baseline,
    ScoutWidget sWidget) {
    myScoutWidget = sWidget;
    this.north = north;
    this.north.setOriginWidget(this);
    this.south = south;
    this.south.setOriginWidget(this);
    this.east = east;
    this.east.setOriginWidget(this);
    this.west = west;
    this.west.setOriginWidget(this);
    this.baseline = baseline;
    this.baseline.setOriginWidget(this);
    myCost = north.getCost() +
             south.getCost() +
             east.getCost() +
             west.getCost() +
             baseline.getCost();
  }

  @Override
  public String toString() {
    String res;

    if (myScoutWidget.isVerticalGuideline()) {
      res = "V GUIDELINE";
    }
    else if (myScoutWidget.isHorizontalGuideline()) {
      res = "H GUIDELINE";
    }
    else {
      res = String.format("%s %s %s %s %s %f",
                          north.toString(),
                          south.toString(),
                          east.toString(),
                          west.toString(),
                          baseline.toString(),
                          myCost);
    }
    return res;
  }

  public double getCost() {
    return myCost;
  }

  int compareTo(ConstrainedWidget other) {
    int val = myCost < other.getCost() ? 1 : myCost == other.getCost() ? 0 : -1;
    return val;
  }

  public ScoutWidget getScoutWidget() {
    return myScoutWidget;
  }

  /**
   * A backwards connection depends on its orientation, for north and west,
   * a positive margin is backwards. For south and east negative is backwards.
   *
   * @param direction The direction of the anchor that is being queried
   * @return boolean that indicates if the connection is backwards
   */
  public boolean isBackwardsConnection(Direction anchor) {
    if (anchor == Direction.TOP || anchor == Direction.LEFT) {
      if (getConnection(anchor).getMargin() > 0 &&
          getConnection(anchor).isConnected()) {
        return true;
      }
    }
    if (anchor == Direction.BOTTOM || anchor == Direction.RIGHT) {
      if (getConnection(anchor).getMargin() < 0 &&
          getConnection(anchor).isConnected()) {
        return true;
      }
    }
    return false;
  }

  public Connection getConnection(Direction dir) {
    switch (dir) {
      case TOP:
        return north;
      case LEFT:
        return west;
      case RIGHT:
        return east;
      case BOTTOM:
        return south;
      case BASELINE:
        return baseline;
    }
    return baseline;
  }

  /**
   * For a widget with both sides in the orientation connected,
   * calculates the bias for the centered connection in said orientation.
   *
   * @param orientation if the required bias is horizontal or vertical
   * @return double bias
   */
  public double calculateBias(int orientation) {
    double a = (double)getConnection(Direction.getDirections(orientation)[0]).getAbsoluteMargin();
    double b = (double)getConnection(Direction.getDirections(orientation)[1]).getAbsoluteMargin();
    double bias;
    if (a == 0 && b == 0) {
      bias = 0.5;
    }
    else if (a == 0) {
      bias = 0.0;
    }
    else if (b == 0) {
      bias = 1.0;
    }
    else {
      bias = a / (a + b);
    }
    return bias;
  }

  /**
   * Error associated with a centered connected widget
   *
   * @param orientation if the required error is horizontal or vertical
   * @return double error
   */
  public double getCenterError(int orientation) {
    double a = (double)getConnection(Direction.getDirections(orientation)[0]).getAbsoluteMargin();
    double b = (double)getConnection(Direction.getDirections(orientation)[1]).getAbsoluteMargin();
    double size = orientation == Direction.ORIENTATION_HORIZONTAL ?
                  myScoutWidget.getWidth() :
                  myScoutWidget.getHeight();
    double error = Math.abs(0.5 - calculateBias(orientation));
    if (!getConnection(Direction.getDirections(orientation)[0]).isParentConnection()
        || !getConnection(Direction.getDirections(orientation)[1]).isParentConnection()) {
      error *= 2;
    }
    error *= error * (a + b + size) * errorCenterFactor;
    return error;
  }

  /**
   * Returns the number of anchors that have connections for the widget
   * (max 5)
   *
   * @return
   */
  public int numberOfConnections() {
    int connections = 0;
    for (Direction dir : Direction.getAllDirections()) {
      if (getConnection(dir).isConnected()) {
        connections++;
      }
    }
    return connections;
  }
}