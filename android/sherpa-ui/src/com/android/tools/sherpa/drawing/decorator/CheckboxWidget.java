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
import java.awt.image.BufferedImage;

public class CheckboxWidget extends TextWidget {

    private BufferedImage mImage;

    /**
     * Base constructor
     *
     * @param widget the widget we are decorating
     * @param text   the text content
     */
    public CheckboxWidget(ConstraintWidget widget, String text) {
        super(widget, text);
    }

    public String getImagePath() {
        return "/blueprint_checkbox_on.png";
    }

    @Override
    protected void wrapContent() {
        super.wrapContent();
        if (mWidget == null) {
            return;
        }
        if (mImage == null) {
            mImage = WidgetDecorator.loadImage(getImagePath());
        }
        int extra = mImage.getWidth() + 2 * mPadding;
        mWidget.setMinWidth(mWidget.getMinWidth() + extra);
        mWidget.setMinHeight(mWidget.getMinHeight());
        mWidget.setDimension(0, 0);
    }

    @Override
    public void onPaintBackground(ViewTransform transform, Graphics2D g) {
        super.onPaintBackground(transform, g);
        if (WidgetDecorator.isShowFakeUI()) {
            int x = transform.getSwingX(mWidget.getDrawX());
            int y = transform.getSwingX(mWidget.getDrawY());
            int h = transform.getSwingDimension(mWidget.getDrawHeight());
            int ih = transform.getSwingDimension(mImage.getHeight());
            g.drawImage(mImage, x, y + (h - ih) / 2, ih, ih, null);
        }
    }

    protected void drawText(ViewTransform transform, Graphics2D g, int x, int y) {
        super.drawText(transform, g, x + mImage.getWidth(), y);
    }
}
