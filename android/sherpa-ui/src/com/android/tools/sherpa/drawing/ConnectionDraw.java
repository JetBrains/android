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

import com.android.tools.sherpa.interaction.ConstraintHandle;
import com.google.tnt.solver.widgets.ConstraintAnchor;
import com.google.tnt.solver.widgets.ConstraintTableLayout;
import com.google.tnt.solver.widgets.ConstraintWidget;
import com.google.tnt.solver.widgets.ConstraintWidgetContainer;
import com.google.tnt.solver.widgets.Guideline;

import java.awt.BasicStroke;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/**
 * Utility drawing class
 * <p/>
 * Contains functions dealing with drawing connection between ConstraintAnchors
 */
public class ConnectionDraw {

    private static final int MIN_CENTER_CNX = 32;
    private static final boolean DEBUG = false;

    private static Polygon sLeftArrow;
    private static Polygon sTopArrow;
    private static Polygon sRightArrow;
    private static Polygon sBottomArrow;

    static Font sFont = new Font("Helvetica", Font.PLAIN, 12);
    private static Font sSmallFont = new Font("Helvetica", Font.PLAIN, 8);

    static Stroke
            sDashedStroke = new BasicStroke(1, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_BEVEL, 0, new float[] { 2 }, 0);

    static Stroke
            sLongDashedStroke = new BasicStroke(1, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_BEVEL, 0, new float[] { 8 }, 0);

    static final int SECONDARY_INFORMATION_ALPHA = 128;

    static final int ARROW_SIDE = 8;

    public static final int CONNECTION_ANCHOR_SIZE = 6;
    static final int CONNECTION_ARROW_SIZE = 4;
    static final int CONNECTION_RESIZE_SIZE = 4;
    static final int CONNECTION_CURVE_SIZE = 4;
    static final int CURVE_OFFSET = 40;
    static final boolean FORCE_MAX_SIZE_CURVE = true;

    /**
     * Draw the connection between two anchors if it exists
     *
     * @param transform            view transform
     * @param g                    Graphics context
     * @param beginHandle          the anchor source of the connection
     * @param endHandle            the anchor target of the connection
     * @param isSelected           if the connection is from a currently selected widget
     * @param showPercentIndicator show the percent indicator if center constraints
     */
    public static void drawConnection(ViewTransform transform, Graphics2D g,
            ConstraintHandle beginHandle, ConstraintHandle endHandle,
            boolean isSelected, boolean showPercentIndicator) {
        ConstraintAnchor begin = beginHandle.getAnchor();
        ConstraintAnchor end = endHandle.getAnchor();
        if (end.getOwner() instanceof ConstraintTableLayout
                && begin.getOwner().getParent() == end.getOwner()) {
            return;
        }
        if (end.getOwner() instanceof Guideline && !end.getOwner().getParent().isRoot()) {
            return;
        }
        boolean isVertical = isVertical(begin.getType());
        boolean hasBothConnections = hasBothConnections(begin);
        if (hasBothConnections) {
            drawCenterConnection(transform, g, isVertical, beginHandle, endHandle,
                    isSelected, showPercentIndicator);
        } else {
            if (isVertical) {
                drawVerticalConnection(transform, g, beginHandle, endHandle);
            } else {
                drawHorizontalConnection(transform, g, beginHandle, endHandle);
            }
        }
        if (begin.getMargin() > 0) {
            if (!hasBothConnections ||
                    (hasBothConnections
                            && begin.getStrength() == ConstraintAnchor.Strength.STRONG
                            && begin.getStrength() != begin.getOpposite().getStrength())) {
                Color currentColor = g.getColor();
                Color newColor = new Color(currentColor.getRed(),
                        currentColor.getGreen(),
                        currentColor.getBlue(), SECONDARY_INFORMATION_ALPHA);
                g.setColor(newColor);
                drawMargin(transform, g, isVertical, beginHandle, endHandle);
                g.setColor(currentColor);
            }
        }
    }

    /**
     * Utility function to draw a connection between an anchor and a point (typically, the
     * current mouse position...)
     *
     * @param transform   view transform
     * @param g           Graphics context
     * @param beginHandle the anchor source of the connection
     * @param point       the point that is the end of the connection spline
     */
    public static void drawConnection(ViewTransform transform, Graphics2D g,
            ConstraintHandle beginHandle,
            Point point) {
        ConstraintAnchor begin = beginHandle.getAnchor();
        boolean isVertical = isVertical(begin.getType());
        ConstraintHandle endHandle = new ConstraintHandle(null, begin.getType());
        endHandle.setDrawX(point.x);
        endHandle.setDrawY(point.y);
        if (isVertical) {
            drawVerticalConnection(transform, g, beginHandle, endHandle);
        } else {
            drawHorizontalConnection(transform, g, beginHandle, endHandle);
        }
    }

    /**
     * Utility function to check if there is a mirrored connected anchor, for a given anchor.
     * For example, passing a left anchor, we check if the right anchor has a connection
     *
     * @param anchor the anchor we are checking
     * @return true if both the passed anchor and its sibling are connected
     */
    private static boolean hasBothConnections(ConstraintAnchor anchor) {
        if (!anchor.isConnected()) {
            return false;
        }
        ConstraintWidget owner = anchor.getOwner();
        if (owner == null) {
            return false;
        }
        if (owner instanceof Guideline) {
            return false;
        }
        ConstraintAnchor opposite = anchor.getOpposite();
        if (opposite != null && opposite.isConnected()) {
            return true;
        }
        return false;
    }

    /**
     * Utility function to check if we are dealing with a vertical ConstraintAnchor
     *
     * @param type type of ConstraintAnchor
     * @return true if we are dealing with a vertical ConstraintAnchor
     */
    private static boolean isVertical(ConstraintAnchor.Type type) {
        switch (type) {
            case TOP:
            case BOTTOM:
            case BASELINE:
            case CENTER_Y:
                return true;
        }
        return false;
    }

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
    static void drawHorizontalMarginIndicator(Graphics2D g, String text, int x1, int x2, int y) {
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
    static void drawVerticalMarginIndicator(Graphics2D g, String text, int x, int y1, int y2) {
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
     * Draw a representation of the margin existing on a connection between two ConstraintAnchors.
     * The margin is represented as a straight line ended with two line arrows, and the margin value
     * is drawn in the middle of the line (or aside if the line is too small).
     * The color and style used for the drawing will be the current ones in the graphics context.
     *
     * @param transform   view transform
     * @param g           Graphics context
     * @param isVertical  flag to pick between a vertical or horizontal margin
     * @param beginHandle the ConstraintHandle source of the constraint
     * @param endHandle   the ConstraintHandle target of the constraint
     */
    private static void drawMargin(ViewTransform transform, Graphics2D g, boolean isVertical,
            ConstraintHandle beginHandle, ConstraintHandle endHandle) {
        ConstraintAnchor begin = beginHandle.getAnchor();
        ConstraintAnchor end = endHandle.getAnchor();
        int x1 = transform.getSwingX(beginHandle.getDrawX());
        int y1 = transform.getSwingY(beginHandle.getDrawY());
        int x2 = transform.getSwingX(endHandle.getDrawX());
        int y2 = transform.getSwingY(endHandle.getDrawY());
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setStroke(sDashedStroke);

        Canvas c = new Canvas();
        FontMetrics fm = c.getFontMetrics(sFont);
        int padding = transform.getSwingDimension(4);
        String margin = "" + begin.getMargin();
        Rectangle2D bounds = fm.getStringBounds(margin, g);
        int th = (int) bounds.getHeight();
        int tw = (int) bounds.getWidth();

        int offset = 3 * CONNECTION_ARROW_SIZE;

        if (isVertical) {
            // Let's draw the margin indicator with an offset from the
            // connection curve
            if (beginHandle.getDrawX() < endHandle.getDrawX()) {
                offset = -offset;
            }
            x1 += offset;
            x2 += offset;
            if (!(end.getOwner() instanceof Guideline
                    || ((ConstraintWidget) end.getOwner()).isRoot())) {
                g2d.drawLine(x1, y2, x2, y2);
            }
            drawVerticalMarginIndicator(g, margin, x1, y1, y2);
        } else {
            // Let's draw the margin indicator with an offset from the
            // connection curve
            if (beginHandle.getDrawY() < endHandle.getDrawY()) {
                offset = -offset;
            }
            y1 += offset;
            y2 += offset;
            if (!(end.getOwner() instanceof Guideline
                    || ((ConstraintWidget) end.getOwner()).isRoot())) {
                g2d.drawLine(x2, y1, x2, y2);
            }
            drawHorizontalMarginIndicator(g, margin, x1, x2, y1);
        }

        g2d.dispose();
    }

    /**
     * Draw a centered connection between two ConstraintAnchor; this is used when two opposite
     * constraints on a widget are set (e.g. left and right). The connection is drawn as a straight
     * line ended by an arrow. If the connection is weak, it will be drawn with a dashed line.
     * The color and style used for the drawing will be the current ones in the graphics context.
     *
     * @param transform            view transform
     * @param g                    Graphics context
     * @param isVertical           flag to pick between a vertical or horizontal connection
     * @param beginHandle          the ConstraintHandle source of the constraint
     * @param endHandle            the ConstraintHandle target of the constraint
     * @param isSelected           if the connection is from a currently selected widget
     * @param showPercentIndicator show the percent indicator if center constraints
     */
    private static void drawCenterConnection(ViewTransform transform, Graphics2D g,
            boolean isVertical, ConstraintHandle beginHandle,
            ConstraintHandle endHandle, boolean isSelected,
            boolean showPercentIndicator) {
        ConstraintAnchor begin = beginHandle.getAnchor();
        ConstraintAnchor end = endHandle.getAnchor();
        int x1 = transform.getSwingX(beginHandle.getDrawX());
        int y1 = transform.getSwingY(beginHandle.getDrawY());
        int x2 = transform.getSwingX(endHandle.getDrawX());
        int y2 = transform.getSwingY(endHandle.getDrawY());
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setStroke(sDashedStroke);

        if (begin.getOpposite().getTarget() == begin.getTarget()) {
            // pointing on the same target, the widget will be centered on it
            boolean drawLine = true;
            if (end.getOwner() instanceof Guideline) {
                // don't draw the line for guidelines
                drawLine = false;
            }
            ConstraintWidget widget = begin.getOwner();
            int margin = 10;
            if (isVertical) {
                if (drawLine) {
                    g2d.drawLine(x2, y2, x1, y2);
                }
                int x = widget.getDrawX() - margin;
                if (x2 > x1) {
                    x = widget.getDrawRight() + margin;
                }
                ConnectionDraw.drawVerticalMarginIndicator(g, null, x, y1, y2);
            } else {
                if (drawLine) {
                    g2d.drawLine(x2, y2, x2, y1);
                }
                int y = widget.getDrawY() - margin;
                if (y2 > y1) {
                    y = widget.getDrawBottom() + margin;
                }
                ConnectionDraw.drawHorizontalMarginIndicator(g, null, x1, x2, y);
            }
            g2d.dispose();
            return;
        }

        boolean useStraightLine = false;
        if (begin.getStrength() == ConstraintAnchor.Strength.WEAK) {
            Graphics2D g3d = (Graphics2D) g.create();
            g3d.setStroke(sLongDashedStroke);
            useStraightLine = true;
            g = g3d;
        } else {
            ConstraintAnchor opposite = begin.getOpposite();
            if (opposite.getStrength() == ConstraintAnchor.Strength.WEAK) {
                useStraightLine = true;
                g = (Graphics2D) g.create();
            }
        }
        if (isVertical) {
            if (Math.abs(y2 - y1) < MIN_CENTER_CNX) {
                drawVerticalConnection(transform, g, beginHandle, endHandle);
                if (useStraightLine) {
                    g.dispose();
                }
                return;
            }
            if (!(end.getOwner() instanceof Guideline)) {
                g2d.drawLine(x1, y2, x2, y2);
            }
            int margin = transform.getSwingDimension(begin.getMargin());
            if (!useStraightLine && margin > 0) {
                String text = "" + begin.getMargin();
                int ym = y2 - margin;
                if (y1 > y2) {
                    ym = y2 + margin;
                }
                ConnectionDraw.drawVerticalMarginIndicator(g, text, x1, ym, y2);
                if (y1 < y2) {
                    y2 -= margin;
                } else {
                    y2 += margin;
                }
            }
            if (y1 < y2) {
                drawArrow(g, getBottomArrow(), x1, y2);
                if (useStraightLine) {
                    g.drawLine(x1, y1 + CONNECTION_ANCHOR_SIZE, x1, y2);
                } else {
                    WidgetDraw.drawVerticalZigZagLine(g, x1, y1 + CONNECTION_ANCHOR_SIZE, y2);
                }
            } else {
                drawArrow(g, getTopArrow(), x1, y2);
                if (useStraightLine) {
                    g.drawLine(x1, y1 - CONNECTION_ANCHOR_SIZE, x1, y2);
                } else {
                    WidgetDraw.drawVerticalZigZagLine(g, x1, y1 - CONNECTION_ANCHOR_SIZE, y2);
                }
            }
            if (!useStraightLine && isSelected && showPercentIndicator) {
                ConstraintWidget widget = begin.getOwner();
                int percent = (int) (widget.getVerticalBiasPercent() * 100);
                if (begin.getType() == ConstraintAnchor.Type.BOTTOM) {
                    percent = 100 - percent;
                }
                ConnectionDraw.drawCircledText(g, sFont, "" + percent, x1, y1 + (y2 - y1) / 2);
            }
        } else {
            if (Math.abs(x2 - x1) < MIN_CENTER_CNX) {
                drawHorizontalConnection(transform, g, beginHandle, endHandle);
                if (useStraightLine) {
                    g.dispose();
                }
                return;
            }
            if (!(end.getOwner() instanceof Guideline)) {
                g2d.drawLine(x2, y1, x2, y2);
            }
            int margin = transform.getSwingDimension(begin.getMargin());
            if (!useStraightLine && margin > 0) {
                String text = "" + begin.getMargin();
                int xm = x2 - margin;
                if (x1 > x2) {
                    xm = x2 + margin;
                }
                ConnectionDraw.drawHorizontalMarginIndicator(g, text, xm, x2, y1);
                if (x1 < x2) {
                    x2 -= margin;
                } else {
                    x2 += margin;
                }
            }
            if (x1 < x2) {
                drawArrow(g, getRightArrow(), x2, y1);
                if (useStraightLine) {
                    g.drawLine(x1 + CONNECTION_ANCHOR_SIZE, y1, x2, y1);
                } else {
                    WidgetDraw.drawHorizontalZigZagLine(g, x1 + CONNECTION_ANCHOR_SIZE, x2, y1);
                }
            } else {
                drawArrow(g, getLeftArrow(), x2, y1);
                if (useStraightLine) {
                    g.drawLine(x1 - CONNECTION_ANCHOR_SIZE, y1, x2, y1);
                } else {
                    WidgetDraw.drawHorizontalZigZagLine(g, x1 - CONNECTION_ANCHOR_SIZE, x2, y1);
                }
            }
            if (!useStraightLine && isSelected && showPercentIndicator) {
                ConstraintWidget widget = begin.getOwner();
                int percent = (int) (widget.getHorizontalBiasPercent() * 100);
                if (begin.getType() == ConstraintAnchor.Type.RIGHT) {
                    percent = 100 - percent;
                }
                ConnectionDraw.drawCircledText(g, sFont, "" + percent, x1 + (x2 - x1) / 2, y1);
            }
        }

        g2d.dispose();
        if (useStraightLine) {
            g.dispose();
        }
    }

    /**
     * Draw a vertical connection between two ConstraintAnchor. The connection is drawn as a bezier
     * (allowing us to show connection between identical y position), with a final arrow pointing
     * to the target ConstraintAnchor.
     * The color and style used for the drawing will be the current ones in the graphics context.
     *
     * @param transform   view transform
     * @param g           Graphics context
     * @param beginHandle the ConstraintHandle source of the constraint
     * @param endHandle   the ConstraintHandle target of the constraint
     */
    private static void drawVerticalConnection(ViewTransform transform, Graphics2D g,
            ConstraintHandle beginHandle, ConstraintHandle endHandle) {
        ConstraintAnchor begin = beginHandle.getAnchor();
        ConstraintAnchor end = endHandle.getAnchor();
        boolean isBeginBaselineAnchor = begin.getType() == ConstraintAnchor.Type.BASELINE;
        int endAnchorSize = 0;
        if (end != null && end.isConnected()) {
            endAnchorSize = CONNECTION_ANCHOR_SIZE;
        }
        int beginAnchorSize = isBeginBaselineAnchor ? 0 : CONNECTION_ANCHOR_SIZE;
        int x1 = transform.getSwingX(beginHandle.getDrawX());
        int y1 = transform.getSwingY(beginHandle.getDrawY()) + beginAnchorSize;
        int x3 = transform.getSwingX(endHandle.getDrawX());
        int y3 = transform.getSwingY(endHandle.getDrawY()) - endAnchorSize - CONNECTION_ARROW_SIZE;
        if (isBeginBaselineAnchor) {
            // Draw the baseline connections with an offset from the center
            ConstraintWidget widget = (ConstraintWidget) begin.getOwner();
            if (x3 > x1) {
                x1 += widget.getDrawWidth() / 4;
            } else {
                x1 -= widget.getDrawWidth() / 4;
            }
            if (end != null && end.getOwner() != null) {
                ConstraintWidget widgetTarget = end.getOwner();
                if (x3 > x1) {
                    x3 -= widgetTarget.getDrawWidth() / 4;
                } else {
                    x3 += widgetTarget.getDrawWidth() / 4;
                }
            }
        }
        if (transform.getSwingDimension(Math.abs(beginHandle.getDrawY() - endHandle.getDrawY()))
                < CONNECTION_ANCHOR_SIZE
                && (end != null && ((end.getOwner() instanceof Guideline)
                || (end.getOwner() == begin.getOwner().getParent())))) {
            return;
        }
        boolean isEndOurParent = false;
        if (end != null) {
            isEndOurParent = end.getOwner() == begin.getOwner().getParent();
            if ((end.getOwner() instanceof ConstraintWidgetContainer && isEndOurParent)
                    || end.getOwner() instanceof Guideline) {
                x3 = x1;
                y3 = transform.getSwingY(endHandle.getDrawY()) - CONNECTION_ARROW_SIZE;
            }
        }

        if (begin.getType() == ConstraintAnchor.Type.TOP
                || begin.getType() == ConstraintAnchor.Type.LEFT) {
            x1 = transform.getSwingX(endHandle.getDrawX());
            y1 = transform.getSwingY(endHandle.getDrawY()) + endAnchorSize + CONNECTION_ARROW_SIZE;
            x3 = transform.getSwingX(beginHandle.getDrawX());
            y3 = transform.getSwingY(beginHandle.getDrawY()) - CONNECTION_ANCHOR_SIZE;
            if (end != null) {
                if ((end.getOwner() instanceof ConstraintWidgetContainer && isEndOurParent)
                        || end.getOwner() instanceof Guideline) {
                    x1 = x3;
                    y1 = transform.getSwingY(endHandle.getDrawY()) + CONNECTION_ARROW_SIZE;
                }
            }
            if (Math.abs(y3 - y1) > CONNECTION_ARROW_SIZE) {
                drawArrow(g, getTopArrow(), x1, y1 - CONNECTION_ARROW_SIZE);
            }
        } else {
            drawArrow(g, getBottomArrow(), x3, y3 + CONNECTION_ARROW_SIZE);
        }
        int d = (x3 - x1) / 2;
        int x2 = x1 + 2 * d;
        if (d < 0) {
            d = (x1 - x3) / 2;
        }
        if (FORCE_MAX_SIZE_CURVE && d > CURVE_OFFSET) {
            d = CURVE_OFFSET;
        }
        int y2 = y3 - d - CONNECTION_CURVE_SIZE;

        Point p1 = new Point(x1, y1 + d + CONNECTION_CURVE_SIZE);
        Point p2 = new Point(x2, y2);
        Path2D.Double path = new Path2D.Double();
        path.moveTo(x1, y1);
        path.lineTo(x1, y1 + CONNECTION_CURVE_SIZE);
        path.curveTo(p1.x, p1.y, p2.x, p2.y, x3, y3 - CONNECTION_CURVE_SIZE);
        path.lineTo(x3, y3);
        g.draw(path);
    }

    /**
     * Draw a horizontal connection between two ConstraintAnchor. The connection is drawn as a bezier
     * (allowing us to show connection between identical x position), with a final arrow pointing
     * to the target ConstraintAnchor.
     * The color and style used for the drawing will be the current ones in the graphics context.
     *
     * @param transform   view transform
     * @param g           Graphics context
     * @param beginHandle the ConstraintHandle source of the constraint
     * @param endHandle   the ConstraintHandle target of the constraint
     */
    private static void drawHorizontalConnection(ViewTransform transform, Graphics2D g,
            ConstraintHandle beginHandle, ConstraintHandle endHandle) {
        ConstraintAnchor begin = beginHandle.getAnchor();
        ConstraintAnchor end = endHandle.getAnchor();
        int endAnchorSize = 0;
        if (end != null && end.isConnected()) {
            endAnchorSize = CONNECTION_ANCHOR_SIZE;
        }
        int x1 = transform.getSwingX(beginHandle.getDrawX()) + CONNECTION_ANCHOR_SIZE;
        int y1 = transform.getSwingY(beginHandle.getDrawY());
        int x3 = transform.getSwingX(endHandle.getDrawX()) - endAnchorSize - CONNECTION_ARROW_SIZE;
        int y3 = transform.getSwingY(endHandle.getDrawY());
        if (transform.getSwingDimension(Math.abs(beginHandle.getDrawX() - endHandle.getDrawX()))
                < CONNECTION_ANCHOR_SIZE
                && (end != null && ((end.getOwner() instanceof Guideline)
                || (end.getOwner() == begin.getOwner().getParent())))) {
            return;
        }
        boolean isEndOurParent = false;
        if (end != null) {
            isEndOurParent = end.getOwner() == begin.getOwner().getParent();
            if ((end.getOwner() instanceof ConstraintWidgetContainer && isEndOurParent)
                    || end.getOwner() instanceof Guideline) {
                x3 = transform.getSwingX(endHandle.getDrawX()) - CONNECTION_ARROW_SIZE;
                y3 = y1;
            }
        }
        if (begin.getType() == ConstraintAnchor.Type.TOP
                || begin.getType() == ConstraintAnchor.Type.LEFT) {
            x1 = transform.getSwingX(endHandle.getDrawX()) + endAnchorSize + CONNECTION_ARROW_SIZE;
            y1 = transform.getSwingY(endHandle.getDrawY());
            x3 = transform.getSwingX(beginHandle.getDrawX()) - CONNECTION_ANCHOR_SIZE;
            y3 = transform.getSwingY(beginHandle.getDrawY());
            if (end != null) {
                if ((end.getOwner() instanceof ConstraintWidgetContainer && isEndOurParent)
                        || end.getOwner() instanceof Guideline) {
                    x1 = transform.getSwingX(endHandle.getDrawX()) + CONNECTION_ARROW_SIZE;
                    y1 = y3;
                }
            }
            drawArrow(g, getLeftArrow(), x1 - CONNECTION_ARROW_SIZE, y1);
        } else {
            drawArrow(g, getRightArrow(), x3 + CONNECTION_ARROW_SIZE, y3);
        }
        int d = (y3 - y1) / 2;
        int y2 = y1 + 2 * d;
        if (d < 0) {
            d = (y1 - y3) / 2;
        }
        if (FORCE_MAX_SIZE_CURVE && d > CURVE_OFFSET) {
            d = CURVE_OFFSET;
        }
        int x2 = x3 - d - CONNECTION_CURVE_SIZE;

        Point p1 = new Point(x1 + d + CONNECTION_CURVE_SIZE, y1);
        Point p2 = new Point(x2, y2);
        Path2D.Double path = new Path2D.Double();
        path.moveTo(x1, y1);
        path.lineTo(x1 + CONNECTION_CURVE_SIZE, y1);
        path.curveTo(p1.x, p1.y, p2.x, p2.y, x3 - CONNECTION_CURVE_SIZE, y3);
        path.lineTo(x3, y3);
        g.draw(path);
    }

    /**
     * Utility function to draw in (x, y) one of the Polygon used for the arrows
     *
     * @param g     Graphics context
     * @param arrow the polygon representing the arrow we want to draw
     * @param x     x coordinate
     * @param y     y coordinate
     */
    private static void drawArrow(Graphics2D g, Polygon arrow, int x, int y) {
        arrow.translate(x, y);
        g.fill(arrow);
        arrow.translate(-x, -y);
    }

    /**
     * Static accessor to the left arrow
     *
     * @return return a Polygon representing a left arrow
     */
    static Polygon getLeftArrow() {
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
    static Polygon getRightArrow() {
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
    static Polygon getTopArrow() {
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
    static Polygon getBottomArrow() {
        if (sBottomArrow == null) {
            sBottomArrow = new Polygon();
            sBottomArrow.addPoint(0, 0);
            sBottomArrow.addPoint(-CONNECTION_ARROW_SIZE, -ARROW_SIDE);
            sBottomArrow.addPoint(+CONNECTION_ARROW_SIZE, -ARROW_SIDE);
        }
        return sBottomArrow;
    }
}
