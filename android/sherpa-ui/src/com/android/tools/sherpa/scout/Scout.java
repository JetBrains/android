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

import com.android.tools.sherpa.structure.WidgetsScene;
import android.constraint.solver.widgets.ConstraintTableLayout;
import android.constraint.solver.widgets.ConstraintWidget;
import android.constraint.solver.widgets.ConstraintWidgetContainer;

import java.awt.Rectangle;
import java.util.ArrayList;

/**
 * Main entry for the Scout Inference engine.
 * All external access should be through this class
 * TODO support Stash / merge constraints table etc.
 */
public class Scout {

    public enum Arrange {
        AlignVerticallyTop, AlignVerticallyMiddle, AlignVerticallyBottom, AlignHorizontallyLeft,
        AlignHorizontallyCenter, AlignHorizontallyRight, DistributeVertically,
        DistributeHorizontally, VerticalPack, HorizontalPack, ExpandVertically, AlignBaseline,
        ExpandHorizontally, CenterHorizontallyInParent, CenterVerticallyInParent, CenterVertically,
        CenterHorizontally
    }

    private static int sMargin = 8;

    public static int getMargin() {
        return sMargin;
    }

    public static void setMargin(int margin) {
        sMargin = margin;
    }

    public static void arrangeWidgets(Arrange type, ArrayList<ConstraintWidget> widgets,
            boolean applyConstraint) {
        ScoutArrange.align(type, widgets, applyConstraint);
    }

    /**
     * Shrink Wraps around containing widgets
     * @param root
     */
    public static void wrap(ConstraintWidgetContainer root) {
        ArrayList<ConstraintWidget> widgets = root.getChildren();
        Rectangle all = ScoutArrange.getBoundingBox(widgets);
        all.x -= sMargin;
        all.y -= sMargin;
        all.width += sMargin * 2;
        all.height += sMargin * 2;
        for (ConstraintWidget widget : widgets) {
            widget.setX(widget.getX() - all.x);
            widget.setY(widget.getY() - all.y);
        }
        root.setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.FIXED);
        root.setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.FIXED);
        root.setWidth(all.width);
        root.setHeight(all.height);
    }

    /**
     * Given a collection of widgets evaluates probability of a connection
     * and makes connections
     *
     * @param list collection of widgets to connect
     */
    public static void inferConstraints(WidgetsScene list) {
        inferConstraints(list.getRoot());
    }

    /**
     * Recursive decent of widget tree inferring constraints on ConstraintWidgetContainer
     *
     * @param base
     */
    private static void inferConstraints(ConstraintWidgetContainer base) {
        if (base.handlesInternalConstraints()) {
            return;
        }
        int preX = base.getX();
        int preY = base.getY();
        base.setX(0);
        base.setY(0);
        for (ConstraintWidget constraintWidget : base.getChildren()) {
            if (constraintWidget instanceof ConstraintWidgetContainer) {
                inferConstraints((ConstraintWidgetContainer) constraintWidget);
            }
        }

        ArrayList<ConstraintWidget> list = new ArrayList<>(base.getChildren());
        list.add(0, base);

        ConstraintWidget[] widgets = list.toArray(new ConstraintWidget[list.size()]);
        ScoutWidget.computeConstraints(ScoutWidget.create(widgets));
        base.setX(preX);
        base.setY(preY);
    }

    /**
     * Given a collection of widgets evaluates probability of a connection
     * and makes connections
     *
     * @param list collection of widgets to connect
     */
    public static ConstraintWidget[] inferTableList(WidgetsScene list) {
        for (ConstraintWidget widget : list.getWidgets()) {
            widget.resetAnchors();
        }
        return inferTableList(list.getRoot());
    }

    /**
     * Recursive decent of widget tree inferring constraints on ConstraintWidgetContainer
     *
     * @param base
     */
    private static ConstraintWidget[] inferTableList(ConstraintWidgetContainer base) {
        if (base.handlesInternalConstraints()) {
            return null;
        }

        for (ConstraintWidget constraintWidget : base.getChildren()) {
            if (constraintWidget instanceof ConstraintWidgetContainer) {
                inferConstraints((ConstraintWidgetContainer) constraintWidget);
            }
        }

        ArrayList<ConstraintWidget> list = new ArrayList<>(base.getChildren());
        list.add(0, base);

        ConstraintWidget[] widgets = list.toArray(new ConstraintWidget[list.size()]);
        ConstraintWidget[] iw = ScoutGroupInference.computeGroups(ScoutWidget.create(widgets));
        if (iw != null && iw.length > 0) {
            return iw;
        }
        return null;
    }

    /**
     * Given a collection of widgets infer a good group choice
     *
     * @param widgets
     */
    public static ConstraintTableLayout inferGroup(ArrayList<ConstraintWidget> widgets) {
        ScoutGroup group = new ScoutGroup(widgets.toArray(new ConstraintWidget[widgets.size()]));
        ConstraintTableLayout ret = new ConstraintTableLayout();
        if (group.mCols * group.mRows >= widgets.size()) {
            ret.setNumRows(group.mRows);
            ret.setNumCols(group.mCols);
        }
        if (group.mSupported) {
            for (int i = 0; i < group.mCols; i++) {
                ret.setColumnAlignment(i, group.mColAlign[i]);
            }
        }
        return ret;
    }
}
