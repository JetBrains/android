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
package com.android.tools.idea.uibuilder.scene.target;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.draw.DrawHorizontalNotch;
import com.android.tools.idea.uibuilder.scene.draw.DrawVerticalNotch;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Implements the drag behaviour for ConstraintLayout Guideline
 */
public class GuidelineTarget extends DragTarget {
  boolean myIsHorizontal = true;

  @Override
  public int getPreferenceLevel() {
    return Target.GUIDELINE_LEVEL;
  }

  public GuidelineTarget(boolean isHorizontal) {
    myIsHorizontal = isHorizontal;
  }

  @Override
  public int getMouseCursor() {
    if (myIsHorizontal) {
      return Cursor.N_RESIZE_CURSOR;
    }
    return Cursor.E_RESIZE_CURSOR;
  }

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    if (myIsHorizontal) {
      int y = (int)(myTop + (myBottom - myTop) / 2);
      DrawHorizontalNotch.add(list, sceneContext, myLeft, y, myRight);
    } else {
      int x = (int)(myLeft + (myRight - myLeft) / 2);
      DrawVerticalNotch.add(list, sceneContext, x, myTop, myBottom);
    }
  }

  @Override
  public boolean layout(@NotNull SceneContext sceneTransform, int l, int t, int r, int b) {
    int dist = 6;
    SceneComponent parent = myComponent.getParent();
    if (parent != null) {
      if (myIsHorizontal) {
        myLeft = parent.getDrawX();
        myRight = parent.getDrawX() + parent.getDrawWidth();
        myTop = t - dist;
        myBottom = t + dist;
      }
      else {
        myLeft = l - dist;
        myRight = l + dist;
        myTop = parent.getDrawY();
        myBottom = parent.getDrawY() + parent.getDrawHeight();
      }
    }
    return false;
  }

  @Override
  protected void updateAttributes(AttributesTransaction attributes, int x, int y) {
      String begin = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN);
      String end = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_END);
      String percent = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_PERCENT);
      SceneComponent parent = myComponent.getParent();
      int value = y - parent.getDrawY();
      float dimension = parent.getDrawHeight();
      if (!myIsHorizontal) {
        value = x - parent.getDrawX();
        dimension = parent.getDrawWidth();
      }
      if (begin != null) {
        String position = String.format(SdkConstants.VALUE_N_DP, value);
        attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN, position);
      }
      else if (end != null) {
        String position = String.format(SdkConstants.VALUE_N_DP, (int) dimension - value);
        attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_END, position);
      }
      else if (percent != null) {
        String percentValue = String.valueOf(value / dimension);
        attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_PERCENT, percentValue);
      }
  }
}
