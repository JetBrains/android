/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion;

import com.android.SdkConstants;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintSceneInteraction;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.google.common.collect.ImmutableList;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;

class MotionLayoutSceneInteraction extends ConstraintSceneInteraction {

  private NlComponent myPrimary;
  int startX;
  int startY;
  NlComponent selected;
  private MotionLayoutComponentHelper myMotionHelper;
  private boolean didUpdateKeyframe;

  /**
   * Base constructor
   *
   * @param sceneView the ScreenView we belong to
   * @param primary
   */
  public MotionLayoutSceneInteraction(@NotNull SceneView sceneView,
                                      @NotNull NlComponent primary) {
    super(sceneView, primary);
    myPrimary = primary;
    if (primary.getParent() != null) {
      myPrimary = myPrimary.getParent();
    }
  }

  private void useComponent(@NotNull NlComponent component) {
    selected = component;
    NlComponent transitionLayoutComponent = null;
    if (NlComponentHelperKt.isOrHasSuperclass(component, SdkConstants.MOTION_LAYOUT)) {
      transitionLayoutComponent = component;
    } else {
      NlComponent parent = selected.getParent();
      if (parent != null && NlComponentHelperKt.isOrHasSuperclass(parent, SdkConstants.MOTION_LAYOUT)) {
        transitionLayoutComponent = parent;
      }
    }
    myMotionHelper = transitionLayoutComponent != null ? new MotionLayoutComponentHelper(transitionLayoutComponent) : null;
  }

  MotionLayoutTimelinePanel.State getState() {
    Object panel = myPrimary.getClientProperty(MotionLayoutTimelinePanel.TIMELINE);
    if (panel != null) {
      MotionLayoutTimelinePanel timeline = (MotionLayoutTimelinePanel) panel;
      return timeline.getCurrentState();
    }
    return MotionLayoutTimelinePanel.State.TL_UNKNOWN;
  }

  @Override
  public void begin(@SwingCoordinate int x, @SwingCoordinate int y, @JdkConstants.InputEventMask int startMask) {
    if (getState() == MotionLayoutTimelinePanel.State.TL_TRANSITION) {
      NlComponent component = Coordinates.findComponent(mySceneView, x, y);
      selected = component;
      if (component != null) {
        mySceneView.getSelectionModel().setSelection(ImmutableList.of(component));
        useComponent(component);
      }
      startX = x;
      startY = y;
      didUpdateKeyframe = false;
    } else {
      super.begin(x, y, startMask);
    }
  }

  @Override
  public void update(@SwingCoordinate int x, @SwingCoordinate int y, @JdkConstants.InputEventMask int modifiers) {
    if (getState() == MotionLayoutTimelinePanel.State.TL_TRANSITION) {
      if (selected != null) {
        float fx = Coordinates.getAndroidX(mySceneView, x); //(int)(x - startX));
        float fy = Coordinates.getAndroidY(mySceneView, y); //(int) (y - startY));
        if (!myMotionHelper.setKeyframePosition(NlComponentHelperKt.getViewInfo(selected).getViewObject(),
                                                52, MotionLayoutComponentHelper.HORIZONTAL_PATH_X, fx, fy)) {
          didUpdateKeyframe = true;
        }
      }
      if (false && !didUpdateKeyframe && myMotionHelper != null) {
        float startX = Coordinates.getSwingXDip(mySceneView, mySceneView.getScene().getRoot().getDrawX());
        float dw = Math.max(0, x - startX);
        float w = Coordinates.getSwingDimensionDip(mySceneView, mySceneView.getScene().getRoot().getDrawWidth());
        myMotionHelper.setValue(dw / w);
      }
    } else {
      super.update(x, y, modifiers);
    }
  }

  @Override
  public void end(@SwingCoordinate int x, @SwingCoordinate int y, @JdkConstants.InputEventMask int modifiers, boolean canceled) {
    if (getState() == MotionLayoutTimelinePanel.State.TL_TRANSITION) {
      if (false && !didUpdateKeyframe && myMotionHelper != null) {
        float startX = Coordinates.getSwingXDip(mySceneView, mySceneView.getScene().getRoot().getDrawX());
        float dw = Math.max(0, x - startX);
        float w = Coordinates.getSwingDimensionDip(mySceneView, mySceneView.getScene().getRoot().getDrawWidth());
        myMotionHelper.setValue(dw / w);
      }
    } else {
      super.end(x, y, modifiers, canceled);
    }
  }
}
