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
 * Default color set for the "normal" UI mode
 */
public class AndroidColorSet extends ColorSet {

    public AndroidColorSet() {

        mShadow = new Color(255, 255, 255, 50);

        mDrawBackground = false;
        mDrawWidgetInfos = false;

        // Base colors

        mBackground = Color.white;
        mFrames = Color.lightGray;
        mConstraints = Color.blue;
        mText = Color.black;
        mSnapGuides = Color.red;

        // Subdued colors

        mSubduedConstraints = Color.blue;
        mSubduedBackground = Color.white;
        mSubduedText = Color.black;
        mSubduedFrames = Color.black;

        // Highlight colors

        mHighlightedBackground = Color.white;
        mHighlightedFrames = Color.blue;
        mHighlightedSnapGuides = Color.orange;
        mHighlightedConstraints = Color.cyan;

        // Selected colors

        mSelectedBackground = Color.white;
        mSelectedFrames = Color.blue;
        mSelectedConstraints = Color.blue;
        mSelectedText = Color.black;

        // Anchor colors

        mAnchorCircle = Color.black;
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
