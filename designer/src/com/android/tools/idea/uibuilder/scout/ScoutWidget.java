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

import android.support.constraint.solver.widgets.ConstraintAnchor;
import android.support.constraint.solver.widgets.ConstraintWidget;
import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.scene.ConstraintComponentUtilities;
import org.jetbrains.annotations.NotNull;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

/**
 * Main Wrapper class for Constraint Widgets
 */
public class ScoutWidget implements Comparable<ScoutWidget> {
  private static final boolean DEBUG = false;
  private static final float MAXIMUM_STRETCH_GAP = 0.6f; // percentage
  private int mX;
  private int mY;
  private int mWidth;
  private int mHeight;
  private int mBaseLine;
  private ScoutWidget mParent;
  private float mRootDistance;
  private float[] mDistToRootCache = new float[]{-1, -1, -1, -1};
  NlComponent mNlComponent;
  private boolean mKeepExistingConnections = true;
  private Rectangle mRectangle;
  HashMap<String, ScoutWidget> myChildMap = new HashMap<String, ScoutWidget>();
  private static final String ATT_LL = SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF;
  private static final String ATT_LR = SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF;
  private static final String ATT_RL = SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF;
  private static final String ATT_RR = SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF;
  private static final String ATT_TT = SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF;
  private static final String ATT_TB = SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF;
  private static final String ATT_BT = SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF;
  private static final String ATT_BB = SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF;
  private static final String ATT_BASE = SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF;
  private static final String[] ATTR_LEFT = {ATT_LL, ATT_LR};
  private static final String[] ATTR_RIGHT = {ATT_RR, ATT_RL};
  private static final String[] ATTR_TOP = {ATT_TB, ATT_TT};
  private static final String[] ATTR_BOTTOM = {ATT_BT, ATT_BB};
  private static final String[] ATTR_BASE = {ATT_BASE};
  static final String[][] ATTR_CONNECTIONS = {ATTR_TOP, ATTR_BOTTOM, ATTR_LEFT, ATTR_RIGHT, ATTR_BASE};
  private static final Direction[] DIR_LEFT = {Direction.LEFT, Direction.RIGHT};
  private static final Direction[] DIR_RIGHT = {Direction.RIGHT, Direction.LEFT};
  private static final Direction[] DIR_TOP = {Direction.BOTTOM, Direction.TOP};
  private static final Direction[] DIR_BOTTOM = {Direction.TOP, Direction.BOTTOM};
  private static final Direction[] DIR_BASE = {Direction.BASELINE};
  static final Direction[][] ATTR_DIR_CONNECT = {DIR_TOP, DIR_BOTTOM, DIR_LEFT, DIR_RIGHT, DIR_BASE};

  public ScoutWidget(NlComponent component, ScoutWidget parent) {
    this.mNlComponent = component;
    this.mParent = parent;
    this.mX = ConstraintComponentUtilities.getDpX(component);
    this.mY = ConstraintComponentUtilities.getDpY(component);

    this.mWidth = ConstraintComponentUtilities.getDpWidth(component);
    this.mHeight = ConstraintComponentUtilities.getDpHeight(component);
    this.mBaseLine = ConstraintComponentUtilities.getDpBaseline(component) + mY;
    if (parent != null) {
      mRootDistance = distance(parent, this);
    }
    if (parent != null) {
      parent.addChild(this);
    }
  }

  void addChild(ScoutWidget widget) {
    String id = widget.mNlComponent.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
    myChildMap.put(id, widget);
  }

  ScoutWidget getChild(String id) {
    return myChildMap.get(id);
  }

  /**
   * Sets the order root first
   * followed by outside to inside, top to bottom, left to right
   *
   * @param scoutWidget
   * @return
   */
  @Override
  public int compareTo(ScoutWidget scoutWidget) {
    if (mParent == null) {
      return -1;
    }
    if (mRootDistance != scoutWidget.mRootDistance) {
      return Float.compare(mRootDistance, scoutWidget.mRootDistance);
    }
    if (mY != scoutWidget.mY) {
      return Float.compare(mY, scoutWidget.mY);
    }
    if (mX != scoutWidget.mX) {
      return Float.compare(mX, scoutWidget.mX);
    }
    return 0;
  }

  @Override
  public String toString() {
    return mNlComponent.toString() + "[ " + mX + " , " + mY + " ] " + mWidth + " x " + mHeight;
  }

  boolean isRoot() {
    return mParent == null;
  }

  /**
   * is this a guideline
   *
   * @return
   */
  public boolean isGuideline() {
    return ConstraintComponentUtilities.isGuideline(mNlComponent);
  }

  /**
   * is guideline vertical
   *
   * @return
   */
  public boolean isVerticalGuideline() {
    return ConstraintComponentUtilities.isVerticalGuideline(mNlComponent);
  }

  /**
   * is this a horizontal guide line on the image
   *
   * @return
   */
  public boolean isHorizontalGuideline() {
    return ConstraintComponentUtilities.isHorizontalGuideline(mNlComponent);
  }

  /**
   * Wrap an array of ConstraintWidgets into an array of InferWidgets
   *
   * @param array
   * @return
   */
  public static ScoutWidget[] create(NlComponent[] array) {
    ScoutWidget[] ret = new ScoutWidget[array.length];
    NlComponent root = null;

    //search for the root widget
    for (int i = 1; i < ret.length; i++) {
      NlComponent component1 = array[i-1];
      NlComponent component2 = array[i];
     if(component1.getParent() != component2.getParent()) {
       if (component2.getParent() == component1)
         root = component1;
       break;
     }
     if(component1.getParent() != component2.getParent()) {
       if (component1.getParent() == component2)
         root = component2;
       break;
     }
    }
    ScoutWidget rootwidget = new ScoutWidget(root, null);
    ret[0] = rootwidget;
    int count = 1;
    for (int i = 0; i < ret.length; i++) {
      if (array[i] != root) {
        ret[count++] = new ScoutWidget(array[i], rootwidget);
      }
    }
     Arrays.sort(ret,1,ret.length);

    if (DEBUG) {
      for (int i = 0; i < ret.length; i++) {
        System.out.println(
          "[" + i + "] -> " + ret[i].mNlComponent + "    " +
          ret[i].mRootDistance + " : " + ret[i]);
      }
    }
    return ret;
  }

  // above = 0, below = 1, left = 2, right = 3
  float getLocation(Direction dir) {
    switch (dir) {
      case TOP:
        return mY;
      case BOTTOM:
        return mY + mHeight;
      case LEFT:
        return mX;
      case RIGHT:
        return mX + mWidth;
      case BASELINE:
        return mBaseLine;
    }
    return mBaseLine;
  }

  /**
   * simple accessor for the height
   *
   * @return the height of the widget
   */
  public float getHeight() {
    return mHeight;
  }

  public int getHeightInt() {
    // TODO: check that...
    return (int)mHeight;
  }

  /**
   * simple accessor for the width
   *
   * @return the width of the widget
   */
  public float getWidth() {
    return mWidth;
  }

  public int getWidthInt() {
    // TODO: check that...
    return (int)mWidth;
  }

  /**
   * simple accessor for the X position
   *
   * @return the X position of the widget
   */
  final float getX() {
    return mX;
  }

  /**
   * simple accessor for the Y position
   *
   * @return the Y position of the widget
   */
  final float getY() {
    return mY;
  }

  /**
   * This calculates a constraint tables and applies them to the widgets
   * TODO break up into creation of a constraint table and apply
   *
   * @param list ordered list of widgets root must be list[0]
   */
  public static void computeConstraints(ScoutWidget[] list) {
    ScoutProbabilities table = new ScoutProbabilities();
    table.computeConstraints(list);
    table.applyConstraints(list);
  }

  private static Direction lookupType(int dir) {
    return Direction.get(dir);
  }

  /**
   * map integer direction to ConstraintAnchor.Type
   *
   * @param dir integer direction
   * @return
   */
  private static ConstraintAnchor.Type lookupType(Direction dir) {
    switch (dir) {
      case TOP:
        return ConstraintAnchor.Type.TOP;
      case BOTTOM:
        return ConstraintAnchor.Type.BOTTOM;
      case LEFT:
        return ConstraintAnchor.Type.LEFT;
      case RIGHT:
        return ConstraintAnchor.Type.RIGHT;
      case BASELINE:
        return ConstraintAnchor.Type.BASELINE;
    }
    return ConstraintAnchor.Type.NONE;
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////

  // TODO implement set
  public void setDpX(@AndroidDpCoordinate int value) {
    ConstraintComponentUtilities.setAbsoluteDpX(mNlComponent, value);
  }

  public void setDpY(@AndroidDpCoordinate int value) {
    ConstraintComponentUtilities.setAbsoluteDpY(mNlComponent, value);
  }

  public void setDpHeight(@AndroidDpCoordinate int value) {
    ConstraintComponentUtilities.setAbsoluteDpHeight(mNlComponent, value);
  }

  public void setDpWidth(@AndroidDpCoordinate int value) {
    ConstraintComponentUtilities.setAbsoluteDpWidth(mNlComponent, value);
  }

  public void setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour behaviour) {
    switch (behaviour) {
      case FIXED: {
        setDpWidth((int)mWidth);
      }
      break;
      case MATCH_CONSTRAINT: {
        ConstraintComponentUtilities.setAttributeValue(mNlComponent, SdkConstants.ANDROID_URI,
                                                       SdkConstants.ATTR_LAYOUT_WIDTH, SdkConstants.VALUE_MATCH_CONSTRAINT);
      }
      break;
      case WRAP_CONTENT: {
        ConstraintComponentUtilities.setAttributeValue(mNlComponent, SdkConstants.ANDROID_URI,
                                                       SdkConstants.ATTR_LAYOUT_WIDTH, SdkConstants.VALUE_WRAP_CONTENT);
      }
      break;
      case MATCH_PARENT: {
        ConstraintComponentUtilities.setAttributeValue(mNlComponent, SdkConstants.ANDROID_URI,
                                                       SdkConstants.ATTR_LAYOUT_WIDTH, SdkConstants.VALUE_MATCH_PARENT);
      }
    }
  }

  public void setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour behaviour) {
    switch (behaviour) {
      case FIXED: {
        setDpHeight((int)mHeight);
      }
      break;
      case MATCH_CONSTRAINT: {
        ConstraintComponentUtilities.setAttributeValue(mNlComponent, SdkConstants.ANDROID_URI,
                                                       SdkConstants.ATTR_LAYOUT_HEIGHT, SdkConstants.VALUE_MATCH_CONSTRAINT);
      }
      break;
      case WRAP_CONTENT: {
        ConstraintComponentUtilities.setAttributeValue(mNlComponent, SdkConstants.ANDROID_URI,
                                                       SdkConstants.ATTR_LAYOUT_HEIGHT, SdkConstants.VALUE_WRAP_CONTENT);
      }
      break;
      case MATCH_PARENT: {
        ConstraintComponentUtilities.setAttributeValue(mNlComponent, SdkConstants.ANDROID_URI,
                                                       SdkConstants.ATTR_LAYOUT_HEIGHT, SdkConstants.VALUE_MATCH_PARENT);
      }
    }
  }

  private void connect(Direction sourceDirection, ScoutWidget target, Direction targetDirection, int gap) {
    ConstraintComponentUtilities.connect(mNlComponent, sourceDirection, target.mNlComponent, targetDirection, gap);
  }

  private void connectWeak(Direction dir2, ScoutWidget to2, Direction dir21, int gap) {
    // TODO: probably need to be removed
  }


  static ScoutWidget[] getWidgetArray(NlComponent base) {
    ArrayList<NlComponent> list = new ArrayList<>(base.getChildren());
    list.add(0, base);
    return create(list.toArray(new NlComponent[list.size()]));
  }
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////

  public static boolean isConnected(NlComponent component, Direction dir) {
    String[] attrs = ATTR_CONNECTIONS[dir.ordinal()];
    for (int i = 0; i < attrs.length; i++) {
      String attr = attrs[i];
      String id = component.getAttribute(SdkConstants.SHERPA_URI, attr);
      if (id != null) {
        return true;
      }
    }
    return false;
  }

  /**
   * Wrapper for concept of anchor the "connector" on sides of a widget
   */
  class Anchor {
    Direction myDirection;

    Anchor(Direction dir) {
      myDirection = dir;
    }

    public boolean isConnected() {
      String[] attrs = ATTR_CONNECTIONS[myDirection.ordinal()];
      for (int i = 0; i < attrs.length; i++) {
        String attr = attrs[i];
        String id = mNlComponent.getAttribute(SdkConstants.SHERPA_URI, attr);
        if (id != null) {
          return true;
        }
      }
      return false;
    }

    public ScoutWidget getTargetWidget() {
      int dir = myDirection.ordinal();
      String[] attrs = ATTR_CONNECTIONS[dir];
      for (int i = 0; i < attrs.length; i++) {
        String attr = attrs[i];
        String id = mNlComponent.getAttribute(SdkConstants.SHERPA_URI, attr);
        if (id != null) {
          return getParent().getChild(id);
        }
      }
      return null;
    }

    public Anchor getTarget() {
      int dir = myDirection.ordinal();
      String[] attrs = ATTR_CONNECTIONS[dir];
      for (int i = 0; i < attrs.length; i++) {
        String attr = attrs[i];
        String id = mNlComponent.getAttribute(SdkConstants.SHERPA_URI, attr);
        if (id != null && id.equalsIgnoreCase("parent")) {
          return getParent().getAnchor(ATTR_DIR_CONNECT[dir][i]);
        }
        if (id != null && getParent() != null && getParent().getChild(id) !=null) {
          return getParent().getChild(id).getAnchor(ATTR_DIR_CONNECT[dir][i]);
        }
      }
      return null;
    }

    ScoutWidget getOwner() {
      return ScoutWidget.this;
    }

    public boolean isConnectionAllowed(ScoutWidget component) {
      if (isConnected()) {
        return false;
      }
      if (ScoutWidget.this.getParent() == component) {
        return true;
      }
      switch (myDirection) {
        case TOP:
        case BOTTOM:
        case BASELINE:
          if (ScoutWidget.this == component.getAnchor(Direction.TOP).getTargetWidget()) {
            return false;
          }
          if (ScoutWidget.this == component.getAnchor(Direction.BOTTOM).getTargetWidget()) {
            return false;
          }
          if (ScoutWidget.this == component.getAnchor(Direction.BASELINE).getTargetWidget()) {
            return false;
          }
          return true;
        case LEFT:
        case RIGHT:
          if (ScoutWidget.this == component.getAnchor(Direction.LEFT).getTargetWidget()) {
            return false;
          }
          if (ScoutWidget.this == component.getAnchor(Direction.RIGHT).getTargetWidget()) {
            return false;
          }
          return true;
      }
      return true;
    }

    public int getMargin() {
      return ConstraintComponentUtilities.getMargin(mNlComponent, myDirection.getMarginString());
    }

    public Direction getType() {
      return myDirection;
    }
  }

  public Anchor getAnchor(Direction direction) {
    return new Anchor(direction);
  }

  public int getDpX() {
    return mX;
    //return ConstraintComponentUtilities.getDpX(mNlComponent);
  }

  public int getDpY() {
    return mY;
    //return ConstraintComponentUtilities.getDpY(mNlComponent);
  }

  public int getDpWidth() {
    return mWidth;
    //return ConstraintComponentUtilities.getDpWidth(mNlComponent);
  }

  public int getDpHeight() {
    return mHeight;
    //return ConstraintComponentUtilities.getDpHeight(mNlComponent);
  }

  public int getDpBaseline() {
    return mY;
    //return ConstraintComponentUtilities.getDpBaseline(mNlComponent);
  }

  public static boolean hasBaseline(@NotNull NlComponent component) {
    return component.getBaseline() > 0;
  }

  /**
   * set a centered constraint if possible return true if it did
   *
   * @param dir   direction 0 = vertical
   * @param to1   first widget  to connect to
   * @param to2   second widget to connect to
   * @param cDir1 the side of first widget to connect to
   * @param cDir2 the sed of the second widget to connect to
   * @param gap   the gap
   * @return true if it was able to connect
   */
  boolean setCentered(int dir, ScoutWidget to1, ScoutWidget to2, Direction cDir1, Direction cDir2,
                      float gap) {
    Direction ori = (dir == 0) ? Direction.TOP : Direction.LEFT;
    Anchor anchor1 = getAnchor(ori);
    Anchor anchor2 = getAnchor(ori.getOpposite());

    if (mKeepExistingConnections && (anchor1.isConnected() || anchor2.isConnected())) {
      if (anchor1.isConnected() ^ anchor2.isConnected()) {
        return false;
      }
      if (anchor1.isConnected()
          && (anchor1.getTarget().getOwner() != to1)) {
        return false;
      }
      if (anchor2.isConnected()
          && (anchor2.getTarget().getOwner() != to2)) {
        return false;
      }
    }

    if (anchor1.isConnectionAllowed(to1) &&
        anchor2.isConnectionAllowed(to2)) {
      // Resize
      if (!isResizable(dir)) {
        if (dir == 0) {
          int height = getDpHeight();
          float stretchRatio = (gap * 2) / (float)height;
          if (isCandidateResizable(dir) && stretchRatio < MAXIMUM_STRETCH_GAP) {
            setVerticalDimensionBehaviour(
              ConstraintWidget.DimensionBehaviour.MATCH_CONSTRAINT);
          }
          else {
            gap = 0;
          }
        }
        else {
          int width = getDpWidth();
          float stretchRatio = (gap * 2) / (float)width;
          if (isCandidateResizable(dir) && stretchRatio < MAXIMUM_STRETCH_GAP) {
            setHorizontalDimensionBehaviour(
              ConstraintWidget.DimensionBehaviour.MATCH_CONSTRAINT);
          }
          else {
            gap = 0;
          }
        }
      }

      if (to1.equals(to2)) {
        connect(cDir1, to1, cDir1, (int)gap);
        connect(cDir2, to2, cDir2, (int)gap);
      }
      else {

        float pos1 = to1.getLocation(cDir1);
        float pos2 = to2.getLocation(cDir2);
        Direction c1 = (pos1 < pos2) ? (ori) : (ori.getOpposite());
        Direction c2 = (pos1 > pos2) ? (ori) : (ori.getOpposite());
        int gap1 = gap(this, c1, to1, cDir1);
        int gap2 = gap(this, c2, to2, cDir2);

        connect(c1, to1, cDir1, Math.max(0, gap1));
        connect(c2, to2, cDir2, Math.max(0, gap2));
      }
      return true;
    }
    else {

      return false;
    }
  }

  /**
   * Get the gap between two specific edges of widgets
   *
   * @param widget1
   * @param direction1
   * @param widget2
   * @param direction2
   * @return distance in dp
   */
  private static int gap(ScoutWidget widget1, Direction direction1,
                         ScoutWidget widget2, Direction direction2) {
    switch (direction1) {
      case TOP:
      case LEFT:
        return getPos(widget1, direction1) - getPos(widget2, direction2);
      case BOTTOM:
      case RIGHT:
        return getPos(widget2, direction2) - getPos(widget1, direction1);
    }
    return 0;
  }

  /**
   * Get the position of a edge of a widget
   *
   * @param widget
   * @param direction
   * @return
   */
  private static int getPos(ScoutWidget widget, Direction direction) {
    switch (direction) {
      case TOP:
        return widget.getDpY();
      case BOTTOM:
        return widget.getDpY() + widget.getDpHeight();
      case LEFT:
        return widget.getDpX();
      case RIGHT:
        return widget.getDpX() + widget.getDpWidth();
    }
    return 0;
  }

  /**
   * set a centered constraint if possible return true if it did
   *
   * @param dir   direction 0 = vertical
   * @param to1   first widget  to connect to
   * @param cDir1 the side of first widget to connect to
   * @return true if it was able to connect
   */
  boolean setEdgeCentered(int dir, ScoutWidget to1, Direction cDir1) {
    Direction ori = (dir == 0) ? Direction.TOP : Direction.LEFT;
    Anchor anchor1 = getAnchor(ori);
    Anchor anchor2 = getAnchor(ori.getOpposite());

    if (mKeepExistingConnections && (anchor1.isConnected() || anchor2.isConnected())) {
      if (anchor1.isConnected() ^ anchor2.isConnected()) {
        return false;
      }
      if (anchor1.isConnected()
          && (anchor1.getTarget().getOwner() != to1)) {
        return false;
      }
    }

    if (anchor1.isConnectionAllowed(to1)) {
      connect(ori, to1, cDir1, 0);
      connect(ori.getOpposite(), to1, cDir1, 0);
    }
    return true;
  }


  /**
   * set a constraint if possible return true if it did
   *
   * @param dir  the direction of the connection
   * @param to   the widget to connect to
   * @param cDir the direction of
   * @param gap
   * @return false if unable to apply
   */
  boolean setConstraint(int dir, ScoutWidget to, int cDir, float gap) {
    Direction anchorType = lookupType(dir);
    if (to.isGuideline()) {
      cDir &= 0x2;
    }
    Direction cAnchorType = lookupType(cDir);

    Anchor anchor = getAnchor(anchorType);

    if (mKeepExistingConnections) {
      if (anchor.isConnected()) {
        if (anchor.getTarget().getOwner() != to) {
          return false;
        }
        return true;
      }
      if (dir == Direction.BASELINE.getDirection()) {
        if (getAnchor(Direction.BOTTOM).isConnected()) {
          return false;
        }
        if (getAnchor(Direction.TOP).isConnected()) {
          return false;
        }
      }
      else if (dir == Direction.TOP.getDirection()) {
        if (getAnchor(Direction.BOTTOM).isConnected()) {
          return false;
        }
        if (getAnchor(Direction.BASELINE).isConnected()) {
          return false;
        }
      }
      else if (dir == Direction.BOTTOM.getDirection()) {
        if (getAnchor(Direction.TOP).isConnected()) {
          return false;
        }
        if (getAnchor(Direction.BASELINE).isConnected()) {
          return false;
        }
      }
      else if (dir == Direction.LEFT.getDirection()) {
        if (getAnchor(Direction.RIGHT).isConnected()) {
          return false;
        }
      }
      else if (dir == Direction.RIGHT.getDirection()) {
        if (getAnchor(Direction.LEFT).isConnected()) {
          return false;
        }
      }
    }

    if (anchor.isConnectionAllowed(to)) {
      connect(anchorType, to, cAnchorType, (int)gap);
      return true;
    }
    else {
      return false;
    }
  }

  /**
   * set a Weak constraint if possible return true if it did
   *
   * @param dir  the direction of the connection
   * @param to   the widget to connect to
   * @param cDir the direction of
   * @return false if unable to apply
   */
  boolean setWeakConstraint(int dir, ScoutWidget to, int cDir) {
    Direction direction = Direction.get(dir);
    Anchor anchor = getAnchor(direction);
    float gap = 8f;

    if (mKeepExistingConnections && anchor.isConnected()) {
      if (anchor.getTarget().getOwner() != to) {
        return false;
      }
      return true;
    }

    if (anchor.isConnectionAllowed(to)) {
      if (DEBUG) {
        System.out.println(
          "WEAK CONSTRAINT " + mNlComponent + " to " + to.mNlComponent);
      }
      connectWeak(lookupType(dir), to, lookupType(cDir), (int)gap);
      return true;
    }
    else {
      return false;
    }
  }

  /**
   * calculates the distance between two widgets (assumed to be rectangles)
   *
   * @param a
   * @param b
   * @return the distance between two widgets at there closest point to each other
   */
  static float distance(ScoutWidget a, ScoutWidget b) {
    float ax1, ax2, ay1, ay2;
    float bx1, bx2, by1, by2;
    ax1 = a.mX;
    ax2 = a.mX + a.mWidth;
    ay1 = a.mY;
    ay2 = a.mY + a.mHeight;

    bx1 = b.mX;
    bx2 = b.mX + b.mWidth;
    by1 = b.mY;
    by2 = b.mY + b.mHeight;
    float xdiff11 = Math.abs(ax1 - bx1);
    float xdiff12 = Math.abs(ax1 - bx2);
    float xdiff21 = Math.abs(ax2 - bx1);
    float xdiff22 = Math.abs(ax2 - bx2);

    float ydiff11 = Math.abs(ay1 - by1);
    float ydiff12 = Math.abs(ay1 - by2);
    float ydiff21 = Math.abs(ay2 - by1);
    float ydiff22 = Math.abs(ay2 - by2);

    float xmin = Math.min(Math.min(xdiff11, xdiff12), Math.min(xdiff21, xdiff22));
    float ymin = Math.min(Math.min(ydiff11, ydiff12), Math.min(ydiff21, ydiff22));

    boolean yOverlap = ay1 <= by2 && by1 <= ay2;
    boolean xOverlap = ax1 <= bx2 && bx1 <= ax2;
    float xReturn = (yOverlap) ? xmin : (float)Math.hypot(xmin, ymin);
    float yReturn = (xOverlap) ? ymin : (float)Math.hypot(xmin, ymin);
    return Math.min(xReturn, yReturn);
  }

  public ScoutWidget getParent() {
    return mParent;
  }

  /**
   * Return true if the widget is a candidate to be marked
   * as resizable (ANY) -- i.e. if the current dimension is bigger than its minimum.
   *
   * @param dimension the dimension (vertical == 0, horizontal == 1) we are looking at
   * @return true if the widget is a good candidate for resize
   */
  public boolean isCandidateResizable(int dimension) {
    if (dimension == 0) {
      return ConstraintComponentUtilities.hasUserResizedVertically(mNlComponent);
    }
    return ConstraintComponentUtilities.hasUserResizedHorizontally(mNlComponent);
  }

  public boolean isResizable(int dimension) {
    if (dimension == 0) {
      return ConstraintComponentUtilities.isVerticalResizable(mNlComponent);
    }
    else {
      return ConstraintComponentUtilities.isHorizontalResizable(mNlComponent);
    }
  }

  public boolean hasBaseline() {
    return ConstraintComponentUtilities.hasBaseline(mNlComponent);
  }

  /**
   * Gets the neighbour in that direction or root
   * TODO better support for large widgets with several neighbouring widgets
   *
   * @param dir
   * @param list
   * @return
   */
  public ScoutWidget getNeighbor(Direction dir, ScoutWidget[] list) {
    ScoutWidget neigh = list[0];
    float minDist = Float.MAX_VALUE;

    switch (dir) {
      case LEFT: {
        float ay1 = this.getLocation(Direction.TOP);
        float ay2 = this.getLocation(Direction.BOTTOM);
        float ax = this.getLocation(Direction.LEFT);

        for (int i = 1; i < list.length; i++) {
          ScoutWidget iw = list[i];
          if (iw == this) {
            continue;
          }
          float by1 = iw.getLocation(Direction.TOP);
          float by2 = iw.getLocation(Direction.BOTTOM);
          if (Math.max(ay1, by1) <= Math.min(ay2, by2)) { // overlap
            float bx = iw.getLocation(Direction.RIGHT);
            if (bx < ax && (ax - bx) < minDist) {
              minDist = (ax - bx);
              neigh = iw;
            }
          }
        }
        return neigh;
      }
      case RIGHT: {
        float ay1 = this.getLocation(Direction.TOP);
        float ay2 = this.getLocation(Direction.BOTTOM);
        float ax = this.getLocation(Direction.RIGHT);

        for (int i = 1; i < list.length; i++) {
          ScoutWidget iw = list[i];
          if (iw == this) {
            continue;
          }
          float by1 = iw.getLocation(Direction.TOP);
          float by2 = iw.getLocation(Direction.BOTTOM);
          if (Math.max(ay1, by1) <= Math.min(ay2, by2)) { // overlap
            float bx = iw.getLocation(Direction.LEFT);
            if (bx > ax && (bx - ax) < minDist) {
              minDist = (bx - ax);
              neigh = iw;
            }
          }
        }
        return neigh;
      }
      case BOTTOM: {
        float ax1 = this.getLocation(Direction.LEFT);
        float ax2 = this.getLocation(Direction.RIGHT);
        float ay = this.getLocation(Direction.BOTTOM);

        for (int i = 1; i < list.length; i++) {
          ScoutWidget iw = list[i];
          if (iw == this) {
            continue;
          }
          float bx1 = iw.getLocation(Direction.LEFT);
          float bx2 = iw.getLocation(Direction.RIGHT);
          if (Math.max(ax1, bx1) <= Math.min(ax2, bx2)) { // overlap
            float by = iw.getLocation(Direction.TOP);
            if (by > ay && (by - ay) < minDist) {
              minDist = (by - ay);
              neigh = iw;
            }
          }
        }
        return neigh;
      }
      case TOP: {
        float ax1 = this.getLocation(Direction.LEFT);
        float ax2 = this.getLocation(Direction.RIGHT);
        float ay = this.getLocation(Direction.TOP);

        for (int i = 1; i < list.length; i++) {
          ScoutWidget iw = list[i];
          if (iw == this) {
            continue;
          }
          float bx1 = iw.getLocation(Direction.LEFT);
          float bx2 = iw.getLocation(Direction.RIGHT);
          if (Math.max(ax1, bx1) <= Math.min(ax2, bx2)) { // overlap
            float by = iw.getLocation(Direction.BOTTOM);
            if (ay > by && (ay - by) < minDist) {
              minDist = (ay - by);
              neigh = iw;
            }
          }
        }
        return neigh;
      }
      case BASELINE:
      default:
        return null;
    }
  }

  /**
   * is the widet connected in that direction
   *
   * @param direction
   * @return true if connected
   */
  public boolean isConnected(Direction direction) {
    return getAnchor(direction).isConnected();
  }

  /**
   * is the distance to the Root Cached
   *
   * @param direction
   * @return true if distance to root has been cached
   */
  private boolean isDistanceToRootCache(Direction direction) {
    int directionOrdinal = direction.getDirection();
    Float f = mDistToRootCache[directionOrdinal];
    if (f < 0) {  // depends on any comparison involving Float.NaN returns false
      return false;
    }
    return true;
  }

  /**
   * Get the cache distance to the root
   *
   * @param d
   * @param value
   */
  private void cacheRootDistance(Direction d, float value) {
    mDistToRootCache[d.getDirection()] = value;
  }

  /**
   * get distance to the container in a direction
   * caches the distance
   *
   * @param list      list of widgets (container is list[0]
   * @param direction direction to check in
   * @return distance root or NaN if no connection available
   */
  public float connectedDistanceToRoot(ScoutWidget[] list, Direction direction) {
    float value = recursiveConnectedDistanceToRoot(list, direction);
    cacheRootDistance(direction, value);
    return value;
  }

  /**
   * Walk the widget connections to get the distance to the container in a direction
   *
   * @param list      list of widgets (container is list[0]
   * @param direction direction to check in
   * @return distance root or NaN if no connection available
   */
  private float recursiveConnectedDistanceToRoot(ScoutWidget[] list, Direction direction) {

    if (isDistanceToRootCache(direction)) {
      return mDistToRootCache[direction.getDirection()];
    }
    Anchor anchor = getAnchor(direction);

    if (anchor == null || !anchor.isConnected()) {
      return Float.NaN;
    }
    float margin = anchor.getMargin();
    Anchor toAnchor = anchor.getTarget();

    ScoutWidget toWidget = toAnchor.getOwner();
    if (list[0] == toWidget) { // found the base return;
      return margin;
    }

    // if atached to the same side
    if (toAnchor.getType() == direction) {
      for (ScoutWidget scoutWidget : list) {
        if (scoutWidget == toWidget) {
          float dist = scoutWidget.recursiveConnectedDistanceToRoot(list, direction);
          scoutWidget.cacheRootDistance(direction, dist);
          return margin + dist;
        }
      }
    }
    // if atached to the other side (you will need to add the length of the widget
    if (toAnchor.getType() == direction.getOpposite()) {
      for (ScoutWidget scoutWidget : list) {
        if (scoutWidget == toWidget) {
          margin += scoutWidget.getLength(direction);
          float dist = scoutWidget.recursiveConnectedDistanceToRoot(list, direction);
          scoutWidget.cacheRootDistance(direction, dist);
          return margin + dist;
        }
      }
    }
    return Float.NaN;
  }

  /**
   * Get size of widget
   *
   * @param direction the direction north/south gets height east/west gets width
   * @return size of widget in that dimension
   */
  private float getLength(Direction direction) {
    switch (direction) {
      case TOP:
      case BOTTOM:
        return mHeight;
      case RIGHT:
      case LEFT:
        return mWidth;
      default:
        return 0;
    }
  }

  /**
   * is the widget centered
   *
   * @param orientationVertical 1 = checking if vertical
   * @return true if centered
   */
  public boolean isCentered(int orientationVertical) {
    if (isGuideline()) return false;
    if (orientationVertical == Direction.ORIENTATION_VERTICAL) {
      return getAnchor(Direction.TOP).isConnected() &&
             getAnchor(Direction.BOTTOM).isConnected();
    }
    return getAnchor(Direction.LEFT).isConnected() &&
           getAnchor(Direction.RIGHT).isConnected();
  }

  public boolean hasConnection(Direction dir) {
    Anchor anchor = getAnchor(dir);
    return (anchor != null && anchor.isConnected());
  }

  public Rectangle getRectangle() {
    if (mRectangle == null) {
      mRectangle = new Rectangle();
    }
    mRectangle.x = getDpX();
    mRectangle.y = getDpY();
    mRectangle.width = getDpWidth();
    mRectangle.height = getDpHeight();
    return mRectangle;
  }

  /**
   * Calculate the gap in to the nearest widget
   *
   * @param direction the direction to check
   * @param list      list of other widgets (root == list[0])
   * @return the distance on that side
   */
  public int gap(Direction direction, ScoutWidget[] list) {
    int rootWidth = list[0].getDpWidth();
    int rootHeight = list[0].getDpHeight();
    Rectangle rect = new Rectangle();

    switch (direction) {
      case TOP: {
        rect.y = 0;
        rect.x = getDpX() + 1;
        rect.width = getDpWidth() - 2;
        rect.height = getDpY();
      }
      break;
      case BOTTOM: {
        rect.y = getDpY() + getDpHeight();
        rect.x = getDpX() + 1;
        rect.width = getDpWidth() - 2;
        rect.height = rootHeight - rect.y;
      }
      break;
      case LEFT: {
        rect.y = getDpY() + 1;
        rect.x = 0;
        rect.width = getDpX();
        rect.height = getDpHeight() - 2;
      }
      break;
      case RIGHT: {
        rect.y = getDpY() + 1;
        rect.x = getDpX() + getDpWidth();
        rect.width = rootWidth - rect.x;
        rect.height = getDpHeight() - 2;
      }
      break;
    }
    int min = Integer.MAX_VALUE;
    for (int i = 1; i < list.length; i++) {
      ScoutWidget scoutWidget = list[i];
      if (scoutWidget == this) {
        continue;
      }
      Rectangle r = scoutWidget.getRectangle();
      if (r.intersects(rect)) {
        int dist = (int)distance(scoutWidget, this);
        if (min > dist) {
          min = dist;
        }
      }
    }

    if (min > Math.max(rootHeight, rootWidth)) {
      switch (direction) {
        case TOP:
          return getDpY();
        case BOTTOM:
          return rootHeight - (getDpY() + getDpHeight());

        case LEFT:
          return getDpX();

        case RIGHT:
          return rootWidth - (getDpX() + getDpWidth());
      }
    }
    return min;
  }

  public void setX(int x) {
    setDpX(x);
    mX = getDpX();
  }

  public void setY(int y) {
    setDpY(y);
    mY = getDpY();
  }

  public void setWidth(int width) {
    setDpWidth(width);
    mWidth = getDpWidth();
  }

  public void setHeight(int height) {
    setDpHeight(height);
    mHeight = getDpHeight();
  }

  /**
   * Comparator to sort widgets by y
   */
  static Comparator<ScoutWidget> sSortY = (w1, w2) -> w1.getDpY() - w2.getDpY();

  public int rootDistanceY() {
    if (mNlComponent == null || mNlComponent.getParent() == null) {
      return 0;
    }
    int rootHeight = getParent().getDpHeight();
    int aY = getDpY();
    int aHeight = getDpHeight();
    return Math.min(aY, rootHeight - (aY + aHeight));
  }
}
