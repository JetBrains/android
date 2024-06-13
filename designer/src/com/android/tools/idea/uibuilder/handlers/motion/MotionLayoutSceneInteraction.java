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
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.Utils;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
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
  private NlComponent mySelectedComponent;
  public static final String MOTION_DRAG_KEYFRAME  = "MotionDragKeyFrame";
  public static final String MOTION_KEY_POS_TYPE  = "MotionDragKeyPosType";
  public static final String MOTION_KEY_POS_PERCENT  = "MotionDragKeyPosPercent";

  private KeyframeCandidate myKeyframeCandidate = new KeyframeCandidate();

  private final static int MAX_KEY_POSITIONS = 101; // 0-100 inclusive key positions are allowed
  private String[] myPositionAttributes = new String[2];
  private float[] myPositionsValues = new float[2];
  private final DecimalFormat myFloatFormatter = createFloatFormatter();

  private static class KeyframeCandidate {
    Object keyframe;
    int position = -1;
    int[] keyFrameTypes = new int[MAX_KEY_POSITIONS];
    float[] keyFramePos = new float[MAX_KEY_POSITIONS * 2];
    int keyPosType = 0;
    float keyPosPercentX;
    float keyPosPercentY;
    int selectedKeyFrame;

    public void clear() { keyframe = null; position = -1; }
    int[] keyFrameInfo = new int[MAX_KEY_POSITIONS*8];
    MotionLayoutComponentHelper.KeyInfo myKeyInfo = new MotionLayoutComponentHelper.KeyInfo();

  }

  /**
   * Base constructor
   *  @param sceneView the ScreenView we belong to
   * @param primary
   * @param component
   */
  public MotionLayoutSceneInteraction(@NotNull SceneView sceneView,
                                      @NotNull NlComponent primary) {
    super(sceneView, primary);
    mySelectedComponent = primary; //sceneView.getSelectionModel().getPrimary();
    mySurface = (NlDesignSurface)sceneView.getSurface();
  }

  /**
   * On the start of the interaction, we try to find if there's a keyframe close
   * to the mouse down location.
   *
   * @param x           The most recent mouse x coordinate applicable to this interaction
   * @param y           The most recent mouse y coordinate applicable to this interaction
   * @param modifiersEx The initial AWT mask for the interaction
   */
  @Override
  public void begin(@SwingCoordinate int x, @SwingCoordinate int y,
                    @JdkConstants.InputEventMask int modifiersEx) {
    MotionLayoutComponentHelper helper = MotionLayoutComponentHelper.create(mySelectedComponent);
    float fx = Coordinates.getAndroidX(mySceneView, x);
    float fy = Coordinates.getAndroidY(mySceneView, y);

    if (hitKeyframe(helper, mySelectedComponent, fx, fy, myKeyframeCandidate)) {
      mySelectedComponent.putClientProperty(MOTION_DRAG_KEYFRAME, myKeyframeCandidate.selectedKeyFrame);
      mySelectedComponent.putClientProperty(MOTION_KEY_POS_TYPE, myKeyframeCandidate.keyPosType);
      mySelectedComponent.putClientProperty(MOTION_KEY_POS_PERCENT, new float[]{myKeyframeCandidate.keyPosPercentX,myKeyframeCandidate.keyPosPercentY});

      mySurface.setRenderSynchronously(true);
      mySurface.setAnimationScrubbing(true);
    }
  }

  /**
   * Returns true if we hit a keyframe of the component at position (fx, fy) and set the KeyFrame and KeyFrame position
   *
   * @param helper
   * @param component
   * @param fx
   * @param fy
   * @param keyframeCandidate
   * @return
   */
  public static boolean hitKeyframe(@NotNull MotionLayoutComponentHelper helper, @NotNull NlComponent component,
                                    float fx, float fy, @NotNull KeyframeCandidate keyframeCandidate) {

    keyframeCandidate.clear();

    try {
      int noOfKeyPosition = helper.getKeyframeInfo(component, MotionLayoutComponentHelper.KeyInfo.KEY_TYPE_POSITION, keyframeCandidate.keyFrameInfo);
      keyframeCandidate.myKeyInfo.setInfo(keyframeCandidate.keyFrameInfo, noOfKeyPosition);
      while(keyframeCandidate.myKeyInfo.next()) {
        if (keyframeCandidate.myKeyInfo.getType() == MotionLayoutComponentHelper.KeyInfo.KEY_TYPE_POSITION) {
          float x = keyframeCandidate.myKeyInfo.getLocationX();
          float y = keyframeCandidate.myKeyInfo.getLocationY();
          double dx = Math.abs(x - fx);
          double dy = Math.abs(y - fy);
          if (dx < SLOPE && dy < SLOPE) {

            keyframeCandidate.selectedKeyFrame =  keyframeCandidate.myKeyInfo.getIndex();
            keyframeCandidate.keyPosType =  keyframeCandidate.myKeyInfo.getKeyPosType();
            keyframeCandidate.keyPosPercentX =  keyframeCandidate.myKeyInfo.getKeyPosPercentX();
            keyframeCandidate.keyPosPercentY =  keyframeCandidate.myKeyInfo.getKeyPosPercentY();

            break;
          }

        }
      }

    } catch (Exception ex) {

    }
    int keyFrameCount = helper.getKeyframePos(component, keyframeCandidate.keyFrameTypes, keyframeCandidate.keyFramePos);

    if (keyFrameCount <= 0) {
      return false;
    }

    Object view = NlComponentHelperKt.getViewInfo(component).getViewObject();
    for (int i = 0; i < keyframeCandidate.keyFrameTypes.length; i++) {
      if (keyframeCandidate.keyFrameTypes[i] / 1000 == 2) {
        float kx = keyframeCandidate.keyFramePos[i * 2];
        float ky = keyframeCandidate.keyFramePos[i * 2 + 1];
        double dx = Math.sqrt((kx - fx) * (kx - fx));
        double dy = Math.sqrt((ky - fy) * (ky - fy));
        if (dx < SLOPE && dy < SLOPE) {
          int framePosition = (keyframeCandidate.keyFrameTypes[i])%1000;
          keyframeCandidate.keyframe = helper.getKeyframe(view, 2, framePosition);
          keyframeCandidate.position = framePosition;
          break;
        }
      }
    }

    return keyframeCandidate.keyframe != null;
  }

  /**
   * Returns true if we hit a keyframe of the component at position (x, y)
   *
   * @param sceneView
   * @param x
   * @param y
   * @param helper
   * @param component
   * @return
   */
  public static boolean hitKeyFrame(@NotNull SceneView sceneView, int x, int y, MotionLayoutComponentHelper helper, NlComponent component) {
    float fx = Coordinates.getAndroidX(sceneView, x);
    float fy = Coordinates.getAndroidY(sceneView, y);
    return hitKeyframe(helper, component, fx, fy, new KeyframeCandidate());
  }


  /**
   * On update, we modify the selected keyframe live.
   *
   * @param x           The most recent mouse x coordinate applicable to this interaction
   * @param y           The most recent mouse y coordinate applicable to this interaction
   * @param modifiersEx current modifier key mask
   */
  @Override
  public void update(@SwingCoordinate int x, @SwingCoordinate int y,
                     @JdkConstants.InputEventMask int modifiersEx) {
    if (myKeyframeCandidate.keyframe == null) {
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
      if (helper.getPositionKeyframe(myKeyframeCandidate.keyframe, view, fx, fy, myPositionAttributes, myPositionsValues)) {
        helper.setKeyframe(myKeyframeCandidate.keyframe, myPositionAttributes[0], myPositionsValues[0]);
        helper.setKeyframe(myKeyframeCandidate.keyframe, myPositionAttributes[1], myPositionsValues[1]);
        // tell motion layout to display additional path info
        // TODO: move instead to studio-driven drawing
        helper.setKeyframe(myKeyframeCandidate.keyframe, "drawPath", 4);
        mySelectedComponent.getModel().notifyLiveUpdate(false);
      }
    }
    try {
      MotionLayoutComponentHelper helper = MotionLayoutComponentHelper.create(mySelectedComponent);
      int noOfKeyPosition = helper
        .getKeyframeInfo(mySelectedComponent, MotionLayoutComponentHelper.KeyInfo.KEY_TYPE_POSITION, myKeyframeCandidate.keyFrameInfo);
      myKeyframeCandidate.myKeyInfo.setInfo(myKeyframeCandidate.keyFrameInfo, noOfKeyPosition);
      while (myKeyframeCandidate.myKeyInfo.next()) {
        if (myKeyframeCandidate.myKeyInfo.getType() == MotionLayoutComponentHelper.KeyInfo.KEY_TYPE_POSITION) {
          int index = myKeyframeCandidate.myKeyInfo.getIndex();
          if (index == myKeyframeCandidate.selectedKeyFrame) {
            myKeyframeCandidate.keyPosPercentX = myKeyframeCandidate.myKeyInfo.getKeyPosPercentX();
            myKeyframeCandidate.keyPosPercentY = myKeyframeCandidate.myKeyInfo.getKeyPosPercentY();
            float []pos = (float[]) mySelectedComponent.getClientProperty(MOTION_KEY_POS_PERCENT);
            if (pos != null) {
              pos[0] = myKeyframeCandidate.keyPosPercentX;
              pos[1] = myKeyframeCandidate.keyPosPercentY;
          } else {
              mySelectedComponent.putClientProperty(MOTION_KEY_POS_PERCENT,
                                                    new float[]{myKeyframeCandidate.keyPosPercentX,
                                                      myKeyframeCandidate.keyPosPercentY});
            }

            break;
          }
        }
      }

    } catch (Exception ex) {

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
    if (myKeyframeCandidate.keyframe == null || mySelectedComponent == null) {
      return;
    }
    mySelectedComponent.putClientProperty(MOTION_DRAG_KEYFRAME, null);
    mySelectedComponent.putClientProperty(MOTION_KEY_POS_TYPE, null);
    mySelectedComponent.putClientProperty(MOTION_KEY_POS_PERCENT, null);

    myPositionAttributes[0] = "percentX";
    myPositionAttributes[1] = "percentY";
    ViewInfo info = NlComponentHelperKt.getViewInfo(mySelectedComponent);
    String selectedId = mySelectedComponent.ensureId();
    if (info != null) {
      float fx = Coordinates.getAndroidX(mySceneView, x);
      float fy = Coordinates.getAndroidY(mySceneView, y);
      Object view = info.getViewObject();
      MotionLayoutComponentHelper helper = MotionLayoutComponentHelper.create(mySelectedComponent);
      if (helper.getPositionKeyframe(myKeyframeCandidate.keyframe, view, fx, fy, myPositionAttributes, myPositionsValues)) {
        helper.setKeyframe(myKeyframeCandidate.keyframe, myPositionAttributes[0], myPositionsValues[0]);
        helper.setKeyframe(myKeyframeCandidate.keyframe, myPositionAttributes[1], myPositionsValues[1]);
        helper.setKeyframe(myKeyframeCandidate.keyframe, "drawPath", 0);

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
              if (startState.equals(Utils.stripID(transitionStart))
                  && endState.equals(Utils.stripID(transitionEnd))) {
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
                if (framePosition == myKeyframeCandidate.position) {
                  String motionTarget = keyframe.getAttributeValue("motionTarget");
                  if (motionTarget != null && selectedId.equals(Utils.stripID(motionTarget))) {
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
    myKeyframeCandidate.clear();
  }

  @NotNull
  private static DecimalFormat createFloatFormatter() {
    DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
    return new DecimalFormat("#.###", symbols);
  }

  private String formatValue(float value) {
    return myFloatFormatter.format(value);
  }
}
