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
import android.support.constraint.solver.widgets.ConstraintWidget;

import java.awt.*;

/**
 * Radiobutton
 */
public class RadiobuttonWidget extends CheckboxWidget {

    /**
     * Base constructor
     *
     * @param widget the widget we are decorating
     * @param text   the text content
     */
    public RadiobuttonWidget(ConstraintWidget widget, String text) {
        super(widget, text);
    }

    @Override
    public void drawGraphic(Graphics2D g, int x, int y, int h, ViewTransform transform) {
        g.setColor(mTextColor.getColor());
        Stroke stroke = g.getStroke();
        int strokeWidth = transform.getSwingDimension(2);
        g.setStroke(new BasicStroke(strokeWidth));
        int margin = transform.getSwingDimension(6);
        x += margin;
        y += margin;
        h -= margin * 2;
        g.drawRoundRect(x, y, h, h, h, h);
        margin = transform.getSwingDimension(6);
        x += margin;
        y += margin;
        h -= margin * 2;
        g.setStroke(stroke);
        g.drawRoundRect(x, y, h, h, h, h);
        g.fillRoundRect(x, y, h, h, h, h);
    }
}
