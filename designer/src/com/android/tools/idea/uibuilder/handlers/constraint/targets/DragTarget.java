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
import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.draw.*;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.target.BaseTarget;
import com.android.tools.idea.uibuilder.scene.target.Target;
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
@SuppressWarnings("ForLoopReplaceableByForEach")
public class DragTarget extends BaseTarget {

  private static final boolean DEBUG_RENDERER = false;
  protected int myOffsetX;
  protected int myOffsetY;
  protected int myFirstMouseX;
  protected int myFirstMouseY;
  protected boolean myChangedComponent;

  ArrayList<Notch> myHorizontalNotches = new ArrayList<>();
  ArrayList<Notch> myVerticalNotches = new ArrayList<>();
  Notch myCurrentNotchX = null;
  Notch myCurrentNotchY = null;

  private ChainChecker myChainChecker = new ChainChecker();

  /////////////////////////////////////////////////////////////////////////////
  //region Layout
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public boolean canChangeSelection() {
    return true;
  }

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

  @SuppressWarnings("UseJBColor")
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
    SceneComponent targetLeftComponent = getTargetComponent(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourLeftAttributes);
    SceneComponent targetRightComponent = getTargetComponent(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourRightAttributes);
    myChainChecker.checkIsInChain(myComponent);
    SceneComponent parent = myComponent.getParent();
    assert parent != null;
    if (targetLeftComponent != null && targetRightComponent != null) {
      if (!myChainChecker.isInHorizontalChain()) {
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
      applyMargin(attributes, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, dx);
      /*
      // TODO: handles RTL correctly
      if (myComponent.getNlComponent().getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_START) == null) {
        // if start isn't defined, create it based on the margin left
        attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_START,
                                myComponent.getNlComponent().getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT));
      } else {
        applyMargin(attributes, SdkConstants.ATTR_LAYOUT_MARGIN_START, dx);
      }
      */
    }
    else if (targetRightComponent != null) {
      int dx = getRightTargetOrigin(targetRightComponent) - (x + myComponent.getDrawWidth());
      applyMargin(attributes, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, dx);
      /*
      // TODO: handles RTL correctly
      if (myComponent.getNlComponent().getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_END) == null) {
        // if end isn't defined, create it based on the margin right
        attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_END,
                                myComponent.getNlComponent().getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT));
      } else {
        applyMargin(attributes, SdkConstants.ATTR_LAYOUT_MARGIN_END, dx);
      }
      */
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
  }

  private void applyMargin(AttributesTransaction attributes, String attribute, int currentValue) {
    currentValue = Math.max(0, currentValue);
    String marginString = myComponent.getNlComponent().getLiveAttribute(SdkConstants.ANDROID_URI, attribute);
    int marginValue = -1;
    if (marginString != null) {
      marginValue = getMarginValue(attribute);
    }
    if (marginValue != -1 && marginValue == currentValue) {
      attributes.setAttribute(SdkConstants.ANDROID_URI, attribute, marginString);
    } else {
      String marginY = String.format(SdkConstants.VALUE_N_DP, currentValue);
      attributes.setAttribute(SdkConstants.ANDROID_URI, attribute, marginY);
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
    myFirstMouseX = x;
    myFirstMouseY = y;
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
    assert parent != null;
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
    myComponent.setDragging(true);
    NlComponent component = myComponent.getNlComponent();
    AttributesTransaction attributes = component.startAttributeTransaction();
    int dx = x - myOffsetX;
    int dy = y - myOffsetY;
    dx = snapX(dx);
    dy = snapY(dy);
    updateAttributes(attributes, dx, dy);
    ConstraintComponentUtilities.cleanup(attributes, myComponent);
    attributes.apply();
    component.fireLiveChangeEvent();
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
    if (!myComponent.isDragging()) {
      return;
    }
    myComponent.setDragging(false);
    if (myComponent.getParent() != null) {
      boolean commitChanges = true;
      if (Math.abs(x - myFirstMouseX) <= 1 && Math.abs(y - myFirstMouseY) <= 1) {
        commitChanges = false;
      }
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
      ConstraintComponentUtilities.cleanup(attributes, myComponent);
      attributes.apply();

      if (commitChanges) {
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
    }
    if (myChangedComponent) {
      myComponent.getScene().needsLayout(Scene.IMMEDIATE_LAYOUT);
    }
  }

  public boolean hasChangedComponent() {
    return myChangedComponent;
  }

  @Override
  public Cursor getMouseCursor() {
    return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
}
