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

import com.android.tools.sherpa.drawing.ColorSet;
import com.android.tools.sherpa.drawing.decorator.WidgetDecorator;
import com.intellij.ui.JBColor;

import java.awt.*;

/**
 * {@link ColorSet} for the navigation editor.
 */
public class NavColorSet extends ColorSet {
  public NavColorSet() {
    mStyle = WidgetDecorator.ANDROID_STYLE;
    mDrawBackground = true;
    mDrawWidgetInfos = false;

    mFrames = new JBColor(0xa7a7a7, 0x888888);
    mHighlightedFrames = mFrames;

    mSelectedFrames = new JBColor(0x1886f7, 0x9ccdff);
    mSelectedText = mSelectedFrames;

    mBackground = new JBColor(0xfdfdfd, 0x2d2f31);
    mComponentBackground = mBackground;

    mText = JBColor.BLACK;
    mSubduedBackground = new Color(0xfc, 0xfc, 0xfc); // TODO: Darkula color?
  }
}
