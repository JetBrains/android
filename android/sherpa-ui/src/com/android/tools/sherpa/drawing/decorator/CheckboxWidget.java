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
import android.constraint.solver.widgets.ConstraintWidget;

import java.awt.*;

public class CheckboxWidget extends TextWidget {

    /**
     * Base constructor
     *
     * @param widget the widget we are decorating
     * @param text   the text content
     */
    public CheckboxWidget(ConstraintWidget widget, String text) {
        super(widget, text);
        mAlignmentY = TEXT_ALIGNMENT_CENTER;
        mWidget.setMinWidth(32);
        mWidget.setMinHeight(32);
    }

    @Override
    protected void wrapContent() {
        if (mWidget == null) {
            return;
        }
        if (!TextWidget.DO_WRAP) {
            return;
        }
        super.wrapContent();
        int extra = mWidget.getMinHeight() + 2 * mHorizontalPadding;
        mWidget.setMinWidth(mWidget.getMinWidth() + extra);
        mWidget.setMinHeight(mWidget.getMinHeight());
        mWidget.setDimension(0, 0);
    }

    @Override
    public void onPaintBackground(ViewTransform transform, Graphics2D g) {
        super.onPaintBackground(transform, g);
        if (mColorSet.drawBackground()) {
            int x = transform.getSwingX(mWidget.getDrawX());
            int y = transform.getSwingY(mWidget.getDrawY());
            int h = transform.getSwingDimension(mWidget.getDrawHeight());
            drawGraphic(g, x, y, h, transform);
        }
    }

    public void drawGraphic(Graphics2D g, int x, int y, int h, ViewTransform transform) {
        Stroke stroke = g.getStroke();
        int strokeWidth = transform.getSwingDimension(3);
        g.setStroke(new BasicStroke(strokeWidth));
        g.setColor(mTextColor.getColor());
        int margin = transform.getSwingDimension(7);
        x += margin;
        y += margin;
        h -= margin * 2;
        g.drawRoundRect(x, y, h, h, 4, 4);
        margin = transform.getSwingDimension(6);
        x += margin;
        y += margin;
        h -= margin * 2;
        g.drawLine(x, y + h / 2, x + h / 3, y + h);
        g.drawLine(x + h / 3, y + h, x + h, y);
        g.setStroke(stroke);
    }

    @Override
    protected void drawText(ViewTransform transform, Graphics2D g, int x, int y) {
        int h = mWidget.getDrawHeight();
        super.drawText(transform, g, x + h, y);
    }
}
