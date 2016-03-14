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

package com.android.tools.sherpa.drawing.decorator;

import com.android.tools.sherpa.structure.Selection;
import com.android.tools.sherpa.drawing.ViewTransform;
import com.android.tools.sherpa.drawing.WidgetDraw;
import com.google.tnt.solver.widgets.ConstraintTableLayout;
import com.google.tnt.solver.widgets.ConstraintWidget;
import com.google.tnt.solver.widgets.Guideline;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;

/**
 * Decorator for a table container
 */
public class TableDecorator extends WidgetDecorator {

    private ArrayList<TableClickTarget> mTableClickTargets = new ArrayList<TableClickTarget>();

    /**
     * Simple helper class to keep track of hit targets on the table layout
     */
    class TableClickTarget {
        private Rectangle mBounds = new Rectangle();
        private final ConstraintTableLayout mTable;
        private final int mColumn;

        public TableClickTarget(ConstraintTableLayout table, int column, int x, int y, int w,
                int h) {
            mTable = table;
            mColumn = column;
            mBounds.setBounds(x, y, w, h);
        }

        public boolean contains(int x, int y) {
            return mBounds.contains(x, y);
        }

        public ConstraintTableLayout getTable() {
            return mTable;
        }

        public int getColumn() {
            return mColumn;
        }
    }

    /**
     * Base constructor
     *
     * @param widget the widget we are decorating
     */
    public TableDecorator(ConstraintWidget widget) {
        super(widget);
    }

    /**
     * Override the default paint method to draw the table controls when selected
     *
     * @param transform the view transform
     * @param g         the graphics context
     * @return          true if we need to be called again (i.e. if we are animating)
     */
    @Override
    public boolean onPaint(ViewTransform transform, Graphics2D g) {
        boolean needsRepaint = super.onPaint(transform, g);
        if (isSelected()) {
            ConstraintTableLayout table = (ConstraintTableLayout) mWidget;
            WidgetDraw.drawTableControls(transform, g, table);
        }
        return needsRepaint;
    }

    /**
     * Handle mouse press event to add click targets. TODO: clean this up.
     *
     * @param x         mouse x coordinate
     * @param y         mouse y coordinate
     * @param transform view transform
     * @param selection the current selection of widgets
     */
    @Override
    public ConstraintWidget mousePressed(int x, int y, ViewTransform transform, Selection selection) {
        ConstraintTableLayout table = (ConstraintTableLayout) mWidget;
        mTableClickTargets.clear();
        ArrayList<Guideline> vertical = table.getVerticalGuidelines();
        int l = transform.getSwingX(table.getDrawX());
        int t = transform.getSwingY(table.getDrawY());
        int column = 0;
        TableClickTarget firstTarget =
                new TableClickTarget(table, column++, l, t - 20 - 4, 20, 20);
        mTableClickTargets.add(firstTarget);
        for (ConstraintWidget v : vertical) {
            int bx = transform.getSwingX(v.getX()) + l;
            TableClickTarget target =
                    new TableClickTarget(table, column++, bx, t - 20 - 4, 20, 20);
            mTableClickTargets.add(target);
        }
        ConstraintWidget widgetHit = null;
        if (mTableClickTargets.size() > 0) {
            for (TableClickTarget tableClickTarget : mTableClickTargets) {
                if (tableClickTarget.contains(x, y)) {
                    widgetHit = tableClickTarget.getTable();
                    break;
                }
            }
            if (selection.isEmpty()) {
                mTableClickTargets.clear();
            }
        }
        return widgetHit;
    }

    /**
     * Handle mouse release event to check for our table click targets
     *
     * @param x         mouse x coordinate
     * @param y         mouse y coordinate
     * @param transform view transform
     * @param selection the current selection of widgets
     */
    @Override
    public void mouseRelease(int x, int y, ViewTransform transform, Selection selection) {
        for (TableClickTarget target : mTableClickTargets) {
            if (target.contains(x, y)) {
                ConstraintTableLayout table = target.getTable();
                int column = target.getColumn();
                table.cycleColumnAlignment(column);
            }
        }
    }
}
