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

package com.android.tools.idea.uibuilder.handlers.constraint.drawing;

import com.android.tools.idea.common.scene.draw.ColorSet;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.decorator.ColorTheme;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import java.awt.Color;

/**
 * Default color set for the "normal" UI mode
 */
@SuppressWarnings("UseJBColor")
public class AndroidColorSet extends ColorSet {

    public AndroidColorSet() {

        mStyle = ColorSet.ANDROID_STYLE;

        mShadow = new Color(250, 250, 250);

        mDrawBackground = false;
        mDrawWidgetInfos = false;

        // Base colors

        mBackground = new Color(250, 250, 250, 0);
        mComponentObligatoryBackground = new Color(250, 250, 250);
        mFrames = Color.lightGray;
        mConstraints = Color.lightGray;
        mSoftConstraintColor = Color.cyan.darker();
        mMargins = Color.lightGray;
        mText = Color.black;
        mSnapGuides = Color.red;
        mFakeUI = Color.black;
        myUnconstrainedColor = Color.red;

        // Subdued colors

        mSubduedConstraints = ColorTheme.updateBrightness(mConstraints, 1.2f);
        mSubduedBackground = Color.white;
        mSubduedText = Color.black;
        mSubduedFrames = Color.black;

        // Highlight colors

        mComponentHighlightedBackground = new Color(0x591886F7, true);
        mHighlightedBackground = Color.white;
        mHighlightedFrames = new JBColor(0x3573f0, 0x548af7);
        mHighlightedSnapGuides = Color.orange;
        mHighlightedConstraints = Color.blue.brighter();

        // Selected colors

        mSelectedBackground = mBackground;
        mSelectedFrames = new Color(24, 134, 247);
        mSelectedConstraints = new Color(24, 134, 247);
        mSelectedText = Color.black;

        mSelectionColor = Color.black;

        // Anchor colors

        mAnchorCircle = Color.black;
        mAnchorCreationCircle = Color.white;
        mAnchorDisconnectionCircle = new Color(0xDB5860);
        mAnchorConnectionCircle = new Color(10, 130, 10);

        // Widget actions

        mWidgetActionBackground = UIUtil.getPanelBackground();
        mWidgetActionSelectedBackground = UIUtil.getPanelBackground().brighter();
        mWidgetActionSelectedBorder = UIUtil.getFocusedFillColor();

        // Tooltip

        mTooltipBackground = new Color(255, 255, 204);
        mTootipText = Color.black;

        // Inspector colors

        mInspectorTrackBackgroundColor = new Color(228, 228, 238);
        mInspectorTrackColor = new Color(208, 208, 218);
        mInspectorHighlightsStrokeColor = new Color(160, 160, 180, 128);

        mInspectorBackgroundColor =
                ColorTheme.fadeToColor(mBackground, Color.WHITE, 0.1f);
        mInspectorFillColor = ColorTheme
                .fadeToColor(ColorTheme.updateBrightness(mBackground, 1.3f),
                        Color.WHITE, 0.1f);

        // Drag Receiver

        mDragReceiverFrames = new Color(255, 0, 255);
        mDragReceiverBackground = new Color(255, 0, 255, 102);
        mDragOtherReceiversFrame = new Color(255, 0, 255, 102);
        mDragReceiverSiblingBackground = new Color(255, 0, 255, 26);
    }
}
