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
import java.util.Stack;


/**
 * Class to represent a group of constrained widgets that make a valid constraint set.
 */
public class ConstraintSet {
  private ArrayList<ConstrainedWidget> myWidgets;
  private ArrayList<Chain> myChains;
  private ArrayList<Connection> myChainConnnections;
  private double myError;
  private double connectionWeight = 5;


  /**
   * Builds a constraint set based of constrained widgets
   *
   * @param cWidgets   indices of the constrained widgets to be used for this set
   * @param validConns list of all valid connected constrained widgets
   * @param parent     parent scout widget
   */
  public ConstraintSet(int[] cWidgets, ArrayList<ArrayList<ConstrainedWidget>> validConns, ScoutWidget parent) {
    myWidgets = new ArrayList<ConstrainedWidget>();
    myChainConnnections = new ArrayList<Connection>();
    for (int i = 0; i < validConns.size(); i++) {
      ConstrainedWidget widget = validConns.get(i).get(cWidgets[i]);
      myWidgets.add(widget);
    }
  }

  /**
   * Builds a constraint set from widgets, used when building
   * from the already constrained set in the editor
   *
   * @param widgets list of already constrained scout widgets
   */
  public ConstraintSet(ScoutWidget[] widgets) {
    myChainConnnections = new ArrayList<>();
    myWidgets = new ArrayList<>();
    ConstrainedWidget temp;
    Connection[] connections = new Connection[5];
    for (int i = 1; i < widgets.length; i++) {
      ScoutWidget wid = widgets[i];
      for (Direction dir : Direction.getAllDirections()) {
        if (wid.getAnchor(dir).isConnected()) {
          ScoutWidget destWid = wid.getAnchor(dir).getTarget().getOwner();
          Direction destDir = wid.getAnchor(dir).getTarget().getType();
          int destIdx = 0;
          for (int j = 0; j < widgets.length; j++) {
            if (widgets[j] == destWid) {
              destIdx = j - 1;
            }
          }
          if (destIdx == 0) {
            connections[dir.ordinal()] = new Connection(Connection.PARENT_CONNECTION, dir, destDir, destWid);
          }
          else {
            connections[dir.ordinal()] = new Connection(destIdx, dir, destDir, destWid);
          }
          int originLine = wid.getPos(dir);
          int destLine = destWid.getPos(destDir);
          connections[dir.ordinal()].setMargin(destLine - originLine);
        }
        else {
          connections[dir.ordinal()] = new Connection(Connection.NO_CONNECTION, dir, dir, wid);
        }
      }
      temp = new ConstrainedWidget(connections[Direction.TOP.ordinal()],
                                   connections[Direction.BOTTOM.ordinal()],
                                   connections[Direction.RIGHT.ordinal()],
                                   connections[Direction.LEFT.ordinal()],
                                   connections[Direction.BASELINE.ordinal()],
                                   wid);
      myWidgets.add(temp);
    }
  }

  /**
   * Searches for cycles that occur between the constrained widgets in the constraint set
   * and returns a boolean
   *
   * @return
   */
  boolean hasCycles() {
    boolean hasCycles = false;
    ArrayList<ConstrainedWidget> remaining = new ArrayList<ConstrainedWidget>(myWidgets);
    Stack<ConstrainedWidget> visited = new Stack<ConstrainedWidget>();
    while (remaining.size() != 0 && !hasCycles) {
      hasCycles |= searchCycles(remaining, visited, remaining.get(0), Direction.ORIENTATION_VERTICAL);
    }
    remaining.addAll(myWidgets);
    visited.empty();
    while (remaining.size() != 0 && !hasCycles) {
      hasCycles |= searchCycles(remaining, visited, remaining.get(0), Direction.ORIENTATION_HORIZONTAL);
    }
    return hasCycles;
  }

  /**
   * Helper recursive function for finding cycles in the constraint set,
   * returns true if it finds a cycle.
   *
   * @return
   */
  boolean searchCycles(ArrayList<ConstrainedWidget> remaining,
                       Stack<ConstrainedWidget> visited,
                       ConstrainedWidget current,
                       int orientation) {
    boolean cycle = false;
    for (ConstrainedWidget cWid : visited) {
      if (cWid.getScoutWidget() == current.getScoutWidget()) {
        return true;
      }
    }
    visited.add(current);
    if (!current.getScoutWidget().isGuideline()) {
      for (Direction dir : Direction.getDirections(orientation)) {
        if (!cycle &&
            !current.getConnection(dir).isParentConnection() &&
            current.getConnection(dir).isConnected()) {
          if (!formsChain(visited, dir)) {
            cycle |= searchCycles(remaining, visited, myWidgets.get(current.getConnection(dir).destWidget()), orientation);
          }
        }
      }
    }
    if (!cycle) {
      visited.pop();
      remaining.remove(current);
    }
    return cycle;
  }

  /**
   * For a stack of visited widgets and a direction, returns true if the next connection would form a chain.
   * Since chains are a form of valid cycle, they are skipped from the cycle checking.
   *
   * Chains look like -|w|~|w|...|w|~|w|-
   *
   * @return
   */
  public boolean formsChain(Stack<ConstrainedWidget> visited, Direction dir) {
    ConstrainedWidget current = visited.peek();
    // Baseline connections can't be part of chains
    if (dir == Direction.BASELINE) {
      return false;
    }
    if (visited.size() == 1) {
      return false;
    }
    if (myWidgets.get(current.getConnection(dir).destWidget()) != visited.get(visited.size() - 2)) {
      return false;
    }
    ConstrainedWidget looped = visited.get(visited.size() - 2);
    if (looped.getConnection(dir.getOpposite()).isParentConnection() ||
        !looped.getConnection(dir.getOpposite()).isConnected()) {
      return false;
    }
    if (myWidgets.get(looped.getConnection(dir.getOpposite()).destWidget()) != current) {
      return false;
    }
    if (dir == current.getConnection(dir).destDirection()) {
      return false;
    }
    if (dir.getOpposite() == looped.getConnection(dir.getOpposite()).destDirection()) {
      return false;
    }
    if (!current.getConnection(dir.getOpposite()).isConnected()) {
      return false;
    }
    if (!looped.getConnection(dir).isConnected()) {
      return false;
    }
    if (current.isBackwardsConnection(dir) || looped.isBackwardsConnection(dir.getOpposite())) {
      return false;
    }

    myChainConnnections.add(current.getConnection(dir));
    myChainConnnections.add(looped.getConnection(dir.getOpposite()));
    return true;
  }

  /**
   * Backwards connections are not valid in a constraint set unless they're part
   * of a centered connection. This method returns true if all backwards connections
   * are centered, false otherwise.
   *
   * @return
   */
  public boolean isValidCentered() {
    for (ConstrainedWidget wid : myWidgets) {
      for (Direction anchor : Direction.getAllDirections()) {
        // If there's a backwards connection, there needs to be an
        // opposite backwards connection to the same widget
        if (wid.isBackwardsConnection(anchor)) {
          if (!wid.isBackwardsConnection(anchor.getOpposite())) {
            return false;
          }
          if (wid.getConnection(anchor).destWidget() !=
              wid.getConnection(anchor.getOpposite()).destWidget()) {
            return false;
          }
        }
      }
    }
    return true;
  }

  /**
   * Returns true if the constraint set is free of cycles or invalid backwards connections
   *
   * @return
   */
  public boolean validate() {
    return !hasCycles() && isValidCentered();
  }

  @Override
  public String toString() {
    String str = "";
    for (ConstrainedWidget wid : myWidgets) {
      str += wid.toString();
      str += " -- ";
    }
    return str;
  }

  /**
   * Calculates the total error of the constraint set that adds all chain errors,
   * all centered connections and all margin connections.
   */
  void calculateError() {
    double[] error = new double[]{0, 0};
    createChains();
    for (Chain chain : myChains) {
      error[chain.orientation()] += chain.totalError();
    }

    for (ConstrainedWidget wid : myWidgets) {
      // Simple margin connection (vertical)
      if (!(wid.getConnection(Direction.TOP).isConnected() && wid.getConnection(Direction.BOTTOM).isConnected())) {
        error[Direction.ORIENTATION_VERTICAL] += Math.abs(wid.getConnection(Direction.TOP).getMargin() +
                                                          wid.getConnection(Direction.BOTTOM).getMargin());
      }
      else {
        // Centered connection
        if (!myChainConnnections.contains(wid.getConnection(Direction.TOP)) &&
            !myChainConnnections.contains(wid.getConnection(Direction.BOTTOM))) {
          error[Direction.ORIENTATION_VERTICAL] += wid.getCenterError(Direction.ORIENTATION_VERTICAL);
        }
      }

      // Simple margin connection (horizontal)
      if (!(wid.getConnection(Direction.LEFT).isConnected() && wid.getConnection(Direction.RIGHT).isConnected())) {
        error[Direction.ORIENTATION_HORIZONTAL] += Math.abs(wid.getConnection(Direction.LEFT).getMargin() +
                                                            wid.getConnection(Direction.RIGHT).getMargin());
      }
      else {
        // Centered connection
        if (!myChainConnnections.contains(wid.getConnection(Direction.LEFT)) &&
            !myChainConnnections.contains(wid.getConnection(Direction.RIGHT))) {
          error[Direction.ORIENTATION_HORIZONTAL] += wid.getCenterError(Direction.ORIENTATION_HORIZONTAL);
        }
      }
    }

    double connections = 0;
    for (ConstrainedWidget widget : myWidgets) {
      connections += widget.numberOfConnections();
    }

    myError = Arrays.stream(error).sum();
    myError += connectionWeight * connections;
  }

  /**
   * Initializes a list of Chain objects and constructs and adds every chain that
   * was detected in the searchCycles method.
   */
  public void createChains() {
    myChains = new ArrayList<>();
    if (myChainConnnections.size() == 0) {
      return;
    }
    // Find start of chains
    for (ConstrainedWidget wid : myWidgets) {
      if (!myChainConnnections.contains(wid.getConnection(Direction.LEFT)) &&
          myChainConnnections.contains(wid.getConnection(Direction.RIGHT))) {
        Chain hChainStart = new Chain(Direction.ORIENTATION_HORIZONTAL);
        hChainStart.addWidget(wid);
        myChains.add(hChainStart);
      }
      if (!myChainConnnections.contains(wid.getConnection(Direction.TOP)) &&
          myChainConnnections.contains(wid.getConnection(Direction.BOTTOM))) {
        Chain vChainStart = new Chain(Direction.ORIENTATION_VERTICAL);
        vChainStart.addWidget(wid);
        myChains.add(vChainStart);
      }
    }

    for (Chain chain : myChains) {
      ConstrainedWidget chained = chain.getGroup().get(0);
      Direction dir = Direction.getDirections(chain.orientation())[1];
      while (myChainConnnections.contains(chained.getConnection(dir))) {
        chain.addWidget(myWidgets.get(chained.getConnection(dir).destWidget()));
        chained = myWidgets.get(chained.getConnection(dir).destWidget());
      }
      chain.calculateChainError();
    }
  }

  public double error() {
    return myError;
  }

  /**
   * Class to represent a Chain in a constraint set, involving several widgets with
   * cycled connections and two closing widgets with finishing connections.
   */
  private class Chain {
    private int myOrientation;
    private ArrayList<ConstrainedWidget> myGroup;
    private int myTotalError;
    private int errorFactor = 5;

    /**
     * Constructor for chain of a certain orientation
     *
     * @param orientation
     */
    public Chain(int orientation) {
      myOrientation = orientation;
      myGroup = new ArrayList<>();
    }

    public int orientation() {
      return myOrientation;
    }

    public void addWidget(ConstrainedWidget wid) {
      myGroup.add(wid);
    }

    public ArrayList<ConstrainedWidget> getGroup() {
      return myGroup;
    }

    public int totalError() {
      return myTotalError;
    }

    /**
     * Calculates the error of a chain by calculating the error if the chain was spread,
     * packed and spreadIn, final error is the minimum of these three calculations.
     */
    public void calculateChainError() {
      ArrayList<Integer> spread = spreadError();
      int spreadTotal = spread.stream().mapToInt(Integer::intValue).sum();
      ArrayList<Integer> packed = packedError();
      int packedTotal = packed.stream().mapToInt(Integer::intValue).sum();
      ArrayList<Integer> spreadIn = spreadInError();
      int spreadInTotal = spreadIn.stream().mapToInt(Integer::intValue).sum();

      if (spreadTotal < packedTotal &&
          spreadTotal < spreadInTotal) {
        myTotalError = spreadTotal * errorFactor;
      }

      if (packedTotal < spreadTotal &&
          packedTotal < spreadInTotal) {
        myTotalError = packedTotal * errorFactor;
      }

      if (spreadInTotal < packedTotal &&
          spreadInTotal < spreadTotal) {
        myTotalError = spreadInTotal * errorFactor;
      }
    }

    /**
     * Calculation of spread chain error and margins
     *
     * @return
     */
    ArrayList<Integer> spreadError() {
      Direction dir = Direction.getDirections(myOrientation)[1];
      int minMargin = myGroup.get(0).getConnection(dir.getOpposite()).getAbsoluteMargin();
      for (ConstrainedWidget wid : myGroup) {
        minMargin = Math.min(minMargin, wid.getConnection(dir).getAbsoluteMargin());
      }
      ArrayList<Integer> spreadMargins = new ArrayList<>();
      spreadMargins.add(myGroup.get(0).getConnection(dir.getOpposite()).getAbsoluteMargin() - minMargin);
      for (ConstrainedWidget wid : myGroup) {
        spreadMargins.add(wid.getConnection(dir).getAbsoluteMargin() - minMargin);
      }
      return spreadMargins;
    }

    /**
     * Calculation of packed chain error and margins
     *
     * @return
     */
    ArrayList<Integer> packedError() {
      Direction dir = Direction.getDirections(myOrientation)[1];
      ArrayList<Integer> packed = new ArrayList<>();
      for (ConstrainedWidget wid : myGroup.subList(0, myGroup.size() - 1)) {
        packed.add(wid.getConnection(dir).getAbsoluteMargin());
      }

      int closingDiff = myGroup.get(0).getConnection(dir.getOpposite()).getAbsoluteMargin() -
                        myGroup.get(myGroup.size() - 1).getConnection(dir).getAbsoluteMargin();
      if (closingDiff > 0) {
        packed.add(0, closingDiff);
        packed.add(0);
      }
      else {
        packed.add(0, 0);
        packed.add(-1 * closingDiff);
      }
      return packed;
    }

    /**
     * Calculation of packed chain error and margins
     *
     * @return
     */
    ArrayList<Integer> spreadInError() {
      Direction dir = Direction.getDirections(myOrientation)[1];
      int minMargin = Integer.MAX_VALUE;
      for (ConstrainedWidget wid : myGroup.subList(0, myGroup.size() - 1)) {
        minMargin = Math.min(minMargin, wid.getConnection(dir).getMargin());
      }
      ArrayList<Integer> spreadIn = new ArrayList<>();
      for (ConstrainedWidget wid : myGroup.subList(0, myGroup.size() - 1)) {
        spreadIn.add(wid.getConnection(dir).getAbsoluteMargin() - minMargin);
      }
      spreadIn.add(0, myGroup.get(0).getConnection(dir.getOpposite()).getAbsoluteMargin());
      spreadIn.add(myGroup.get(myGroup.size() - 1).getConnection(dir).getAbsoluteMargin());
      return spreadIn;
    }
  }
}