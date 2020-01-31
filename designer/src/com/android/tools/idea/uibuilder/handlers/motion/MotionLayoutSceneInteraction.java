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
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.resources.ResourceType;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.res.ResourceIdManager;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintSceneInteraction;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionLayoutInterface;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.Gantt;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.MotionSceneModel;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.google.common.collect.ImmutableList;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

class MotionLayoutSceneInteraction extends ConstraintSceneInteraction {

  private NlComponent myPrimary;
  int startX;
  int startY;
  NlComponent selected;
  private MotionLayoutComponentHelper myMotionHelper;
  private Object myKeyframe;

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
    //MotionLayoutTimelinePanel panel = MotionLayoutHandler.getTimeline(myPrimary);
    //if (panel != null) {
    //  if (panel.getCurrentState() == MotionLayoutTimelinePanel.State.TL_UNKNOWN) {
    //    panel.updateState();
    //  }
    //  return panel.getCurrentState();
    //}
    return MotionLayoutTimelinePanel.State.TL_UNKNOWN;
  }

  int getFramePosition() {
    //MotionLayoutTimelinePanel panel = MotionLayoutHandler.getTimeline(myPrimary);
    //if (panel != null) {
    //  return ((Gantt)panel.getPanel()).getChart().getFramePosition();
    //}
    return 0;
  }

  MotionSceneModel.KeyFrame getSelectedKeyframe() {
    //MotionLayoutInterface panel = MotionLayoutHandler.getTimeline(myPrimary);
    //if (panel != null) {
    //  return panel.getSelectedKeyframe();
    //}
    return null;
  }

  @Override
  public void begin(@SwingCoordinate int x, @SwingCoordinate int y, @JdkConstants.InputEventMask int modifiersEx) {
    //myKeyframe = null;
    //
    //MotionSceneModel.KeyFrame kf = getSelectedKeyframe();
    //if (kf == null || !kf.getName().equals("KeyPosition") || kf.getFramePosition() != getFramePosition()) {
    //  super.begin(x, y, modifiersEx);
    //  return;
    //}
    //
    //// TODO : cleanup & simplify
    //NlComponent component = Coordinates.findComponent(mySceneView, x, y);
    //selected = component;
    //if (component != null && component.getId() != null) {
    //  mySceneView.getSelectionModel().setSelection(ImmutableList.of(component));
    //  useComponent(component);
    //  MotionLayoutTimelinePanel panel = MotionLayoutHandler.getTimeline(selected);
    //  MotionSceneModel.KeyFrame keyFrame = panel.getSelectedKeyframe();
    //  if (keyFrame != null) {
    //    ResourceIdManager manager = ResourceIdManager.get(component.getModel().getModule());
    //    ResourceReference reference = new ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, component.getId());
    //    Integer resolved = manager.getCompiledId(reference);
    //    if (resolved == null) {
    //      if (component.getDelegate() != null && component.getDelegate() instanceof MotionLayoutComponentDelegate) {
    //        ((MotionLayoutComponentDelegate)component.getDelegate()).updateIds(component);
    //        reference = new ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, component.getId());
    //        resolved = manager.getOrGenerateId(reference);
    //      }
    //    }
    //    myKeyframe = myMotionHelper.getKeyframe(2, resolved, keyFrame.getFramePosition());
    //  }
    //  else {
    //    Object view = NlComponentHelperKt.getViewInfo(selected).getViewObject();
    //    float fx = Coordinates.getAndroidX(mySceneView, x);
    //    float fy = Coordinates.getAndroidY(mySceneView, y);
    //    myKeyframe = myMotionHelper.getKeyframeAtLocation(view, fx, fy);
    //  }
    //  if (myKeyframe != null) {
    //    float progress = keyFrame.getFramePosition() / 100f;
    //    panel.setTimelineProgress(progress);
    //  }
    //}
    //startX = x;
    //startY = y;
  }

  @Override
  public void update(@SwingCoordinate int x, @SwingCoordinate int y, @JdkConstants.InputEventMask int modifiersEx) {
    //MotionSceneModel.KeyFrame keyFrame = getSelectedKeyframe();
    //if (keyFrame == null || !keyFrame.getName().equals("KeyPosition")) {
    //  super.update(x, y, modifiersEx);
    //  return;
    //}
    //
    //// TODO : cleanup & simplify
    //if (selected != null) {
    //  if (myKeyframe != null) {
    //    String[] positionAttributes = new String[2];
    //    positionAttributes[0] = "percentX";
    //    positionAttributes[1] = "percentY";
    //    float[] positionsValues = new float[2];
    //    ViewInfo info = NlComponentHelperKt.getViewInfo(selected);
    //    if (info != null) {
    //      float fx = Coordinates.getAndroidX(mySceneView, x);
    //      float fy = Coordinates.getAndroidY(mySceneView, y);
    //      Object view = info.getViewObject();
    //      if (myMotionHelper.getPositionKeyframe(myKeyframe, view, fx, fy, positionAttributes, positionsValues)) {
    //        myMotionHelper.setKeyframe(myKeyframe, positionAttributes[0], positionsValues[0]);
    //        myMotionHelper.setKeyframe(myKeyframe, positionAttributes[1], positionsValues[1]);
    //        myMotionHelper.setKeyframe(myKeyframe, "drawPath", 4);
    //        myPrimary.getModel().notifyLiveUpdate(false);
    //      }
    //    }
    //  }
    //}
  }

  @Override
  public void end(@SwingCoordinate int x, @SwingCoordinate int y, @JdkConstants.InputEventMask int modifiersEx, boolean canceled) {
    //MotionSceneModel.KeyFrame keyFrame = getSelectedKeyframe();
    //if (keyFrame == null || !keyFrame.getName().equals("KeyPosition")) {
    //  super.end(x, y, modifiersEx, canceled);
    //  return;
    //}
    //
    //// TODO : cleanup & simplify
    //if (selected != null && myKeyframe != null) {
    //  MotionLayoutTimelinePanel panel = MotionLayoutHandler.getTimeline(selected);
    //  ViewInfo info = NlComponentHelperKt.getViewInfo(selected);
    //  if (info != null && panel != null) {
    //    float fx = Coordinates.getAndroidX(mySceneView, x);
    //    float fy = Coordinates.getAndroidY(mySceneView, y);
    //    Object view = info.getViewObject();
    //    String[] positionAttributes = new String[2];
    //    positionAttributes[0] = "percentX";
    //    positionAttributes[1] = "percentY";
    //    float[] positionsValues = new float[2];
    //    if (myMotionHelper.getPositionKeyframe(myKeyframe, view, fx, fy, positionAttributes, positionsValues)) {
    //      HashMap<AttrName, String> values = new HashMap<>();
    //      values.put(AttrName.motionAttr(positionAttributes[0]), Float.toString(positionsValues[0]));
    //      values.put(AttrName.motionAttr(positionAttributes[1]), Float.toString(positionsValues[1]));
    //      panel.setKeyframeAttributes(values);
    //    }
    //  }
    //}
    //
    //myKeyframe = null;
  }
}
