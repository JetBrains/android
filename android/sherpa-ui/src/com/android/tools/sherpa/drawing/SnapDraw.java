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

package com.android.tools.sherpa.drawing;

import com.android.tools.sherpa.interaction.SnapCandidate;
import com.android.tools.sherpa.interaction.WidgetInteractionTargets;
import com.android.tools.sherpa.interaction.ConstraintHandle;
import com.google.tnt.solver.widgets.ConstraintAnchor;
import com.google.tnt.solver.widgets.Guideline;

import java.awt.BasicStroke;
import java.awt.Canvas;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;

/**
 * Utility drawing class
 * <p/>
 * Contains functions dealing with drawing snap indications
 */
public class SnapDraw {

    private static final int OVER = 20; // in pixels
    private static final int OVER_MARGIN = 8; // in pixels

    public static Stroke
            sDashedStroke = new BasicStroke(1, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_BEVEL, 0, new float[] { 2 }, 0);

    static Stroke
            sLongDashedStroke = new BasicStroke(1, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_BEVEL, 0, new float[] { 8 }, 0);

    public static Stroke sThinDashedStroke = new BasicStroke(1, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_BEVEL, 0, new float[] { 2, 8 }, 0);

    public static Stroke sNormalStroke = new BasicStroke(1);

    private static Font sFont = new Font("Helvetica", Font.PLAIN, 12);

    static final int CONNECTION_ARROW_SIZE = 3;

    /**
     * Draw a snap indicator for the snap candidate
     *
     * @param transform view transform
     * @param g         Graphics context
     * @param candidate the snap candidate
     */
    public static void drawSnapIndicator(ViewTransform transform, Graphics2D g,
            SnapCandidate candidate) {
        if (candidate.source == null || candidate.target == null) {
            return;
        }
        if (candidate.source.isVerticalAnchor()) {
            drawSnapVerticalIndicator(transform, g, candidate);
        } else {
            drawSnapHorizontalIndicator(transform, g, candidate);
        }
    }

    /**
     * Draw a snap indicator for horizontal anchors
     *
     * @param transform view transform
     * @param g         Graphics context
     * @param candidate the snap candidate
     */
    private static void drawSnapHorizontalIndicator(ViewTransform transform, Graphics2D g,
            SnapCandidate candidate) {
        ConstraintAnchor source = candidate.source;
        ConstraintAnchor target = candidate.target;
        ConstraintHandle sourceHandle = WidgetInteractionTargets.constraintHandle(source);
        ConstraintHandle targetHandle = WidgetInteractionTargets.constraintHandle(target);

        int x = transform.getSwingX(candidate.x);
        if (targetHandle != null) {
            x = transform.getSwingX(targetHandle.getDrawX());
        }
        int y1 = transform.getSwingY(source.getOwner().getDrawY());
        int y2 = transform.getSwingY(source.getOwner().getDrawY() + source.getOwner().getHeight());
        int y3 = transform.getSwingY(target.getOwner().getDrawY());
        int y4 = transform.getSwingY(target.getOwner().getDrawY() + target.getOwner().getHeight());
        int minY = Math.min(y1, y3);
        int maxY = Math.max(y2, y4);
        if (candidate.margin != 0) {
            int x2 = transform.getSwingX(sourceHandle.getDrawX());
            String textMargin = "" + Math.abs(candidate.margin);
            int yS = y2;
            int yT = y4 + OVER_MARGIN / 2;
            int mY = yS + OVER_MARGIN;
            boolean textOver = false;
            if (y1 < y3) {
                yS = y1;
                yT = y3 - OVER_MARGIN / 2;
                mY = yS - OVER_MARGIN;
                textOver = true;
            }
            drawSnapHorizontalMargin(transform, g, x, x2, mY, textMargin, textOver);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setStroke(sDashedStroke);
            g2.drawLine(x, yS, x, yT);
            g2.dispose();
        } else {
            Graphics2D g2 = (Graphics2D) g.create();
            boolean insideIndicator =
                    (source.getOwner().getParent() == candidate.target.getOwner())
                            || (candidate.target.getOwner() instanceof Guideline);
            if (insideIndicator) {
                g2.setStroke(sLongDashedStroke);
                g2.drawLine(x, minY, x, maxY);
            } else {
                g2.setStroke(sDashedStroke);
                g2.drawLine(x, minY - OVER, x, maxY + OVER);
            }
            g2.dispose();
        }
    }

    /**
     * Draw a snap indicator for vertical anchors
     *
     * @param transform view transform
     * @param g         Graphics context
     * @param candidate the snap candidate
     */
    private static void drawSnapVerticalIndicator(ViewTransform transform, Graphics2D g,
            SnapCandidate candidate) {
        ConstraintAnchor source = candidate.source;
        ConstraintAnchor target = candidate.target;
        ConstraintHandle sourceHandle = WidgetInteractionTargets.constraintHandle(source);
        ConstraintHandle targetHandle = WidgetInteractionTargets.constraintHandle(target);

        int y = transform.getSwingY(candidate.y);
        if (targetHandle != null) {
            y = transform.getSwingY(targetHandle.getDrawY());
        }
        int x1 = transform.getSwingX(source.getOwner().getDrawX());
        int x2 = transform
                .getSwingX(source.getOwner().getDrawX() + source.getOwner().getDrawWidth());
        int x3 = transform.getSwingX(target.getOwner().getDrawX());
        int x4 = transform
                .getSwingX(target.getOwner().getDrawX() + target.getOwner().getDrawWidth());
        int minX = Math.min(x1, x3);
        int maxX = Math.max(x2, x4);
        if (candidate.margin != 0) {
            int y2 = transform.getSwingY(sourceHandle.getDrawY());
            String textMargin = "" + Math.abs(candidate.margin);
            int xS = x2;
            int xT = x4 + OVER_MARGIN / 2;
            int mX = xS + OVER_MARGIN;
            if (x1 < x3) {
                xS = x1;
                xT = x3 - OVER_MARGIN / 2;
                mX = xS - OVER_MARGIN;
            }
            drawSnapVerticalMargin(transform, g, mX, y, y2, textMargin);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setStroke(sDashedStroke);
            g2.drawLine(xS, y, xT, y);
            g2.dispose();
        } else {
            Graphics2D g2 = (Graphics2D) g.create();
            boolean insideIndicator =
                    (source.getOwner().getParent() == candidate.target.getOwner())
                            || (candidate.target.getOwner() instanceof Guideline);
            if (insideIndicator) {
                g2.setStroke(sLongDashedStroke);
                g2.drawLine(minX, y, maxX, y);
            } else {
                g2.setStroke(sDashedStroke);
                g2.drawLine(minX - OVER, y, maxX + OVER, y);
            }
            g2.dispose();
        }
    }

    /**
     * Draw a snap horizontal margin indicator
     *
     * @param transform view transform
     * @param g         Graphics context
     * @param x1        start x coordinate
     * @param x2        end x coordinate
     * @param y1        y coordinate
     * @param text      text to display below the indicator
     * @param textOver  draw text over the indicator if offsetted
     */
    public static void drawSnapHorizontalMargin(ViewTransform transform, Graphics2D g,
            int x1, int x2, int y1, String text, boolean textOver) {
        Canvas c = new Canvas();
        FontMetrics fm = c.getFontMetrics(sFont);
        int padding = transform.getSwingDimension(4);
        Rectangle2D bounds = fm.getStringBounds(text, g);
        int tw = (int) bounds.getWidth();
        int offset = 6 * CONNECTION_ARROW_SIZE;

        if (x1 > x2) {
            int temp = x1;
            x1 = x2;
            x2 = temp;
        }
        int w = ((x2 - x1) - (tw + 2 * padding)) / 2;
        if (w <= padding) {
            g.drawLine(x1, y1, x2, y1);
            if (textOver) {
                offset = -1 * offset / 2;
            }
            g.drawString(text, x1 + w + padding, y1 + offset);
        } else {
            g.drawLine(x1, y1, x1 + w, y1);
            g.drawLine(x2 - w, y1, x2, y1);
            g.drawString(text, x1 + w + padding, (int) (y1 + (bounds.getHeight() / 2)));
        }
        g.drawLine(x1, y1 - CONNECTION_ARROW_SIZE, x1, y1 + CONNECTION_ARROW_SIZE);
        g.drawLine(x2, y1 - CONNECTION_ARROW_SIZE, x2, y1 + CONNECTION_ARROW_SIZE);
    }

    /**
     * Draw a snap vertical margin indicator
     *
     * @param transform view transform
     * @param g         Graphics context
     * @param x1        x coordinate
     * @param y1        start y coordinate
     * @param y2        end y coordinate
     * @param text      text to display below the indicator
     */
    public static void drawSnapVerticalMargin(ViewTransform transform, Graphics2D g, int x1, int y1,
            int y2, String text) {
        Canvas c = new Canvas();
        FontMetrics fm = c.getFontMetrics(sFont);
        int padding = transform.getSwingDimension(4);
        Rectangle2D bounds = fm.getStringBounds(text, g);
        int th = (int) bounds.getHeight();
        int offset = 6 * CONNECTION_ARROW_SIZE;

        if (y1 > y2) {
            int temp = y1;
            y1 = y2;
            y2 = temp;
        }
        int h = ((y2 - y1) - (th + 2 * padding)) / 2;
        if (h <= padding) {
            g.drawLine(x1, y1, x1, y2);
            g.drawString(text, (int) (x1 - bounds.getWidth() / 2) + offset, y2 - h - padding);
        } else {
            g.drawLine(x1, y1, x1, y1 + h);
            g.drawLine(x1, y2 - h, x1, y2);
            g.drawString(text, (int) (x1 - bounds.getWidth() / 2), y2 - h - padding);
        }
        g.drawLine(x1 - CONNECTION_ARROW_SIZE, y1, x1 + CONNECTION_ARROW_SIZE, y1);
        g.drawLine(x1 - CONNECTION_ARROW_SIZE, y2, x1 + CONNECTION_ARROW_SIZE, y2);
    }
}
