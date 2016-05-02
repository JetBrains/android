package com.android.tools.sherpa.animation;

import com.android.tools.sherpa.drawing.ColorSet;
import com.android.tools.sherpa.drawing.ViewTransform;
import com.android.tools.sherpa.interaction.ConstraintHandle;
import com.android.tools.sherpa.interaction.WidgetInteractionTargets;
import com.google.tnt.solver.widgets.ConstraintAnchor;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Animate anchor connections
 */
public class AnimatedConnection extends Animation {
    final ConstraintAnchor mAnchor;
    private final ColorSet mColorSet;
    protected Color mColor;
    private int mOriginalCreator;

    public AnimatedConnection(ColorSet colorSet, ConstraintAnchor anchor) {
        super();
        mAnchor = anchor;
        mColorSet = colorSet;
        mColor = colorSet.getSelectedConstraints();
        if (mAnchor.getConnectionCreator() == ConstraintAnchor.SCOUT_CREATOR) {
            mColor = colorSet.getCreatedConstraints();
        }
        mOriginalCreator = mAnchor.getConnectionCreator();
    }

    @Override
    public void onPaint(ViewTransform transform, Graphics2D g) {
        double progress = getProgress();
        int alpha = getPulsatingAlpha(progress);
        Color highlight = new Color(mColor.getRed(), mColor.getGreen(), mColor.getBlue(), alpha);
        g.setColor(highlight);
        ConstraintHandle sourceHandle = WidgetInteractionTargets.constraintHandle(mAnchor);
        ConstraintHandle targetHandle = WidgetInteractionTargets.constraintHandle(mAnchor.getTarget());
        if (sourceHandle != null && targetHandle != null && progress >= 0.05) {
            float p = (float) (progress * 2);
            if (p > 1) {
                p = 1;
            }
            sourceHandle.drawConnection(transform, g, mColorSet, true, mOriginalCreator, p);
        }
    }
}
