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

package com.android.tools.sherpa.interaction;

import com.android.tools.sherpa.drawing.ColorSet;
import com.android.tools.sherpa.drawing.ConnectionDraw;
import com.android.tools.sherpa.drawing.ViewTransform;
import com.android.tools.sherpa.drawing.decorator.WidgetDecorator;
import com.android.tools.sherpa.structure.WidgetCompanion;
import com.google.tnt.solver.widgets.ConstraintAnchor;
import com.google.tnt.solver.widgets.ConstraintWidget;
import com.google.tnt.solver.widgets.Guideline;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;

/**
 * Represents a constraint handle on the widget.
 * Set its bounds given its owner's bounds and its type.
 */
public class ConstraintHandle {

    private final WidgetInteractionTargets mOwner;
    private final ConstraintAnchor.Type mType;
    private ConstraintAnchor mAnchor;

    private int mX;
    private int mY;
    private int[] mCurve = new int[8];
    private boolean mHasCurve = false;

    static final Color sShadowColor = new Color(0, 0, 0, 50);
    static final Stroke sShadowStroke = new BasicStroke(3);
    static final Stroke sLineShadowStroke = new BasicStroke(5);
    static final Stroke sSimpleStroke = new BasicStroke(1);

    class ConnectionDrawing {
        ArrayList<Shape> mShapes = new ArrayList();
        Path2D.Float mPath = new Path2D.Float();
        Polygon mArrow;
        boolean mDrawEndCircle = false;
        boolean mDrawArrow = true;
        int mArrowX;
        int mArrowY;

        public void draw(Graphics2D g) {
            g.draw(mPath);
            if (mArrow != null && mDrawArrow) {
                Stroke pre = g.getStroke();
                g.setStroke(sSimpleStroke);
                if (mDrawEndCircle) {
                    int radius = ConnectionDraw.CONNECTION_ANCHOR_SIZE;
                    int diameter = radius * 2;
                    g.drawRoundRect(mArrowX - radius, mArrowY - radius, diameter, diameter,
                            diameter, diameter);
                }
                ConnectionDraw.drawArrow(g, mArrow, mArrowX, mArrowY);
                g.setStroke(pre);
            }
            for (Shape shape : mShapes) {
                g.draw(shape);
            }
        }

        public void setArrow(Polygon shape, int x, int y) {
            mArrow = shape;
            mArrowX = x;
            mArrowY = y;
        }

        public void addShape(Shape shape) {
            mShapes.add(shape);
        }
    }

    /**
     * Default constructor
     *
     * @param owner the owner of this ConstraintHandle
     * @param type  the type of ConstraintHandle
     */
    public ConstraintHandle(
            WidgetInteractionTargets owner,
            ConstraintAnchor.Type type) {
        mOwner = owner;
        mType = type;
    }

    /**
     * Return the owner of this ConstraintHandle (the widget it belongs to)
     *
     * @return the ConstraintHandle's owner
     */
    public ConstraintWidget getOwner() {
        return mOwner.getConstraintWidget();
    }

    /**
     * Return the ConstraintAnchor we represent
     *
     * @return the ConstraintAnchor we represent
     */
    public ConstraintAnchor getAnchor() {
        return mAnchor;
    }

    /**
     * Retrieve the anchor from the owner's constraint widget
     */
    public void updateAnchor() {
        if (mOwner != null) {
            ConstraintWidget widget = mOwner.getConstraintWidget();
            // FIXME: should use a subclass
            if (widget instanceof Guideline) {
                mAnchor = ((Guideline) widget).getAnchor();
            } else {
                mAnchor = widget.getAnchor(mType);
            }
        } else {
            mAnchor = null;
        }
    }

    /**
     * Set the curve parameters
     *
     * @param x1
     * @param y1
     * @param x2
     * @param y2
     * @param x3
     * @param y3
     * @param x4
     * @param y4
     */
    public void setCurveParameters(int x1, int y1, int x2, int y2, int x3, int y3, int x4, int y4) {
        mCurve[0] = x1;
        mCurve[1] = y1;
        mCurve[2] = x2;
        mCurve[3] = y2;
        mCurve[4] = x3;
        mCurve[5] = y3;
        mCurve[6] = x4;
        mCurve[7] = y4;
        mHasCurve = true;
    }

    /**
     * Set true if the connection is represented by a curve
     *
     * @param hasCurve
     */
    public void setCurve(boolean hasCurve) {
        mHasCurve = hasCurve;
    }

    /**
     * Return true if the connection is represented by a curve
     *
     * @return true if curve, false otherwise
     */
    public boolean hasCurve() {
        return mHasCurve;
    }

    /**
     * Return an array of 4 pairs of integers representing a curve
     *
     * @return the curve parameters
     */
    public int[] getCurve() {
        return mCurve;
    }

    /**
     * Return the x position of this anchor
     *
     * @return x position
     */
    public int getDrawX() {
        return mX;
    }

    /**
     * Return the y position of this anchor
     *
     * @return y position
     */
    public int getDrawY() {
        return mY;
    }

    /**
     * Setter for the x position of this anchor
     *
     * @param x the new x position
     */
    public void setDrawX(int x) {
        mX = x;
    }

    /**
     * Setter for the y position of this anchor
     *
     * @param y the new y position
     */
    public void setDrawY(int y) {
        mY = y;
    }

    /**
     * Update the position of the anchor depending on the owner's position
     *
     * @param viewTransform the view transform
     */
    void updatePosition(ViewTransform viewTransform) {
        if (mOwner == null) {
            mX = 0;
            mY = 0;
            return;
        }
        ConstraintWidget widget = mOwner.getConstraintWidget();
        int x = widget.getDrawX();
        int y = widget.getDrawY();
        int w = widget.getDrawWidth();
        int h = widget.getDrawHeight();
        switch (mAnchor.getType()) {
            case LEFT: {
                mX = x;
                mY = y + h / 2;
            }
            break;
            case TOP: {
                mX = x + w / 2;
                mY = y;
            }
            break;
            case RIGHT: {
                mX = x + w;
                mY = y + h / 2;
            }
            break;
            case BOTTOM: {
                mX = x + w / 2;
                mY = y + h;
            }
            break;
            case CENTER:
            case CENTER_X:
            case CENTER_Y: {
                mX = x + w / 2;
                mY = y + h / 2;
            }
            break;
            case BASELINE: {
                mX = x + w / 2;
                mY = y + widget.getBaselineDistance();
            }
            break;
        }
    }

    /**
     * Return the value of the margin at creation time, getting
     * the distance between the anchor source and the anchor target
     *
     * @param anchor the target anchor
     * @return the computed margin
     */
    public int getCreationMarginFrom(ConstraintHandle anchor) {
        int distance = 0;
        switch (mAnchor.getType()) {
            case LEFT: {
                distance = mX - anchor.mX;
            }
            break;
            case RIGHT: {
                distance = anchor.mX - mX;
            }
            break;
            case TOP: {
                distance = mY - anchor.mY;
            }
            break;
            case BOTTOM: {
                distance = anchor.mY - mY;
            }
            break;
        }
        if (distance < 0) {
            return 0;
        }
        return distance;
    }

    /**
     * Return the straight line distance from this anchor to another.
     * Basically, we return only the horizontal or vertical distance
     * for a compatible anchor, or Integer.MAX_VALUE otherwise.
     *
     * @param anchor the anchor we are comparing to
     * @return the straight line distance or Integer.MAX_VALUE if incompatible anchor.
     */
    public int getStraightDistanceFrom(ConstraintHandle anchor) {
        if (anchor == null) {
            return Integer.MAX_VALUE;
        }
        switch (mAnchor.getType()) {
            case CENTER: {
                if (anchor.getAnchor().isVerticalAnchor()) {
                    return Math.abs(anchor.mY - mY);
                } else {
                    return Math.abs(anchor.mX - mX);
                }
            }
            case LEFT:
            case RIGHT:
            case CENTER_X: {
                return Math.abs(anchor.mX - mX);
            }
            case TOP:
            case BOTTOM:
            case BASELINE:
            case CENTER_Y: {
                return Math.abs(anchor.mY - mY);
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Return the minimum distance from this anchor's side to another component's side
     * (hitting the first compatible side of the component). For example, looking at the
     * left anchor, we'll return the minimum distance between the vertical sides of this anchor's
     * component to the vertical sides of the target component.
     *
     * @param component the component we are looking at
     * @return the distance between this anchor's component to another component.
     */
    public int getDistanceFrom(ConstraintWidget component) {
        int myComponentX = getOwner().getX();
        int myComponentY = getOwner().getY();
        int myComponentWidth = getOwner().getWidth();
        int myComponentHeight = getOwner().getHeight();
        switch (mAnchor.getType()) {
            case LEFT:
            case RIGHT:
            case CENTER_X: {
                int ya1 = myComponentY;
                int ya2 = myComponentY + myComponentHeight;
                int yb1 = component.getDrawY();
                int yb2 = component.getDrawY() + component.getDrawHeight();
                int d1 = Math.abs(ya1 - yb1);
                int d2 = Math.abs(ya1 - yb2);
                int d3 = Math.abs(ya2 - yb1);
                int d4 = Math.abs(ya2 - yb2);
                return Math.min(Math.min(d1, d2), Math.min(d3, d4));
            }
            case TOP:
            case BOTTOM:
            case BASELINE:
            case CENTER_Y: {
                int xa1 = myComponentX;
                int xa2 = myComponentX + myComponentWidth;
                int xb1 = component.getDrawX();
                int xb2 = component.getDrawX() + component.getDrawWidth();
                int d1 = Math.abs(xa1 - xb1);
                int d2 = Math.abs(xa1 - xb2);
                int d3 = Math.abs(xa2 - xb1);
                int d4 = Math.abs(xa2 - xb2);
                return Math.min(Math.min(d1, d2), Math.min(d3, d4));
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Return the current size of the baseline handle
     *
     * @param transform
     * @return
     */
    public int getBaselineHandleWidth(ViewTransform transform) {
        if (mType != ConstraintAnchor.Type.BASELINE) {
            return 0;
        }
        int w = transform.getSwingDimension(getOwner().getDrawWidth());
        int padding = (int) ((w * 0.4) / 2f);
        if (w - 2 * padding < 16) {
            padding = (w - 16) / 2;
        }
        return w - 2 * padding;
    }

    /**
     * Draw function for the ConstraintHandle
     *
     * @param transform  the view transform
     * @param g          the graphics context
     * @param colorSet   the current colorset
     * @param isSelected if the constraint is selected or not
     */
    public void draw(ViewTransform transform, Graphics2D g,
            ColorSet colorSet, boolean isSelected) {

        ConstraintWidget widget = getOwner();
        WidgetCompanion companion = (WidgetCompanion) widget.getCompanionWidget();
        WidgetDecorator decorator = companion.getWidgetDecorator(colorSet.getStyle());
        Color backgroundColor = decorator.getBackgroundColor();

        if (mType == ConstraintAnchor.Type.BASELINE) {
            int x = transform.getSwingX(getOwner().getDrawX());
            int y = transform.getSwingY(getOwner().getDrawY());
            int w = transform.getSwingDimension(getOwner().getDrawWidth());
            int h = transform.getSwingDimension(getOwner().getDrawHeight());
            int baseline = transform.getSwingDimension(getOwner().getBaselineDistance());
            int padding = (w - getBaselineHandleWidth(transform)) / 2;
            int bh = 7;
            int by = y + baseline;

            if (isSelected) {
                Color pre = g.getColor();
                Stroke preStroke = g.getStroke();
                g.setColor(colorSet.getShadow());
                g.setStroke(colorSet.getShadowStroke());
                g.drawRoundRect(x + padding, by - bh / 2, w - 2 * padding, bh, bh, bh);
                g.setStroke(preStroke);
                g.setColor(pre);
            }

            Color previous = g.getColor();

            g.setColor(new Color(backgroundColor.getRed(), backgroundColor.getGreen(),
                    backgroundColor.getBlue(), previous.getAlpha()));
            g.fillRoundRect(x + padding, by - bh / 2, w - 2 * padding, bh, bh, bh);
            g.setColor(previous);
            g.drawRoundRect(x + padding, by - bh / 2, w - 2 * padding, bh, bh, bh);
            g.drawLine(x, by, x + padding, by);
            g.drawLine(x + w - padding, by, x + w, by);
            if (mAnchor.isConnected()) {
                int margin = 2;
                g.fillRoundRect(x + padding + margin,
                        by - bh / 2 + margin,
                        w - 2 * padding - 2 * margin,
                        bh - 2 * margin, bh, bh);
                g.drawRoundRect(x + padding + margin,
                        by - bh / 2 + margin,
                        w - 2 * padding - 2 * margin,
                        bh - 2 * margin, bh, bh);
            }
        } else {
            int innerMargin = 3;
            int radius = ConnectionDraw.CONNECTION_ANCHOR_SIZE;
            int dimension = radius * 2;
            int cx = transform.getSwingFX(mX) - dimension / 2;
            int cy = transform.getSwingFY(mY) - dimension / 2;
            Ellipse2D.Float outerCircle = new Ellipse2D.Float(cx, cy, dimension, dimension);
            if (isSelected) {
                Color pre = g.getColor();
                Stroke preStroke = g.getStroke();
                g.setColor(sShadowColor);
                g.setStroke(sShadowStroke);
                g.draw(outerCircle);
                g.setStroke(preStroke);
                g.setColor(pre);
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(backgroundColor);
            g2.fill(outerCircle);
            g2.dispose();
            g.draw(outerCircle);
            if (mAnchor.isConnected()) {
                int d = dimension - innerMargin * 2;
                g.fillRoundRect(cx + innerMargin, cy + innerMargin, d, d, d, d);
                g.drawRoundRect(cx + innerMargin, cy + innerMargin, d, d, d, d);
            }
        }
    }

    /*-----------------------------------------------------------------------*/
    // Connection drawings
    /*-----------------------------------------------------------------------*/

    public void drawConnection(ViewTransform transform, Graphics2D g,
            ColorSet colorSet, boolean isSelected) {
        drawConnection(transform, g, colorSet, isSelected, -1, 0);
    }

    /**
     * Implements the drawing of the connection from this anchor to its target
     *
     * @param transform  the view transform
     * @param g          the graphics context
     * @param colorSet   the current colorset
     * @param isSelected if the connection is selected
     */
    public void drawConnection(ViewTransform transform, Graphics2D g,
            ColorSet colorSet, boolean isSelected, int originalCreator, float progress) {
        if (!mAnchor.isConnected()) {
            return;
        }

        ConnectionDrawing drawing = new ConnectionDrawing();

        ConstraintWidget targetWidget = mAnchor.getTarget().getOwner();
        WidgetCompanion targetCompanion = (WidgetCompanion) targetWidget.getCompanionWidget();
        if (targetCompanion == null) {
            return;
        }
        WidgetInteractionTargets interactionTargets =
                targetCompanion.getWidgetInteractionTargets();
        ConstraintHandle targetHandle = interactionTargets.getConstraintHandle(mAnchor.getTarget());

        if (isSelected) {
            drawing.mDrawEndCircle = true;
        }
        if (mAnchor.getOpposite() != null && mAnchor.getOpposite().isConnected()) {
            // Draw centered connections
            if (mAnchor.getOpposite().getTarget() == mAnchor.getTarget()) {
                // Center connection on same anchor
                addPathCenteredConnectionOnSameAnchor(transform, g, isSelected, drawing,
                        targetHandle,
                        targetWidget);
            } else if ((mAnchor.getOpposite().getTarget().getOwner() ==
                    mAnchor.getTarget().getOwner())
                    && targetWidget != getOwner().getParent()) {
                // Center connection on same widget (save our parent)
                addPathCenteredConnectionOnSameWidget(transform, g, isSelected, drawing, colorSet,
                        targetHandle,
                        targetWidget);
            } else {
                // Center connection on different widgets (or our parent)
                addPathCenteredConnection(transform, g, isSelected, drawing, colorSet, targetHandle,
                        targetWidget);
            }
        } else {
            addPathConnection(transform, g, isSelected, true, drawing, colorSet,
                    targetHandle.getDrawX(), targetHandle.getDrawY());
        }

        boolean drawShadow = progress == 1 && isSelected
                && mAnchor.getConnectionCreator() != ConstraintAnchor.AUTO_CONSTRAINT_CREATOR;
        if (true || drawShadow) {
            Color pre = g.getColor();
            Stroke s = g.getStroke();
            if (colorSet.getStyle() == WidgetDecorator.BLUEPRINT_STYLE) {
                g.setPaint(colorSet.getBackgroundPaint());
                g.setStroke(sLineShadowStroke);
            } else {
                g.setColor(sShadowColor);
                g.setStroke(sShadowStroke);
            }
            drawing.draw(g);
            g.setColor(pre);
            g.setStroke(s);
        }
        // If a lock timer is active, draw the path a second time
        if (progress <= 1 && progress > 0.1) {
            Stroke s = g.getStroke();
            int distance = lengthOfPath(drawing.mPath);
            int dashFull = (int) (distance * progress);
            int dashEmpty = (int) (distance * (1 - progress));
            if (dashFull > 0 || dashEmpty > 0) {
                if (originalCreator == ConstraintAnchor.AUTO_CONSTRAINT_CREATOR) {
                    g.setColor(colorSet.getWeakConstraintColor());
                    g.setStroke(ConnectionDraw.sDashedStroke);
                    drawing.draw(g);
                    Stroke progressStroke = new BasicStroke(2, BasicStroke.CAP_BUTT,
                            BasicStroke.JOIN_BEVEL, 0, new float[] { dashFull, dashEmpty }, 0);
                    g.setStroke(progressStroke);
                    g.setColor(colorSet.getSelectedConstraints());
                    if (progress != 1) {
                        drawing.mDrawArrow = false;
                    }
                    drawing.draw(g);
                } else {
                    g.setColor(colorSet.getWeakConstraintColor());
                    g.setStroke(ConnectionDraw.sDashedStroke);
                    drawing.draw(g);
                    Stroke progressStroke = new BasicStroke(2, BasicStroke.CAP_BUTT,
                            BasicStroke.JOIN_BEVEL, 0, new float[] { dashEmpty, dashFull }, 0);
                    g.setColor(colorSet.getSelectedConstraints());
                    g.setStroke(progressStroke);
                    if (progress == 1) {
                        drawing.mDrawArrow = false;
                    }
                    drawing.draw(g);
                }
                g.setStroke(s);
            }
        } else {
            drawing.draw(g);
        }
    }

    /**
     * Draw a connection from an anchor to a given position
     *
     * @param transform  the view transform
     * @param g          the graphics context
     * @param colorSet   the current colorset
     * @param isSelected if the connection is selected
     * @param target     the geometry point the connection should point to
     */
    public void drawConnection(ViewTransform transform, Graphics2D g, ColorSet colorSet,
            boolean isSelected,
            Point target) {

        ConnectionDrawing drawing = new ConnectionDrawing();
        addPathConnection(transform, g, isSelected, false, drawing, colorSet,
                (int) target.getX(), (int) target.getY());

        boolean drawShadow = isSelected
                && mAnchor.getConnectionCreator() != ConstraintAnchor.AUTO_CONSTRAINT_CREATOR;
        if (drawShadow) {
            Color pre = g.getColor();
            Stroke s = g.getStroke();
            g.setColor(sShadowColor);
            g.setStroke(sShadowStroke);
            drawing.draw(g);
            g.setColor(pre);
            g.setStroke(s);
        }
        drawing.draw(g);
    }

    /**
     * Add to a given path to represent a single connection
     *
     * @param transform  the view transform
     * @param g          the graphics context
     * @param isSelected if the connection is selected
     * @param showMargin
     * @param drawing    the drawing we are adding to
     * @param colorSet   the current colorset
     */
    private void addPathConnection(ViewTransform transform, Graphics2D g,
            boolean isSelected,
            boolean showMargin, ConnectionDrawing drawing, ColorSet colorSet, int targetX,
            int targetY) {

        int radius = 4;
        int sradius = transform.getSwingDimension(radius);
        int scurvature = transform.getSwingDimension(2);
        int scurvature2 = transform.getSwingDimension(3);
        int marginLineOffset = transform.getSwingDimension(16);
        boolean isVertical = mAnchor.isVerticalAnchor();
        int x0 = transform.getSwingFX(mX);
        int y0 = transform.getSwingFY(mY);
        int x1 = transform.getSwingFX(targetX);
        int y1 = transform.getSwingFY(targetY);
        drawing.mPath.moveTo(x0, y0);
        int distanceX = Math.abs(targetX - mX);
        int distanceY = Math.abs(targetY - mY);
        int distance = (int) Math.sqrt(distanceX * distanceX + distanceY * distanceY);
        int maxDistance = Math.min(24 + (int) (0.1f * distance), 64);
        maxDistance = distance > maxDistance ? maxDistance : distance;
        int controlDistance = transform.getSwingDimension(maxDistance);

        // If the connection points to our parent, draw the connection in a straight line
        boolean beStraight = mAnchor.isConnected()
                && mAnchor.getTarget().getOwner() == getOwner().getParent();
        if (beStraight) {
            if (isVertical) {
                x1 = x0;
            } else {
                y1 = y0;
            }
        }

        if (isVertical) {
            boolean isBaseline = mAnchor.getType() == ConstraintAnchor.Type.BASELINE;
            boolean isTopConnection = mAnchor.getType() == ConstraintAnchor.Type.TOP;
            isTopConnection |= isBaseline;
            if (isTopConnection) {
                controlDistance = -controlDistance;
            }
            if (mAnchor.getTarget() != null
                    && mAnchor.getType() == mAnchor.getTarget().getType()
                    && !isBaseline
                    && mAnchor.getMargin() == 0) {
                int base = y0 - sradius - ConnectionDraw.ARROW_SIDE;
                if (!isTopConnection) {
                    base = y0 + sradius + ConnectionDraw.ARROW_SIDE;
                }
                if (x0 > x1) {
                    sradius = -sradius;
                }
                addQuarterArc(drawing.mPath, x0, y0, x0 + sradius, base, scurvature, true);
                drawing.mPath.lineTo(x1 - 2 * sradius, base);
                int yt = y1 - ConnectionDraw.ARROW_SIDE;
                if (!isTopConnection) {
                    yt = y1 + ConnectionDraw.ARROW_SIDE;
                }
                addQuarterArc(drawing.mPath, x1 - 2 * sradius, base, x1, yt, scurvature2, false);
                if (isTopConnection) {
                    drawing.setArrow(ConnectionDraw.getBottomArrow(), x1, y1);
                } else {
                    drawing.setArrow(ConnectionDraw.getTopArrow(), x1, y1);
                }
            } else {
                if (isBaseline) {
                    drawing.mDrawEndCircle = false;
                    // In case of baseline connections, we don't want to connect directly from the
                    // center of the widget (where the baseline anchor (mX, mY) is, so we offset a little
                    int offset1 =
                            (int) (transform.getSwingDimension(mAnchor.getOwner().getDrawWidth()) *
                                    0.2f);
                    int offset2 = 0;
                    if (mAnchor.getTarget() != null) {
                        ConstraintWidget widget = mAnchor.getTarget().getOwner();
                        offset2 = (int) (transform.getSwingDimension(widget.getDrawWidth()) * 0.2f);
                        int tl = transform.getSwingX(widget.getDrawX());
                        int tr = transform.getSwingX(widget.getDrawRight());
                        int tt = transform.getSwingY(widget.getDrawY());
                        int tb = transform.getSwingDimension(widget.getBaselineDistance());
                        Shape line = new Line2D.Float(tl, tt + tb, tr, tt + tb);
                        drawing.addShape(line);
                    }
                    if (x0 < x1) {
                        x0 += offset1;
                        x1 -= offset2;
                    } else {
                        x0 -= offset1;
                        x1 += offset2;
                    }
                    drawing.mPath.moveTo(x0, y0);
                }
                int cx1 = x0;
                int cy1 = y0 + controlDistance;
                int cx2 = x1;
                int cy2 = y1 - controlDistance;
                int yt = y1 + ConnectionDraw.ARROW_SIDE;
                if (!isTopConnection) {
                    yt = y1 - ConnectionDraw.ARROW_SIDE;
                }
                drawing.mPath.curveTo(cx1, cy1, cx2, cy2, x1, yt);
                if (!isTopConnection) {
                    drawing.setArrow(ConnectionDraw.getBottomArrow(), x1, y1);
                } else {
                    drawing.setArrow(ConnectionDraw.getTopArrow(), x1, y1);
                }
            }
            if ((colorSet.alwaysShowMargins() || isSelected) && mAnchor.getMargin() > 0 &&
                    showMargin) {
                Color pre = g.getColor();
                g.setColor(colorSet.getMargins());

                // We may want to position the margin draw a little offset to the center,
                // depending on the direction of the other connections
                int marginPosition = x0;
                ConstraintAnchor left = getOwner().getAnchor(ConstraintAnchor.Type.LEFT);
                ConstraintAnchor right = getOwner().getAnchor(ConstraintAnchor.Type.RIGHT);
                boolean centerConnectionAnchor = (left != null
                        && right != null
                        && left.isConnected()
                        && left.getTarget() == right.getTarget());
                boolean drawMarginToTheRight = x0 > x1;
                if (centerConnectionAnchor) {
                    if (left.getTarget().getType() == ConstraintAnchor.Type.LEFT) {
                        drawMarginToTheRight = true;
                    } else {
                        drawMarginToTheRight = false;
                    }
                }
                if (drawMarginToTheRight) {
                    marginPosition = x0 + marginLineOffset;
                } else {
                    marginPosition = x0 - marginLineOffset;
                }


                Stroke pres = g.getStroke();
                g.setStroke(sSimpleStroke);
                ConnectionDraw.drawVerticalMarginIndicator(g, "" + mAnchor.getMargin(),
                        marginPosition, y0, y1);
                g.setStroke(ConnectionDraw.sDashedStroke);
                if (x0 > x1) {
                    g.drawLine(marginPosition + marginLineOffset, y1, x1, y1);
                } else {
                    g.drawLine(marginPosition - marginLineOffset, y1, x1, y1);
                }
                g.setColor(pre);
                g.setStroke(pres);
            }
        } else {
            boolean isLeftConnection = mAnchor.getType() == ConstraintAnchor.Type.LEFT;
            if (isLeftConnection) {
                controlDistance = -controlDistance;
            }
            if (mAnchor.getTarget() != null
                    && mAnchor.getType() == mAnchor.getTarget().getType()
                    & mAnchor.getMargin() == 0) {
                int base = x0 - sradius - ConnectionDraw.ARROW_SIDE;
                if (!isLeftConnection) {
                    base = x0 + sradius + ConnectionDraw.ARROW_SIDE;
                }
                if (y0 > y1) {
                    sradius = -sradius;
                }
                addQuarterArc(drawing.mPath, x0, y0, base, y0 + sradius, scurvature, false);
                drawing.mPath.lineTo(base, y1 - 2 * sradius);
                int xt = x1 - ConnectionDraw.ARROW_SIDE;
                if (!isLeftConnection) {
                    xt = x1 + ConnectionDraw.ARROW_SIDE;
                }
                addQuarterArc(drawing.mPath, base, y1 - 2 * sradius, xt, y1, scurvature2, true);
                if (isLeftConnection) {
                    drawing.setArrow(ConnectionDraw.getRightArrow(), x1, y1);
                } else {
                    drawing.setArrow(ConnectionDraw.getLeftArrow(), x1, y1);
                }
            } else {
                int cx1 = x0 + controlDistance;
                int cy1 = y0;
                int cx2 = x1 - controlDistance;
                int cy2 = y1;
                int xt = x1 + ConnectionDraw.ARROW_SIDE;
                if (!isLeftConnection) {
                    xt = x1 - ConnectionDraw.ARROW_SIDE;
                }
                drawing.mPath.curveTo(cx1, cy1, cx2, cy2, xt, y1);
                if (!isLeftConnection) {
                    drawing.setArrow(ConnectionDraw.getRightArrow(), x1, y1);
                } else {
                    drawing.setArrow(ConnectionDraw.getLeftArrow(), x1, y1);
                }
            }
            if ((colorSet.alwaysShowMargins() || isSelected) && mAnchor.getMargin() > 0 &&
                    showMargin) {
                Color pre = g.getColor();
                g.setColor(colorSet.getMargins());

                // We may want to position the margin draw a little offset to the center,
                // depending on the direction of the other connections
                int marginPosition = y0;
                ConstraintAnchor top = getOwner().getAnchor(ConstraintAnchor.Type.TOP);
                ConstraintAnchor bottom = getOwner().getAnchor(ConstraintAnchor.Type.BOTTOM);
                boolean centerConnectionAnchor = (top != null
                        && bottom != null
                        && top.isConnected()
                        && top.getTarget() == bottom.getTarget());
                boolean drawMarginToTheBottom = y0 > y1;
                if (centerConnectionAnchor) {
                    if (top.getTarget().getType() == ConstraintAnchor.Type.TOP) {
                        drawMarginToTheBottom = true;
                    } else {
                        drawMarginToTheBottom = false;
                    }
                }
                if (drawMarginToTheBottom) {
                    marginPosition = y0 + marginLineOffset;
                } else {
                    marginPosition = y0 - marginLineOffset;
                }

                Stroke pres = g.getStroke();
                g.setStroke(sSimpleStroke);
                ConnectionDraw.drawHorizontalMarginIndicator(g, "" + mAnchor.getMargin(),
                        x0, x1, marginPosition);
                g.setStroke(ConnectionDraw.sDashedStroke);
                if (y0 > y1) {
                    g.drawLine(x1, y0 + marginLineOffset, x1, y1);
                } else {
                    g.drawLine(x1, y0 - marginLineOffset, x1, y1);
                }
                g.setColor(pre);
                g.setStroke(pres);
            }
        }
    }

    /**
     * Add to a given path to represent a centered connection
     *
     * @param transform    the view transform
     * @param g            the graphics context
     * @param isSelected   if the connection is selected
     * @param drawing      the drawing we are adding to
     * @param colorSet     the current colorset
     * @param targetHandle the target handle
     * @param targetWidget the target widget
     */

    private void addPathCenteredConnection(ViewTransform transform, Graphics2D g,
            boolean isSelected,
            ConnectionDrawing drawing, ColorSet colorSet,
            ConstraintHandle targetHandle,
            ConstraintWidget targetWidget) {
        boolean isVertical = mAnchor.isVerticalAnchor();
        int x0 = transform.getSwingFX(mX);
        int y0 = transform.getSwingFY(mY);
        int x1 = transform.getSwingFX(targetHandle.getDrawX());
        int y1 = transform.getSwingFY(targetHandle.getDrawY());
        drawing.mPath.moveTo(x0, y0);
        if (isVertical) {
            boolean isTopConnection = targetHandle.getDrawY() < getDrawY();
            if (isSelected) {
                int start = y0;
                int end = y1;
                if ((colorSet.alwaysShowMargins() || isSelected) && mAnchor.getMargin() > 0) {
                    if (isTopConnection) {
                        end += transform.getSwingDimensionF(mAnchor.getMargin());
                    } else {
                        end -= transform.getSwingDimensionF(mAnchor.getMargin());
                    }
                    Color pre = g.getColor();
                    g.setColor(colorSet.getMargins());
                    Stroke pres = g.getStroke();
                    g.setStroke(sSimpleStroke);
                    ConnectionDraw.drawVerticalMarginIndicator(g, "" + mAnchor.getMargin(),
                            x0, end, y1);
                    g.setStroke(pres);
                    g.setColor(pre);
                    Shape line = new Line2D.Float(x0 - transform.getSwingDimension(4),
                            end, x0 + transform.getSwingDimension(4), end);
                    drawing.addShape(line);
                }
                addVerticalSmallSpring(drawing.mPath, x0,
                        start, end);
                Shape line = new Line2D.Float(x0 - transform.getSwingDimension(4),
                        y1, x0 + transform.getSwingDimension(4), y1);
                drawing.addShape(line);
            } else {
                drawing.mPath.lineTo(x0, y1);
                if (isTopConnection) {
                    drawing.setArrow(ConnectionDraw.getTopArrow(), x0, y1);
                } else {
                    drawing.setArrow(ConnectionDraw.getBottomArrow(), x0, y1);
                }
            }
            if (targetWidget != getOwner().getParent()) {
                Stroke pre = g.getStroke();
                g.setStroke(ConnectionDraw.sDashedStroke);
                g.drawLine(x0, y1, x1, y1);
                g.setStroke(pre);
            }
        } else {
            boolean isLeftConnection = targetHandle.getDrawX() < getDrawX();
            if (isSelected) {
                int start = x0;
                int end = x1;
                if ((colorSet.alwaysShowMargins() || isSelected) && mAnchor.getMargin() > 0) {
                    if (isLeftConnection) {
                        end += transform.getSwingDimensionF(mAnchor.getMargin());
                    } else {
                        end -= transform.getSwingDimensionF(mAnchor.getMargin());
                    }
                    Color pre = g.getColor();
                    g.setColor(colorSet.getMargins());
                    Stroke pres = g.getStroke();
                    g.setStroke(sSimpleStroke);
                    ConnectionDraw.drawHorizontalMarginIndicator(g, "" + mAnchor.getMargin(),
                            end, x1, y0);
                    g.setStroke(pres);
                    g.setColor(pre);
                    g.drawLine(end, y0 - transform.getSwingDimension(4),
                            end, y0 + transform.getSwingDimension(4));
                }
                addHorizontalSmallSpring(drawing.mPath, y0, start, end);
                g.drawLine(x1, y0 - transform.getSwingDimension(4),
                        x1, y0 + transform.getSwingDimension(4));
            } else {
                drawing.mPath.lineTo(x1, y0);
                if (isLeftConnection) {
                    drawing.setArrow(ConnectionDraw.getLeftArrow(), x1, y0);
                } else {
                    drawing.setArrow(ConnectionDraw.getRightArrow(), x1, y0);
                }
            }
            if (targetWidget != getOwner().getParent()) {
                Stroke pre = g.getStroke();
                g.setStroke(ConnectionDraw.sDashedStroke);
                g.drawLine(x1, y0, x1, y1);
                g.setStroke(pre);
            }
        }
    }


    /**
     * Add to a given path to represent a centered connection on the same anchor
     *
     * @param transform    the view transform
     * @param g            the graphics context
     * @param isSelected   if the connection is selected
     * @param drawing      the drawing we are adding to
     * @param targetHandle the target handle
     * @param targetWidget the target widget
     */
    private void addPathCenteredConnectionOnSameAnchor(ViewTransform transform, Graphics2D g,
            boolean isSelected,
            ConnectionDrawing drawing,
            ConstraintHandle targetHandle, ConstraintWidget targetWidget) {

        float l = getOwner().getDrawX();
        float t = getOwner().getDrawY();
        float r = getOwner().getDrawRight();
        float b = getOwner().getDrawBottom();
        float w = getOwner().getDrawWidth();
        float h = getOwner().getDrawHeight();

        int radius = 8;

        float connectionX = l + w / 2f + radius;
        float connectionY = t + h / 2f + radius;
        int connectionRadius = 16;
        int curvature = 2;

        connectionX = Math.max(targetHandle.getDrawX() + ConnectionDraw.ARROW_SIDE + radius,
                connectionX);
        connectionY = Math.max(targetHandle.getDrawY() + ConnectionDraw.ARROW_SIDE + radius,
                connectionY);

        boolean rightConnection = true;
        if (targetHandle.getAnchor().getType() == ConstraintAnchor.Type.LEFT) {
            rightConnection = false;
            connectionX = l + w / 2f - radius;
            connectionX = Math.min(targetHandle.getDrawX() - ConnectionDraw.ARROW_SIDE - radius,
                    connectionX);
        }
        boolean bottomConnection = true;
        if (targetHandle.getAnchor().getType() == ConstraintAnchor.Type.TOP) {
            bottomConnection = false;
            connectionY = t + h / 2f - radius;
            connectionY = Math.min(targetHandle.getDrawY() - ConnectionDraw.ARROW_SIDE - radius,
                    connectionY);
        }

        // TODO: handle cases when w is too small to have the current values for connection

        if (mAnchor.getType() == ConstraintAnchor.Type.LEFT
                || mAnchor.getType() == ConstraintAnchor.Type.RIGHT) {

            float x0 = mX;
            float y0 = mY;
            float x1 = mX - radius;
            float y1 = mY - radius;
            boolean isRightConnection = mAnchor.getType() == ConstraintAnchor.Type.RIGHT;
            boolean isAboveConnection = targetWidget.getDrawBottom() < getOwner().getDrawY();

            drawing.mPath.moveTo(transform.getSwingFX(x0), transform.getSwingFY(y0));

            // First, draw a dashed line if we are selected
            if (isSelected) {
                Stroke preStroke = g.getStroke();
                g.setStroke(ConnectionDraw.sDashedStroke);
                int centerX = transform.getSwingFX(l + w / 2f);
                if (isAboveConnection) {
                    g.drawLine(centerX, transform.getSwingFY(t), centerX,
                            transform.getSwingY(targetWidget.getDrawBottom()));
                } else {
                    g.drawLine(centerX, transform.getSwingFY(b), centerX,
                            transform.getSwingY(targetWidget.getDrawY()));
                }
                g.setStroke(preStroke);
            }
            if (isRightConnection) {
                x1 = mX + radius;
            }
            if (!isAboveConnection) {
                y1 = mY + radius;
            }
            addQuarterArc(drawing.mPath, transform.getSwingFX(x0), transform.getSwingFY(y0),
                    transform.getSwingFX(x1), transform.getSwingFY(y1), curvature,
                    false);
            float x2 = x1;
            float y2 = Math.min(t, y1);
            float x3 = mX;
            float y3 = Math.min(t - radius, y2 - radius);
            if (!isAboveConnection) {
                y2 = Math.max(b, y1);
                y3 = Math.min(b + radius, y2 + radius);
            }
            drawing.mPath.lineTo(transform.getSwingFX(x2), transform.getSwingFY(y2));
            addQuarterArc(drawing.mPath, transform.getSwingFX(x2), transform.getSwingFY(y2),
                    transform.getSwingFX(x3), transform.getSwingFY(y3), curvature,
                    true);
            float x4 = Math.max(x3, connectionX - connectionRadius);
            if (isRightConnection) {
                x4 = Math.min(x3, connectionX + connectionRadius);
            }
            float y4 = y3;
            drawing.mPath.lineTo(transform.getSwingFX(x4), transform.getSwingFY(y4));
            float x5 = connectionX;
            float y5 = y4 - radius;
            if (!isAboveConnection) {
                y5 = y4 + radius;
            }

            addQuarterArc(drawing.mPath, transform.getSwingFX(x4), transform.getSwingFY(y4),
                    transform.getSwingFX(x5), transform.getSwingFY(y5), curvature,
                    false);

            // Now draw the final connection to the anchor

            float xt = targetHandle.getDrawX();
            float yt = targetHandle.getDrawY();
            float y6 = yt + radius;

            if (!isAboveConnection) {
                y6 = yt - radius;
            }
            drawing.mPath.lineTo(transform.getSwingFX(connectionX), transform.getSwingFY(y6));
            if (rightConnection) {
                addQuarterArc(drawing.mPath, transform.getSwingFX(connectionX),
                        transform.getSwingFY(y6),
                        transform.getSwingFX(xt) + ConnectionDraw.ARROW_SIDE,
                        transform.getSwingFY(yt), curvature, true);
                drawing.setArrow(ConnectionDraw.getLeftArrow(),
                        transform.getSwingFX(xt),
                        transform.getSwingFY(yt));
            } else {
                addQuarterArc(drawing.mPath, transform.getSwingFX(connectionX),
                        transform.getSwingFY(y6),
                        transform.getSwingFX(xt) - ConnectionDraw.ARROW_SIDE,
                        transform.getSwingFY(yt), curvature, true);
                drawing.setArrow(ConnectionDraw.getRightArrow(),
                        transform.getSwingFX(xt),
                        transform.getSwingFY(yt));
            }

        } else if (mAnchor.getType() == ConstraintAnchor.Type.TOP
                || mAnchor.getType() == ConstraintAnchor.Type.BOTTOM) {

            float x0 = mX;
            float y0 = mY;
            float x1 = mX - radius;
            float y1 = mY - radius;
            boolean isBottomConnection = mAnchor.getType() == ConstraintAnchor.Type.BOTTOM;
            boolean isLeftConnection =
                    targetWidget.getDrawRight() < getOwner().getDrawX();

            drawing.mPath.moveTo(transform.getSwingFX(x0), transform.getSwingFY(y0));

            // First, draw a dashed line if we are selected
            if (isSelected) {
                Stroke preStroke = g.getStroke();
                g.setStroke(ConnectionDraw.sDashedStroke);
                int centerY = transform.getSwingFY(t + h / 2f);
                if (isLeftConnection) {
                    g.drawLine(transform.getSwingFX(targetWidget.getDrawRight()),
                            centerY, transform.getSwingFX(l), centerY);
                } else {
                    g.drawLine(transform.getSwingFX(r), centerY,
                            transform.getSwingFX(targetWidget.getDrawX()), centerY);
                }
                g.setStroke(preStroke);
            }
            if (isBottomConnection) {
                y1 = mY + radius;
            }
            if (!isLeftConnection) {
                x1 = mX + radius;
            }
            addQuarterArc(drawing.mPath, transform.getSwingFX(x0),
                    transform.getSwingFY(y0),
                    transform.getSwingFX(x1), transform.getSwingFY(y1), curvature,
                    true);

            float y2 = y1;
            float x2 = Math.min(l, x1);
            float y3 = mY;
            float x3 = Math.min(l - radius, x2 - radius);
            if (!isLeftConnection) {
                x2 = Math.max(r, x1);
                x3 = Math.min(r + radius, x2 + radius);
            }
            drawing.mPath.lineTo(transform.getSwingFX(x2), transform.getSwingFY(y2));

            addQuarterArc(drawing.mPath, transform.getSwingFX(x2),
                    transform.getSwingFY(y2),
                    transform.getSwingFX(x3), transform.getSwingFY(y3), curvature,
                    false);

            float y4 = Math.max(y3, connectionY - connectionRadius);
            if (isBottomConnection) {
                y4 = Math.min(y3, connectionY + connectionRadius);
            }
            float x4 = x3;
            drawing.mPath.lineTo(transform.getSwingFX(x4), transform.getSwingFY(y4));
            float y5 = connectionY;
            float x5 = x4 - radius;
            if (!isLeftConnection) {
                x5 = x4 + radius;
            }

            addQuarterArc(drawing.mPath, transform.getSwingFX(x4),
                    transform.getSwingFY(y4),
                    transform.getSwingFX(x5), transform.getSwingFY(y5), curvature,
                    true);

            // Now draw the final connection to the anchor

            float xt = targetHandle.getDrawX();
            float yt = targetHandle.getDrawY();
            float x6 = xt + radius;

            if (!isLeftConnection) {
                x6 = xt - radius;
            }
            drawing.mPath.lineTo(transform.getSwingFX(x6), transform.getSwingFY(connectionY));
            if (bottomConnection) {
                addQuarterArc(drawing.mPath,
                        transform.getSwingFX(x6),
                        transform.getSwingFY(connectionY),
                        transform.getSwingFX(xt),
                        transform.getSwingFY(yt) + ConnectionDraw.ARROW_SIDE, curvature,
                        false);
                drawing.setArrow(ConnectionDraw.getTopArrow(),
                        transform.getSwingFX(xt),
                        transform.getSwingFY(yt));
            } else {
                addQuarterArc(drawing.mPath,
                        transform.getSwingFX(x6),
                        transform.getSwingFY(connectionY),
                        transform.getSwingFX(xt),
                        transform.getSwingFY(yt) - ConnectionDraw.ARROW_SIDE, curvature,
                        false);
                drawing.setArrow(ConnectionDraw.getBottomArrow(),
                        transform.getSwingFX(xt),
                        transform.getSwingFY(yt));
            }
        }

    }

    /**
     * Add to a given path to represent a centered connection on the same widget
     *
     * @param transform    the view transform
     * @param g            the graphics context
     * @param isSelected   if the connection is selected
     * @param drawing      the drawing we are adding to
     * @param colorSet     the current colorset
     * @param targetHandle the target handle
     * @param targetWidget the target widget
     */
    private void addPathCenteredConnectionOnSameWidget(ViewTransform transform, Graphics2D g,
            boolean isSelected,
            ConnectionDrawing drawing, ColorSet colorSet,
            ConstraintHandle targetHandle,
            ConstraintWidget targetWidget) {

        int radius = 8;

        float x0 = mX;
        float y0 = mY;
        float xt = targetHandle.getDrawX();
        float yt = targetHandle.getDrawY();

        boolean isTopConnection = mAnchor.getType() == ConstraintAnchor.Type.TOP;
        boolean isLeftConnection = targetHandle.getDrawX() < getDrawX();

        boolean isVerticalConnection = mAnchor.isVerticalAnchor();

        if (isVerticalConnection) {
            float base = Math.min(transform.getSwingFY(y0),
                    transform.getSwingFY(yt) - ConnectionDraw.ARROW_SIDE);
            if (!isTopConnection) {
                base = Math.max(transform.getSwingFY(y0),
                        transform.getSwingFY(yt) + ConnectionDraw.ARROW_SIDE);
            }
            drawing.mPath.moveTo(transform.getSwingFX(x0), transform.getSwingFY(y0));

            if (isSelected && Math.abs(transform.getSwingFY(y0) - base) > 0) {
                int start = transform.getSwingFY(y0);
                int end = transform.getSwingFY(yt);
                if ((colorSet.alwaysShowMargins() || isSelected) && mAnchor.getMargin() > 0) {
                    if (isTopConnection) {
                        end += transform.getSwingDimensionF(mAnchor.getMargin());
                    } else {
                        end -= transform.getSwingDimensionF(mAnchor.getMargin());
                    }
                    Color pre = g.getColor();
                    g.setColor(colorSet.getMargins());
                    Stroke pres = g.getStroke();
                    g.setStroke(sSimpleStroke);
                    ConnectionDraw.drawVerticalMarginIndicator(g, "" + mAnchor.getMargin(),
                            transform.getSwingFX(x0) + ConnectionDraw.ARROW_SIDE, end,
                            transform.getSwingFY(yt));
                    g.setStroke(pres);
                    g.setColor(pre);
                    g.drawLine(transform.getSwingFX(x0 - 4),
                            end, transform.getSwingFX(x0 + 4), end);
                }
                addVerticalSmallSpring(drawing.mPath, transform.getSwingFX(x0),
                        start, end);
            }
            drawing.mPath.lineTo(transform.getSwingFX(x0), base);

            float x1 = x0 - radius;
            float sy1 = base - transform.getSwingDimension(radius);

            if (!isTopConnection) {
                sy1 = base + transform.getSwingDimension(radius);
            }
            if (!isLeftConnection) {
                x1 = x0 + radius;
            }

            addQuarterArc(drawing.mPath,
                    transform.getSwingFX(x0),
                    base,
                    transform.getSwingFX(x1),
                    sy1, 1,
                    true);

            float x2 = xt + 2 * radius;

            if (!isLeftConnection) {
                x2 = xt - 2 * radius;
            }

            drawing.mPath.lineTo(transform.getSwingFX(x2), sy1);

            float syt = transform.getSwingFY(yt) - ConnectionDraw.ARROW_SIDE;
            if (!isTopConnection) {
                syt = transform.getSwingFY(yt) + ConnectionDraw.ARROW_SIDE;
            }

            addQuarterArc(drawing.mPath,
                    transform.getSwingFX(x2),
                    sy1,
                    transform.getSwingFX(xt),
                    syt, radius,
                    false);

            if (isSelected) {
                Stroke pre = g.getStroke();
                g.setStroke(ConnectionDraw.sDashedStroke);
                if (isLeftConnection) {
                    g.drawLine(transform.getSwingFX(targetWidget.getDrawRight()),
                            transform.getSwingFY(yt),
                            transform.getSwingFX(x0 - 4), transform.getSwingFY(yt));
                } else {
                    g.drawLine(transform.getSwingFX(targetWidget.getDrawX()),
                            transform.getSwingFY(yt),
                            transform.getSwingFX(x0 - 4), transform.getSwingFY(yt));
                }
                g.setStroke(pre);
                Shape line =
                        new Line2D.Float(transform.getSwingFX(x0 - 4), transform.getSwingFX(yt),
                                transform.getSwingFX(x0 + 4), transform.getSwingFX(yt));
                drawing.addShape(line);
            }

            if (isTopConnection) {
                drawing.setArrow(ConnectionDraw.getBottomArrow(),
                        transform.getSwingFX(xt),
                        transform.getSwingFY(yt));
            } else {
                drawing.setArrow(ConnectionDraw.getTopArrow(),
                        transform.getSwingFX(xt),
                        transform.getSwingFY(yt));
            }

        } else {

            // Horizontal connection

            isTopConnection = targetHandle.getDrawY() < getDrawY();
            isLeftConnection = mAnchor.getType() == ConstraintAnchor.Type.LEFT;

            float base = Math.min(transform.getSwingFX(x0),
                    transform.getSwingFX(xt) - ConnectionDraw.ARROW_SIDE);
            if (!isLeftConnection) {
                base = Math.max(transform.getSwingFX(x0),
                        transform.getSwingFX(xt) + ConnectionDraw.ARROW_SIDE);
            }
            drawing.mPath.moveTo(transform.getSwingFX(x0), transform.getSwingFY(y0));

            if (isSelected && Math.abs(transform.getSwingFX(x0) - base) > 0) {
                int start = transform.getSwingFX(x0);
                int end = transform.getSwingFX(xt);
                if ((colorSet.alwaysShowMargins() || isSelected) && mAnchor.getMargin() > 0) {
                    if (isLeftConnection) {
                        end += transform.getSwingDimensionF(mAnchor.getMargin());
                    } else {
                        end -= transform.getSwingDimensionF(mAnchor.getMargin());
                    }
                    Color pre = g.getColor();
                    g.setColor(colorSet.getMargins());
                    Stroke pres = g.getStroke();
                    g.setStroke(sSimpleStroke);
                    ConnectionDraw.drawHorizontalMarginIndicator(g, "" + mAnchor.getMargin(),
                            end, transform.getSwingFX(xt),
                            transform.getSwingFY(y0) + ConnectionDraw.ARROW_SIDE);
                    g.setStroke(pres);
                    g.setColor(pre);
                    Shape line = new Line2D.Float(end, transform.getSwingFX(y0 - 4),
                            end, transform.getSwingFX(y0 + 4));
                    drawing.addShape(line);
                }
                addHorizontalSmallSpring(drawing.mPath, transform.getSwingFY(y0),
                        start, end);
            }
            drawing.mPath.lineTo(base, transform.getSwingFY(y0));

            float y1 = y0 - radius;
            float sx1 = base - transform.getSwingDimension(radius);

            if (!isLeftConnection) {
                sx1 = base + transform.getSwingDimension(radius);
            }
            if (!isTopConnection) {
                y1 = y0 + radius;
            }

            addQuarterArc(drawing.mPath,
                    base,
                    transform.getSwingFY(y0),
                    sx1,
                    transform.getSwingFY(y1),
                    1,
                    false);

            float y2 = yt + 2 * radius;

            if (!isTopConnection) {
                y2 = yt - 2 * radius;
            }

            drawing.mPath.lineTo(sx1, transform.getSwingFY(y2));

            float sxt = transform.getSwingFX(xt) - ConnectionDraw.ARROW_SIDE;
            if (!isLeftConnection) {
                sxt = transform.getSwingFX(xt) + ConnectionDraw.ARROW_SIDE;
            }

            addQuarterArc(drawing.mPath,
                    sx1,
                    transform.getSwingFY(y2),
                    sxt,
                    transform.getSwingFY(yt),
                    radius,
                    true);

            if (isSelected) {
                Stroke pre = g.getStroke();
                g.setStroke(ConnectionDraw.sDashedStroke);
                if (isTopConnection) {
                    g.drawLine(
                            transform.getSwingFX(xt),
                            transform.getSwingFY(targetWidget.getDrawBottom()),
                            transform.getSwingFX(xt),
                            transform.getSwingFY(y0 - 4));
                } else {
                    g.drawLine(
                            transform.getSwingFX(xt),
                            transform.getSwingFY(targetWidget.getDrawY()),
                            transform.getSwingFX(xt),
                            transform.getSwingFY(y0 - 4));
                }
                g.setStroke(pre);
                Shape line =
                        new Line2D.Float(transform.getSwingFX(xt), transform.getSwingFY(y0 - 4),
                                transform.getSwingFX(xt), transform.getSwingFY(y0 + 4));
                drawing.addShape(line);
            }

            if (isLeftConnection) {
                drawing.setArrow(ConnectionDraw.getRightArrow(),
                        transform.getSwingFX(xt),
                        transform.getSwingFY(yt));
            } else {
                drawing.setArrow(ConnectionDraw.getLeftArrow(),
                        transform.getSwingFX(xt),
                        transform.getSwingFY(yt));
            }

        }
    }

    /*-----------------------------------------------------------------------*/
    // Utilities draw functions for path
    /*-----------------------------------------------------------------------*/

    /**
     * Add an vertical spring between (x0, y1) and (x0, y1) to the given path object
     *
     * @param path the path object we'll add the spring to
     * @param x0   the x coordinate of the spring
     * @param y1   the y start coordinate
     * @param y2   the y end coordiante
     */
    private void addVerticalSmallSpring(Path2D.Float path, int x0, int y1, int y2) {
        int springHeight = 2;
        int springWidth = 2;
        int distance = Math.abs(y2 - y1);
        int numSprings = (distance / (springHeight));
        int leftOver = (distance - (numSprings * springHeight)) / 2;
        path.lineTo(x0, y1);
        path.lineTo(x0, y1 - leftOver);
        int count = 0;
        if (y1 > y2) {
            for (int y = y1 - leftOver; y > y2 + leftOver; y -= springHeight) {
                int x = (count % 2 == 0) ? x0 - springWidth : x0 + springWidth;
                path.lineTo(x, y);
                count++;
            }
        } else {
            for (int y = y1 + leftOver; y < y2 - leftOver; y += springHeight) {
                int x = (count % 2 == 0) ? x0 - springWidth : x0 + springWidth;
                path.lineTo(x, y);
                count++;
            }
        }
        path.lineTo(x0, y2 + leftOver);
        path.lineTo(x0, y2);
    }

    /**
     * Add an horizontal spring between (x1, y0) and (x2, y0) to the given path object
     *
     * @param path the path object we'll add the spring to
     * @param y0   the y coordinate of the spring
     * @param x1   the x start coordinate
     * @param x2   the x end coordiante
     */
    private void addHorizontalSmallSpring(Path2D.Float path, int y0, int x1, int x2) {
        int springHeight = 2;
        int springWidth = 2;
        int distance = Math.abs(x2 - x1);
        int numSprings = (distance / (springHeight));
        int leftOver = (distance - (numSprings * springHeight)) / 2;
        path.lineTo(x1, y0);
        path.lineTo(x1 - leftOver, y0 - leftOver);
        int count = 0;
        if (x1 > x2) {
            for (int x = x1 - leftOver; x > x2 + leftOver; x -= springHeight) {
                int y = (count % 2 == 0) ? y0 - springWidth : y0 + springWidth;
                path.lineTo(x, y);
                count++;
            }
        } else {
            for (int x = x1 + leftOver; x < x2 - leftOver; x += springHeight) {
                int y = (count % 2 == 0) ? y0 - springWidth : y0 + springWidth;
                path.lineTo(x, y);
                count++;
            }
        }
        path.lineTo(x2 + leftOver, y0);
        path.lineTo(x2, y0);
    }

    /**
     * Add a quarter circular path to a given path object, starting from (x1, y1) to (x2, y2).
     *
     * @param path          the path object we'll add the arc to
     * @param x1            x start coordinate
     * @param y1            y start coordinate
     * @param x2            x end coordinate
     * @param y2            y end coordinate
     * @param curvature     the curvature of the path
     * @param verticalStart true to start the arc vertically, false otherwise
     */
    private void addQuarterArc(Path2D.Float path, float x1, float y1,
            float x2, float y2,
            float curvature, boolean verticalStart) {
        boolean down = y1 < y2;
        boolean left = x1 > x2;

        float cx1 = 0;
        float cy1 = 0;
        float cx2 = 0;
        float cy2 = 0;

        if (verticalStart) {
            cx1 = x1;
            cy2 = y2;
            if (left) {
                cx2 = x1 - curvature;
            } else {
                cx2 = x1 + curvature;
            }
            if (down) {
                cy1 = y2 - curvature;
            } else {
                cy1 = y2 + curvature;
            }
        } else {
            cx2 = x2;
            cy1 = y1;
            if (left) {
                cx1 = x2 + curvature;
            } else {
                cx1 = x2 - curvature;
            }
            if (down) {
                cy2 = y1 + curvature;
            } else {
                cy2 = y1 - curvature;
            }
        }
        path.curveTo(cx1, cy1, cx2, cy2, x2, y2);
    }

    /**
     * Return the length of the given path
     *
     * @param path
     * @return the length of the path
     */
    private int lengthOfPath(Path2D.Float path) {
        FlatteningPathIterator f = new FlatteningPathIterator(
                path.getPathIterator(null), 1);
        double sum = 0;
        float x1, x2, y1, y2;
        float[] coords = new float[6];
        f.currentSegment(coords);
        x1 = coords[0];
        y1 = coords[1];
        f.next();
        do {
            f.currentSegment(coords);
            f.next();
            x2 = coords[0];
            y2 = coords[1];
            sum += Math.hypot(x2 - x1, y2 - y1);
            x1 = x2;
            y1 = y2;
        } while (!f.isDone());
        return (int) sum;
    }

}
