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

import com.android.tools.sherpa.interaction.ResizeHandle;
import com.android.tools.sherpa.interaction.WidgetInteractionTargets;
import android.support.constraint.solver.widgets.ConstraintAnchor;
import android.support.constraint.solver.widgets.ConstraintTableLayout;
import android.support.constraint.solver.widgets.ConstraintWidget;
import android.support.constraint.solver.widgets.ConstraintWidgetContainer;
import android.support.constraint.solver.widgets.Guideline;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.EnumSet;

import static com.android.tools.sherpa.drawing.ConnectionDraw.sFont;

/**
 * Utility drawing class
 * Contains functions dealing with drawing widgets
 */
public class WidgetDraw {

    private static final boolean DEBUG = false;

    static final int ZIGZAG = 2;
    static final int CENTER_ZIGZAG = 3;

    public static final long TOOLTIP_DELAY = 800; // in ms

    private static final int GUIDDELINE_ICON_SIZE = 16;
    private static final int GUIDELINE_ARROW_SIZE = 6;
    private static final int ARROW_SIDE = 10;

    private static Polygon sVerticalGuidelineHandle;
    private static Polygon sHorizontalGuidelineHandle;

    // TODO: fix the loading image pattern
    public static Image sGuidelinePercent = null;
    public static Image sGuidelineArrowLeft = null;
    public static Image sGuidelineArrowRight = null;
    public static Image sGuidelineArrowUp = null;
    public static Image sGuidelineArrowDown = null;

    /**
     * Enum encapsulating the policy deciding how to display anchors
     */
    public enum ANCHORS_DISPLAY {
        NONE, HORIZONTAL, VERTICAL, LEFT, RIGHT, BASELINE,
        TOP, BOTTOM, CENTER, CONNECTED, ALL, SELECTED
    }

    /**
     * Draw the widget frame (resizable area...) as well as
     * the constraints anchors and their state.
     * The color and style used for the drawing will be the current ones in the graphics context.
     *
     * @param transform         view transform
     * @param g                 Graphics context
     * @param widget            the widget we are drawing
     * @param showAnchors       determinate how to display the Constraints anchors points
     * @param showResizeHandles pass true to show Resize handles
     * @param isSelected        if the widget is currently selected
     */
    public static void drawWidgetFrame(ViewTransform transform, Graphics2D g,
            ConstraintWidget widget,
            EnumSet<ANCHORS_DISPLAY> showAnchors, boolean showResizeHandles,
            boolean showSizeIndicator, boolean isSelected) {
        g.setStroke(SnapDraw.sNormalStroke);
        int l = transform.getSwingX(widget.getDrawX());
        int t = transform.getSwingY(widget.getDrawY());
        int w = transform.getSwingDimension(widget.getDrawWidth());
        int h = transform.getSwingDimension(widget.getDrawHeight());
        int r = transform.getSwingX(widget.getDrawX() + widget.getDrawWidth());
        int b = transform.getSwingY(widget.getDrawY() + widget.getDrawHeight());
        int radius = ConnectionDraw.CONNECTION_ANCHOR_SIZE;
        int radiusRect = ConnectionDraw.CONNECTION_RESIZE_SIZE;
        int rectDimension = radiusRect * 2;
        int midX = transform.getSwingX((int) (widget.getDrawX() + widget.getDrawWidth() / 2f));
        int midY = transform.getSwingY((int) (widget.getDrawY() + widget.getDrawHeight() / 2f));

        if (widget.getParent() instanceof ConstraintWidgetContainer) {
            ConstraintWidgetContainer parent = (ConstraintWidgetContainer) widget.getParent();
            if (widget instanceof Guideline) {
                if (parent.isRootContainer()) {
                    drawRootGuideline(transform, g, parent, (Guideline) widget, isSelected);
                }
                return;
            }
        }

        if (widget.getVisibility() == ConstraintWidget.INVISIBLE) {
            g.setStroke(SnapDraw.sDashedStroke);
        }
        ConstraintAnchor leftAnchor = widget.getAnchor(ConstraintAnchor.Type.LEFT);
        ConstraintAnchor rightAnchor = widget.getAnchor(ConstraintAnchor.Type.RIGHT);
        ConstraintAnchor topAnchor = widget.getAnchor(ConstraintAnchor.Type.TOP);
        ConstraintAnchor bottomAnchor = widget.getAnchor(ConstraintAnchor.Type.BOTTOM);

        boolean leftAnchorIsConnected = leftAnchor.isConnected();
        boolean rightAnchorIsConnected = rightAnchor.isConnected();
        boolean topAnchorIsConnected = topAnchor.isConnected();
        boolean bottomAnchorIsConnected = bottomAnchor.isConnected();
        boolean baselineAnchorIsConnected =
                widget.getAnchor(ConstraintAnchor.Type.BASELINE).isConnected();
        boolean centerAnchorIsConnected =
                (leftAnchorIsConnected && rightAnchorIsConnected
                        && leftAnchor.getTarget() == rightAnchor.getTarget())
                        || (topAnchorIsConnected && bottomAnchorIsConnected
                        && topAnchor.getTarget() == bottomAnchor.getTarget());

        // First, resize handles...
        if (showResizeHandles) {
            g.fillRect(l - radiusRect, t - radiusRect, rectDimension, rectDimension);
            g.fillRect(r - radiusRect, t - radiusRect, rectDimension, rectDimension);
            g.fillRect(l - radiusRect, b - radiusRect, rectDimension, rectDimension);
            g.fillRect(r - radiusRect, b - radiusRect, rectDimension, rectDimension);
            if (showSizeIndicator) {
                ConnectionDraw
                        .drawHorizontalMarginIndicator(g, String.valueOf(widget.getWidth()), l, r,
                                t - 20);
                ConnectionDraw
                        .drawVerticalMarginIndicator(g, String.valueOf(widget.getHeight()), l - 20,
                                t, b);
            }
        }

        // Then, let's draw the constraints anchors

        boolean displayAllAnchors = showAnchors.contains(ANCHORS_DISPLAY.ALL);
        boolean showLeftAnchor = displayAllAnchors
                || showAnchors.contains(ANCHORS_DISPLAY.LEFT)
                || showAnchors.contains(ANCHORS_DISPLAY.HORIZONTAL);
        boolean showRightAnchor = displayAllAnchors
                || showAnchors.contains(ANCHORS_DISPLAY.RIGHT)
                || showAnchors.contains(ANCHORS_DISPLAY.HORIZONTAL);
        boolean showTopAnchor = displayAllAnchors
                || showAnchors.contains(ANCHORS_DISPLAY.TOP)
                || showAnchors.contains(ANCHORS_DISPLAY.VERTICAL);
        boolean showBottomAnchor = displayAllAnchors
                || showAnchors.contains(ANCHORS_DISPLAY.BOTTOM)
                || showAnchors.contains(ANCHORS_DISPLAY.VERTICAL);
        boolean showCenterAnchor = displayAllAnchors
                || showAnchors.contains(ANCHORS_DISPLAY.CENTER);
        boolean showBaselineAnchor =
                displayAllAnchors || showAnchors.contains(ANCHORS_DISPLAY.BASELINE);

        if (!showAnchors.contains(ANCHORS_DISPLAY.NONE)
                && showAnchors.contains(ANCHORS_DISPLAY.CONNECTED)) {
            showLeftAnchor |= leftAnchorIsConnected;
            showRightAnchor |= rightAnchorIsConnected;
            showTopAnchor |= topAnchorIsConnected;
            showBottomAnchor |= bottomAnchorIsConnected;
            showCenterAnchor |= centerAnchorIsConnected;
            showBaselineAnchor |= baselineAnchorIsConnected;
        }

        if (showBaselineAnchor && !(widget instanceof ConstraintWidgetContainer) &&
                widget.getBaselineDistance() > 0) {
            int baselineY = transform
                    .getSwingY(
                            WidgetInteractionTargets.constraintHandle(
                                    widget.getAnchor(ConstraintAnchor.Type.BASELINE)).getDrawY());
            g.drawLine(l, baselineY, r, baselineY);
        }

        // Now, let's draw the widget's frame

        boolean horizontalSpring =
                widget.getHorizontalDimensionBehaviour() == ConstraintWidget.DimensionBehaviour.ANY;
        boolean verticalSpring =
                widget.getVerticalDimensionBehaviour() == ConstraintWidget.DimensionBehaviour.ANY;
        Graphics2D g2 = (Graphics2D) g.create();
        if (widget instanceof ConstraintWidgetContainer) {
            g2.setStroke(SnapDraw.sLongDashedStroke);
            if (widget instanceof ConstraintTableLayout) {
                drawTableLayoutGuidelines(transform, g2, (ConstraintTableLayout) widget);
            }
        }
        if (!widget.isRootContainer() && (horizontalSpring || verticalSpring)) {
            int x = l;
            int y = t;
            Stroke previousStroke = g.getStroke();
            if (baselineAnchorIsConnected) {
                g2.setStroke(ConnectionDraw.sSpreadDashedStroke);
            }
            if (horizontalSpring) {
                if (showTopAnchor) {
                    drawHorizontalZigZagLine(g2, l, midX - radius, t, ZIGZAG, 0);
                    drawHorizontalZigZagLine(g2, midX + radius, r, t, ZIGZAG, 0);
                } else {
                    drawHorizontalZigZagLine(g2, l, r, t, ZIGZAG, 0);
                }
                if (showBottomAnchor) {
                    drawHorizontalZigZagLine(g2, l, midX - radius, b, -ZIGZAG, 0);
                    drawHorizontalZigZagLine(g2, midX + radius, r, b, -ZIGZAG, 0);
                } else {
                    drawHorizontalZigZagLine(g2, l, r, b, -ZIGZAG, 0);
                }
            } else {
                g2.drawLine(x, y, x + w, y);
                g2.drawLine(x, y + h, x + w, y + h);
            }
            g2.setStroke(previousStroke);

            if (verticalSpring) {
                if (showLeftAnchor) {
                    drawVerticalZigZagLine(g2, l, t, midY - radius, ZIGZAG, 0);
                    drawVerticalZigZagLine(g2, l, midY + radius, b, ZIGZAG, 0);
                } else {
                    drawVerticalZigZagLine(g2, l, t, b, ZIGZAG, 0);
                }
                if (showRightAnchor) {
                    drawVerticalZigZagLine(g2, r, t, midY - radius, -ZIGZAG, 0);
                    drawVerticalZigZagLine(g2, r, midY + radius, b, -ZIGZAG, 0);
                } else {
                    drawVerticalZigZagLine(g2, r, t, b, -ZIGZAG, 0);
                }
            } else {
                g2.drawLine(x, y, x, y + h);
                g2.drawLine(x + w, y, x + w, y + h);
            }
        } else {
            Stroke previousStroke = g.getStroke();
            if (baselineAnchorIsConnected) {
                g2.setStroke(ConnectionDraw.sSpreadDashedStroke);
            }
            if (showTopAnchor) {
                g2.drawLine(l, t, midX - radius, t);
                g2.drawLine(midX + radius, t, r, t);
            } else {
                g2.drawLine(l, t, r, t);
            }
            if (showBottomAnchor) {
                g2.drawLine(l, b, midX - radius, b);
                g2.drawLine(midX + radius, b, r, b);
            } else {
                g2.drawLine(l, b, r, b);
            }
            g2.setStroke(previousStroke);
            if (showLeftAnchor) {
                g2.drawLine(l, t, l, midY - radius);
                g2.drawLine(l, midY + radius, l, b);
            } else {
                g2.drawLine(l, t, l, b);
            }
            if (showRightAnchor) {
                g2.drawLine(r, t, r, midY - radius);
                g2.drawLine(r, midY + radius, r, b);
            } else {
                g2.drawLine(r, t, r, b);
            }
        }
        g2.dispose();

        if (DEBUG) {
            // Draw diagonals
            g.drawLine(l, t, r, b);
            g.drawLine(l, b, r, t);
        }
        g.setStroke(SnapDraw.sNormalStroke);
    }

    /**
     * Draw the internal guidelines of a ConstraintTableLayout
     *
     * @param transform view transform
     * @param g         graphics context
     * @param table     the ConstraintTableLayout we are drawing
     */
    private static void drawTableLayoutGuidelines(ViewTransform transform, Graphics2D g,
            ConstraintTableLayout table) {
        Graphics2D g2 = (Graphics2D) g.create();
        ArrayList<Guideline> vertical = table.getVerticalGuidelines();
        ArrayList<Guideline> horizontal = table.getHorizontalGuidelines();
        g2.setStroke(SnapDraw.sThinDashedStroke);
        int l = transform.getSwingX(table.getDrawX());
        int t = transform.getSwingY(table.getDrawY());
        int r = transform.getSwingX(table.getDrawX() + table.getDrawWidth());
        int b = transform.getSwingY(table.getDrawY() + table.getDrawHeight());
        for (ConstraintWidget v : vertical) {
            int x = transform.getSwingX(v.getX()) + l;
            g2.drawLine(x, t, x, b);
        }
        for (ConstraintWidget h : horizontal) {
            int y = transform.getSwingY(h.getY()) + t;
            g2.drawLine(l, y, r, y);
        }
        g2.dispose();
    }

    /**
     * Draw table controls when selected
     *
     * @param transform view transform
     * @param g         graphics context
     * @param table     the ConstraintTableLayout we are drawing
     */
    public static void drawTableControls(ViewTransform transform, Graphics2D g,
            ConstraintTableLayout table) {
        ArrayList<Guideline> vertical = table.getVerticalGuidelines();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setStroke(new BasicStroke(1));
        int l = transform.getSwingX(table.getDrawX());
        int t = transform.getSwingY(table.getDrawY());
        g2.setFont(sFont);
        g2.drawRect(l, t - 20 - 4, 20, 20);
        int column = 0;
        String align = table.getColumnAlignmentRepresentation(column++);
        g2.drawString(align, l + 5, t - 4 - 5);
        for (ConstraintWidget v : vertical) {
            int x = transform.getSwingX(v.getX()) + l;
            g2.drawRect(x, t - 20 - 4, 20, 20);
            align = table.getColumnAlignmentRepresentation(column++);
            g2.drawString(align, x + 5, t - 4 - 5);
        }
        g2.dispose();
    }

    /**
     * Draw the guideline of a root ConstraintLayout
     *
     * @param transform  view transform
     * @param g          graphics context
     * @param root       the root container we are drawing on
     * @param guideline  the guideline to draw
     * @param isSelected if the guideline is currently selected
     */
    private static void drawRootGuideline(ViewTransform transform, Graphics2D g,
                                          ConstraintWidgetContainer root, Guideline guideline, boolean isSelected) {
        Graphics2D g2 = (Graphics2D)g.create();
        g2.setStroke(SnapDraw.sThinDashedStroke);
        int l = transform.getSwingX(root.getDrawX());
        int t = transform.getSwingY(root.getDrawY());
        int r = transform.getSwingX(root.getDrawX() + root.getWidth());
        int b = transform.getSwingY(root.getDrawY() + root.getHeight());
        g.setFont(sFont);
        if (guideline.getOrientation() == Guideline.VERTICAL) {
            int x = transform.getSwingX(guideline.getDrawX());
            g2.drawLine(x, t, x, b);
            int offset = 2;
            int circleSize = GUIDDELINE_ICON_SIZE / 2 + 1;
            Shape circle = new Ellipse2D.Float(x - circleSize,
                                               t - 2 * circleSize - offset,
                                               2 * circleSize, 2 * circleSize);
            g.fill(circle);
            int relative = guideline.getRelativeBehaviour();
            if (relative == Guideline.RELATIVE_PERCENT) {
                int iconWidth = sGuidelinePercent.getWidth(null);
                int iconHeight = sGuidelinePercent.getHeight(null);
                g.drawImage(sGuidelinePercent, x - GUIDDELINE_ICON_SIZE / 2, t - GUIDDELINE_ICON_SIZE - 3, x + GUIDDELINE_ICON_SIZE / 2, t - 3,
                            0, 0, iconWidth, iconHeight, null);
                if (isSelected) {
                    int percent = (guideline.getX() * 100) / root.getWidth();
                    ConnectionDraw.drawCircledText(g, sFont, String.valueOf(percent), x, t + 20);
                }
            }
            else if (relative == Guideline.RELATIVE_BEGIN) {
                int iconWidth = sGuidelineArrowLeft.getWidth(null);
                int iconHeight = sGuidelineArrowLeft.getHeight(null);
                int ty = t - ConnectionDraw.ARROW_SIDE - offset / 2;
                g.drawImage(sGuidelineArrowLeft, x - GUIDDELINE_ICON_SIZE / 2, t - GUIDDELINE_ICON_SIZE - 3, x + GUIDDELINE_ICON_SIZE / 2, t - 3,
                            0, 0, iconWidth, iconHeight, null);
                if (isSelected) {
                    ConnectionDraw
                      .drawHorizontalMarginIndicator(g, String.valueOf(guideline.getX()), l,
                                                     x, ty + 20);
                }
            }
            else if (relative == Guideline.RELATIVE_END) {
                int iconWidth = sGuidelineArrowRight.getWidth(null);
                int iconHeight = sGuidelineArrowRight.getHeight(null);
                int ty = t - ConnectionDraw.ARROW_SIDE - offset / 2;
                g.drawImage(sGuidelineArrowRight, x - GUIDDELINE_ICON_SIZE / 2 + 1, t - GUIDDELINE_ICON_SIZE - 3, x + GUIDDELINE_ICON_SIZE / 2, t - 3,
                            0, 0, iconWidth, iconHeight, null);
                if (isSelected) {
                    ConnectionDraw.drawHorizontalMarginIndicator(
                      g, String.valueOf(root.getWidth() - guideline.getX()), x, r, ty + 20);
                }
            }
        }
        else {
            int y = transform.getSwingY(guideline.getDrawY());
            g2.drawLine(l, y, r, y);
            int offset = 2;
            int circleSize = GUIDDELINE_ICON_SIZE / 2 + 1;
            Shape circle = new Ellipse2D.Float(l - 2 * circleSize - offset,
                                               y - circleSize, 2 * circleSize, 2 * circleSize);
            g.fill(circle);
            int relative = guideline.getRelativeBehaviour();
            if (relative == Guideline.RELATIVE_PERCENT) {
                int iconWidth = sGuidelinePercent.getWidth(null);
                int iconHeight = sGuidelinePercent.getHeight(null);
                g.drawImage(sGuidelinePercent, l - GUIDDELINE_ICON_SIZE - 3, y - GUIDDELINE_ICON_SIZE / 2, l - 3, y + GUIDDELINE_ICON_SIZE / 2,
                            0, 0, iconWidth, iconHeight, null);
                if (isSelected) {
                    int percent = (guideline.getY() * 100) / root.getHeight();
                    ConnectionDraw.drawCircledText(g, sFont, String.valueOf(percent), l + 20, y);
                }
            }
            else if (relative == Guideline.RELATIVE_BEGIN) {
                int iconWidth = sGuidelineArrowUp.getWidth(null);
                int iconHeight = sGuidelineArrowUp.getHeight(null);
                g.drawImage(sGuidelineArrowUp, l - GUIDDELINE_ICON_SIZE - 3, y - GUIDDELINE_ICON_SIZE / 2, l - 3, y + GUIDDELINE_ICON_SIZE / 2,
                            0, 0, iconWidth, iconHeight, null);
                if (isSelected) {
                    ConnectionDraw.drawVerticalMarginIndicator(g, String.valueOf(guideline.getY()),
                                                               l + 20, t, y);
                }
            }
            else if (relative == Guideline.RELATIVE_END) {
                int iconWidth = sGuidelineArrowDown.getWidth(null);
                int iconHeight = sGuidelineArrowDown.getHeight(null);
                Polygon arrow = ConnectionDraw.getBottomArrow();
                g.drawImage(sGuidelineArrowDown, l - GUIDDELINE_ICON_SIZE - 3, y - GUIDDELINE_ICON_SIZE / 2 + 1, l - 3, y + GUIDDELINE_ICON_SIZE / 2,
                            0, 0, iconWidth, iconHeight, null);
                if (isSelected) {
                    ConnectionDraw.drawVerticalMarginIndicator(
                      g, String.valueOf(root.getHeight() - guideline.getY()), l + 20, y, b);
                }
            }
        }
        g2.dispose();
    }

    /**
     * Draw the widget informations (i.e. android id)
     *
     * @param transform view transform
     * @param g         Graphics context
     * @param widget    the widget we draw on / get the info from
     */
    public static void drawWidgetInfo(ViewTransform transform, Graphics2D g,
            ConstraintWidget widget) {
        String debugName = widget.getDebugName();
        if (debugName != null) {
            int l = transform.getSwingX(widget.getDrawX());
            int t = transform.getSwingY(widget.getDrawY());
            int w = transform.getSwingDimension(widget.getDrawWidth());
            int h = transform.getSwingDimension(widget.getDrawHeight());
            int b = transform.getSwingY(widget.getDrawY() + widget.getDrawHeight());
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setClip(l, t, w, h);
            g2.drawString(debugName, l + 2, b - 2);
            g2.dispose();
        }
    }

    /**
     * Draw resize handle selection
     *
     * @param transform            view transform
     * @param g                    Graphics context
     * @param selectedResizeHandle the resize handle
     */
    public static void drawResizeHandleSelection(ViewTransform transform, Graphics2D g,
            ResizeHandle selectedResizeHandle) {
        Rectangle bounds = selectedResizeHandle.getSwingBounds(transform);
        g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    /**
     * Draw an horizontal, centered zig-zag line.
     * The color and style used for the drawing will be the current ones in the graphics context.
     *
     * @param g  Graphics context
     * @param x1 start x point
     * @param x2 end x point
     * @param y  y point
     */
    public static void drawHorizontalZigZagLine(Graphics2D g, int x1, int x2, int y) {
        drawHorizontalZigZagLine(g, x1, x2, y, CENTER_ZIGZAG, CENTER_ZIGZAG);
    }

    /**
     * Draw an horizontal zig-zag line.
     * The color and style used for the drawing will be the current ones in the graphics context.
     *
     * @param g   Graphics context
     * @param x1  start x point
     * @param x2  end x point
     * @param y   y point
     * @param dY1 positive height of the zig-zag
     * @param dY2 negative height of the zig-zag
     */
    static void drawHorizontalZigZagLine(Graphics2D g, int x1, int x2, int y, int dY1, int dY2) {
        if (x2 < x1) {
            int temp = x1;
            x1 = x2;
            x2 = temp;
        }
        int distance = x2 - x1;
        int step = ZIGZAG * 2 + (dY2 > 0 ? ZIGZAG : 0);
        int count = (distance / step) - 2;
        int remainings = distance - (count * step);
        int x = x1 + remainings / 2;
        g.drawLine(x1, y, x, y);
        for (int i = 0; i < count; i++) {
            g.drawLine(x, y, x + ZIGZAG, y + dY1);
            g.drawLine(x + ZIGZAG, y + dY1, x + 2 * ZIGZAG, y - dY2);
            if (dY2 != 0) {
                g.drawLine(x + 2 * ZIGZAG, y - dY2, x + 3 * ZIGZAG, y);
            }
            x += step;
        }
        g.drawLine(x, y, x2, y);
    }

    /**
     * Draw a vertical, centered zig-zag line.
     * The color and style used for the drawing will be the current ones in the graphics context.
     *
     * @param g  Graphics context
     * @param x  x point
     * @param y1 start y point
     * @param y2 end y point
     */
    public static void drawVerticalZigZagLine(Graphics2D g, int x, int y1, int y2) {
        drawVerticalZigZagLine(g, x, y1, y2, CENTER_ZIGZAG, CENTER_ZIGZAG);
    }

    /**
     * Draw a vertical zig-zag line.
     * The color and style used for the drawing will be the current ones in the graphics context.
     *
     * @param g   Graphics context
     * @param x   x point
     * @param y1  start y point
     * @param y2  end y point
     * @param dX1 positive width of the zig-zag
     * @param dX2 negative width of the zig-zag
     */
    static void drawVerticalZigZagLine(Graphics2D g, int x, int y1, int y2, int dX1, int dX2) {
        if (y2 < y1) {
            int temp = y1;
            y1 = y2;
            y2 = temp;
        }
        int distance = y2 - y1;
        int step = ZIGZAG * 2 + (dX2 > 0 ? ZIGZAG : 0);
        int count = (distance / step) - 2;
        int remainings = distance - (count * step);
        int y = y1 + remainings / 2;
        g.drawLine(x, y1, x, y);
        for (int i = 0; i < count; i++) {
            g.drawLine(x, y, x + dX1, y + ZIGZAG);
            g.drawLine(x + dX1, y + ZIGZAG, x - dX2, y + 2 * ZIGZAG);
            if (dX2 != 0) {
                g.drawLine(x - dX2, y + 2 * ZIGZAG, x, y + 3 * ZIGZAG);
            }
            y += step;
        }
        g.drawLine(x, y, x, y2);
    }

    /**
     * Static accessor to the vertical guideline handle
     *
     * @return return a Polygon representing a vertical guideline handle
     */
    private static Polygon getVerticalGuidelineHandle() {
        if (sVerticalGuidelineHandle == null) {
            sVerticalGuidelineHandle = new Polygon();
            sVerticalGuidelineHandle.addPoint(0, 0);
            sVerticalGuidelineHandle.addPoint(-GUIDELINE_ARROW_SIZE, -ARROW_SIDE);
            sVerticalGuidelineHandle.addPoint(+GUIDELINE_ARROW_SIZE, -ARROW_SIDE);
        }
        return sVerticalGuidelineHandle;
    }

    /**
     * Static accessor to the horizontal guideline handle
     *
     * @return return a Polygon representing a horizontal guideline handle
     */
    private static Polygon getHorizontalGuidelineHandle() {
        if (sHorizontalGuidelineHandle == null) {
            sHorizontalGuidelineHandle = new Polygon();
            sHorizontalGuidelineHandle.addPoint(0, 0);
            sHorizontalGuidelineHandle.addPoint(-ARROW_SIDE, -GUIDELINE_ARROW_SIZE);
            sHorizontalGuidelineHandle.addPoint(-ARROW_SIDE, +GUIDELINE_ARROW_SIZE);
        }
        return sHorizontalGuidelineHandle;
    }

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

        g.setColor(colorSet.getBackground());
        g.setStroke(sLineShadowStroke);
        triangle.translate(x, triangleY);
        g.fillPolygon(triangle);
        g.draw(triangle);
        triangle.translate(-x, -triangleY);
        g.fillRoundRect(rectX, rectY, textWidth, textHeight, 2, 2);
        g.drawRoundRect(rectX, rectY, textWidth, textHeight, 2, 2);

        g.setColor(colorSet.getTooltipBackground());
        g.setStroke(sBasicStroke);
        triangle.translate(x, triangleY);
        g.fillPolygon(triangle);
        g.draw(triangle);
        triangle.translate(-x, -triangleY);
        g.fillRoundRect(rectX, rectY, textWidth, textHeight, 2, 2);
        g.drawRoundRect(rectX, rectY, textWidth, textHeight, 2, 2);

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
