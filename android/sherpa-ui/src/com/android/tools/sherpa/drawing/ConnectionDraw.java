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

import java.awt.BasicStroke;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

/**
 * Utility drawing class
 * <p/>
 * Contains functions dealing with drawing connection between ConstraintAnchors
 */
public class ConnectionDraw {

    private static final boolean DEBUG = false;

    private static Polygon sLeftArrow;
    private static Polygon sTopArrow;
    private static Polygon sRightArrow;
    private static Polygon sBottomArrow;

    static Font sFont = new Font("Helvetica", Font.PLAIN, 12);

    private static Font sSmallFont = new Font("Helvetica", Font.PLAIN, 8);

    public static Stroke
            sSpreadDashedStroke = new BasicStroke(1, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_BEVEL, 0, new float[] { 1, 4 }, 0);

    public static Stroke
            sDashedStroke = new BasicStroke(1, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_BEVEL, 0, new float[] { 2 }, 0);

    static Stroke
            sLongDashedStroke = new BasicStroke(1, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_BEVEL, 0, new float[] { 8 }, 0);

    public static final int ARROW_SIDE = 8;

    public static int CONNECTION_ANCHOR_SIZE = 6;
    public static final int CONNECTION_ARROW_SIZE = 4;
    static final int CONNECTION_RESIZE_SIZE = 4;

    /**
     * Utility function to draw a circle text centered at coordinates (x, y)
     *
     * @param g    graphics context
     * @param font the font we use to draw the text
     * @param text the text to display
     * @param x    x coordinate
     * @param y    y coordinate
     */
    public static void drawCircledText(Graphics2D g, Font font, String text, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        int padding = 4;
        Rectangle2D bounds = fm.getStringBounds(text, g2);
        double th = bounds.getHeight();
        double tw = bounds.getWidth();
        float radius = (float) (Math.max(th, tw) / 2f + padding);
        Ellipse2D.Float circle =
                new Ellipse2D.Float(x - radius, y - radius, 2 * radius + 1, 2 * radius + 1);
        g2.fill(circle);
        g2.setColor(Color.BLACK);
        g2.drawString(text, (int) (x - tw / 2), (y + fm.getAscent() / 2));
        if (DEBUG) {
            g2.setColor(Color.RED);
            g2.drawLine(x - 50, y, x + 50, y);
            g2.drawLine(x, y - 50, x, y + 50);
        }
        g2.dispose();
    }

    /**
     * Utility function to draw a circle text centered at coordinates (x, y)
     *
     * @param g    graphics context
     * @param text the text to display
     * @param x    x coordinate
     * @param y    y coordinate
     */
    public static void drawCircledText(Graphics2D g, String text, int x, int y) {
        drawCircledText(g, sSmallFont, text, x, y);
    }

    /**
     * Utility function to draw an horizontal margin indicator
     *
     * @param g    graphics context
     * @param text the text to display
     * @param x1   x1 coordinate
     * @param x2   x2 coordinate
     * @param y    y coordinate
     */
    public static void drawHorizontalMarginIndicator(Graphics2D g, String text, int x1, int x2,
            int y) {
        if (x1 > x2) {
            int temp = x1;
            x1 = x2;
            x2 = temp;
        }

        if (text == null) {
            g.drawLine(x1, y, x2, y);
            g.drawLine(x1, y, x1 + CONNECTION_ARROW_SIZE, y - CONNECTION_ARROW_SIZE);
            g.drawLine(x1, y, x1 + CONNECTION_ARROW_SIZE, y + CONNECTION_ARROW_SIZE);
            g.drawLine(x2, y, x2 - CONNECTION_ARROW_SIZE, y - CONNECTION_ARROW_SIZE);
            g.drawLine(x2, y, x2 - CONNECTION_ARROW_SIZE, y + CONNECTION_ARROW_SIZE);
            return;
        }

        Canvas c = new Canvas();
        FontMetrics fm = c.getFontMetrics(sFont);
        g.setFont(sFont);
        int padding = 4;
        Rectangle2D bounds = fm.getStringBounds(text, g);
        int th = (int) bounds.getHeight();
        int tw = (int) bounds.getWidth();

        int offset = 3 * CONNECTION_ARROW_SIZE;

        int w = ((x2 - x1) - (tw + 2 * padding)) / 2;
        if (w <= padding) {
            g.drawLine(x1, y, x2, y);
            g.drawString(text, x1 + w + padding, y + offset);
            g.drawLine(x1, y - CONNECTION_ARROW_SIZE, x1, y + CONNECTION_ARROW_SIZE);
            g.drawLine(x2, y - CONNECTION_ARROW_SIZE, x2, y + CONNECTION_ARROW_SIZE);
        } else {
            g.drawLine(x1, y, x1 + w, y);
            g.drawLine(x2 - w, y, x2, y);
            g.drawString(text, x1 + w + padding, (int) (y + (bounds.getHeight() / 2)));
            g.drawLine(x1, y, x1 + CONNECTION_ARROW_SIZE, y - CONNECTION_ARROW_SIZE);
            g.drawLine(x1, y, x1 + CONNECTION_ARROW_SIZE, y + CONNECTION_ARROW_SIZE);
            g.drawLine(x2, y, x2 - CONNECTION_ARROW_SIZE, y - CONNECTION_ARROW_SIZE);
            g.drawLine(x2, y, x2 - CONNECTION_ARROW_SIZE, y + CONNECTION_ARROW_SIZE);
        }
    }

    /**
     * Utility function to draw a vertical margin indicator
     *
     * @param g    graphics context
     * @param text the text to display
     * @param x    x coordinate
     * @param y1   y1 coordinate
     * @param y2   y2 coordinate
     */
    public static void drawVerticalMarginIndicator(Graphics2D g, String text, int x, int y1, int y2) {
        if (y1 > y2) {
            int temp = y1;
            y1 = y2;
            y2 = temp;
        }
        if (text == null) {
            g.drawLine(x, y1, x, y2);
            g.drawLine(x, y1, x - CONNECTION_ARROW_SIZE, y1 + CONNECTION_ARROW_SIZE);
            g.drawLine(x, y1, x + CONNECTION_ARROW_SIZE, y1 + CONNECTION_ARROW_SIZE);
            g.drawLine(x, y2, x - CONNECTION_ARROW_SIZE, y2 - CONNECTION_ARROW_SIZE);
            g.drawLine(x, y2, x + CONNECTION_ARROW_SIZE, y2 - CONNECTION_ARROW_SIZE);
            return;
        }
        Canvas c = new Canvas();
        FontMetrics fm = c.getFontMetrics(sFont);
        g.setFont(sFont);
        int padding = 4;
        Rectangle2D bounds = fm.getStringBounds(text, g);
        int th = (int) bounds.getHeight();
        int tw = (int) bounds.getWidth();

        int offset = 3 * CONNECTION_ARROW_SIZE;

        int h = ((y2 - y1) - (th + 2 * padding)) / 2;
        if (h <= padding) {
            g.drawLine(x, y1, x, y2);
            g.drawString(text, (int) (x - bounds.getWidth() / 2) + offset, y2 - h - padding);
            g.drawLine(x - CONNECTION_ARROW_SIZE, y1, x + CONNECTION_ARROW_SIZE, y1);
            g.drawLine(x - CONNECTION_ARROW_SIZE, y2, x + CONNECTION_ARROW_SIZE, y2);
        } else {
            g.drawLine(x, y1, x, y1 + h);
            g.drawLine(x, y2 - h, x, y2);
            g.drawString(text, (int) (x - bounds.getWidth() / 2), y2 - h - padding);
            g.drawLine(x, y1, x - CONNECTION_ARROW_SIZE, y1 + CONNECTION_ARROW_SIZE);
            g.drawLine(x, y1, x + CONNECTION_ARROW_SIZE, y1 + CONNECTION_ARROW_SIZE);
            g.drawLine(x, y2, x - CONNECTION_ARROW_SIZE, y2 - CONNECTION_ARROW_SIZE);
            g.drawLine(x, y2, x + CONNECTION_ARROW_SIZE, y2 - CONNECTION_ARROW_SIZE);
        }
    }

    /**
     * Utility function to draw in (x, y) one of the Polygon used for the arrows
     *
     * @param g     Graphics context
     * @param arrow the polygon representing the arrow we want to draw
     * @param x     x coordinate
     * @param y     y coordinate
     */
    public static void drawArrow(Graphics2D g, Polygon arrow, int x, int y) {
        arrow.translate(x, y);
        g.fill(arrow);
        arrow.translate(-x, -y);
    }

    /**
     * Static accessor to the left arrow
     *
     * @return return a Polygon representing a left arrow
     */
    public static Polygon getLeftArrow() {
        if (sLeftArrow == null) {
            sLeftArrow = new Polygon();
            sLeftArrow.addPoint(0, 0);
            sLeftArrow.addPoint(ARROW_SIDE, -CONNECTION_ARROW_SIZE);
            sLeftArrow.addPoint(ARROW_SIDE, +CONNECTION_ARROW_SIZE);
        }
        return sLeftArrow;
    }

    /**
     * Static accessor to the right arrow
     *
     * @return return a Polygon representing a right arrow
     */
    public static Polygon getRightArrow() {
        if (sRightArrow == null) {
            sRightArrow = new Polygon();
            sRightArrow.addPoint(0, 0);
            sRightArrow.addPoint(-ARROW_SIDE, -CONNECTION_ARROW_SIZE);
            sRightArrow.addPoint(-ARROW_SIDE, +CONNECTION_ARROW_SIZE);
        }
        return sRightArrow;
    }

    /**
     * Static accessor to the top arrow
     *
     * @return return a Polygon representing a top arrow
     */
    public static Polygon getTopArrow() {
        if (sTopArrow == null) {
            sTopArrow = new Polygon();
            sTopArrow.addPoint(0, 0);
            sTopArrow.addPoint(-CONNECTION_ARROW_SIZE, ARROW_SIDE);
            sTopArrow.addPoint(+CONNECTION_ARROW_SIZE, ARROW_SIDE);
        }
        return sTopArrow;
    }

    /**
     * Static accessor to the bottom arrow
     *
     * @return return a Polygon representing a bottom arrow
     */
    public static Polygon getBottomArrow() {
        if (sBottomArrow == null) {
            sBottomArrow = new Polygon();
            sBottomArrow.addPoint(0, 0);
            sBottomArrow.addPoint(-CONNECTION_ARROW_SIZE, -ARROW_SIDE);
            sBottomArrow.addPoint(+CONNECTION_ARROW_SIZE, -ARROW_SIDE);
        }
        return sBottomArrow;
    }

}
