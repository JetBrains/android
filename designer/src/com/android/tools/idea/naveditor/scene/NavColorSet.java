/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.scene;

import com.android.tools.idea.uibuilder.handlers.constraint.drawing.ColorSet;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.decorator.WidgetDecorator;
import com.intellij.ui.JBColor;

/**
 * {@link ColorSet} for the navigation editor.
 */
public class NavColorSet extends ColorSet {
  public NavColorSet() {
    mStyle = WidgetDecorator.ANDROID_STYLE;
    mDrawBackground = true;
    mDrawWidgetInfos = false;

    mFrames = new JBColor(0xa7a7a7, 0x2d2f31);
    mHighlightedFrames = new JBColor(0xa7a7a7, 0xa1a1a1);
    mSelectedFrames = new JBColor(0x1886f7, 0x9ccdff);
    mSubduedFrames = new JBColor(0xa7a7a7, 0xa1a1a1);

    mBackground = new JBColor(0xf5f5f5, 0x2d2f31);
    mSubduedBackground = new JBColor(0xfcfcfc, 0x313435);
    mComponentBackground = new JBColor(0xfafafa, 0x515658);

    mText = new JBColor(0xa7a7a7, 0x888888);
    mSelectedText = new JBColor(0x1886f7, 0x9ccdff);
    mSubduedText = new JBColor(0x656565, 0xbababb);

    mConstraints = new JBColor(0xb2a7a7a7, 0xb2888888);
    mHighlightedConstraints = new JBColor(0xa7a7a7, 0x888888);
    mSelectedConstraints = new JBColor(0x1886f7, 0x9ccdff);
  }
}
