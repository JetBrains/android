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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_CHAIN_PACKED;
import static com.android.SdkConstants.ATTR_LAYOUT_CHAIN_SPREAD;
import static com.android.SdkConstants.ATTR_LAYOUT_CHAIN_SPREAD_INSIDE;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_HORIZONTAL_CHAIN_STYLE;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_END;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_START;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_TOP;
import static com.android.SdkConstants.ATTR_LAYOUT_VERTICAL_CHAIN_STYLE;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.SHERPA_URI;
import static com.android.SdkConstants.VALUE_ZERO_DP;
import static com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities.scoutChainConnect;

import com.android.tools.idea.common.model.NlComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Logic to infer and create Chains
 */
public final class ScoutChains {
  static final int MAX_ERROR = 1000;
  static final int MIN_SPREAD = 3;

  enum ChainMode {
    CHAIN_SPREAD,
    CHAIN_SPREAD_INSIDE,
    CHAIN_PACKED
  }

  enum Dir {
    HORIZONTAL,
    VERTICAL
  }

  private final static boolean DEBUG = true;

  public static void pick(ScoutWidget[] list) {
    ScoutWidget base = list[0];
    ScoutWidget[] children = new ScoutWidget[list.length - 1];
    System.arraycopy(list, 1, children, 0, children.length);
    ChainMode chain_mode;
    // ===============  CHECK for horizontal chain ===============
    for (int i = 0; i < children.length; i++) {
      ScoutWidget child = children[i];
      child.mCheckedForChain = child.isConnected(Direction.LEFT) || child.isConnected(Direction.RIGHT);
    }
    for (int i = 0; i < children.length; i++) {
      ScoutWidget child = children[i];
      ScoutWidget[] group = getCandiateListX(base, child, children);
      group = removeOverlapsX(group);
      if (group != null) { // We have a solid candidate group
        Dir dir = Dir.HORIZONTAL;
        if (anyNotWrap(group, dir)) { // go with a "normal" chain calc margins
          int[] margins = new int[group.length + 1];
          getMargins(base, group, margins, dir);
          creatHorizontalChain(group, base, ChainMode.CHAIN_SPREAD, margins);
        }
        else {
          int[] marginsSpread = new int[group.length + 1];
          int errorSpread = getMarginsSpread(base, group, marginsSpread, dir);
          int[] marginsInside = new int[group.length + 1];
          int errorInside = getMarginsInside(base, group, marginsInside, dir);
          int[] marginsPacked = new int[group.length + 1];
          int errorPacked = getMarginsPacked(base, group, marginsPacked, dir);
          chain_mode = (errorInside < errorSpread) ?
                       ((errorInside < errorPacked) ? ChainMode.CHAIN_SPREAD_INSIDE : ChainMode.CHAIN_PACKED) :
                       ((errorSpread < errorPacked) ? ChainMode.CHAIN_SPREAD : ChainMode.CHAIN_PACKED);
          switch (chain_mode) {
            case CHAIN_PACKED:
              if (errorPacked > MAX_ERROR) {
                return;
              }
              creatHorizontalChain(group, base, chain_mode, marginsPacked);
              break;
            case CHAIN_SPREAD:
              if (errorSpread > MAX_ERROR) {
                return;
              }
              creatHorizontalChain(group, base, chain_mode, marginsSpread);
              break;
            case CHAIN_SPREAD_INSIDE:
              if (errorInside > MAX_ERROR || group.length < MIN_SPREAD) {
                return;
              }
              creatHorizontalChain(group, base, chain_mode, marginsInside);
              break;
          }
        }
      }
    }

    // =============== CHECK for a vertical chain ===============
    for (int i = 0; i < children.length; i++) {
      ScoutWidget child = children[i];
      child.mCheckedForChain =
        child.isConnected(Direction.BOTTOM) || child.isConnected(Direction.TOP) || child.isConnected(Direction.BASELINE);
    }
    for (int i = 0; i < children.length; i++) {
      ScoutWidget child = children[i];
      ScoutWidget[] group = getCandiateListY(base, child, children);
      group = removeOverlapsY(group);

      if (group != null) {
        Dir dir = Dir.VERTICAL;
        if (anyNotWrap(group, dir)) { // go with a "normal" chain calc margins
          int[] margins = new int[group.length + 1];
          getMargins(base, group, margins, dir);
          creatVerticalChain(group, base, ChainMode.CHAIN_SPREAD, margins);
        }
        else {
          int[] marginsSpread = new int[group.length + 1];
          int errorSpread = getMarginsSpread(base, group, marginsSpread, dir);
          int[] marginsInside = new int[group.length + 1];
          int errorInside = getMarginsInside(base, group, marginsInside, dir);
          int[] marginsPacked = new int[group.length + 1];
          int errorPacked = getMarginsPacked(base, group, marginsPacked, dir);
          chain_mode = (errorInside < errorSpread) ?
                       ((errorInside < errorPacked) ? ChainMode.CHAIN_SPREAD_INSIDE : ChainMode.CHAIN_PACKED) :
                       ((errorSpread < errorPacked) ? ChainMode.CHAIN_SPREAD : ChainMode.CHAIN_PACKED);
          switch (chain_mode) {
            case CHAIN_PACKED:
              if (errorPacked > MAX_ERROR) {
                return;
              }
              creatVerticalChain(group, base, chain_mode, marginsPacked);
              break;
            case CHAIN_SPREAD:
              if (errorSpread > MAX_ERROR) {
                return;
              }
              creatVerticalChain(group, base, chain_mode, marginsSpread);
              break;
            case CHAIN_SPREAD_INSIDE:
              if (errorInside > MAX_ERROR || group.length < MIN_SPREAD) {
                return;
              }
              creatVerticalChain(group, base, chain_mode, marginsInside);
              break;
          }
        }
      }
    }
  }

  private static void getMargins(ScoutWidget base, ScoutWidget[] group, int[] margin, Dir dir) {

    if (dir == Dir.HORIZONTAL) {
      margin[0] = group[0].getDpX();
      for (int i = 1; i < group.length; i++) {
        margin[i] = group[i].getDpX() - group[i - 1].getDpX() - group[i - 1].getDpWidth();
      }
      margin[group.length] = base.getDpWidth() - group[group.length - 1].getDpWidth() - group[group.length - 1].getDpX();
    }
    else { // VERTICAL
      margin[0] = group[0].getDpY();
      for (int i = 1; i < group.length; i++) {
        margin[i] = group[i].getDpY() - group[i - 1].getDpY() - group[i - 1].getDpHeight();
      }
      margin[group.length] = base.getDpHeight() - group[group.length - 1].getDpHeight() - group[group.length - 1].getDpY();
    }
  }

  /**
   * assumes |-[]-[]-[]-|
   *
   * @param base   root
   * @param group  array to be in chain
   * @param margin margins needed
   * @param dir    chain direction
   * @return
   */
  private static int getMarginsSpread(ScoutWidget base, ScoutWidget[] group, int[] margin, Dir dir) {
    int error = 0;
    if (dir == Dir.HORIZONTAL) {
      int minGap = group[0].getDpX();
      for (int i = 1; i < group.length; i++) {
        int gap = group[i].getDpX() - group[i - 1].getDpX() - group[i - 1].getDpWidth();
        minGap = Math.min(gap, minGap);
      }
      minGap = Math.min(minGap, base.getDpWidth() - group[group.length - 1].getDpX() - group[group.length - 1].getDpWidth());
      int naturalSpace = minGap;

      margin[0] = group[0].getDpX() - naturalSpace;

      for (int i = 1; i < group.length; i++) {
        margin[i] = group[i].getDpX() - group[i - 1].getDpX() - group[i - 1].getDpWidth() - naturalSpace;
      }
      margin[group.length] = base.getDpWidth() - group[group.length - 1].getDpWidth() - group[group.length - 1].getDpX() - naturalSpace;
    }
    else { // VERTICAL
      int minGap = group[0].getDpY();
      for (int i = 1; i < group.length; i++) {
        int gap = group[i].getDpY() - group[i - 1].getDpY() - group[i - 1].getDpHeight();
        minGap = Math.min(gap, minGap);
      }
      minGap = Math.min(minGap, base.getDpHeight() - group[group.length - 1].getDpY() - group[group.length - 1].getDpHeight());
      int naturalSpace = minGap;

      margin[0] = group[0].getDpY() - naturalSpace;
      for (int i = 1; i < group.length; i++) {
        margin[i] = group[i].getDpY() - group[i - 1].getDpY() - group[i - 1].getDpHeight() - naturalSpace;
      }
      margin[group.length] = base.getDpHeight() - group[group.length - 1].getDpHeight() - group[group.length - 1].getDpY() - naturalSpace;
    }

    for (int i = 0; i < margin.length; i++) {
      int e = margin[i];
      if (e < -2) {
        error = Integer.MAX_VALUE;
        return error;
      }
      error += e * e;
    }
    System.out.println(" error = " + error);
    return error;
  }

  /**
   * assumes |[]-[]-[]|
   *
   * @param base   root
   * @param group  array to be in chain
   * @param margin margins needed
   * @param dir    chain direction
   * @return
   */
  private static int getMarginsInside(ScoutWidget base, ScoutWidget[] group, int[] margin, Dir dir) {
    int error = 0;
    if (dir == Dir.HORIZONTAL) {
      int minGap = Integer.MAX_VALUE;
      for (int i = 1; i < group.length; i++) {
        int gap = group[i].getDpX() - group[i - 1].getDpX() - group[i - 1].getDpWidth();
        minGap = Math.min(gap, minGap);
      }
      int naturalSpace = minGap;
      margin[0] = group[0].getDpX();

      for (int i = 1; i < group.length; i++) {
        margin[i] = group[i].getDpX() - group[i - 1].getDpX() - group[i - 1].getDpWidth() - naturalSpace;
      }
      margin[group.length] = base.getDpWidth() - group[group.length - 1].getDpWidth() - group[group.length - 1].getDpX();
    }
    else { // VERTICAL
      int minGap = Integer.MAX_VALUE;
      for (int i = 1; i < group.length; i++) {
        int gap = group[i].getDpY() - group[i - 1].getDpY() - group[i - 1].getDpHeight();
        minGap = Math.min(gap, minGap);
      }
      int naturalSpace = minGap;

      margin[0] = group[0].getDpY();
      for (int i = 1; i < group.length; i++) {
        margin[i] = group[i].getDpY() - group[i - 1].getDpY() - group[i - 1].getDpHeight() - naturalSpace;
      }
      margin[group.length] = base.getDpHeight() - group[group.length - 1].getDpHeight() - group[group.length - 1].getDpY();
    }

    for (int i = 0; i < margin.length; i++) {
      int e = margin[i]; // since the spread should be exactly the average value spread is value greater than a
      if (e < -2) {
        error = Integer.MAX_VALUE;
        return error;
      }
      error += e * e;
    }
    return error;
  }

  /**
   * assumes |-[][][]-|
   *
   * @param base   root
   * @param group  array to be in chain
   * @param margin margins needed
   * @param dir    chain direction
   * @return
   */
  private static int getMarginsPacked(ScoutWidget base, ScoutWidget[] group, int[] margin, Dir dir) {
    int error = 0;
    if (dir == Dir.HORIZONTAL) {
      int topSpace = group[0].getDpX();
      int bottomSpace = base.getDpWidth() - (group[group.length - 1].getDpWidth() + group[group.length - 1].getDpX());
      int naturalSpace = Math.min(topSpace, bottomSpace);

      margin[0] = group[0].getDpX() - naturalSpace;
      for (int i = 1; i < group.length; i++) {
        margin[i] = group[i].getDpX() - group[i - 1].getDpX() - group[i - 1].getDpWidth();
      }
      margin[group.length] = base.getDpWidth() - group[group.length - 1].getDpWidth() - group[group.length - 1].getDpX() - naturalSpace;
    }
    else { // VERTICAL
      int topSpace = group[0].getDpY();
      int bottomSpace = base.getDpHeight() - (group[group.length - 1].getDpHeight() + group[group.length - 1].getDpY());
      int naturalSpace = Math.min(topSpace, bottomSpace);

      margin[0] = group[0].getDpY() - naturalSpace;
      for (int i = 1; i < group.length; i++) {
        margin[i] = group[i].getDpY() - group[i - 1].getDpY() - group[i - 1].getDpHeight();
      }
      margin[group.length] = base.getDpHeight() - group[group.length - 1].getDpHeight() - group[group.length - 1].getDpY() - naturalSpace;
    }

    for (int i = 0; i < margin.length; i++) {
      int e = margin[i]; // since the spread should be exactly the average value spread is value greater than a
      if (e < -2) {
        error = Integer.MAX_VALUE;
        return error;
      }
      error += e * e;
    }
    return error;
  }

  private static boolean anyNotWrap(ScoutWidget[] group, Dir dir) {
    for (int i = 0; i < group.length; i++) {
      ScoutWidget widget = group[i];
      if (widget.isCandidateResizable((dir == Dir.HORIZONTAL) ? 1 : 0)) {
        return true;
      }
    }
    return false;
  }

  private static void creatHorizontalChain(ScoutWidget[] scoutWidgets, ScoutWidget parentScoutWidget, ChainMode chain_mode, int[] margins) {

    ScoutWidget leftConnect, rightConnect;
    ArrayList<String[]> attrList = new ArrayList<>();
    for (int i = 0; i < scoutWidgets.length; i++) {
      ScoutWidget widget = scoutWidgets[i];
      Direction ldir = Direction.RIGHT;
      Direction rdir = Direction.LEFT;
      if (i + 1 < scoutWidgets.length) {
        rightConnect = scoutWidgets[i + 1];
      }
      else {
        rightConnect = parentScoutWidget;
        rdir = Direction.RIGHT;
      }
      if (i > 0) {
        leftConnect = scoutWidgets[i - 1];
      }
      else {
        leftConnect = parentScoutWidget;
        ldir = Direction.LEFT;
      }

      attrList.clear();
      if (widget.isCandidateResizable(1)) {
        attrList.add(new String[]{ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_ZERO_DP});
      }

      scoutChainConnect(widget.mNlComponent, Direction.LEFT, leftConnect.mNlComponent, ldir, attrList);
      attrList.clear();
      String[] modes = {ATTR_LAYOUT_CHAIN_SPREAD, ATTR_LAYOUT_CHAIN_SPREAD_INSIDE, ATTR_LAYOUT_CHAIN_PACKED};
      if (i == 0) {
        String mode = modes[chain_mode.ordinal()];
        if (chain_mode != ChainMode.CHAIN_SPREAD) {
          attrList.add(new String[]{SHERPA_URI, ATTR_LAYOUT_HORIZONTAL_CHAIN_STYLE, mode});
        }
        int gap = margins[i];
        if (gap > 0) {
          attrList.add(new String[]{ANDROID_URI, ATTR_LAYOUT_MARGIN_START, Integer.toString(gap) + "dp"});
          attrList.add(new String[]{ANDROID_URI, ATTR_LAYOUT_MARGIN_LEFT, Integer.toString(gap) + "dp"});
        }
      }

      int gap = margins[i + 1];
      if (gap > 0) {
        attrList.add(new String[]{ANDROID_URI, ATTR_LAYOUT_MARGIN_END, Integer.toString(gap) + "dp"});
        attrList.add(new String[]{ANDROID_URI, ATTR_LAYOUT_MARGIN_RIGHT, Integer.toString(gap) + "dp"});
      }

      scoutChainConnect(widget.mNlComponent, Direction.RIGHT, rightConnect.mNlComponent, rdir, attrList);
      // TODO set  wrap & margins
    }
  }

  private static void creatVerticalChain(ScoutWidget[] scoutWidgets, ScoutWidget parentScoutWidget, ChainMode chain_mode, int[] margin) {
    Arrays.sort(scoutWidgets, ScoutArrange.sSortRecY);
    NlComponent parent = parentScoutWidget.mNlComponent;
    ScoutWidget topConnect, bottomConnect;
    ArrayList<String[]> attrList = new ArrayList<>();
    for (int i = 0; i < scoutWidgets.length; i++) {
      ScoutWidget widget = scoutWidgets[i];
      Direction tdir = Direction.BOTTOM;
      Direction bdir = Direction.TOP;
      if (i + 1 < scoutWidgets.length) {
        bottomConnect = scoutWidgets[i + 1];
      }
      else {
        bottomConnect = parentScoutWidget;
        bdir = Direction.BOTTOM;
      }

      if (i > 0) {
        topConnect = scoutWidgets[i - 1];
      }
      else {
        topConnect = parentScoutWidget;
        tdir = Direction.TOP;
      }

      attrList.clear();
      if (widget.isCandidateResizable(0)) {
        attrList.add(new String[]{ANDROID_URI, ATTR_LAYOUT_HEIGHT, VALUE_ZERO_DP});
      }

      scoutChainConnect(widget.mNlComponent, Direction.TOP, topConnect.mNlComponent, tdir, attrList);
      attrList.clear();
      String[] modes = {ATTR_LAYOUT_CHAIN_SPREAD, ATTR_LAYOUT_CHAIN_SPREAD_INSIDE, ATTR_LAYOUT_CHAIN_PACKED};
      if (i == 0) {
        String mode = modes[chain_mode.ordinal()];
        if (chain_mode != ChainMode.CHAIN_SPREAD) {
          attrList.add(new String[]{SHERPA_URI, ATTR_LAYOUT_VERTICAL_CHAIN_STYLE, mode});
        }
        int gap = margin[i];
        if (gap > 0) {
          attrList.add(new String[]{ANDROID_URI, ATTR_LAYOUT_MARGIN_TOP, Integer.toString(gap) + "dp"});
        }
      }

      int gap = margin[i + 1];
      if (gap > 0) {
        attrList.add(new String[]{ANDROID_URI, ATTR_LAYOUT_MARGIN_BOTTOM, Integer.toString(gap) + "dp"});
      }

      scoutChainConnect(widget.mNlComponent, Direction.BOTTOM, bottomConnect.mNlComponent, bdir, attrList);
      // TODO set  wrap & margins
    }
  }

  private static int[] getGapsX(ScoutWidget[] group) {
    int[] ret = new int[group.length - 1];
    for (int i = 0; i < ret.length; i++) {
      ret[i] = group[i + 1].getDpX() - (group[i].getDpX() + group[i].getDpWidth());
    }

    return ret;
  }

  private static int consistantGapX(ScoutWidget[] group) {
    int sum = 0;
    int count = 0;
    int max_error = 0;
    int len_1 = group.length - 1;

    for (int i = 0; i < len_1; i++) {
      int dist = group[i + 1].getDpX() - (group[i].getDpX() + group[i].getDpWidth());
      count++;
      sum += dist;
    }
    int avg_gap = sum / count; // average gap
    for (int i = 0; i < len_1; i++) {
      int dist = group[i + 1].getDpX() - (group[i].getDpX() + group[i].getDpWidth());
      max_error = Math.max(max_error, Math.abs(dist - avg_gap));
    }
    if (max_error > 4) {
      return -1;
    }
    return avg_gap;
  }

  private static int consistantGapY(ScoutWidget[] group) {
    int sum = 0;
    int count = 0;
    int max_error = 0;
    int len_1 = group.length - 1;

    for (int i = 0; i < len_1; i++) {
      int dist = group[i + 1].getDpY() - (group[i].getDpY() + group[i].getDpHeight());
      count++;
      sum += dist;
    }
    int avg_gap = sum / count; // average gap
    for (int i = 0; i < len_1; i++) {
      int dist = group[i + 1].getDpY() - (group[i].getDpY() + group[i].getDpHeight());
      max_error = Math.max(max_error, Math.abs(dist - avg_gap));
    }
    if (max_error > 4) {
      return -1;
    }
    return avg_gap;
  }

  /**
   * At this point the candidate horizontal chain may have two or more views to choose from
   * This assumes the chain is sorted in X so the comparison is
   */
  private static ScoutWidget[] removeOverlapsX(ScoutWidget[] group) {
    if (group == null || group.length < 3) {
      return group;
    }
    boolean overlap = false;
    for (int i = 1; i < group.length; i++) {
      if (xRangeOverlap(group[i - 1], group[i - 1])) { // one must go
        overlap = true;
        break;
      }
    }
    if (!overlap) {
      return group;
    }
    ArrayList<ScoutWidget> list = new ArrayList<>();
    for (int i = 0; i < group.length; i++) {
      if ((i + 1) < group.length && xRangeOverlap(group[i], group[i + 1])) { // one must go
        int p = medianY(group, i);
        int first = group[i].getDpY() + group[i].getDpHeight() / 2;
        int second = group[i + 1].getDpY() + group[i + 1].getDpHeight() / 2;
        list.add(Math.abs(first - p) > Math.abs(second - p) ? group[i] : group[i + 1]);
        i++;
      }
      else {
        list.add(group[i]);
      }
    }
    return list.toArray(new ScoutWidget[list.size()]);
  }

  /**
   * At this point the candidate horizontal chain may have two or more views to choose from
   * This assumes the chain is sorted in X so the comparison is
   */
  private static ScoutWidget[] removeOverlapsY(ScoutWidget[] group) {
    if (group == null || group.length < 3) {
      return group;
    }
    boolean overlap = false;
    for (int i = 1; i < group.length; i++) {
      if (yRangeOverlap(group[i - 1], group[i - 1])) { // one must go
        overlap = true;
        break;
      }
    }
    if (!overlap) {
      return group;
    }
    ArrayList<ScoutWidget> list = new ArrayList<>();
    for (int i = 0; i < group.length; i++) {
      if ((i + 1) < group.length && yRangeOverlap(group[i], group[i + 1])) { // one must go
        int p = medianX(group, i);
        int first = group[i].getDpX() + group[i].getDpWidth() / 2;
        int second = group[i + 1].getDpX() + group[i + 1].getDpWidth() / 2;
        list.add(Math.abs(first - p) > Math.abs(second - p) ? group[i] : group[i + 1]);
        i++;
      }
      else {
        list.add(group[i]);
      }
    }
    return list.toArray(new ScoutWidget[list.size()]);
  }

  private static int medianX(ScoutWidget[] group, int excluding) {
    int count = 0;
    int sum = 0;
    for (int i = 0; i < group.length; i++) {
      if (i < excluding || i > excluding + 1) {
        count++;
        sum += group[i].getDpX() + group[i].getDpWidth() / 2;
      }
    }
    return sum / count;
  }

  private static int medianY(ScoutWidget[] group, int excluding) {
    int count = 0;
    int sum = 0;
    for (int i = 0; i < group.length; i++) {
      if (i < excluding || i > excluding + 1) {
        count++;
        sum += group[i].getDpY() + group[i].getDpHeight() / 2;
      }
    }
    return sum / count;
  }

  public static ScoutWidget[] getCandiateListX(ScoutWidget base, ScoutWidget candidate,
                                               ScoutWidget[] list) {
    //int[] hist = new int[base.getDpWidth()];
    ScoutWidget a = candidate;
    ScoutWidget[] shortList = new ScoutWidget[list.length];
    int shortListCount = 1;
    shortList[0] = candidate;
    for (int j = 0; j < list.length; j++) {
      ScoutWidget b = list[j];
      if (b.mCheckedForChain || a == b) {
        continue;
      }
      if (yRangeOverlap(a, b)) { // overlap in y
        int ba_gap = b.getDpX() + b.getDpWidth() - a.getDpX();
        int ab_gap = a.getDpX() + a.getDpWidth() - b.getDpX();
        if (ba_gap * ab_gap <= 0) { // does not overlap in x
          shortList[shortListCount++] = b;
          int gap = (b.getDpX() > a.getDpX()) ? -ab_gap : -ba_gap;
        }
      }
    }
    if (shortListCount < 2) {
      return null;
    }

    shortList = Arrays.copyOf(shortList, shortListCount);
    for (int i = 0; i < shortList.length; i++) {
      shortList[i].mCheckedForChain = true;
    }
    Arrays.sort(shortList, new Comparator<ScoutWidget>() {
      @Override
      public int compare(ScoutWidget o1, ScoutWidget o2) {
        return Float.compare(o1.getDpX(), o2.getDpX());
      }
    });
    return shortList;
  }

  public static ScoutWidget[] getCandiateListY(ScoutWidget base, ScoutWidget candidate,
                                               ScoutWidget[] list) {
    //int[] hist = new int[base.getDpWidth()];
    ScoutWidget a = candidate;
    ScoutWidget[] shortList = new ScoutWidget[list.length];
    int shortListCount = 1;
    shortList[0] = candidate;
    for (int j = 0; j < list.length; j++) {
      ScoutWidget b = list[j];
      if (b.mCheckedForChain || a == b) {
        continue;
      }
      if (xRangeOverlap(a, b)) { // overlap in y
        int ba_gap = b.getDpY() + b.getDpHeight() - a.getDpY();
        int ab_gap = a.getDpY() + a.getDpHeight() - b.getDpY();
        if (ba_gap * ab_gap <= 0) { // does not overlap in x
          shortList[shortListCount++] = b;
          int gap = (b.getDpY() > a.getDpY()) ? -ab_gap : -ba_gap;
        }
      }
    }
    if (shortListCount < 2) {
      return null;
    }

    shortList = Arrays.copyOf(shortList, shortListCount);
    for (int i = 0; i < shortList.length; i++) {
      shortList[i].mCheckedForChain = true;
    }
    Arrays.sort(shortList, new Comparator<ScoutWidget>() {
      @Override
      public int compare(ScoutWidget o1, ScoutWidget o2) {
        return Float.compare(o1.getDpY(), o2.getDpY());
      }
    });
    return shortList;
  }

  static boolean xRangeOverlap(ScoutWidget a, ScoutWidget b) {
    return rangeOverlap(a.getDpX(), a.getDpWidth(), b.getDpX(), b.getDpWidth());
  }

  static boolean yRangeOverlap(ScoutWidget a, ScoutWidget b) {
    return rangeOverlap(a.getDpY(), a.getDpHeight(), b.getDpY(), b.getDpHeight());
  }

  static boolean rangeOverlap(int x1, int w1, int x2, int w2) {
    return (x1 < (x2 + w2)) && (x2 < (x1 + w1));
  }
}
