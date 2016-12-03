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
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.draw.DrawComponent;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;

/**
 * Implements a target allowing dragging a widget for the ConstraintLayout viewgroup
 */
public class DragTarget extends ConstraintTarget {

  private static final boolean DEBUG_RENDERER = false;
  private int mOffsetX;
  private int mOffsetY;
  private boolean myIsParent;

  /////////////////////////////////////////////////////////////////////////////
  //region Layout
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public boolean layout(int l, int t, int r, int b) {
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
  public void render(@NotNull DisplayList list, SceneContext sceneContext) {
    if (DEBUG_RENDERER) {
      list.addRect(sceneContext, myLeft, myTop, myRight, myBottom, mIsOver ? Color.yellow : Color.green);
      list.addLine(sceneContext, myLeft, myTop, myRight, myBottom, Color.red);
      list.addLine(sceneContext, myLeft, myBottom, myRight, myTop, Color.red);
    } else {
      DrawComponent.add(list, sceneContext, myLeft, myTop, myRight, myBottom, mIsOver ? DrawComponent.OVER : DrawComponent.NORMAL);
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

  /**
   * Return a dp value correctly resolved. This is only intended for generic
   * dimensions (number + unit). Do not use this if the string can contain
   * wrap_content or match_parent. See {@link #getLayoutDimensionDpValue(NlComponent, String)}.
   *
   * @param component the component we are looking at
   * @param value     the attribute value we want to parse
   * @return the value of the attribute in Dp, or zero if impossible to resolve
   */
  private static int getDpValue(@NotNull NlComponent component, String value) {
    if (value != null) {
      Configuration configuration = component.getModel().getConfiguration();
      ResourceResolver resourceResolver = configuration.getResourceResolver();
      if (resourceResolver != null) {
        Integer px = ViewEditor.resolveDimensionPixelSize(resourceResolver, value, configuration);
        return px == null ? 0 : (int)(0.5f + px / (configuration.getDensity().getDpiValue() / 160.0f));
      }
    }
    return 0;
  }

  private int getMarginValue(String attribute) {
    // TODO handles RTL + margin
    NlComponent nlComponent = myComponent.getNlComponent();
    return getDpValue(nlComponent, nlComponent.getAttribute(SdkConstants.ANDROID_URI, attribute));
  }

  private void updateAttributes(AttributesTransaction attributes, int x, int y) {
    SceneComponent targetLeftComponent = getTargetComponent(SdkConstants.SHERPA_URI, ourLeftAttributes);
    SceneComponent targetRightComponent = getTargetComponent(SdkConstants.SHERPA_URI, ourRightAttributes);
    if (targetLeftComponent != null && targetRightComponent != null) {
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
      attributes.setAttribute(SdkConstants.SHERPA_URI,
                              SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS, String.valueOf(bias));
    }
    else if (targetLeftComponent != null) {
      int dx = x - getLeftTargetOrigin(targetLeftComponent);
      String marginX = String.format(SdkConstants.VALUE_N_DP, dx);
      attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, marginX);
      //attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_START, marginX); // TODO: handles RTL correctly
    }
    else if (targetRightComponent != null) {
      int dx = getRightTargetOrigin(targetRightComponent) - (x + myComponent.getDrawWidth());
      String marginX = String.format(SdkConstants.VALUE_N_DP, dx);
      attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, marginX);
      //attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_END, marginX); // TODO: handles RTL correctly
    }
    else {
      String positionX = String.format(SdkConstants.VALUE_N_DP, x);
      attributes.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, positionX);
    }

    SceneComponent targetTopComponent = getTargetComponent(SdkConstants.SHERPA_URI, ourTopAttributes);
    SceneComponent targetBottomComponent = getTargetComponent(SdkConstants.SHERPA_URI, ourBottomAttributes);
    if (targetTopComponent != null && targetBottomComponent != null) {
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
      attributes.setAttribute(SdkConstants.SHERPA_URI,
                              SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, String.valueOf(bias));
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
  public int getPreferenceLevel() {
    return myIsParent ? -1 : 0;
  }

  public void setIsParent(boolean isParent) {
    myIsParent = isParent;
  }

  @Override
  public void mouseDown(int x, int y) {
    mOffsetX = x - myComponent.getDrawX(System.currentTimeMillis());
    mOffsetY = y - myComponent.getDrawY(System.currentTimeMillis());
  }

  @Override
  public void mouseDrag(int x, int y, @Nullable Target closestTarget) {
    if (myComponent.getParent() == null) {
      return;
    }
    NlComponent component = myComponent.getNlComponent();
    AttributesTransaction attributes = component.startAttributeTransaction();
    int dx = x - myComponent.getParent().getDrawX() - mOffsetX;
    int dy = y - mOffsetY;
    updateAttributes(attributes, dx, dy);
    cleanup(attributes);
    attributes.apply();
    myComponent.getScene().needsLayout(Scene.IMMEDIATE_LAYOUT);
  }

  @Override
  public void mouseRelease(int x, int y, @Nullable Target closestTarget) {
    if (myComponent.getParent() != null) {
      NlComponent component = myComponent.getNlComponent();
      AttributesTransaction attributes = component.startAttributeTransaction();
      int dx = x - myComponent.getParent().getDrawX() - mOffsetX;
      int dy = y - mOffsetY;
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
    if (closestTarget == this && !myComponent.isSelected()) {
      myComponent.getScene().select(myComponent);
    }
    myComponent.getScene().needsLayout(Scene.IMMEDIATE_LAYOUT);
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
}
