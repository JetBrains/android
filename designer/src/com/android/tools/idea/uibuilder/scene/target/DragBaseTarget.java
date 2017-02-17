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
package com.android.tools.idea.uibuilder.scene.target;

import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Base class for dragging targets.
 */
public abstract class DragBaseTarget extends BaseTarget {

  private static final boolean DEBUG_RENDERER = false;

  protected int myOffsetX;
  protected int myOffsetY;
  protected int myFirstMouseX;
  protected int myFirstMouseY;
  protected boolean myChangedComponent;

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

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    if (DEBUG_RENDERER) {
      list.addRect(sceneContext, myLeft, myTop, myRight, myBottom, mIsOver ? JBColor.yellow : JBColor.green);
      list.addLine(sceneContext, myLeft, myTop, myRight, myBottom, JBColor.red);
      list.addLine(sceneContext, myLeft, myBottom, myRight, myTop, JBColor.red);
    }
  }

  protected abstract void updateAttributes(@NotNull AttributesTransaction attributes, int x, int y);

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
  }

  @Override
  public void mouseDrag(int x, int y, @Nullable Target closestTarget) {
    if (myComponent.getParent() == null) {
      return;
    }
    myComponent.setDragging(true);
    NlComponent component = myComponent.getNlComponent();
    AttributesTransaction attributes = component.startAttributeTransaction();
    updateAttributes(attributes, x - myOffsetX, y - myOffsetY);
    attributes.apply();
    component.fireLiveChangeEvent();
    myComponent.getScene().needsLayout(Scene.IMMEDIATE_LAYOUT);
    myChangedComponent = true;
  }

  @Override
  public void mouseRelease(int x, int y, @Nullable Target closestTarget) {
    myComponent.setDragging(false);
    if (myComponent.getParent() != null) {
      boolean commitChanges = true;
      if (Math.abs(x - myFirstMouseX) <= 1 && Math.abs(y - myFirstMouseY) <= 1) {
        commitChanges = false;
      }
      NlComponent component = myComponent.getNlComponent();
      AttributesTransaction attributes = component.startAttributeTransaction();
      updateAttributes(attributes, x - myOffsetX, y - myOffsetY);
      attributes.apply();

      if (commitChanges) {
        NlModel nlModel = component.getModel();
        Project project = nlModel.getProject();
        XmlFile file = nlModel.getFile();

        String commandName = "Dragged " + StringUtil.getShortName(component.getTagName());
        WriteCommandAction action = new WriteCommandAction(project, commandName, file) {
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

  @Override
  public Cursor getMouseCursor() {
    return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
  }

  @Override
  public String getToolTipText(){
    return "View";
  }
  //endregion
  /////////////////////////////////////////////////////////////////////////////
}
