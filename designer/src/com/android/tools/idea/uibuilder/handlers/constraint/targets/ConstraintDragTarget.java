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
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.target.DragBaseTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutGuidelineHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * Implements a target allowing dragging a widget for the ConstraintLayout viewgroup
 */
@SuppressWarnings("ForLoopReplaceableByForEach")
public class ConstraintDragTarget extends DragBaseTarget  implements MultiComponentTarget {

  @AndroidDpCoordinate protected int myOffsetX;
  @AndroidDpCoordinate protected int myOffsetY;

  @AndroidDpCoordinate private final static int ourSnapMarginDistance = 4; // snap on 4dp

  private final ChainChecker myChainChecker = new ChainChecker();

  @Nullable
  private SceneComponent getTargetComponent(@NotNull String uri, @NotNull ArrayList<String> attributes) {
    NlComponent nlComponent = myComponent.getAuthoritativeNlComponent();
    String target;
    int count = attributes.size();
    for (int i = 0; i < count; i++) {
      target = nlComponent.getAttribute(uri, attributes.get(i));
      if (target != null) {
        if (target.equalsIgnoreCase(SdkConstants.ATTR_PARENT)) {
          return myComponent.getParent();
        }
        target = NlComponent.extractId(target);
        if (target != null) {
          return myComponent.getScene().getSceneComponent(target);
        }
      }
    }
    return null;
  }

  private int getStartTargetOrigin(SceneComponent target, boolean isInRtl) {
    int origin = target.getDrawX();
    if (isInRtl) {
      origin += target.getDrawWidth();
    }
    NlComponent nlComponent = myComponent.getAuthoritativeNlComponent();
    if (nlComponent.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_START_TO_END_OF) != null) {
      if (isInRtl) {
        origin = target.getDrawX();
      }
      else {
        origin += target.getDrawWidth();
      }
    }
    return origin;
  }

  private int getEndTargetOrigin(SceneComponent target, boolean isInRtl) {
    int origin = target.getDrawX();
    if (isInRtl) {
      origin += target.getDrawWidth();
    }
    NlComponent nlComponent = myComponent.getAuthoritativeNlComponent();
    if (nlComponent.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_END_TO_END_OF) != null) {
      if (isInRtl) {
        origin = target.getDrawX();
      }
      else {
        origin += target.getDrawWidth();
      }
    }
    return origin;
  }

  protected int getLeftTargetOrigin(SceneComponent target) {
    int origin = target.getDrawX();
    NlComponent nlComponent = myComponent.getAuthoritativeNlComponent();
    if (nlComponent.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF) != null) {
      origin += target.getDrawWidth();
    }
    return origin;
  }

  private int getRightTargetOrigin(SceneComponent target) {
    int origin = target.getDrawX();
    NlComponent nlComponent = myComponent.getAuthoritativeNlComponent();
    if (nlComponent.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF) != null) {
      origin += target.getDrawWidth();
    }
    return origin;
  }

  protected int getTopTargetOrigin(SceneComponent target) {
    int origin = target.getDrawY();
    NlComponent nlComponent = myComponent.getAuthoritativeNlComponent();
    if (nlComponent.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF) != null) {
      origin += target.getDrawHeight();
    }
    return origin;
  }

  private int getBottomTargetOrigin(SceneComponent target) {
    int origin = target.getDrawY();
    NlComponent nlComponent = myComponent.getAuthoritativeNlComponent();
    if (nlComponent.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF) != null) {
      origin += target.getDrawHeight();
    }
    return origin;
  }

  private int getMarginValue(String attribute) {
    // TODO handles RTL + margin
    NlComponent nlComponent = myComponent.getAuthoritativeNlComponent();
    return ConstraintComponentUtilities.getDpValue(nlComponent, nlComponent.getAttribute(SdkConstants.ANDROID_URI, attribute));
  }

  @Override
  protected void updateAttributes(@NotNull AttributesTransaction attributes, @AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    SceneComponent targetStartComponent = getTargetComponent(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourStartAttributes);
    SceneComponent targetEndComponent = getTargetComponent(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourEndAttributes);
    SceneComponent targetLeftComponent = getTargetComponent(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourLeftAttributes);
    SceneComponent targetRightComponent = getTargetComponent(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourRightAttributes);
    String targetStartMargin = SdkConstants.ATTR_LAYOUT_MARGIN_START;
    String targetEndMargin = SdkConstants.ATTR_LAYOUT_MARGIN_END;
    boolean useStartEnd = myComponent.useRtlAttributes();
    boolean isInRTL = myComponent.getScene().isInRTL();

    int dx1 = 0;
    int dx2 = 0;

    if (targetStartComponent == null && targetEndComponent == null) {
      // No start/end, let's use left/right
      targetStartComponent = targetLeftComponent;
      targetEndComponent = targetRightComponent;
      targetStartMargin = SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
      targetEndMargin = SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
      useStartEnd = false;
      isInRTL = false;
      if (targetStartComponent != null) {
        dx1 = getLeftTargetOrigin(targetStartComponent) + getMarginValue(targetStartMargin);
      }
      if (targetEndComponent != null) {
        dx2 = getRightTargetOrigin(targetEndComponent) - getMarginValue(targetEndMargin);
      }
    }
    else {
      if (targetStartComponent != null) {
        dx1 = getStartTargetOrigin(targetStartComponent, isInRTL);
        int margin = getMarginValue(targetStartMargin);
        if (isInRTL) {
          dx1 -= margin;
        }
        else {
          dx1 += margin;
        }
      }
      if (targetEndComponent != null) {
        dx2 = getEndTargetOrigin(targetEndComponent, isInRTL);
        int margin = getMarginValue(targetEndMargin);
        if (isInRTL) {
          dx2 += margin;
        }
        else {
          dx2 -= margin;
        }
      }
    }
    myChainChecker.checkIsInChain(myComponent);
    SceneComponent parent = myComponent.getParent();
    assert parent != null;
    if (targetStartComponent != null && targetEndComponent != null) {
      if (!myChainChecker.isInHorizontalChain()) {
        float dw = dx2 - dx1 - myComponent.getDrawWidth();
        float bias = (x - dx1) / dw;
        if (useStartEnd && isInRTL) {
          dw = dx1 - dx2 - myComponent.getDrawWidth();
          bias = (dw - (x - dx2)) / dw;
        }
        if (bias < 0) {
          bias = 0;
        }
        if (bias > 1) {
          bias = 1;
        }
        String biasValue = null;
        if ((int)(bias * 1000) != 500) {
          bias = (int)(bias * 1000) / 1000f;
          biasValue = String.valueOf(bias);
          if (biasValue.equalsIgnoreCase("NaN")) {
            biasValue = null;
          }
        }
        attributes.setAttribute(SdkConstants.SHERPA_URI,
                                SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS, biasValue);
      }
    }
    else if (targetStartComponent != null) {
      int dx = x - getLeftTargetOrigin(targetStartComponent);
      if (useStartEnd) {
        if (isInRTL) {
          dx = getStartTargetOrigin(targetStartComponent, isInRTL) - (x + myComponent.getDrawWidth());
        }
        else {
          dx = x - getStartTargetOrigin(targetStartComponent, isInRTL);
        }
      }
      applyMargin(attributes, targetStartMargin, dx);
    }
    else if (targetEndComponent != null) {
      int dx = getRightTargetOrigin(targetEndComponent) - (x + myComponent.getDrawWidth());
      if (useStartEnd) {
        if (isInRTL) {
          dx = x - getEndTargetOrigin(targetEndComponent, isInRTL);
        }
        else {
          dx = getEndTargetOrigin(targetEndComponent, isInRTL) - (x + myComponent.getDrawWidth());
        }
      }
      applyMargin(attributes, targetEndMargin, dx);
    }
    else {
      int dx = Math.max(0, x - parent.getDrawX());
      String positionX = String.format(SdkConstants.VALUE_N_DP, dx);
      attributes.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, positionX);
    }

    SceneComponent targetTopComponent = getTargetComponent(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourTopAttributes);
    SceneComponent targetBottomComponent = getTargetComponent(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourBottomAttributes);
    if (targetTopComponent != null && targetBottomComponent != null) {
      if (!myChainChecker.isInVerticalChain()) {
        int dy1 = getTopTargetOrigin(targetTopComponent) + getMarginValue(SdkConstants.ATTR_LAYOUT_MARGIN_TOP);
        int dy2 = getBottomTargetOrigin(targetBottomComponent) - getMarginValue(SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM);
        float dh = dy2 - dy1 - myComponent.getDrawHeight();
        float bias = (y - dy1) / dh;
        if (bias < 0) {
          bias = 0;
        }
        if (bias > 1) {
          bias = 1;
        }
        String biasValue = null;
        if ((int)(bias * 1000) != 500) {
          bias = (int)(bias * 1000) / 1000f;
          biasValue = String.valueOf(bias);
          if (biasValue.equalsIgnoreCase("NaN")) {
            biasValue = null;
          }
        }
        attributes.setAttribute(SdkConstants.SHERPA_URI,
                                SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, biasValue);
      }
    }
    else if (targetTopComponent != null) {
      int dy = y - getTopTargetOrigin(targetTopComponent);
      applyMargin(attributes, SdkConstants.ATTR_LAYOUT_MARGIN_TOP, dy);
    }
    else if (targetBottomComponent != null) {
      int dy = getBottomTargetOrigin(targetBottomComponent) - (y + myComponent.getDrawHeight());
      applyMargin(attributes, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM, dy);
    }
    else {
      int dy = Math.max(0, y - parent.getDrawY());
      String positionY = String.format(SdkConstants.VALUE_N_DP, dy);
      attributes.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, positionY);
    }
    ConstraintComponentUtilities.cleanup(attributes, myComponent.getNlComponent());
  }

  private void applyMargin(AttributesTransaction attributes, String attribute, @AndroidDpCoordinate int currentValue) {
    currentValue = Math.max(0, currentValue);
    currentValue = currentValue / ourSnapMarginDistance * ourSnapMarginDistance; // snap
    String marginString = myComponent.getAuthoritativeNlComponent().getLiveAttribute(SdkConstants.ANDROID_URI, attribute);
    int marginValue = -1;
    if (marginString != null) {
      marginValue = getMarginValue(attribute);
    }
    if (marginValue != -1 && marginValue == currentValue) {
      attributes.setAttribute(SdkConstants.ANDROID_URI, attribute, marginString);
    }
    else {
      String marginY = String.format(SdkConstants.VALUE_N_DP, currentValue);
      attributes.setAttribute(SdkConstants.ANDROID_URI, attribute, marginY);
    }
  }

  //endregion
}
