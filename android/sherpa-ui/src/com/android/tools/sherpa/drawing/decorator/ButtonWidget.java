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

import com.android.tools.sherpa.drawing.ViewTransform;
import com.google.tnt.solver.widgets.ConstraintWidget;

import java.awt.*;

/**
 * Button ui element
 */
public class ButtonWidget extends TextWidget {

    /**
     * Base constructor
     *
     * @param widget the widget we are decorating
     * @param text   the text content
     */
    public ButtonWidget(ConstraintWidget widget, String text) {
        super(widget, text);
        mHorizontalPadding = 4;
        mVerticalPadding = 8;
        mHorizontalMargin = 4;
        mVerticalMargin = 6;
        mToUpperCase = true;
        mAlignmentX = TEXT_ALIGNMENT_CENTER;
        mAlignmentY = TEXT_ALIGNMENT_CENTER;
        mWidget.setMinWidth(48);
        mWidget.setMinHeight(48);
    }

    @Override
    public void onPaintBackground(ViewTransform transform, Graphics2D g) {
        super.onPaintBackground(transform, g);
        if (mColorSet.drawBackground()) {
            int x = transform.getSwingX(mWidget.getDrawX() + mHorizontalMargin);
            int y = transform.getSwingY(mWidget.getDrawY() + mVerticalMargin);
            int w = transform.getSwingDimension(mWidget.getDrawWidth() - mHorizontalMargin * 2);
            int h = transform.getSwingDimension(mWidget.getDrawHeight() - mVerticalMargin * 2);
            int round = transform.getSwingDimension(5);
            Stroke stroke = g.getStroke();
            int strokeWidth = transform.getSwingDimension(3);
            g.setStroke(new BasicStroke(strokeWidth));
            g.drawRoundRect(x, y, w, h, round, round);
            g.setStroke(stroke);
        }
    }
}
