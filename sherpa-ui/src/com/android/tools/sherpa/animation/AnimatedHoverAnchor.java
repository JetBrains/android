package com.android.tools.sherpa.animation;

import com.android.tools.sherpa.drawing.ColorSet;
import com.android.tools.sherpa.drawing.SceneDraw;
import com.android.tools.sherpa.drawing.ViewTransform;
import com.android.tools.sherpa.drawing.WidgetDraw;
import com.android.tools.sherpa.interaction.ConstraintHandle;
import android.support.constraint.solver.widgets.ConstraintAnchor;
import android.support.constraint.solver.widgets.ConstraintWidget;

import java.awt.*;
import java.awt.geom.Ellipse2D;

/**
 * Implements a highlight hover animation on a circular anchor
 */
public class AnimatedHoverAnchor extends Animation {
    private static final BasicStroke sStroke = new BasicStroke(4);
    private static final BasicStroke sThinStroke = new BasicStroke(3);

    private final ColorSet mColorSet;
    private ConstraintHandle mAnchor;
    private boolean mIsBaseline = false;
    private ConstraintAnchor mTargetAnchor;
    protected Color mColor = Color.white;
    private ConstraintAnchor mOriginalTarget;
    private Color mFrame;
    private boolean mShowTooltip = true;
    private long mStartTime;
    private final Ellipse2D.Float mCircle = new Ellipse2D.Float();
    private final Ellipse2D.Float mInnerCircle = new Ellipse2D.Float();

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
            text[0] = "Click To Delete";
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
        boolean isNewConnection = mAnchor.getAnchor().getTarget() != null
                && mOriginalTarget != mAnchor.getAnchor().getTarget();

        Composite savedComposite = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,  alpha / 255f));

        ConstraintWidget widget = mAnchor.getOwner();
        int l = transform.getSwingX(widget.getDrawX());
        int t = transform.getSwingY(widget.getDrawY());
        int w = transform.getSwingDimension(widget.getDrawWidth());
        if (mIsBaseline) {
            int extra = radius - 3;
            g.setColor(mColor);
            g.setStroke(sThinStroke);
            int handleWidth = mAnchor.getBaselineHandleWidth(transform);
            int padding = (w - handleWidth) / 2;
            g.drawRoundRect(l + padding,
                    t + transform.getSwingDimension(widget.getBaselineDistance()) - extra / 2,
                    handleWidth + 1, extra, radius, radius);
        } else {
            if (isNewConnection) {
                // use smaller circle
                radius = anchorSize + 3;
            }
            mCircle.setFrame(x - radius, y - radius, radius * 2, radius * 2);
            g.setColor(mFrame);
            g.setStroke(isNewConnection ? sThinStroke : sStroke);
            g.draw(mCircle);
            if (isNewConnection) {
                g.setColor(mColorSet.getBackground());
                g.fill(mCircle);
                g.setColor(mColorSet.getAnchorConnectionCircle());
                radius -= 4;
                mInnerCircle.setFrame(x - radius, y - radius, radius * 2, radius * 2);
                g.fill(mInnerCircle);
                g.draw(mInnerCircle);
            } else {
                mCircle.setFrame(x - radius, y - radius, radius * 2, radius * 2);
                g.setColor(mColor);
            }
            g.draw(mCircle);
        }

        g.setComposite(savedComposite);

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
