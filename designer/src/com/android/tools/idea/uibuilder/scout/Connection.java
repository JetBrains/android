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

  private int myDestWidget;
  private Direction myDestAnchor;
  private int myMargin;

  /**
   * Constructor for a Connection
   *
   * @param destWidget   index of destination widget within the constraint set
   * @param destAnchor   the destination direction of the connection
   */
  public Connection(int destWidget, Direction destAnchor) {
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

  public void setMargin(int margin) {
    myMargin = margin;
  }

  public int getMargin() {
    return myMargin;
  }

  public int getAbsoluteMargin() {
    return Math.abs(myMargin);
  }

  public int destWidget() {
    return myDestWidget;
  }

  public Direction destDirection() {
    return myDestAnchor;
  }

  public boolean isConnected() {
    return !(myDestWidget == NO_CONNECTION);
  }

  public boolean isParentConnection() {
    return myDestWidget == PARENT_CONNECTION;
  }
}
