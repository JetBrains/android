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

import com.android.tools.sherpa.drawing.decorator.ColorTheme;

import java.awt.Color;

/**
 * Default color set for the "blueprint" UI mode
 */
public class BlueprintColorSet extends ColorSet {

    public BlueprintColorSet() {

        mDrawBackground = true;

        // Base colors

        mBackground = new Color(24, 55, 112);
        mFrames = new Color(100, 152, 199);
        mConstraints = new Color(102, 129, 204);
        mText = new Color(220, 220, 220);
        mSnapGuides = new Color(220, 220, 220);

        // Subdued colors

        mSubduedConstraints = ColorTheme.updateBrightness(mConstraints, 0.7f);
        mSubduedBackground = ColorTheme.updateBrightness(mBackground, 0.8f);
        mSubduedText = ColorTheme.fadeToColor(mText, mSubduedBackground, 0.6f);
        mSubduedFrames = ColorTheme.updateBrightness(mFrames, 0.8f);

        // Light colors

        mHighlightedBackground = ColorTheme.updateBrightness(mBackground, 1.1f);
        mHighlightedFrames = ColorTheme.updateBrightness(mFrames, 1.2f);
        mHighlightedSnapGuides = new Color(220, 220, 220, 128);
        mHighlightedConstraints = ColorTheme.updateBrightness(mConstraints, 1.2f);

        // Selected colors

        mSelectedBackground = ColorTheme.updateBrightness(mBackground, 1.3f);
        mSelectedConstraints = ColorTheme.fadeToColor(
                ColorTheme.updateBrightness(mConstraints, 2f),
                Color.white, 0.7f);
        mSelectedFrames = ColorTheme.fadeToColor(mSelectedConstraints, mSelectedBackground, 0.2f);
        mSelectedText = ColorTheme.fadeToColor(mText, mSelectedBackground, 0.7f);

        // Anchor colors

        mAnchorCircle = Color.white;
        mAnchorCreationCircle = Color.white;
        mAnchorDisconnectionCircle = new Color(180, 0, 0);
        mAnchorConnectionCircle = new Color(10, 130, 10);

        // Inspector colors

        mInspectorTrackBackgroundColor = new Color(228, 228, 238);
        mInspectorTrackColor = new Color(208, 208, 218);
        mInspectorHighlightsStrokeColor = new Color(160, 160, 180, 128);

        mInspectorBackgroundColor =
                ColorTheme.fadeToColor(mBackground, Color.WHITE, 0.1f);
        mInspectorFillColor = ColorTheme
                .fadeToColor(ColorTheme.updateBrightness(mBackground, 1.3f),
                        Color.WHITE, 0.1f);
    }
}
