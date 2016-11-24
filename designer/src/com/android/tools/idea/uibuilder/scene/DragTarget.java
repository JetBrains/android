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
package com.android.tools.idea.uibuilder.scene;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Implements a target allowing dragging a widget for the ConstraintLayout viewgroup
 */
public class DragTarget implements Target {
  private SceneComponent myComponent;

  private int myLeft = 0;
  private int myTop = 0;
  private int myRight = 0;
  private int myBottom = 0;

  private boolean mIsOver = false;
  private int mOffsetX;
  private int mOffsetY;

  /////////////////////////////////////////////////////////////////////////////
  //region Accessors
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public void setComponent(@NotNull SceneComponent component) {
    myComponent = component;
  }

  @Override
  public void setOver(boolean over) {
    mIsOver = over;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Layout
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public boolean layout(int l, int t, int r, int b) {
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
  public void render(@NotNull DisplayList list) {
    list.addRect(myLeft, myTop, myRight, myBottom, mIsOver ? Color.yellow : Color.green);
    list.addLine(myLeft, myTop, myRight, myBottom, Color.red);
    list.addLine(myLeft, myBottom, myRight, myTop, Color.red);
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Utilities
  /////////////////////////////////////////////////////////////////////////////

  private void updateAttributes(AttributesTransaction attributes, int x, int y) {
    String positionX = String.format(SdkConstants.VALUE_N_DP, x);
    attributes.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, positionX);
    String positionY = String.format(SdkConstants.VALUE_N_DP, y);
    attributes.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, positionY);
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Mouse Handling
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public int getPreferenceLevel() {
    return 0;
  }

  @Override
  public void addHit(@NotNull ScenePicker picker) {
    mIsOver = false;
    picker.addRect(this, 0, myLeft, myTop, myRight, myBottom);
  }

  @Override
  public void mouseDown(int x, int y) {
    mOffsetX = x - myComponent.getDrawX(System.currentTimeMillis());
    mOffsetY = y - myComponent.getDrawY(System.currentTimeMillis());
  }

  @Override
  public void mouseDrag(int x, int y, @Nullable Target closestTarget) {
    NlComponent component = myComponent.getNlComponent();
    AttributesTransaction attributes = component.startAttributeTransaction();
    int dx = x - myComponent.getParent().getDrawX() - mOffsetX;
    int dy = y - myComponent.getParent().getDrawY() - mOffsetY;
    updateAttributes(attributes, dx, dy);
    attributes.apply();
    myComponent.getScene().needsLayout(Scene.IMMEDIATE_LAYOUT);
  }

  @Override
  public void mouseRelease(int x, int y, @Nullable Target closestTarget) {
    NlComponent component = myComponent.getNlComponent();
    AttributesTransaction attributes = component.startAttributeTransaction();
    int dx = x - myComponent.getParent().getDrawX() - mOffsetX;
    int dy = y - myComponent.getParent().getDrawY() - mOffsetY;
    updateAttributes(attributes, dx, dy);
    attributes.apply();

    NlModel nlModel = component.getModel();
    Project project = nlModel.getProject();
    XmlFile file = nlModel.getFile();

    String label = "Constraint";
    WriteCommandAction action = new WriteCommandAction(project, label, file) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        attributes.commit();
      }
    };
    action.execute();
    myComponent.getScene().needsLayout(Scene.IMMEDIATE_LAYOUT);
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
}
