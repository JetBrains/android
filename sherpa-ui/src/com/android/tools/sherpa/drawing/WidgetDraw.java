/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tools.sherpa.drawing;

import android.support.constraint.solver.widgets.ConstraintTableLayout;
import android.support.constraint.solver.widgets.ConstraintWidget;
import android.support.constraint.solver.widgets.Guideline;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;

import static com.android.tools.sherpa.drawing.ConnectionDraw.sFont;

/**
 * Utility drawing class
 * Contains functions dealing with drawing widgets
 */
public class WidgetDraw {

    public static final long TOOLTIP_DELAY = 800; // in ms

    // TODO: fix the loading image pattern
    public static Image sGuidelinePercent = null;
    public static Image sGuidelineArrowLeft = null;
    public static Image sGuidelineArrowRight = null;
    public static Image sGuidelineArrowUp = null;
    public static Image sGuidelineArrowDown = null;

    private static final RoundRectangle2D.Float sCachedRoundRect = new RoundRectangle2D.Float();

    // Used for drawing the tooltips

    private static final Stroke sLineShadowStroke = new BasicStroke(5);
    private static final Stroke sBasicStroke = new BasicStroke(1);
    private static final Polygon sTooltipTriangleDown = new Polygon();
    private static final Polygon sTooltipTriangleUp = new Polygon();
    private static final int sArrowBase = 3;
    private static final int sArrowHeight = 3;

    static {
        sTooltipTriangleDown.addPoint(-sArrowBase, 0);
        sTooltipTriangleDown.addPoint(0, sArrowHeight);
        sTooltipTriangleDown.addPoint(sArrowBase, 0);
        sTooltipTriangleUp.addPoint(-sArrowBase, 0);
        sTooltipTriangleUp.addPoint(0, -sArrowHeight);
        sTooltipTriangleUp.addPoint(sArrowBase, 0);
    }

    /**
     * Utility function to draw a tooltip
     *
     * @param g        the graphics context
     * @param colorSet the current colorset
     * @param lines    the text we want to show
     * @param x        the tooltip anchor point x coordinate
     * @param y        the tooltip anchor point y coordinate
     * @param above    the tooltip should be drawn above the anchor point if true, other it will be drawn below
     */
    public static void drawTooltip(Graphics2D g, ColorSet colorSet, String[] lines, int x, int y,
            boolean above) {
        if (lines == null) {
            return;
        }
        Font prefont = g.getFont();
        Color precolor = g.getColor();
        Stroke prestroke = g.getStroke();

        g.setFont(sFont);
        FontMetrics fm = g.getFontMetrics(sFont);

        int offset = 4 * sArrowBase;
        int margin = 2;
        int padding = 5;
        int textWidth = 0;
        int textHeight = 2 * padding;
        for (String line : lines) {
            textWidth = Math.max(textWidth, fm.stringWidth(line));
            int th = (int) fm.getStringBounds(line, g).getHeight();
            textHeight += th + margin;
        }
        textHeight -= margin;
        textWidth += 2 * padding;
        int rectX = x - offset;
        int rectY = y - textHeight - 2 * sArrowHeight - offset;
        if (!above) {
            rectY = y + offset + 2 * sArrowHeight;
        }

        Polygon triangle = sTooltipTriangleDown;
        int triangleY = rectY + textHeight;
        if (!above) {
            triangle = sTooltipTriangleUp;
            triangleY = rectY;
        }

        sCachedRoundRect.setRoundRect(rectX, rectY, textWidth, textHeight, 2, 2);

        g.setColor(colorSet.getBackground());
        g.setStroke(sLineShadowStroke);
        triangle.translate(x, triangleY);
        g.fillPolygon(triangle);
        g.draw(triangle);
        triangle.translate(-x, -triangleY);
        g.fill(sCachedRoundRect);
        g.draw(sCachedRoundRect);

        g.setColor(colorSet.getTooltipBackground());
        g.setStroke(sBasicStroke);
        triangle.translate(x, triangleY);
        g.fillPolygon(triangle);
        g.draw(triangle);
        triangle.translate(-x, -triangleY);
        g.fill(sCachedRoundRect);
        g.draw(sCachedRoundRect);

        int ty = rectY + padding;
        for (int i = 0; i < lines.length; i++) {
            int tw = fm.stringWidth(lines[i]);
            int tx = rectX + textWidth / 2 - tw / 2; // x - tw / 2;
            g.setColor(colorSet.getTooltipText());
            g.drawString(lines[i], tx, ty + fm.getMaxAscent());
            ty += fm.getStringBounds(lines[i], g).getHeight() + margin;
        }

        g.setFont(prefont);
        g.setColor(precolor);
        g.setStroke(prestroke);
    }

}
