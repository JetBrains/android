/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.sherpa.scout;

import com.google.tnt.solver.widgets.ConstraintWidget;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.BitSet;

/**
 * used in estimating the probability of a group being formed around a set of widgets.
 */
class ScoutCandidateGroup {
    int mNorth;
    int mSouth;
    int mEast;
    int mWest;
    Rectangle mRect;
    BitSet mContainSet = new BitSet();
    int mWidgetArea;
    int mGroupArea;
    int mCount = 0;
    Rectangle[] mRectList;
    int mGapArea;
    int mRows;
    int mCols;
    boolean mValidTable = false;

    /**
     * Create a candidate for grouping based on a rectangle
     *
     * @param group
     * @param list
     * @param rectList
     * @return Candidate for group or null if it would not be viable
     */
    static public ScoutCandidateGroup create(Rectangle group, ScoutWidget[] list,
            Rectangle[] rectList) {
        BitSet set = new BitSet();
        int count = 0;
        int groupArea = group.width * group.height;
        int widgetArea = 0;
        for (int i = 1; i < list.length; i++) {
            ScoutWidget widget = list[i];
            if (group.intersects(rectList[i]) && !group.contains(rectList[i])) {
                return null;
            }
            if (group.contains(rectList[i])) {
                set.set(i);
                count++;
                widgetArea += rectList[i].height * rectList[i].width;
            }
        }
        if (count < 4) {
            return null;
        }
        if (widgetArea * 2 < groupArea) { // if it is to much empty space
            return null;
        }

        ScoutCandidateGroup c = new ScoutCandidateGroup();
        c.mNorth = group.y;
        c.mSouth = group.y + group.height;
        c.mEast = group.x + group.width;
        c.mWest = group.x;
        c.mContainSet = set;
        c.mCount = count;
        c.mGroupArea = groupArea;
        c.mWidgetArea = widgetArea;
        c.mRect = new Rectangle(group);
        c.mRectList = buildRectList(set, rectList);
        return c;
    }

    /**
     * build list of constraintWidgets from the ScoutWidget list
     * @param list
     * @return
     */
    public ArrayList<ConstraintWidget> buildList(ScoutWidget[] list) {
        ArrayList<ConstraintWidget> ret = new ArrayList<ConstraintWidget>();
        int count = 0;
        for (int i = mContainSet.nextSetBit(0); i >= 0; i = mContainSet.nextSetBit(i + 1)) {
            ret.add(list[i].mConstraintWidget);
        }
        return ret;
    }

    /**
     * given a subset build a rectangle list of the subset of the original rectangle list
     *
     * @param subset
     * @param rectList
     * @return
     */
    private static Rectangle[] buildRectList(BitSet subset, Rectangle[] rectList) {
        Rectangle[] inside = new Rectangle[subset.cardinality()];

        int count = 0;
        for (int i = subset.nextSetBit(0); i >= 0; i = subset.nextSetBit(i + 1)) {
            inside[count++] = new Rectangle(rectList[i]);
        }
        return inside;
    }

    /**
     * compute the area of the gaps between rectangles in the list
     */
    public void computeGapAreas() {
        Rectangle gap = new Rectangle();
        int area = 0;
        for (int i = 0; i < mRectList.length; i++) {
            Rectangle rectangleA = mRectList[i];
            for (int j = i + 1; j < mRectList.length; j++) {
                Rectangle rectangleB = mRectList[j];
                boolean viable = calculateGap(rectangleA, rectangleB, gap);
                if (viable) {
                    for (int k = 0; k < mRectList.length; k++) {
                        if (k != j && k != i) {
                            if (gap.intersects(mRectList[k])) {
                                viable = false;
                                break;
                            }
                        }
                    }
                }
                if (viable) {
                    area += gap.width * gap.height;
                }
            }
        }
        mGapArea = area;
    }

    /**
     * build a array of rectangles representing the gaps
     * used for debugging purposes
     *
     * @return
     */
    public Rectangle[] computeGaps() {
        ArrayList<Rectangle> ret = new ArrayList<Rectangle>();
        Rectangle gap = new Rectangle();
        int area = 0;
        for (int i = 0; i < mRectList.length; i++) {
            Rectangle rectangleA = mRectList[i];
            for (int j = i + 1; j < mRectList.length; j++) {
                Rectangle rectangleB = mRectList[j];
                boolean viable = calculateGap(rectangleA, rectangleB, gap);
                if (viable) {
                    for (int k = 0; k < mRectList.length; k++) {
                        if (k != j && k != i) {
                            if (gap.intersects(mRectList[k])) {
                                viable = false;
                                break;
                            }
                        }
                    }
                }
                if (viable) {
                    ret.add(new Rectangle(gap));
                    area += gap.width * gap.height;
                }
            }
        }
        return ret.toArray(new Rectangle[ret.size()]);
    }

    /**
     * Calculate the gap rectangle between two rectangles
     *
     * @param a
     * @param b
     * @param gap
     * @return
     */
    private static boolean calculateGap(Rectangle a, Rectangle b, Rectangle gap) {
        if (a.intersects(b)) {
            gap.width = 0;
            return false;
        }
        int ax1 = a.x;
        int ax2 = a.x + a.width;
        int ay1 = a.y;
        int ay2 = a.y + a.height;

        int bx1 = b.x;
        int bx2 = b.x + b.width;
        int by1 = b.y;
        int by2 = b.y + b.height;

        int xOverlap = Math.min(ax2, bx2) - Math.max(ax1, bx1);
        int yOverlap = Math.min(ay2, by2) - Math.max(ay1, by1);

        if (xOverlap <= 0 && yOverlap <= 0) {
            gap.width = 0;
            return false;
        }
        if (xOverlap > 0) {
            gap.x = Math.max(ax1, bx1);
            gap.y = (ay1 > by1) ? by2 : ay2;
            gap.width = xOverlap;
            gap.height = -yOverlap;
        }
        if (yOverlap > 0) {
            gap.x = (ax1 > bx1) ? bx2 : ax2;
            gap.y = Math.max(ay1, by1);
            gap.width = -xOverlap;
            gap.height = yOverlap;
        }
        return true;
    }

    /**
     * Calculated the fraction of the area filled with widgets
     *
     * @return
     */
    public float fractionFilled() {
        return mWidgetArea / (float) mGroupArea;
    }

    /**
     * does another group contain this group
     *
     * @param candidate
     * @return
     */
    public boolean contains(ScoutCandidateGroup candidate) {
        return mRect.contains(candidate.mRect);
    }

    /**
     * estimate the probability of this being a good group
     *
     * @return
     */
    public float calcProb() {
        float empty = mGroupArea - (mWidgetArea + mGapArea);
        empty /= mCount * mGroupArea;
        empty = (1 - empty); // counts for half of your score
        float tableProb = calculateTableConfidence(mRectList);
        float prob = (tableProb + empty + (1 - 1 / ((float) mCount))) / 3;
        return prob;
    }

    /**
     * quick test if this would even make a good candidate group
     *
     * @return
     */
    public boolean viable() {
        return (mWidgetArea + mGapArea) / (float) mGroupArea > .40f;
    }

    /**
     * calculate the probability of this being a table
     *
     * @param widgets
     */
    public float calculateTableConfidence(Rectangle[] widgets) {
        int[][] bounds;
        int[] mColAlign;
        mValidTable = true;

        bounds = new int[4][widgets.length];
        for (int i = 0; i < widgets.length; i++) {
            Rectangle widget = widgets[i];
            bounds[0][i] = widget.y;
            bounds[1][i] = bounds[0][i] + widget.height;
            bounds[2][i] = widget.x;
            bounds[3][i] = bounds[2][i] + widget.width;
        }
        mRows = Utils.gaps(bounds[0], bounds[1]);
        mCols = Utils.gaps(bounds[2], bounds[3]);
        int[] r = Utils.cells(bounds[0], bounds[1]);
        int[] c = Utils.cells(bounds[2], bounds[3]);

        Rectangle[][] table = new Rectangle[mCols][mRows];
        for (int i = 0; i < widgets.length; i++) {
            Rectangle widget = widgets[i];

            int row = Utils.getPosition(r, widget.y, widget.y + widget.height);
            int col = Utils.getPosition(c, widget.x, widget.x + widget.width);
            if (row == -1 || col == -1) { // multi cell span not supported
                mValidTable = false;
                return 0;
            }
            table[col][row] = widget;
        }

        // add support for skipping
        int skips = 0;
        for (int row = 0; row < mRows; row++) {
            for (int col = 0; col < mCols; col++) {
                if (table[col][row] == null) {
                    skips++;
                }
            }
        }

        // Compute column alignment
        mColAlign = new int[mCols];
        float sumprob = 0;
        for (int col = 0; col < table.length; col++) {
            Rectangle[] rec = table[col];
            float prob = alignmentProbability(rec);
            sumprob = prob + sumprob - (sumprob * prob); // sum independent probabilities
        }
        return sumprob;
    }

    /**
     * Infer alignment for each column
     *
     * @param widget
     * @return
     */
    private float alignmentProbability(Rectangle[] widget) {
        float[] start = new float[widget.length];
        float[] center = new float[widget.length];
        float[] end = new float[widget.length];
        float widthSum = 0;
        int count = 0;
        for (int i = 0; i < end.length; i++) {
            if (widget[i] == null) {
                start[i] = Float.NaN;
                end[i] = Float.NaN;
                center[i] = Float.NaN;
                continue;
            }
            start[i] = widget[i].x;
            end[i] = start[i] + widget[i].width;
            center[i] = (start[i] + end[i]) / 2;
            widthSum += widget[i].width;
            count++;
        }
        float startDiv = standardDeviation(start);
        float centerDiv = standardDeviation(center);
        float endDiv = standardDeviation(end);
        if (count > 2) {
            return 1 - Math.min(startDiv, Math.min(centerDiv, endDiv)) / (widthSum / count);
        }
        return 0;
    }

    /**
     * Calculate the standard deviation skipping Nan floats
     *
     * @param pos
     * @return
     */
    private static float standardDeviation(float[] pos) {
        float sum = 0.f;
        float sumSqr = 0.f;
        int count = 0;
        for (int i = 0; i < pos.length; i++) {
            if (Float.isNaN(pos[i])) {
                continue;
            }
            count++;
            sum += pos[i];
            sumSqr += pos[i] * pos[i];
        }
        return (float) Math.sqrt(sumSqr / count - (sum / count) * (sum / count));
    }
}
