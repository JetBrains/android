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
import com.android.tools.idea.common.command.NlWriteCommandAction;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawGuidelineCycle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Implements the guideline cycle
 */
public class GuidelineCycleTarget extends ConstraintDragTarget {

  private final boolean myIsHorizontal;

  public GuidelineCycleTarget(boolean isHorizontal) {
    myIsHorizontal = isHorizontal;
  }

  @Override
  public boolean canChangeSelection() {
    return false;
  }

  @Override
  public int getPreferenceLevel() {
    return Target.GUIDELINE_LEVEL;
  }

  @Override
  public boolean layout(@NotNull SceneContext sceneTransform,
                        @AndroidDpCoordinate int l,
                        @AndroidDpCoordinate int t,
                        @AndroidDpCoordinate int r,
                        @AndroidDpCoordinate int b) {
    int size = 16;

    if (myIsHorizontal) {
      myLeft = l - size;
      myTop = t - size / 2F;
      myRight = l;
      myBottom = t + size / 2F;
    }
    else {
      myLeft = l - size / 2F;
      myTop = t - size;
      myRight = l + size / 2F;
      myBottom = t;
    }
    return false;
  }

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneTransform) {
    int mode = ConstraintComponentUtilities.getGuidelineMode(myComponent);
    DrawGuidelineCycle.add(list, sceneTransform, myIsHorizontal, myLeft, myTop, myRight, myBottom, mode, myComponent.isSelected());
  }

  @Override
  public void mouseDown(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    super.mouseDown(x, y);
    myComponent.setSelected(true);
  }

  @Override
  public void mouseRelease(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @Nullable List<Target> closestTargets) {
    super.mouseRelease(x, y, closestTargets);
    if (Math.abs(x - myFirstMouseX) > 1 || Math.abs(y - myFirstMouseY) > 1) {
      return;
    }

    NlComponent component = myComponent.getAuthoritativeNlComponent();
    AttributesTransaction attributes = component.startAttributeTransaction();
    String begin = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN);
    String end = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_END);
    String percent = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_PERCENT);
    SceneComponent parent = myComponent.getParent();
    assert parent != null;
    int value = myComponent.getDrawY() - parent.getDrawY();
    int dimension = parent.getDrawHeight();
    if (!myIsHorizontal) {
      value = myComponent.getDrawX() - parent.getDrawX();
      dimension = parent.getDrawWidth();
    }
    if (begin != null) {
      setEnd(attributes, dimension - value);
    }
    else if (end != null) {
      setPercent(attributes, value / (float)dimension);
    }
    else if (percent != null) {
      setBegin(attributes, value);
    }

    attributes.apply();
    NlWriteCommandAction.run(component, "Cycle Guideline", attributes::commit);
  }

  @Override
  protected void updateAttributes(@NotNull AttributesTransaction attributes, int x, int y) {
    String begin = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN);
    String end = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_END);
    String percent = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_PERCENT);
    SceneComponent parent = myComponent.getParent();
    assert parent != null;
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
      String percentStringValue;
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

  private static void setBegin(AttributesTransaction transaction, @AndroidDpCoordinate int value) {
    String position = String.format(SdkConstants.VALUE_N_DP, value);
    transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN, position);
    transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_END, null);
    transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_PERCENT, null);
  }

  private static void setEnd(AttributesTransaction transaction, @AndroidDpCoordinate int value) {
    String position = String.format(SdkConstants.VALUE_N_DP, value);
    transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN, null);
    transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_END, position);
    transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_PERCENT, null);
  }

  private static void setPercent(AttributesTransaction transaction, float value) {
    String position = String.valueOf(value);
    transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN, null);
    transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_END, null);
    transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_PERCENT, position);
  }

  @Override
  public String getToolTipText() {
    return "Cycle Guideline";
  }
}
