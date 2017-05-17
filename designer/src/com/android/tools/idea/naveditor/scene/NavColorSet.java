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

import java.awt.*;

/**
 * {@link ColorSet} for the navigation editor.
 */
public class NavColorSet extends ColorSet {
  public NavColorSet() {
    // TODO: most of the values below are wrong or unnecessary

    mStyle = WidgetDecorator.ANDROID_STYLE;

    mShadow = new Color(250, 250, 250);
    mSelectedFrames = Color.blue;
    mComponentBackground = Color.WHITE;
    mBackground = Color.WHITE;
    mFrames = Color.black;
    mSelectedText = Color.blue;
    mText = Color.black;
    mHighlightedFrames = new Color(106, 161, 211);
    mSubduedBackground = new Color(0xfc, 0xfc, 0xfc);

    mDrawBackground = true;
    mDrawWidgetInfos = false;
  }
}
