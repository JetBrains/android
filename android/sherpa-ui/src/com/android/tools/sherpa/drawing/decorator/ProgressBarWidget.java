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

import com.google.tnt.solver.widgets.ConstraintWidget;

/**
 * ProgressBar Widget decorator
 */
public class ProgressBarWidget extends WidgetDecorator {

    /**
     * Base constructor
     *
     * @param widget the widget we are decorating
     */
    public ProgressBarWidget(ConstraintWidget widget) {
        super(widget);
        wrapContent();
    }

    public void setTextSize( ) {
        wrapContent();
    }

    /**
     * Apply the size behaviour
     */
    @Override
    public void applyDimensionBehaviour() {
        wrapContent();
    }

    /**
     * Utility method computing the size of the widget if dimensions are set
     * to wrap_content, using the default font
     */
    protected void wrapContent() {
        mWidget.setMinWidth(100);
        mWidget.setMinHeight(30);
        int tw = mWidget.getMinWidth();
        int th = mWidget.getMinHeight();
        if (mWidget.getHorizontalDimensionBehaviour()
                == ConstraintWidget.DimensionBehaviour.WRAP_CONTENT) {
            mWidget.setWidth(tw);
        }
        if (mWidget.getVerticalDimensionBehaviour()
                == ConstraintWidget.DimensionBehaviour.WRAP_CONTENT) {
            mWidget.setHeight(th);
        }
        if (mWidget.getHorizontalDimensionBehaviour() ==
                ConstraintWidget.DimensionBehaviour.FIXED) {
            if (mWidget.getWidth() <= mWidget.getMinWidth()) {
                mWidget.setHorizontalDimensionBehaviour(
                        ConstraintWidget.DimensionBehaviour.WRAP_CONTENT);
            }
        }
        if (mWidget.getVerticalDimensionBehaviour() == ConstraintWidget.DimensionBehaviour.FIXED) {
            if (mWidget.getHeight() <= mWidget.getMinHeight()) {
                mWidget.setVerticalDimensionBehaviour(
                        ConstraintWidget.DimensionBehaviour.WRAP_CONTENT);
            }
        }
        mWidget.setBaselineDistance(0);
    }
}
