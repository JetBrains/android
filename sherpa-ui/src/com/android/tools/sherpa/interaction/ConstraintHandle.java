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

import android.support.constraint.solver.widgets.ConstraintAnchor;
import android.support.constraint.solver.widgets.ConstraintWidget;
import android.support.constraint.solver.widgets.Guideline;
import com.android.tools.sherpa.drawing.ColorSet;
import com.android.tools.sherpa.drawing.ConnectionDraw;
import com.android.tools.sherpa.drawing.ViewTransform;
import com.android.tools.sherpa.drawing.decorator.WidgetDecorator;
import com.android.tools.sherpa.structure.WidgetCompanion;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;

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

    static final Color sShadowColor = new Color(0, 0, 0, 50);
    static final Stroke sShadowStroke = new BasicStroke(3);

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
     * Update the position of the anchor depending on the owner's position
     *
     */
    void updatePosition() {
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
        if (mAnchor == null) {
            return;
        }
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
            case NONE:
                break;
        }
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

    @Override
    public String toString() {
        return mAnchor.toString();
    }

}
