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

import com.google.tnt.solver.widgets.ConstraintTableLayout;
import com.google.tnt.solver.widgets.ConstraintWidget;


/**
 * class used to Infer group properties
 */
public class ScoutGroup {
    int[][] bounds;
    int mRows;
    int mCols;
    int[] mColAlign;
    boolean mSupported = true;
    public static final int ALIGN_LEFT = ConstraintTableLayout.ALIGN_LEFT;
    public static final int ALIGN_RIGHT = ConstraintTableLayout.ALIGN_RIGHT;
    private static final int ALIGN_CENTER = ConstraintTableLayout.ALIGN_CENTER;

    /**
     * Create an Scout Group class
     *
     * @param widgets list of widgets to analyze
     */
    ScoutGroup(ConstraintWidget[] widgets) {
        bounds = new int[4][widgets.length];
        for (int i = 0; i < widgets.length; i++) {
            ConstraintWidget widget = widgets[i];
            bounds[0][i] = widget.getY();
            bounds[1][i] = bounds[0][i] + widget.getHeight();
            bounds[2][i] = widget.getX();
            bounds[3][i] = bounds[2][i] + widget.getWidth();
        }
        mRows = Utils.gaps(bounds[0], bounds[1]);
        mCols = Utils.gaps(bounds[2], bounds[3]);
        int[] r = Utils.cells(bounds[0], bounds[1]);
        int[] c = Utils.cells(bounds[2], bounds[3]);

        ConstraintWidget[][] table = new ConstraintWidget[mCols][mRows];
        for (int i = 0; i < widgets.length; i++) {
            ConstraintWidget widget = widgets[i];

            int row = Utils.getPosition(r, widget.getY(), widget.getY() + widget.getHeight());
            int col = Utils.getPosition(c, widget.getX(), widget.getX() + widget.getWidth());
            if (row == -1 || col == -1) { // multi cell span not supported
                mSupported = false;
                return;
            }
            table[col][row] = widget;
        }

        // add support for skipping
        int skip = 0;
        for (int row = 0; row < mRows; row++) {
            for (int col = 0; col < mCols; col++) {
                if (table[col][row] == null) {
                    skip++;
                } else {
                    if (skip > 0) {
                        table[col][row].setContainerItemSkip(skip);
                        skip = 0;
                    } else {
                        table[col][row].setContainerItemSkip(0);
                    }
                }
            }
        }

        // Compute column alignment
        mColAlign = new int[mCols];
        for (int i = 0; i < table.length; i++) {
            ConstraintWidget[] constraintWidgets = table[i];
            mColAlign[i] = inferAlignment(constraintWidgets);
        }
    }

    /**
     * Infer alignment for each column
     *
     * @param widget
     * @return
     */
    private int inferAlignment(ConstraintWidget[] widget) {
        float[] start = new float[widget.length];
        float[] center = new float[widget.length];
        float[] end = new float[widget.length];

        for (int i = 0; i < end.length; i++) {
            if (widget[i] == null) {
                start[i] = Float.NaN;
                end[i] = Float.NaN;
                center[i] = Float.NaN;
                continue;
            }
            start[i] = widget[i].getX();
            end[i] = start[i] + widget[i].getWidth();
            center[i] = (start[i] + end[i]) / 2;
        }
        float startDiv = standardDeviation(start);
        float centerDiv = standardDeviation(center);
        float endDiv = standardDeviation(end);

        if (endDiv > startDiv && centerDiv > startDiv) {
            return ALIGN_LEFT;
        } else if (startDiv > endDiv && centerDiv > endDiv) {
            return ALIGN_RIGHT;
        }
        return ALIGN_CENTER;
    }

    /**
     * Calculate the standard deviation skipping Nan floats
     *
     * @param pos
     * @return
     */
    private float standardDeviation(float[] pos) {
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
