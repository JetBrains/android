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
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.scene.ConstraintComponentUtilities;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.draw.*;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;

/**
 * Implements a target allowing dragging a widget for the ConstraintLayout viewgroup
 */
public class DragTarget extends ConstraintTarget {

  private static final boolean DEBUG_RENDERER = false;
  protected int myOffsetX;
  protected int myOffsetY;
  protected boolean myChangedComponent;

  ArrayList<Notch> myHorizontalNotches = new ArrayList<>();
  ArrayList<Notch> myVerticalNotches = new ArrayList<>();
  Notch myCurrentNotchX = null;
  Notch myCurrentNotchY = null;

  /////////////////////////////////////////////////////////////////////////////
  //region Layout
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public boolean layout(@NotNull SceneContext sceneTransform, int l, int t, int r, int b) {
    int minWidth = 16;
    int minHeight = 16;
    if (r - l < minWidth) {
      int d = (minWidth - (r - l)) / 2;
      l -= d;
      r += d;
    }
    if (b - t < minHeight) {
      int d = (minHeight - (b - t)) / 2;
      t -= d;
      b += d;
    }
    myLeft = l;
    myTop = t;
    myRight = r;
    myBottom = b;
    return false;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Display
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    if (DEBUG_RENDERER) {
      list.addRect(sceneContext, myLeft, myTop, myRight, myBottom, mIsOver ? Color.yellow : Color.green);
      list.addLine(sceneContext, myLeft, myTop, myRight, myBottom, Color.red);
      list.addLine(sceneContext, myLeft, myBottom, myRight, myTop, Color.red);
    }
    if (myCurrentNotchX != null) {
      myCurrentNotchX.render(list, sceneContext, myComponent);
    }
    if (myCurrentNotchY != null) {
      myCurrentNotchY.render(list, sceneContext, myComponent);
    }
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Utilities
  /////////////////////////////////////////////////////////////////////////////

  @Nullable
  private SceneComponent getTargetComponent(@NotNull String uri, @NotNull ArrayList<String> attributes) {
    NlComponent nlComponent = myComponent.getNlComponent();
    String target = null;
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

  private int getLeftTargetOrigin(SceneComponent target) {
    int origin = target.getDrawX();
    NlComponent nlComponent = myComponent.getNlComponent();
    if (nlComponent.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF) != null) {
      origin += target.getDrawWidth();
    }
    return origin;
  }

  private int getRightTargetOrigin(SceneComponent target) {
    int origin = target.getDrawX();
    NlComponent nlComponent = myComponent.getNlComponent();
    if (nlComponent.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF) != null) {
      origin += target.getDrawWidth();
    }
    return origin;
  }

  private int getTopTargetOrigin(SceneComponent target) {
    int origin = target.getDrawY();
    NlComponent nlComponent = myComponent.getNlComponent();
    if (nlComponent.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF) != null) {
      origin += target.getDrawHeight();
    }
    return origin;
  }

  private int getBottomTargetOrigin(SceneComponent target) {
    int origin = target.getDrawY();
    NlComponent nlComponent = myComponent.getNlComponent();
    if (nlComponent.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF) != null) {
      origin += target.getDrawHeight();
    }
    return origin;
  }

  private int getMarginValue(String attribute) {
    // TODO handles RTL + margin
    NlComponent nlComponent = myComponent.getNlComponent();
    return ConstraintComponentUtilities.getDpValue(nlComponent, nlComponent.getAttribute(SdkConstants.ANDROID_URI, attribute));
  }

  protected void updateAttributes(AttributesTransaction attributes, int x, int y) {
    SceneComponent targetLeftComponent = getTargetComponent(SdkConstants.SHERPA_URI, ourLeftAttributes);
    SceneComponent targetRightComponent = getTargetComponent(SdkConstants.SHERPA_URI, ourRightAttributes);
    checkIsInChain();
    if (targetLeftComponent != null && targetRightComponent != null) {
      if (!myIsInHorizontalChain) {
        int dx1 = getLeftTargetOrigin(targetLeftComponent) + getMarginValue(SdkConstants.ATTR_LAYOUT_MARGIN_LEFT);
        int dx2 = getRightTargetOrigin(targetRightComponent) - getMarginValue(SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
        float dw = dx2 - dx1 - myComponent.getDrawWidth();
        float bias = (x - dx1) / dw;
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
    else if (targetLeftComponent != null) {
      int dx = x - getLeftTargetOrigin(targetLeftComponent);
      String marginX = String.format(SdkConstants.VALUE_N_DP, dx);
      attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, marginX);
      attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_START, marginX); // TODO: handles RTL correctly
    }
    else if (targetRightComponent != null) {
      int dx = getRightTargetOrigin(targetRightComponent) - (x + myComponent.getDrawWidth());
      String marginX = String.format(SdkConstants.VALUE_N_DP, dx);
      attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, marginX);
      attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_END, marginX); // TODO: handles RTL correctly
    }
    else {
      int dx = x - myComponent.getParent().getDrawX();
      String positionX = String.format(SdkConstants.VALUE_N_DP, dx);
      attributes.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, positionX);
    }

    SceneComponent targetTopComponent = getTargetComponent(SdkConstants.SHERPA_URI, ourTopAttributes);
    SceneComponent targetBottomComponent = getTargetComponent(SdkConstants.SHERPA_URI, ourBottomAttributes);
    if (targetTopComponent != null && targetBottomComponent != null) {
      if (!myIsInVerticalChain) {
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
      String marginY = String.format(SdkConstants.VALUE_N_DP, dy);
      attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_TOP, marginY);
    }
    else if (targetBottomComponent != null) {
      int dy = getBottomTargetOrigin(targetBottomComponent) - (y + myComponent.getDrawHeight());
      String marginY = String.format(SdkConstants.VALUE_N_DP, dy);
      attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM, marginY);
    }
    else {
      int dy = y - myComponent.getParent().getDrawY();
      String positionY = String.format(SdkConstants.VALUE_N_DP, dy);
      attributes.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, positionY);
    }
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Mouse Handling
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public int getPreferenceLevel() { return Target.DRAG_LEVEL; }

  @Override
  public void mouseDown(int x, int y) {
    if (myComponent.getParent() == null) {
      return;
    }
    myOffsetX = x - myComponent.getDrawX(System.currentTimeMillis());
    myOffsetY = y - myComponent.getDrawY(System.currentTimeMillis());
    myChangedComponent = false;
    gatherNotches();
  }

  protected void gatherNotches() {
    myCurrentNotchX = null;
    myCurrentNotchY = null;
    myHorizontalNotches.clear();
    myVerticalNotches.clear();
    SceneComponent parent = myComponent.getParent();
    Notch.Provider notchProvider = parent.getNotchProvider();
    if (notchProvider != null) {
      notchProvider.fill(parent, myComponent, myHorizontalNotches, myVerticalNotches);
    }
    int count = parent.getChildCount();
    for (int i = 0; i < count; i++) {
      SceneComponent child = parent.getChild(i);
      if (child == myComponent) {
        continue;
      }
      Notch.Provider provider = child.getNotchProvider();
      if (provider != null) {
        provider.fill(child, myComponent, myHorizontalNotches, myVerticalNotches);
      }
    }
  }

  @Override
  public void mouseDrag(int x, int y, @Nullable Target closestTarget) {
    if (myComponent.getParent() == null) {
      return;
    }
    NlComponent component = myComponent.getNlComponent();
    AttributesTransaction attributes = component.startAttributeTransaction();
    int dx = x - myOffsetX;
    int dy = y - myOffsetY;
    dx = snapX(dx);
    dy = snapY(dy);
    updateAttributes(attributes, dx, dy);
    cleanup(attributes);
    attributes.apply();
    myComponent.getScene().needsLayout(Scene.IMMEDIATE_LAYOUT);
    myChangedComponent = true;
  }

  protected int snapX(int dx) {
    int count = myHorizontalNotches.size();
    for (int i = 0; i < count; i++) {
      Notch notch = myHorizontalNotches.get(i);
      int x = notch.apply(dx);
      if (notch.didApply()) {
        myCurrentNotchX = notch;
        return x;
      }
    }
    myCurrentNotchX = null;
    return dx;
  }

  protected int snapY(int dy) {
    int count = myVerticalNotches.size();
    for (int i = 0; i < count; i++) {
      Notch notch = myVerticalNotches.get(i);
      int y = notch.apply(dy);
      if (notch.didApply()) {
        myCurrentNotchY = notch;
        return y;
      }
    }
    myCurrentNotchY = null;
    return dy;
  }

  @Override
  public void mouseRelease(int x, int y, @Nullable Target closestTarget) {
    if (myComponent.getParent() != null) {
      NlComponent component = myComponent.getNlComponent();
      AttributesTransaction attributes = component.startAttributeTransaction();
      int dx = x - myOffsetX;
      int dy = y - myOffsetY;
      if (myCurrentNotchX != null) {
        dx = myCurrentNotchX.apply(dx);
        if (myComponent.allowsAutoConnect()) {
          myCurrentNotchX.apply(attributes);
        }
        myCurrentNotchX = null;
      }
      if (myCurrentNotchY != null) {
        dy = myCurrentNotchY.apply(dy);
        if (myComponent.allowsAutoConnect()) {
          myCurrentNotchY.apply(attributes);
        }
        myCurrentNotchY = null;
      }
      updateAttributes(attributes, dx, dy);
      cleanup(attributes);
      attributes.apply();

      NlModel nlModel = component.getModel();
      Project project = nlModel.getProject();
      XmlFile file = nlModel.getFile();

      String label = "Component dragged";
      WriteCommandAction action = new WriteCommandAction(project, label, file) {
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          attributes.commit();
        }
      };
      action.execute();
    }
    if (myChangedComponent) {
      myComponent.getScene().needsLayout(Scene.IMMEDIATE_LAYOUT);
    }
  }

  public boolean hasChangedComponent() {
    return myChangedComponent;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
}
