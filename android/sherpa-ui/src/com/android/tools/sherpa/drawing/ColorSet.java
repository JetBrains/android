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

import java.awt.*;

/**
 * Holds a set of colors for drawing a scene
 */
public class ColorSet {

    protected boolean mDrawBackground = true;
    protected boolean mDrawWidgetInfos = true;
    protected boolean mUseTooltips = true;
    protected boolean mAlwaysShowMargins = false;

    protected Color mBackground;
    protected Color mFrames;
    protected Color mConstraints;
    protected Color mWeakConstraintColor;
    protected Color mMargins;
    protected Color mText;
    protected Color mSnapGuides;
    protected Color mCreatedConstraints = new Color(250, 187, 32);

    protected Color mSubduedText;
    protected Color mSubduedBackground;
    protected Color mSubduedFrames;
    protected Color mSubduedConstraints;

    protected Color mHighlightedBackground;
    protected Color mHighlightedFrames;
    protected Color mHighlightedSnapGuides;
    protected Color mHighlightedConstraints;

    protected Color mSelectedBackground;
    protected Color mSelectedFrames;
    protected Color mSelectedConstraints;
    protected Color mSelectedText;

    protected Color mInspectorBackgroundColor;
    protected Color mInspectorFillColor;

    protected Color mInspectorTrackBackgroundColor;
    protected Color mInspectorTrackColor;
    protected Color mInspectorHighlightsStrokeColor;

    protected Color mAnchorCircle;
    protected Color mAnchorCreationCircle;
    protected Color mAnchorDisconnectionCircle;
    protected Color mAnchorConnectionCircle;

    protected Color mWidgetActionBackground;
    protected Color mWidgetActionSelectedBackground;

    protected Color mSelectionColor;

    protected Color mShadow = new Color(0, 0, 0, 50);
    protected Stroke mShadowStroke = new BasicStroke(3);

    protected Color mTooltipBackground = new Color(0, 0, 0, 160);

    protected int mStyle;

    private Paint mBackgroundPaint;

    public Paint getBackgroundPaint() {
        return mBackgroundPaint;
    }

    public void setBackgroundPaint(Paint backgroundPaint) {
        mBackgroundPaint = backgroundPaint;
    }

    public Color getAnchorCircle() { return mAnchorCircle; }

    public Color getAnchorCreationCircle() { return mAnchorCreationCircle; }

    public Color getAnchorDisconnectionCircle() { return mAnchorDisconnectionCircle; }

    public Color getAnchorConnectionCircle() { return mAnchorConnectionCircle; }

    public Color getSubduedText() { return mSubduedText; }

    public Color getSelectedFrames() { return mSelectedFrames; }

    public Color getBackground() { return mBackground; }

    public Color getFrames() { return mFrames; }

    public Color getConstraints() { return mConstraints; }

    public Color getWeakConstraintColor() { return mWeakConstraintColor; }

    public Color getMargins() { return mMargins; }

    public Color getText() { return mText; }

    public Color getHighlightedFrames() { return mHighlightedFrames; }

    public Color getSnapGuides() { return mSnapGuides; }

    public Color getHighlightedSnapGuides() { return mHighlightedSnapGuides; }

    public Color getInspectorStroke() { return mFrames; }

    public Color getSubduedBackground() {
        return mSubduedBackground;
    }

    public Color getSubduedConstraints() { return mSubduedConstraints; }

    public Color getSubduedFrames() {
        return mSubduedFrames;
    }

    public Color getHighlightedBackground() { return mHighlightedBackground; }

    public Color getSelectedBackground() { return mSelectedBackground; }

    public Color getSelectedConstraints() { return mSelectedConstraints; }

    public Color getInspectorBackgroundColor() { return mInspectorBackgroundColor; }

    public Color getInspectorFillColor() { return mInspectorFillColor; }

    public Color getInspectorTrackBackgroundColor() { return mInspectorTrackBackgroundColor; }

    public Color getInspectorTrackColor() { return mInspectorTrackColor; }

    public Color getInspectorHighlightsStrokeColor() { return mInspectorHighlightsStrokeColor; }

    public Color getHighlightedConstraints() { return mHighlightedConstraints; }

    public void setHighlightedConstraints(Color highlightedConstraints) {
        mHighlightedConstraints = highlightedConstraints;
    }

    public boolean drawWidgetInfos() {
        return mDrawWidgetInfos;
    }

    public void setDrawWidgetInfos(boolean drawWidgetInfos) {
        mDrawWidgetInfos = drawWidgetInfos;
    }

    public boolean drawBackground() {
        return mDrawBackground;
    }

    public Color getSelectedText() {
        return mSelectedText;
    }

    public Color getShadow() {
        return mShadow;
    }

    public Stroke getShadowStroke() {
        return mShadowStroke;
    }

    public int getStyle() {
        return mStyle;
    }

    public boolean useTooltips() { return mUseTooltips; }

    public void setUseTooltips(boolean value) { mUseTooltips = value; }

    public boolean alwaysShowMargins() { return mAlwaysShowMargins; }

    public void setAlwaysShowMargins(boolean value) { mAlwaysShowMargins = value; }

    public Color getTooltipBackground() {
        return mTooltipBackground;
    }

    public Color getCreatedConstraints() {
        return mCreatedConstraints;
    }

    public Color getSelectionColor() {
        return mSelectionColor;
    }

    public Color getWidgetActionBackground() { return mWidgetActionBackground; }

    public Color getWidgetActionSelectedBackground() { return mWidgetActionSelectedBackground; }
}
