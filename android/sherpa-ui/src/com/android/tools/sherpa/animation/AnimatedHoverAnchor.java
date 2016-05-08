package com.android.tools.sherpa.animation;

import com.android.tools.sherpa.drawing.ColorSet;
import com.android.tools.sherpa.drawing.SceneDraw;
import com.android.tools.sherpa.drawing.ViewTransform;
import com.android.tools.sherpa.drawing.WidgetDraw;
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

    private final ColorSet mColorSet;
    private ConstraintHandle mAnchor;
    private boolean mIsBaseline = false;
    private ConstraintAnchor mTargetAnchor;
    protected Color mColor = Color.white;
    private ConstraintAnchor mOriginalTarget;
    private Color mFrame;
    private boolean mShowTooltip = true;
    private long mStartTime;

    /**
     * Constructor, create a new AnimatedCircle at the given anchor's position
     *
     * @param colorSet
     * @param anchor   ConstraintAnchor we animate on
     */
    public AnimatedHoverAnchor(ColorSet colorSet, ConstraintHandle anchor) {
        mAnchor = anchor;
        mColorSet = colorSet;
        mOriginalTarget = mAnchor.getAnchor().getTarget();
        mFrame = mColorSet.getAnchorCircle();
        if (mAnchor.getAnchor().isConnected()) {
            mColor = mColorSet.getAnchorDisconnectionCircle();
            mFrame = mColor;
            mTargetAnchor = mAnchor.getAnchor().getTarget();
        } else {
            mColor = mColorSet.getAnchorCreationCircle();
        }

        if (mAnchor.getAnchor().getType() == ConstraintAnchor.Type.BASELINE) {
            mIsBaseline = true;
        }

        setDuration(1200);
        setLoop(true);
        mStartTime = System.currentTimeMillis();
    }

    private String[] getText() {
        String[] text = new String[2];
        boolean isNewConnection = mAnchor.getAnchor().getTarget() != null
                && mOriginalTarget != mAnchor.getAnchor().getTarget();
        if (!mAnchor.getAnchor().isConnected()) {
            text[0] = "Drag To Create";
        } else if (isNewConnection){
            text[0] = "Release to Create";
        } else {
            text[0] = "Delete";
            if (mAnchor.getAnchor().getConnectionCreator() == ConstraintAnchor.AUTO_CONSTRAINT_CREATOR) {
                text[0] += " Unlocked";
            }
        }
        switch (mAnchor.getAnchor().getType()) {
            case LEFT: {
                text[1] = "Left Constraint";
            }
            break;
            case RIGHT: {
                text[1] = "Right Constraint";
            }
            break;
            case TOP: {
                text[1] = "Top Constraint";
            }
            break;
            case BOTTOM: {
                text[1] = "Bottom Constraint";
            }
            break;
            case BASELINE: {
                text[1] = "Baseline Constraint";
            }
            break;
        }
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
        int alpha = 255 - getPulsatingAlpha(progress);
        int anchorSize = (int) SceneDraw.getAnchorSize(transform.getScale());
        int radius = anchorSize + 4;
        int strokeWidth = 4;
        boolean isNewConnection = mAnchor.getAnchor().getTarget() != null
                && mOriginalTarget != mAnchor.getAnchor().getTarget();
        Color frame =
                new Color(mFrame.getRed(), mFrame.getGreen(), mFrame.getBlue(), alpha);
        Color highlight =
                new Color(mColor.getRed(), mColor.getGreen(), mColor.getBlue(), alpha);
        ConstraintWidget widget = mAnchor.getOwner();
        int l = transform.getSwingX(widget.getDrawX());
        int t = transform.getSwingY(widget.getDrawY());
        int w = transform.getSwingDimension(widget.getDrawWidth());
        if (mIsBaseline) {
            int extra = radius - 3;
            g.setColor(highlight);
            g.setStroke(new BasicStroke(strokeWidth - 1));
            int handleWidth = mAnchor.getBaselineHandleWidth(transform);
            int padding = (w - handleWidth) / 2;
            g.drawRoundRect(l + padding,
                    t + transform.getSwingDimension(widget.getBaselineDistance()) - extra / 2,
                    handleWidth + 1, extra, radius, radius);
        } else {
            if (isNewConnection) {
                // use smaller circle
                radius = anchorSize + 3;
                strokeWidth = 3;
            }
            Ellipse2D.Float circle = new Ellipse2D.Float(x - radius, y - radius,
                    radius * 2, radius * 2);
            g.setColor(frame);
            g.setStroke(new BasicStroke(strokeWidth));
            g.draw(circle);
            if (isNewConnection) {
                g.setColor(mColorSet.getBackground());
                g.fill(circle);
                g.setColor(mColorSet.getAnchorConnectionCircle());
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
            g.setStroke(new BasicStroke(strokeWidth - 1));
            g.draw(circle);
        }
        if (!mColorSet.useTooltips()) {
            return;
        }
        boolean showTooltip = mShowTooltip;
        boolean newConnection = mAnchor.getAnchor().getTarget() != mTargetAnchor;
        showTooltip |= newConnection;
        if (showTooltip && (System.currentTimeMillis() - mStartTime > WidgetDraw.TOOLTIP_DELAY)) {
            WidgetDraw.drawTooltip(g, mColorSet, getText(), x, y, true);
        }
    }

    public void setShowTooltip(boolean showTooltip) {
        mShowTooltip = showTooltip;
    }
}
