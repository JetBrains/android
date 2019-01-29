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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * Represents the possible connection points for a widget in each of its directions
 */
public class WidgetPossibleConnections {

  private ScoutWidget myWidget;
  public ArrayList<Connection> north;
  public ArrayList<Connection> south;
  public ArrayList<Connection> east;
  public ArrayList<Connection> west;
  public ArrayList<Connection> baseline;

  /**
   * Constructor for possible connections
   *
   * @param widget widget for which all the connections will be calculated
   */
  public WidgetPossibleConnections(ScoutWidget widget) {
    myWidget = widget;
    this.north = new ArrayList<Connection>();
    this.south = new ArrayList<Connection>();
    this.east = new ArrayList<Connection>();
    this.west = new ArrayList<Connection>();
    this.baseline = new ArrayList<Connection>();
  }

  ScoutWidget getWidget() {
    return myWidget;
  }

  public ArrayList<Connection> getList(Direction dir) {
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
   * Generate the possible anchor points to which the widget can get connected.
   * These connections can be to other widgets, parent or baselines. Already existing
   * connections are taken into account as the only possibility for their anchor point.
   *
   * @param otherWidgets the rest of widgets in the layout that the current widget can connect to
   */
  public void generateAllConnections(ScoutWidget[] otherWidgets) {
    Connection tempConnection;
    for (Direction anchor : Direction.getAllDirections()) {
      /* if there is a preexisting connection, preserve it */
      if (myWidget.getAnchor(anchor).isConnected()) {
        ScoutWidget.Anchor existing = myWidget.getAnchor(anchor);
        ScoutWidget targetWidget = existing.getTarget().getOwner();
        int targetIndex = Arrays.asList(otherWidgets).indexOf(targetWidget) - 1;
        Direction destAnchor = existing.getTarget().myDirection;
        Connection preservedConnection = new Connection(targetIndex, anchor, destAnchor, targetWidget);
        getList(anchor).add(preservedConnection);
        continue;
      }
      for (int i = 1; i < otherWidgets.length; i++) {
        ScoutWidget destWidget = otherWidgets[i];
        if (myWidget == destWidget) {
          continue;
        }

        if ((anchor == Direction.LEFT || anchor == Direction.RIGHT) &&
            destWidget.isHorizontalGuideline()) {
          continue;
        }

        if ((anchor == Direction.TOP || anchor == Direction.BOTTOM) &&
            destWidget.isVerticalGuideline()) {
          continue;
        }

        if (anchor == Direction.BASELINE && destWidget.isGuideline()) {
          continue;
        }

        int baselineDiff = Math.abs(destWidget.getPos(anchor) - myWidget.getPos(anchor));
        if (anchor == Direction.BASELINE && baselineDiff > 15) {
          continue;
        }

        double distanceX = myWidget.getRectangle().getCenterX() - destWidget.getRectangle().getCenterX();
        double distanceY = myWidget.getRectangle().getCenterY() - destWidget.getRectangle().getCenterY();
        int originLine = myWidget.getPos(anchor);

        int destLine = destWidget.getPos(anchor);
        tempConnection = new Connection(i - 1, anchor, anchor, destWidget);
        tempConnection.setMargin(destLine - originLine);
        tempConnection.setDistanceX(distanceX);
        tempConnection.setDistanceY(distanceY);
        tempConnection.calculateCost();
        getList(anchor).add(tempConnection);

        if (anchor != Direction.BASELINE && !destWidget.isGuideline()) {
          destLine = destWidget.getPos(anchor.getOpposite());
          tempConnection = new Connection(i - 1, anchor, anchor.getOpposite(), destWidget);
          tempConnection.setMargin(destLine - originLine);
          tempConnection.setDistanceX(distanceX);
          tempConnection.setDistanceY(distanceY);
          tempConnection.calculateCost();
          getList(anchor).add(tempConnection);
        }
      }

      /* Add connections to parent and null */
      if (anchor != Direction.BASELINE) {
        ScoutWidget parent = otherWidgets[0];
        int originLine = myWidget.getPos(anchor);
        int destLine = parent.getPos(anchor);
        tempConnection = new Connection(Connection.PARENT_CONNECTION, anchor, anchor, parent);
        tempConnection.setMargin(destLine - originLine);
        tempConnection.calculateCost();
        getList(anchor).add(tempConnection);
      }
      tempConnection = new Connection(Connection.NO_CONNECTION, anchor, anchor, null);
      getList(anchor).add(tempConnection);
    }
    sortConnections();
  }

  /**
   * Sorts the potential connections in the widget based on cost of the connection
   */
  void sortConnections() {
    Collections.sort(north, (a, b) -> b.compareTo(a));
    Collections.sort(south, (a, b) -> b.compareTo(a));
    Collections.sort(east, (a, b) -> b.compareTo(a));
    Collections.sort(west, (a, b) -> b.compareTo(a));
    Collections.sort(baseline, (a, b) -> b.compareTo(a));
  }

  public String toString() {
    String northS = "";
    for (Connection conn : north) {
      northS += conn.toString();
      northS += " ";
    }

    String southS = "";
    for (Connection conn : south) {
      southS += conn.toString();
      southS += " ";
    }

    String eastS = "";
    for (Connection conn : east) {
      eastS += conn.toString();
      eastS += " ";
    }

    String westS = "";
    for (Connection conn : west) {
      westS += conn.toString();
      westS += " ";
    }

    String baselineS = "";
    for (Connection conn : baseline) {
      baselineS += conn.toString();
      baselineS += " ";
    }

    String result = String
      .format("%s\nNORTH: %s\nSOUTH: %s\nEAST: %s\nWEST: %s\nBASELINE: %s\n", myWidget.toString(), northS, southS, eastS, westS,
              baselineS);
    return result;
  }
}