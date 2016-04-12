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

import com.android.tools.sherpa.drawing.SceneDraw;
import com.android.tools.sherpa.drawing.ViewTransform;
import com.google.tnt.solver.widgets.ConstraintWidget;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/**
 * ImageView Widget widget decorator
 */
public class ImageViewWidget extends WidgetDecorator {
    static double[] move = { 196.0, 319.99908 };
    static double[][] curve = {
            { 3.9168854, -4.08313, 18.501312, -17.415588, 23.501312, -24.498688 },
            { 5.0, -7.08313, 1.7489014, -10.666687, 6.4986877, -18.0 },
            { 4.7497864, -7.3333435, 14.166672, -19.916443, 22.0, -26.0 },
            { 7.8333282, -6.083557, 13.416443, 2.9986877, 25.0, -10.501312 },
            { 11.583557, -13.5, 35.334656, -58.498688, 44.501312, -70.49869 },
            { 9.1666565, -12.0, 6.3320312, 1.0822296, 10.498688, -1.5013123 },
            { 4.1666565, -2.5835571, 11.167969, -11.5, 14.501312, -14.0 },
            { 3.3333435, -2.5, -4.417755, 9.083115, 5.4986877, -1.0 },
            { 9.916443, -10.083115, 38.5, -53.41558, 54.0, -59.498695 },
            { 15.5, -6.0831146, 30.666656, 18.416885, 39.0, 23.0 },
            { 8.3333435, 4.5831146, 3.1666565, -4.0013123, 11.0, 4.4986877 },
            { 7.8333435, 8.500008, 19.249786, 33.417763, 36.0, 46.50132 },
            { 16.750214, 13.083542, 50.917786, 27.250214, 64.50134, 32.0 },
            { 13.583496, 4.749771, 6.833313, -12.251099, 17.0, -3.5013123 },
            { 10.166626, 8.749771, 26.166626, 36.74977, 44.0, 56.0 },
            { 17.833313, 19.250214, 51.750183, 49.001312, 63.0, 59.501312 },
            { 11.249756, 10.5, 3.7489014, 2.9155579, 4.498657, 3.4986877 }
    };
    static Path2D sPath2D = new Path2D.Float();
    static Path2D sClosedPath2D;
    static int sPathWidth;
    static int sPathHeight;

    private AffineTransform mTransform = new AffineTransform();
    private Font mFont = new Font("Helvetica", Font.PLAIN, 12);

    static {
        sPath2D.moveTo(move[0], move[1]);
        double cx = move[0];
        double cy = move[1];
        double cpx = cx;
        double cpy = cy;

        for (int i = 0; i < curve.length; i++) {
            double val[] = curve[i];
            int k = 0;
            sPath2D.curveTo(cx + val[k + 0], cy + val[k + 1], cx + val[k + 2],
                    cy + val[k + 3], cx + val[k + 4], cy + val[k + 5]);
            cpx = cx + val[k + 2];
            cpy = cy + val[k + 3];
            cx += val[k + 4];
            cy += val[k + 5];
        }
        Rectangle bounds = sPath2D.getBounds();
        sClosedPath2D = (Path2D) sPath2D.clone();
        sClosedPath2D.lineTo(cx,cy+=20);
        sClosedPath2D.lineTo(move[0],cy);
        sClosedPath2D.closePath();
        System.out.println("bounds = "+sClosedPath2D.getBounds());
        AffineTransform transform = new AffineTransform();
        transform.translate(-bounds.x, -bounds.y);
        sPath2D.transform(transform);
        sClosedPath2D.transform(transform);
        double scale = 100 / (double) Math.max(bounds.width, bounds.height);
        transform.setToIdentity();
        transform.scale(scale, scale);
        sPath2D.transform(transform);
        sClosedPath2D.transform(transform);
        bounds = sPath2D.getBounds();
        sPathWidth = bounds.width;
        sPathHeight = bounds.height;
    }

    /**
     * Base constructor
     *
     * @param widget the widget we are decorating
     */
    public ImageViewWidget(ConstraintWidget widget) {
        super(widget);
        wrapContent();
    }

    public void setTextSize() {
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
        mWidget.setMinHeight(100);
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

    public void onPaintBackground(ViewTransform transform, Graphics2D g) {
        if (mColorSet == null) {
            return;
        }
        super.onPaintBackground(transform, g);
        int l = transform.getSwingX(mWidget.getDrawX());
        int t = transform.getSwingY(mWidget.getDrawY());
        int w = transform.getSwingDimension(mWidget.getDrawWidth());
        int h = transform.getSwingDimension(mWidget.getDrawHeight());
        if (WidgetDecorator.isShowFakeUI()) {
            fakeUIPaint(transform, g, mWidget.getDrawX(), mWidget.getDrawY());
        }
    }

    Graphics2D getClipGraphics(ViewTransform transform, Graphics2D g) {
        int l = transform.getSwingX(mWidget.getDrawX());
        int t = transform.getSwingY(mWidget.getDrawY());
        int w = transform.getSwingDimension(mWidget.getDrawWidth());
        int h = transform.getSwingDimension(mWidget.getDrawHeight());
        Graphics2D g2 = (Graphics2D) g.create(l, t, w, h);
        return g2;
    }

    protected void fakeUIPaint(ViewTransform transform, Graphics2D g, int x, int y) {
        int tx = transform.getSwingX(x);
        int ty = transform.getSwingY(y);
        int h = transform.getSwingDimension(mWidget.getDrawHeight());
        int w = transform.getSwingDimension(mWidget.getDrawWidth());

        mTransform.setToIdentity();
        double sw = w/(double)sPathWidth;
        double sh = h/(double)sPathHeight;

        double s = Math.max(sw,sh);
        double dx = (w- sPathWidth*s)/2;
        double dy = (h- sPathHeight*s)/2;
        mTransform.translate(dx, 0);
        mTransform.scale(s, s);
        Shape shape ;

        Graphics2D clipGraphics = ((Graphics2D) g.create(tx, ty, w, h));

        clipGraphics.setColor(ColorTheme.updateBrightness(mColorSet.getBlueprintBackground(), 0.8f));

        shape = sClosedPath2D.createTransformedShape(mTransform);
        clipGraphics.fill(shape);

        shape = sPath2D.createTransformedShape(mTransform);
        clipGraphics.setColor(mColorSet.getBlueprintText());
        clipGraphics.draw(shape);

        String text = "ImageView";
        int originalSize = mFont.getSize();
        float scaleSize = transform.getSwingDimension(originalSize);
        g.setFont(mFont.deriveFont(scaleSize));
        FontMetrics fontMetrics = g.getFontMetrics();
        g.setColor(Color.WHITE);
        Rectangle2D bounds = fontMetrics.getStringBounds(text, g);
        g.drawString(text, tx + (int) ((w - bounds.getWidth()) / 2f), ty + (int) (h - (h - bounds.getHeight()) / 3f));
    }
}
