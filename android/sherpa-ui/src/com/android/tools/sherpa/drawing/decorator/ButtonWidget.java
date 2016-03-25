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

import java.awt.Graphics2D;

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
        mPadding = 2;
    }

    @Override
    public void onPaintBackground(ViewTransform transform, Graphics2D g) {
        super.onPaintBackground(transform, g);
        if (WidgetDecorator.isShowFakeUI()) {
            int x = transform.getSwingX(mWidget.getDrawX() + mPadding);
            int y = transform.getSwingX(mWidget.getDrawY() + mPadding);
            int w = transform.getSwingDimension(mWidget.getDrawWidth() - mPadding * 2);
            int h = transform.getSwingDimension(mWidget.getDrawHeight() - mPadding * 2);
            g.drawRect(x, y, w, h);
        }
    }
}
