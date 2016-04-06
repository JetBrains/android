package com.android.tools.sherpa.animation;

import com.android.tools.sherpa.drawing.SceneDraw;
import com.android.tools.sherpa.drawing.ViewTransform;
import com.android.tools.sherpa.interaction.ConstraintHandle;
import com.google.tnt.solver.widgets.ConstraintAnchor;
import com.google.tnt.solver.widgets.ConstraintWidget;

import java.awt.BasicStroke;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;

/**
 * Implements a highlight hover animation on a circular anchor
 */
public class AnimatedHoverAnchor extends Animation {

    private ConstraintHandle mAnchor;
    private boolean mIsBaseline = false;
    private boolean mConnected = false;
    protected Color mColor = Color.white;
    protected Color mBackgroundColor = new Color(24, 55, 112);
    private Color mConnectionColor = new Color(10, 130, 10);
    private ConstraintAnchor mOriginalTarget;
    private static Font sFont = new Font("Helvetica", Font.PLAIN, 12);

    /**
     * Constructor, create a new AnimatedCircle at the given anchor's position
     *
     * @param anchor ConstraintAnchor we animate on
     */
    public AnimatedHoverAnchor(ConstraintHandle anchor) {
        mAnchor = anchor;
        mOriginalTarget = mAnchor.getAnchor().getTarget();
        if (mAnchor.getAnchor().isConnected()) {
            mColor = new Color(180, 0, 0);
            mConnected = true;
        }
        if (mAnchor.getAnchor().getType() == ConstraintAnchor.Type.BASELINE) {
            mIsBaseline = true;
        }

        setDuration(1200);
        setLoop(true);
    }

    private String getText() {
        String text;
        boolean isNewConnection = mAnchor.getAnchor().getTarget() != null
                && mOriginalTarget != mAnchor.getAnchor().getTarget();
        if (isNewConnection || !mAnchor.getAnchor().isConnected()) {
            text = "Create ";
        } else {
            text = "Delete ";
        }
        switch (mAnchor.getAnchor().getType()) {
            case LEFT: {
                text += "Left";
            } break;
            case RIGHT: {
                text += "Right";
            } break;
            case TOP: {
                text += "Top";
            } break;
            case BOTTOM: {
                text += "Bottom";
            } break;
            case BASELINE: {
                text += "Baseline";
            } break;
        }
        text += " Constraint";
        return text;
    }

    /**
     * Paint method for the animation. We simply draw an opaque circle at (x, y),
     * applying a transparency as the animation progresses.
     *
     * @param transform view transform
     * @param g         Graphics context
     */
    @Override
    public void onPaint(ViewTransform transform, Graphics2D g) {
        int x = transform.getSwingX(mAnchor.getDrawX());
        int y = transform.getSwingY(mAnchor.getDrawY());
        double progress = getProgress();
        int alpha = getPulsatingAlpha(progress);
        int anchorSize = (int) SceneDraw.getAnchorSize(transform.getScale());
        int radius = anchorSize + 4;
        int strokeWidth = 4;
        boolean isNewConnection = mAnchor.getAnchor().getTarget() != null
                && mOriginalTarget != mAnchor.getAnchor().getTarget();
        Color highlight =
                new Color(mColor.getRed(), mColor.getGreen(), mColor.getBlue(), alpha);
        ConstraintWidget widget = mAnchor.getOwner();
        int l = transform.getSwingX(widget.getDrawX());
        int t = transform.getSwingY(widget.getDrawY());
        int w = transform.getSwingDimension(widget.getDrawWidth());
        if (mIsBaseline) {
            int extra = radius;
            g.setColor(highlight);
            g.setStroke(new BasicStroke(strokeWidth));
            g.drawRoundRect(l,
                    t + transform.getSwingDimension(widget.getBaselineDistance()) - extra / 2,
                    w + 1, extra, radius, radius);
        } else {
            if (isNewConnection) {
                // use smaller circle
                radius = anchorSize + 3;
                strokeWidth = 3;
            }
            Ellipse2D.Float circle = new Ellipse2D.Float(x - radius, y - radius,
                    radius * 2, radius * 2);
            if (isNewConnection) {
                g.setColor(mBackgroundColor);
                g.fill(circle);
                g.setColor(mConnectionColor);
                radius -= 4;
                Ellipse2D.Float innerCircle = new Ellipse2D.Float(x - radius, y - radius,
                        radius * 2, radius * 2);
                g.fill(innerCircle);
                g.draw(innerCircle);
            } else {
                circle = new Ellipse2D.Float(x - radius, y - radius,
                        radius * 2, radius * 2);
                g.setColor(highlight);
            }
            g.setStroke(new BasicStroke(strokeWidth));
            g.draw(circle);
        }
        g.setFont(sFont);
        String text = getText();
        FontMetrics fm = g.getFontMetrics(sFont);
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getMaxAscent() + fm.getMaxDescent();
        int tx = l + w / 2 - textWidth / 2;
        int ty = t - 8 - textHeight;
        g.drawString(text, tx, ty);
    }
}
