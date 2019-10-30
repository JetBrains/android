/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintSceneInteraction;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.MotionSceneModel;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import java.text.DecimalFormat;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;

/**
 * Implements MotionLayout interaction
 * <p>
 * For now, we only do it while in a transition, and only to move KeyFrame positions elements.
 */
class MotionLayoutSceneInteraction extends ConstraintSceneInteraction {

  private static final double SLOPE = 20.0;
  private final NlDesignSurface mySurface;
  private int startX;
  private int startY;
  private NlComponent mySelectedComponent;
  private Object myKeyframe;
  private final static int MAX_KEY_POSITIONS = 101; // 0-100 inclusive key positions are allowed
  private int[] myKeyFrameTypes = new int[MAX_KEY_POSITIONS];
  private float[] myKeyFramePos = new float[MAX_KEY_POSITIONS * 2];
  private int myKeyframePosition = -1;
  private String[] myPositionAttributes = new String[2];
  private float[] myPositionsValues = new float[2];

  /**
   * Base constructor
   *
   * @param sceneView the ScreenView we belong to
   * @param primary
   */
  public MotionLayoutSceneInteraction(@NotNull SceneView sceneView,
                                      @NotNull NlComponent primary) {
    super(sceneView, primary);
    if (sceneView.getSelectionModel().isEmpty()) {
      mySelectedComponent = primary;
    }
    else {
      mySelectedComponent = sceneView.getSelectionModel().getPrimary();
    }
    mySurface = (NlDesignSurface)sceneView.getSurface();
  }

  /**
   * On the start of the interaction, we try to find if there's a keyframe close
   * to the mouse down location.
   *
   * @param x         The most recent mouse x coordinate applicable to this interaction
   * @param y         The most recent mouse y coordinate applicable to this interaction
   * @param modifiersEx The initial AWT mask for the interaction
   */
  @Override
  public void begin(@SwingCoordinate int x, @SwingCoordinate int y,
                    @JdkConstants.InputEventMask int modifiersEx) {
    MotionLayoutComponentHelper helper = MotionLayoutComponentHelper.create(mySelectedComponent);
    Object view = NlComponentHelperKt.getViewInfo(mySelectedComponent).getViewObject();
    float fx = Coordinates.getAndroidX(mySceneView, x);
    float fy = Coordinates.getAndroidY(mySceneView, y);

    myKeyframePosition = -1;
    myKeyframe = null;

    int keyFrameCount = helper.getKeyframePos(mySelectedComponent, myKeyFrameTypes, myKeyFramePos);
    if (keyFrameCount == 0) {
      return;
    }

    mySurface.setRenderSynchronously(true);
    mySurface.setAnimationScrubbing(true);

    for (int i = 0; i < myKeyFrameTypes.length; i++) {
      if (myKeyFrameTypes[i] / 1000 == 2) {
        float kx = myKeyFramePos[i * 2];
        float ky = myKeyFramePos[i * 2 + 1];
        double dx = Math.sqrt((kx - fx) * (kx - fx));
        double dy = Math.sqrt((ky - fy) * (ky - fy));
        if (dx < SLOPE && dy < SLOPE) {
          int framePosition = myKeyFrameTypes[i] - 2000;
          myKeyframe = helper.getKeyframe(view, 2, framePosition);
          myKeyframePosition = framePosition;
        }
      }
    }
    startX = x;
    startY = y;
  }

  /**
   * On update, we modify the selected keyframe live.
   *
   * @param x         The most recent mouse x coordinate applicable to this interaction
   * @param y         The most recent mouse y coordinate applicable to this interaction
   * @param modifiersEx current modifier key mask
   */
  @Override
  public void update(@SwingCoordinate int x, @SwingCoordinate int y,
                     @JdkConstants.InputEventMask int modifiersEx) {
    if (myKeyframe == null) {
      return;
    }
    myPositionAttributes[0] = "percentX";
    myPositionAttributes[1] = "percentY";
    ViewInfo info = NlComponentHelperKt.getViewInfo(mySelectedComponent);
    if (info != null) {
      float fx = Coordinates.getAndroidX(mySceneView, x);
      float fy = Coordinates.getAndroidY(mySceneView, y);
      Object view = info.getViewObject();
      MotionLayoutComponentHelper helper = MotionLayoutComponentHelper.create(mySelectedComponent);
      if (helper.getPositionKeyframe(myKeyframe, view, fx, fy, myPositionAttributes, myPositionsValues)) {
        helper.setKeyframe(myKeyframe, myPositionAttributes[0], myPositionsValues[0]);
        helper.setKeyframe(myKeyframe, myPositionAttributes[1], myPositionsValues[1]);
        // tell motion layout to display additional path info
        // TODO: move instead to studio-driven drawing
        helper.setKeyframe(myKeyframe, "drawPath", 4);
        mySelectedComponent.getModel().notifyLiveUpdate(false);
      }
    }
  }

  /**
   * On end, we commit the keyframe modifications.
   *
   * @param x
   * @param y
   * @param modifiersEx
   * @param canceled
   */
  @Override
  public void end(@SwingCoordinate int x, @SwingCoordinate int y,
                  @JdkConstants.InputEventMask int modifiersEx) {
    mySurface.setRenderSynchronously(false);
    mySurface.setAnimationScrubbing(false);
    if (myKeyframe == null || mySelectedComponent == null) {
      return;
    }

    myPositionAttributes[0] = "percentX";
    myPositionAttributes[1] = "percentY";
    ViewInfo info = NlComponentHelperKt.getViewInfo(mySelectedComponent);
    String selectedId = mySelectedComponent.ensureId();
    if (info != null) {
      float fx = Coordinates.getAndroidX(mySceneView, x);
      float fy = Coordinates.getAndroidY(mySceneView, y);
      Object view = info.getViewObject();
      MotionLayoutComponentHelper helper = MotionLayoutComponentHelper.create(mySelectedComponent);
      if (helper.getPositionKeyframe(myKeyframe, view, fx, fy, myPositionAttributes, myPositionsValues)) {
        helper.setKeyframe(myKeyframe, myPositionAttributes[0], myPositionsValues[0]);
        helper.setKeyframe(myKeyframe, myPositionAttributes[1], myPositionsValues[1]);
        helper.setKeyframe(myKeyframe, "drawPath", 0);

        NlComponent motionLayoutComponent = MotionUtils.getMotionLayoutAncestor(mySelectedComponent);
        if (motionLayoutComponent != null) {
          MTag motionScene = MotionUtils.getMotionScene(motionLayoutComponent);
          MTag[] transitions = motionScene.getChildTags("Transition");
          String startState = helper.getStartState();
          String endState = helper.getEndState();
          MTag[] keyframes = null;
          if (startState != null && endState != null) {
            for (int i = 0; i < transitions.length; i++) {
              MTag transition = transitions[i];
              String transitionStart = transition.getAttributeValue("constraintSetStart");
              String transitionEnd = transition.getAttributeValue("constraintSetEnd");
              if (startState.equals(MotionSceneModel.stripID(transitionStart))
                  && endState.equals(MotionSceneModel.stripID(transitionEnd))) {
                // we found the correct transition
                MTag[] keyframesSet = transition.getChildTags("KeyFrameSet");
                if (keyframesSet != null && keyframesSet.length > 0) {
                  keyframes = keyframesSet[0].getChildTags("KeyPosition");
                  break;
                }
              }
            }
            if (keyframes != null) {
              for (int i = 0; i < keyframes.length; i++) {
                MTag keyframe = keyframes[i];
                String framePositionValue = keyframe.getAttributeValue("framePosition");
                int framePosition = framePositionValue != null ? Integer.parseInt(framePositionValue) : -1;
                if (framePosition == myKeyframePosition) {
                  String motionTarget = keyframe.getAttributeValue("motionTarget");
                  if (motionTarget != null && selectedId.equals(MotionSceneModel.stripID(motionTarget))) {
                    // we are on the correct keyframe
                    MTag.TagWriter keyframeModification = keyframe.getTagWriter();
                    keyframeModification.setAttribute(SdkConstants.SHERPA_URI,
                                                      myPositionAttributes[0], formatValue(myPositionsValues[0]));
                    keyframeModification.setAttribute(SdkConstants.SHERPA_URI,
                                                      myPositionAttributes[1], formatValue(myPositionsValues[1]));
                    keyframeModification.commit("Modified Position Keyframe");
                  }
                }
              }
            }
          }
        }

        mySelectedComponent.getModel().notifyLiveUpdate(false);
      }
    }
    myKeyframe = null;
  }

  private String formatValue(float value) {
    return new DecimalFormat("#.###").format(value);
  }
}
