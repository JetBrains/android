package com.android.tools.sherpa.animation;

import com.android.tools.sherpa.drawing.ConnectionDraw;
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
    protected Color mColor = Color.white;

    public AnimatedConnection(ConstraintAnchor anchor) {
        super();
        mAnchor = anchor;
    }

    @Override
    public void onPaint(ViewTransform transform, Graphics2D g) {
        double progress = getProgress();
        int alpha = getPulsatingAlpha(progress);
        Color highlight = new Color(mColor.getRed(), mColor.getGreen(), mColor.getBlue(), alpha);
        g.setColor(highlight);
        ConstraintHandle sourceHandle = WidgetInteractionTargets.constraintHandle(mAnchor);
        ConstraintHandle targetHandle = WidgetInteractionTargets.constraintHandle(mAnchor.getTarget());
        ConnectionDraw
                .drawConnection(transform, g, sourceHandle, targetHandle, true, false);
    }
}
