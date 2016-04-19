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

import com.android.tools.sherpa.drawing.decorator.WidgetDecorator;
import com.android.tools.sherpa.interaction.ConstraintHandle;
import com.android.tools.sherpa.interaction.ResizeHandle;
import com.android.tools.sherpa.interaction.WidgetInteractionTargets;
import com.android.tools.sherpa.structure.WidgetCompanion;
import com.google.tnt.solver.widgets.ConstraintAnchor;
import com.google.tnt.solver.widgets.ConstraintTableLayout;
import com.google.tnt.solver.widgets.ConstraintWidget;
import com.google.tnt.solver.widgets.ConstraintWidgetContainer;
import com.google.tnt.solver.widgets.Guideline;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.EnumSet;

/**
 * Utility drawing class
 * Contains functions dealing with drawing widgets
 */
public class WidgetDraw {

    private static final boolean DEBUG = false;

    static final int ZIGZAG = 3;
    static final int CENTER_ZIGZAG = 3;

    private static final int GUIDELINE_ARROW_SIZE = 6;
    private static final int ARROW_SIDE = 10;

    private static Polygon sVerticalGuidelineHandle;
    private static Polygon sHorizontalGuidelineHandle;

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
     * @param widgetStyle       current widget style
     */
    public static void drawWidgetFrame(ViewTransform transform, Graphics2D g,
            ConstraintWidget widget, ColorSet colorSet,
            EnumSet<ANCHORS_DISPLAY> showAnchors, boolean showResizeHandles,
            boolean showSizeIndicator, boolean isSelected, int widgetStyle) {
        g.setStroke(SnapDraw.sNormalStroke);
        int l = transform.getSwingX(widget.getDrawX());
        int t = transform.getSwingY(widget.getDrawY());
        int w = transform.getSwingDimension(widget.getDrawWidth());
        int h = transform.getSwingDimension(widget.getDrawHeight());
        int r = transform.getSwingX(widget.getDrawX() + widget.getDrawWidth());
        int b = transform.getSwingY(widget.getDrawY() + widget.getDrawHeight());
        int radius = ConnectionDraw.CONNECTION_ANCHOR_SIZE;
        int circleDimension = radius * 2;
        int radiusRect = ConnectionDraw.CONNECTION_RESIZE_SIZE;
        int rectDimension = radiusRect * 2;
        int midX = transform.getSwingX((int) (widget.getDrawX() + widget.getDrawWidth() / 2f));
        int midY = transform.getSwingY((int) (widget.getDrawY() + widget.getDrawHeight() / 2f));

        if (widget.getParent() instanceof ConstraintWidgetContainer) {
            ConstraintWidgetContainer parent = (ConstraintWidgetContainer) widget.getParent();
            if (widget instanceof Guideline) {
                if (parent.isRoot()) {
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
            if (!leftAnchorIsConnected && !topAnchorIsConnected) {
                g.fillRect(l - radiusRect, t - radiusRect, rectDimension, rectDimension);
            }
            if (!rightAnchorIsConnected && !topAnchorIsConnected) {
                g.fillRect(r - radiusRect, t - radiusRect, rectDimension, rectDimension);
            }
            if (!leftAnchorIsConnected && !bottomAnchorIsConnected) {
                g.fillRect(l - radiusRect, b - radiusRect, rectDimension, rectDimension);
            }
            if (!rightAnchorIsConnected && !bottomAnchorIsConnected) {
                g.fillRect(r - radiusRect, b - radiusRect, rectDimension, rectDimension);
            }
            if (showSizeIndicator) {
                ConnectionDraw
                        .drawHorizontalMarginIndicator(g, "" + widget.getWidth(), l, r, t - 20);
                ConnectionDraw
                        .drawVerticalMarginIndicator(g, "" + widget.getHeight(), l - 20, t, b);
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

        if (true || showAnchors.contains(ANCHORS_DISPLAY.CONNECTED)) {
            showLeftAnchor |= leftAnchorIsConnected;
            showRightAnchor |= rightAnchorIsConnected;
            showTopAnchor |= topAnchorIsConnected;
            showBottomAnchor |= bottomAnchorIsConnected;
            showCenterAnchor |= centerAnchorIsConnected;
            showBaselineAnchor |= baselineAnchorIsConnected;
        }

        if (!ConstraintAnchor.USE_CENTER_ANCHOR) {
            showCenterAnchor = false;
        }

        int anchorInnerMargin = 3;

        WidgetCompanion widgetCompanion = (WidgetCompanion) widget.getCompanionWidget();
        WidgetDecorator decorator = widgetCompanion.getWidgetDecorator(widgetStyle);
        Color backgroundColor = decorator.getBackgroundColor();
        Color previousColor = g.getColor();
        if (isSelected) {
            g.setColor(colorSet.getSelectedConstraints());
        }
        if (showLeftAnchor) {
            ConnectionDraw.drawAnchor(g, backgroundColor, isSelected, l, midY, circleDimension, anchorInnerMargin,
                    leftAnchorIsConnected);
        }
        if (showRightAnchor) {
            ConnectionDraw.drawAnchor(g, backgroundColor, isSelected, r, midY, circleDimension, anchorInnerMargin,
                    rightAnchorIsConnected);
        }
        if (showTopAnchor) {
            ConnectionDraw.drawAnchor(g, backgroundColor, isSelected, midX, t, circleDimension, anchorInnerMargin,
                    topAnchorIsConnected);
        }
        if (showBottomAnchor) {
            ConnectionDraw.drawAnchor(g, backgroundColor, isSelected, midX, b, circleDimension, anchorInnerMargin,
                    bottomAnchorIsConnected);
        }
        if (showCenterAnchor) {
            ConnectionDraw.drawAnchor(g, backgroundColor, isSelected, midX, midY, circleDimension, anchorInnerMargin,
                    centerAnchorIsConnected);
        }

        g.setColor(previousColor);

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
        if (!widget.isRoot() && (horizontalSpring || verticalSpring)) {
            int x = l;
            int y = t;
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
            if (showTopAnchor) {
                g2.drawLine(l, t, midX - radius, t);
                g2.drawLine(midX + radius, t, r, t);
            } else {
                g2.drawLine(l, t, r, t);
            }
            if (showLeftAnchor) {
                g2.drawLine(l, t, l, midY - radius);
                g2.drawLine(l, midY + radius, l, b);
            } else {
                g2.drawLine(l, t, l, b);
            }
            if (showBottomAnchor) {
                g2.drawLine(l, b, midX - radius, b);
                g2.drawLine(midX + radius, b, r, b);
            } else {
                g2.drawLine(l, b, r, b);
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
        g2.setFont(ConnectionDraw.sFont);
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
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setStroke(SnapDraw.sThinDashedStroke);
        int l = transform.getSwingX(root.getDrawX());
        int t = transform.getSwingY(root.getDrawY());
        int r = transform.getSwingX(root.getDrawX() + root.getWidth());
        int b = transform.getSwingY(root.getDrawY() + root.getHeight());
        g.setFont(ConnectionDraw.sFont);
        if (guideline.getOrientation() == Guideline.HORIZONTAL) {
            int x = transform.getSwingX(guideline.getX()) + l;
            g2.drawLine(x, t, x, b);
            int offset = 2;
            int circleSize = ConnectionDraw.ARROW_SIDE - 1;
            Shape circle = new Ellipse2D.Float(x - circleSize,
                    t - 2 * circleSize - offset,
                    2 * circleSize, 2 * circleSize);
            g.draw(circle);
            int relative = guideline.getRelativeBehaviour();
            if (relative == Guideline.RELATIVE_PERCENT) {
                g.drawString("%", x - 4, t - 4);
                if (isSelected) {
                    int percent = (guideline.getX() * 100) / root.getWidth();
                    ConnectionDraw
                            .drawCircledText(g, ConnectionDraw.sFont, "" + percent, x, t + 20);
                }
            } else if (relative == Guideline.RELATIVE_BEGIN) {
                Polygon arrow = ConnectionDraw.getLeftArrow();
                int tx = x - ConnectionDraw.ARROW_SIDE / 2;
                int ty = t - ConnectionDraw.ARROW_SIDE - offset / 2;
                arrow.translate(tx, ty);
                g.fill(arrow);
                arrow.translate(-tx, -ty);
                if (isSelected) {
                    ConnectionDraw
                            .drawHorizontalMarginIndicator(g, "" + guideline.getX(), l, x, ty + 20);
                }
            } else if (relative == Guideline.RELATIVE_END) {
                Polygon arrow = ConnectionDraw.getRightArrow();
                int tx = x + ConnectionDraw.ARROW_SIDE / 2 + 1;
                int ty = t - ConnectionDraw.ARROW_SIDE - offset / 2;
                arrow.translate(tx, ty);
                g.fill(arrow);
                arrow.translate(-tx, -ty);
                if (isSelected) {
                    ConnectionDraw
                            .drawHorizontalMarginIndicator(g,
                                    "" + (root.getWidth() - guideline.getX()), x, r, ty + 20);
                }
            }
        } else {
            int y = transform.getSwingY(guideline.getY()) + t;
            g2.drawLine(l, y, r, y);
            int offset = 2;
            int circleSize = ConnectionDraw.ARROW_SIDE - 1;
            Shape circle = new Ellipse2D.Float(l - 2 * circleSize - offset,
                    y - circleSize, 2 * circleSize, 2 * circleSize);
            g.draw(circle);
            int relative = guideline.getRelativeBehaviour();
            if (relative == Guideline.RELATIVE_PERCENT) {
                g.drawString("%", l - 2 * circleSize + 1, y + 5);
                if (isSelected) {
                    int percent = (guideline.getY() * 100) / root.getHeight();
                    ConnectionDraw
                            .drawCircledText(g, ConnectionDraw.sFont, "" + percent, l + 20, y);
                }
            } else if (relative == Guideline.RELATIVE_BEGIN) {
                Polygon arrow = ConnectionDraw.getTopArrow();
                int tx = l - ConnectionDraw.ARROW_SIDE;
                int ty = y - ConnectionDraw.CONNECTION_ARROW_SIZE;
                arrow.translate(tx, ty);
                g.fill(arrow);
                arrow.translate(-tx, -ty);
                if (isSelected) {
                    ConnectionDraw
                            .drawVerticalMarginIndicator(g, "" + guideline.getY(), l + 20, t, ty);
                }
            } else if (relative == Guideline.RELATIVE_END) {
                Polygon arrow = ConnectionDraw.getBottomArrow();
                int tx = l - ConnectionDraw.ARROW_SIDE;
                int ty = y + ConnectionDraw.CONNECTION_ARROW_SIZE;
                arrow.translate(tx, ty);
                g.fill(arrow);
                arrow.translate(-tx, -ty);
                if (isSelected) {
                    ConnectionDraw
                            .drawVerticalMarginIndicator(g,
                                    "" + (root.getHeight() - guideline.getY()), l + 20, ty, b);
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
            int r = transform.getSwingX(widget.getDrawX() + widget.getDrawWidth());
            int b = transform.getSwingY(widget.getDrawY() + widget.getDrawHeight());
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setClip(l, t, w, h);
            g2.drawString(debugName, l + 2, b - 2);
            g2.dispose();
        }
    }

    /**
     * Draw the constraints of a widget.
     * The color and style used for the drawing will be the current ones in the graphics context.
     *
     * @param transform            view transform
     * @param g                    Graphics context
     * @param widget               the widget whose constraints we draw
     * @param isSelected           if the widget is currently selected
     * @param showPercentIndicator show the percent indicator if center constraints
     */
    public static void drawConstraints(ViewTransform transform, Graphics2D g,
            ColorSet colorSet,
            ConstraintWidget widget, boolean isSelected, boolean showPercentIndicator,
            boolean showMargins) {
        if (widget.getVisibility() == ConstraintWidget.INVISIBLE) {
            g.setStroke(SnapDraw.sDashedStroke);
        }
        ArrayList<ConstraintAnchor.Type> anchors = new ArrayList<ConstraintAnchor.Type>();
        if (isSelected && widget.hasBaseline()) {
            anchors.add(ConstraintAnchor.Type.BASELINE);
        }
        anchors.add(ConstraintAnchor.Type.LEFT);
        anchors.add(ConstraintAnchor.Type.TOP);
        anchors.add(ConstraintAnchor.Type.RIGHT);
        anchors.add(ConstraintAnchor.Type.BOTTOM);
        Color currentColor = g.getColor();
        for (ConstraintAnchor.Type type : anchors) {
            ConstraintAnchor anchor = widget.getAnchor(type);
            if (anchor == null) {
                continue;
            }
            if (anchor.isConnected()) {
                ConstraintAnchor target = anchor.getTarget();
                if (target.getOwner().getVisibility() == ConstraintWidget.GONE) {
                    continue;
                }
                ConstraintHandle startHandle = WidgetInteractionTargets.constraintHandle(anchor);
                ConstraintHandle endHandle = WidgetInteractionTargets.constraintHandle(target);
                if (startHandle == null || endHandle == null) {
                    continue;
                }
                if (startHandle.getAnchor().isConnected()
                        && startHandle.getAnchor().getConnectionCreator()
                        == ConstraintAnchor.AUTO_CONSTRAINT_CREATOR) {
                    g.setColor(new Color(currentColor.getRed(), currentColor.getGreen(),
                            currentColor.getBlue(), 60));
                } else {
                    g.setColor(currentColor);
                }
                ConnectionDraw.drawConnection(transform, g, startHandle, endHandle,
                        isSelected, showPercentIndicator, isSelected);
            } else if (isSelected) {
                ConstraintHandle startHandle = WidgetInteractionTargets.constraintHandle(anchor);
                if (startHandle == null) {
                    continue;
                }
                ConnectionDraw.drawConnection(transform, g, startHandle, null,
                        isSelected, showPercentIndicator, isSelected);
            }
        }
        g.setStroke(SnapDraw.sNormalStroke);
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
}
