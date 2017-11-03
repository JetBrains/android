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
package com.android.tools.idea.uibuilder.handlers.constraint.targets;

import com.android.SdkConstants;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawHorizontalGuideline;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawVerticalGuideline;
import com.android.tools.idea.common.scene.target.Target;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Implements the drag behaviour for ConstraintLayout Guideline
 */
public class GuidelineTarget extends ConstraintDragTarget {
  boolean myIsHorizontal = true;
  int myBegin = 20;
  int myEnd = -1;
  float myPercent = -1;

  @Override
  public int getPreferenceLevel() {
    return Target.GUIDELINE_LEVEL;
  }

  public GuidelineTarget(boolean isHorizontal) {
    myIsHorizontal = isHorizontal;
  }

  @Override
  public Cursor getMouseCursor() {
    if (myIsHorizontal) {
      return Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
    }
    return Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
  }

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    if (myIsHorizontal) {
      int y = (int)(myTop + (myBottom - myTop) / 2);
      SceneComponent parent = myComponent.getParent();
      DrawHorizontalGuideline
        .add(list, sceneContext, myLeft, y, myRight, parent.getDrawX(), parent.getDrawY(), parent.getDrawHeight(), myBegin, myEnd, myPercent, myComponent.isSelected());
    }
    else {
      int x = (int)(myLeft + (myRight - myLeft) / 2);
      SceneComponent parent = myComponent.getParent();
      DrawVerticalGuideline
        .add(list, sceneContext, x, myTop, myBottom, parent.getDrawX(), parent.getDrawY(), parent.getDrawWidth(), myBegin, myEnd,
             myPercent, myComponent.isSelected());
    }
  }

  @Override
  public boolean layout(@NotNull SceneContext sceneTransform,
                        @AndroidDpCoordinate int l,
                        @AndroidDpCoordinate int t,
                        @AndroidDpCoordinate int r,
                        @AndroidDpCoordinate int b) {
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
    NlComponent component = myComponent.getAuthoritativeNlComponent();
    String begin = component.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN);
    String end = component.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_END);
    String percent = component.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_PERCENT);
    if (begin != null) {
      myBegin = ConstraintComponentUtilities.getDpValue(component, begin);
      myEnd = -1;
      myPercent = -1;
    }
    else if (end != null) {
      myBegin = -1;
      myEnd = ConstraintComponentUtilities.getDpValue(component, end);
      myPercent = -1;
    }
    else if (percent != null) {
      myBegin = -1;
      myEnd = -1;
      try {
        myPercent = Float.valueOf(percent);
      } catch (NumberFormatException e) {
        myPercent = 0;
      }
    }
    return false;
  }

  @Override
  protected void updateAttributes(@NotNull AttributesTransaction attributes, @AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
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
      String position = String.format(SdkConstants.VALUE_N_DP, (int)dimension - value);
      attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_END, position);
    }
    else if (percent != null) {
      String percentStringValue = null;
      float percentValue = value / dimension;
      if (percentValue > 1) {
        percentValue = 1;
      }
      if (percentValue < 0) {
        percentValue = 0;
      }
      percentValue = Math.round(percentValue * 100) / 100f;
      percentStringValue = String.valueOf(percentValue);
      if (percentStringValue.equalsIgnoreCase("NaN")) {
        percentStringValue = "0.5";
      }
      attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_PERCENT, percentStringValue);
    }
    ConstraintComponentUtilities.cleanup(attributes, myComponent);
  }

  @Override
  public String getToolTipText() {
    String str = "Guideline (";
    if (myBegin != -1) {
      str += "< "+myBegin+")";
    }
    else if (myEnd != -1) {
      str += myEnd+" >)";
    }
    else {
      float percentValue = myPercent;
      if (percentValue > 1) {
        percentValue = 1;
      }
      if (percentValue < 0) {
        percentValue = 0;
      }
      percentValue = 100*Math.round(percentValue * 100) / 100f;
      if (!Float.isNaN(percentValue)) {
        str += String.valueOf(percentValue)+"%)";
      }
      else {
        str += "50";
      }
    }
    return str;
  }
}
