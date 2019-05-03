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
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.common.model.ModelListener;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlComponentDelegate;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.handlers.motion.property2.TimelineListener;
import com.android.tools.idea.uibuilder.handlers.motion.property2.TimelineOwner;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.Gantt;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.GanttCommands;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.GanttEventListener;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.MotionSceneModel;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.surface.AccessoryPanel;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static com.android.tools.idea.uibuilder.handlers.motion.MotionLayoutTimelinePanel.State.*;

/**
 * The Timeline Accessory Panel for MotionLayout editing
 */
class MotionLayoutTimelinePanel implements AccessoryPanelInterface, GanttEventListener, ModelListener, TimelineOwner {
  public static final boolean DEBUG = false;

  private final ViewGroupHandler.AccessoryPanelVisibility myVisibilityCallback;
  private final DesignSurface mySurface;
  private final List<TimelineListener> myTimelineListeners;
  private NlComponent myMotionLayout;
  private MotionLayoutComponentHelper myMotionLayoutComponentHelper;
  private Gantt myPanel;
  private GanttCommands mGanttCommands;
  private Timer myPositionTimer;
  public static boolean myLoopMode;
  public static boolean myDirectionBackward;
  private float myLastPos;
  private NlComponent mySelection;
  MotionLayoutAttributePanel myMotionLayoutAttributePanel;
  private boolean myInStateChange;
  private NlComponentDelegate myNlComponentDelegate = new MotionLayoutComponentDelegate(this);
  private NlModel myModel;
  private MotionSceneModel myMotionSceneModel;

  public State getCurrentState() {
    return myCurrentState;
  }

  public NlComponentDelegate getNlComponentDelegate() {
    return myNlComponentDelegate;
  }

  public void setTimelineProgress(float progress) {
    myPanel.setProgress(progress);
  }

  enum State {TL_UNKNOWN, TL_START, TL_PLAY, TL_PAUSE, TL_TRANSITION, TL_END}

  private State myCurrentState = TL_UNKNOWN;

  public static final String TIMELINE = TimelineOwner.TIMELINE_PROPERTY;

  public MotionLayoutTimelinePanel(@NotNull DesignSurface surface,
                                   @NotNull NlComponent parent,
                                   @NotNull ViewGroupHandler.AccessoryPanelVisibility visibility) {
    mySurface = surface;
    myVisibilityCallback = visibility;
    myTimelineListeners = new ArrayList<>();

    myMotionLayoutComponentHelper = new MotionLayoutComponentHelper(parent);
    parent.putClientProperty(TIMELINE, this);
    updateModel(parent.getModel());
  }

  @Override
  @NotNull
  public JPanel getPanel() {
    if (myPanel == null) {
      myPanel = (Gantt)createPanel(AccessoryPanel.Type.SOUTH_PANEL);
    }
    return myPanel;
  }

  @Override
  @NotNull
  public JPanel createPanel(AccessoryPanel.Type type) {
    JPanel panel = new Gantt(this);
    panel.setPreferredSize(new Dimension(0, 300));
    return panel;
  }

  @Nullable
  public MotionSceneModel.KeyFrame getSelectedKeyframe() {
    return myPanel.getSelectedKey(mySelection.getId());
  }

  public void clearSelectedKeyframe() {
    myPanel.clearSelectedKey();
  }

  @Nullable
  public MotionSceneModel.TransitionTag getTransitionTag() {
    return myPanel.getTransitionTag();
  }

  @Nullable
  public MotionSceneModel.OnSwipeTag getOnSwipeTag() {
    return myPanel.getOnSwipeTag();
  }

  /**
   * Update our current model
   * @param model
   */
  private void updateModel(@Nullable NlModel model) {
    if (myModel == model) {
      return;
    }
    if (myModel != null) {
      myModel.removeListener(this);
    }
    myModel = model;
    if (myModel != null) {
      myModel.addListener(this);
    }
  }

  @Override
  public void updateAccessoryPanelWithSelection(@NotNull AccessoryPanel.Type type,
                                                @NotNull List<NlComponent> selection) {
    myCurrentState = TL_UNKNOWN;

    if (selection.isEmpty()) {
      mySelection = null;
      return;
    }

    NlComponent component = selection.get(0);
    if (component != mySelection) {
      myNlComponentDelegate.clearCaches();
    }

    mySelection = component;
    if (!NlComponentHelperKt.isOrHasSuperclass(component, SdkConstants.CLASS_MOTION_LAYOUT)) {
      component = component.getParent();
      if (component != null && !NlComponentHelperKt.isOrHasSuperclass(component, SdkConstants.CLASS_MOTION_LAYOUT)) {
        return; // not found
      }
    }
    // component is a motion layout
    if (myMotionLayout != component) {
      myMotionLayout = component;
      updateModel(myMotionLayout != null ? myMotionLayout.getModel() : null);
      loadMotionScene();
    }
    updateState();
    addDelegate();
  }

  public void updateState() {
    if (myCurrentState == State.TL_UNKNOWN) {
      float position = myPanel.getChart().getProgress();
      if (position == 0) {
        setState(TL_START);
      } else if (position == 1) {
        setState(TL_END);
      } else {
        setState(TL_UNKNOWN);
      }
      SceneComponent root = mySurface.getScene().getRoot();
      if (root != null) {
        root.updateTargets();
      }
    }
  }

  private void addDelegate() {
    if (myMotionLayout == null) {
      return;
    }
    myMotionLayout.setDelegate(myNlComponentDelegate);
    for (NlComponent child : myMotionLayout.getChildren()) {
      child.setDelegate(myNlComponentDelegate);
    }
  }

  private void loadMotionScene() {
    if (myMotionLayout == null) {
      return;
    }

    String referencedFile =
      myMotionLayout.getAttribute(SdkConstants.AUTO_URI, "layoutDescription"); // TODO SdkConstants.ATTR_MOTION_SCENE_REFERENCE);
    if (referencedFile != null) {
      parseMotionScene(myMotionLayout, referencedFile);
    }

    myMotionLayoutComponentHelper = new MotionLayoutComponentHelper(myMotionLayout);
    switch (myPanel.getMode()) {
      case START:
        setState(TL_START);
        setProgress(0);
        break;
      case PLAY:
        setState(TL_PLAY);
        myDirectionBackward = false;
        break;
      case PAUSE:
        setState(TL_PAUSE);
        myDirectionBackward = false;
        break;
      case TRANSITION:
        float position = myPanel.getChart().getProgress();
        setProgress(position);
        break;
      case END:
        setState(TL_END);
        setProgress(1);
        break;
      case UNKNOWN:
        setState(TL_START);
        setProgress(0);
        break;
      default:
    }
  }

  private void parseMotionScene(@NotNull NlComponent component, @NotNull String file) {
    if (DEBUG) {
      System.out.println("====================================================================================");
      System.out.println(" parseMotionScene  ");
    }
    if (file == null) {
      return;
    }
    int index = file.lastIndexOf("@xml/");
    String fileName = file.substring(index + 5);
    if (fileName == null || fileName.isEmpty()) {
      return;
    }

    // let's open the file
    Project project = component.getModel().getProject();
    AndroidFacet facet = component.getModel().getFacet();

    List<VirtualFile> resourcesXML = AndroidResourceUtil.getResourceSubdirs(ResourceFolderType.XML, facet.getAllResourceDirectories());
    if (resourcesXML.isEmpty()) {
      return;
    }
    VirtualFile directory = resourcesXML.get(0);
    VirtualFile virtualFile = directory.findFileByRelativePath(fileName + ".xml");

    XmlFile xmlFile = (XmlFile)AndroidPsiUtils.getPsiFileSafely(project, virtualFile);

    MotionSceneModel motionSceneModel = MotionSceneModel.parse(component.getModel(), project, virtualFile, xmlFile);
    myPanel.setMotionScene(motionSceneModel);
    myMotionSceneModel = motionSceneModel;

  }

  public MotionSceneModel getModel() {
    return myMotionSceneModel;
  }

  @Override
  public void deactivate() {
    myVisibilityCallback.show(AccessoryPanel.Type.EAST_PANEL, false);
    stopPlaying();
    updateModel(null);
    myMotionLayout = null;
  }

  @Override
  public void modelDerivedDataChanged(@NotNull NlModel model) {
    updateAfterModelDerivedDataChanged();
  }

  @Override
  public void updateAfterModelDerivedDataChanged() {
    loadMotionScene();
    if (myMotionLayoutAttributePanel != null) {
      // make sure this happens after our update.
      myMotionLayoutAttributePanel.updateAfterModelDerivedDataChanged();
    }
    myTimelineListeners.forEach(listener -> listener.updateSelection(getSelectedKeyframe()));
  }

  @Override
  public void setProgress(float percent) {
    if (!myMotionLayoutComponentHelper.setProgress(percent)) {
      myMotionLayoutComponentHelper = new MotionLayoutComponentHelper(myMotionLayout);
    }
    if (myCurrentState != TL_PLAY) {
      if (percent == 0) {
        setState(TL_START);
      }
      else if (percent == 1) {
        setState(TL_END);
      }
      else {
        setState(TL_TRANSITION);
      }
    }
  }

  private void setState(State state) {
    if (myCurrentState == state || myInStateChange) {
      return;
    }
    myInStateChange = true;

    switch (state) {
      case TL_START:
        mGanttCommands.setMode(GanttCommands.Mode.START);
        stopPlaying();
        myVisibilityCallback.show(AccessoryPanel.Type.EAST_PANEL, false);
        mGanttCommands.setProgress(0);
        break;
      case TL_END:
        mGanttCommands.setMode(GanttCommands.Mode.END);
        stopPlaying();
        myVisibilityCallback.show(AccessoryPanel.Type.EAST_PANEL, false);
        mGanttCommands.setProgress(1);
        break;
      case TL_PLAY:
        mGanttCommands.setMode(GanttCommands.Mode.PLAY);
        myVisibilityCallback.show(AccessoryPanel.Type.EAST_PANEL, true);
        startPlaying();
        break;
      case TL_PAUSE:
        stopPlaying();
        mGanttCommands.setMode(GanttCommands.Mode.PAUSE);
        break;
      case TL_TRANSITION:
        stopPlaying();
        mGanttCommands.setMode(GanttCommands.Mode.TRANSITION);
        myVisibilityCallback.show(AccessoryPanel.Type.EAST_PANEL, true);
        break;
      case TL_UNKNOWN:
        stopPlaying();
    }
    myCurrentState = state;
    myInStateChange = false;
    SceneComponent root = mySurface.getScene().getRoot();
    if (root != null) {
      root.updateTargets();
    }
  }

  private void startPlaying() {
    final int timer_delay_in_ms = 16;
    if (myPositionTimer != null) {
      myPositionTimer.stop();
    }
    myPositionTimer = new Timer(0, e -> {

      float increment = timer_delay_in_ms / 1000.f;
      if (myPanel != null) {
        float speedMultiplier = myPanel.getChart().getPlayBackSpeed();
        float timeMs = myPanel.getChart().getAnimationTimeInMs();
        increment = speedMultiplier * timer_delay_in_ms / timeMs;
      }
      float value = myLastPos + increment;
      if (myDirectionBackward) {
        value = myLastPos - increment;
        if (value < 0) {
          if (myLoopMode) {
            value = 0;
            myDirectionBackward = false;
          }
          else {
            value = 100;
          }
        }
      }
      else {
        if (value > 1) {
          if (myLoopMode) {
            value = 1;
            myDirectionBackward = true;
          }
          else {
            value = 0;
          }
        }
      }
      myLastPos = value;
      if (!myMotionLayoutComponentHelper.setProgress(value)) {
        myMotionLayoutComponentHelper = new MotionLayoutComponentHelper(myMotionLayout);
      }
      if (mGanttCommands != null) {
        mGanttCommands.setProgress(value);
      }
    });
    myPositionTimer.setRepeats(true);
    myPositionTimer.setDelay(timer_delay_in_ms);
    myPositionTimer.start();
  }

  private void stopPlaying() {
    if (myCurrentState == TL_PLAY) {
      if (myPositionTimer != null) {
        myPositionTimer.stop();
      }
    }
  }

  @Override
  public void buttonPressed(ActionEvent e, Actions action) {
    switch (action) {
      case START_ACTION:
        setState(State.TL_START);
        break;
      case END_ACTION:
        setState(State.TL_END);
        break;
      case LOOP_ACTION:
        myLoopMode = ! myLoopMode;
        myDirectionBackward = false;
        if (myCurrentState == TL_PAUSE) {
          setState(State.TL_PLAY);
        }
        break;
      case PLAY_ACTION:
      case SLOW_MOTION:
        if (myCurrentState == TL_PLAY) {
          setState(State.TL_PAUSE);
        }
        else {
          setState(State.TL_PLAY);
        }
        break;
      default:
    }
  }

  @Override
  public void selectionEvent() {
    if (myMotionLayoutAttributePanel != null) {
      myMotionLayoutAttributePanel.updateSelection();
    }
    myTimelineListeners.forEach(listener -> listener.updateSelection(getSelectedKeyframe()));
    String selectedElementName = myPanel.getChart().getSelectedKeyView();
    if (selectedElementName != null) {
      List<NlComponent> selection = getSelectionFrom(myMotionLayout, selectedElementName);
      mySurface.getSelectionModel().setSelection(selection);
    }
  }

  private List<NlComponent> getSelectionFrom(@NotNull NlComponent component, @NotNull String id) {
    if (component.getId().equals(id)) {
      return Collections.singletonList(component);
    }
    for (NlComponent child : component.getChildren()) {
      if (child.getId().equals(id)) {
        return Collections.singletonList(child);
      }
    }
    return new ArrayList<>();
  }

  @Override
  public void transitionDuration(int duration) {
    // TODO set in transition
  }

  @Override
  public void motionLayoutAccess(int cmd, String type, float[] in, int inLength, float[] out, int outLength) {
    myMotionLayoutComponentHelper.motionLayoutAccess(cmd, type, null, in, inLength, out, outLength);
  }

  @Override
  public void onInit(GanttCommands commands) {
    mGanttCommands = commands;
  }

  /**
   * Set the value of the attribute on the currently selected keyframe
   * @param model
   * @param attributeName
   * @param value
   */
  public void setKeyframeAttribute(@NotNull String attributeName, float value) {
    MotionSceneModel.KeyFrame keyFrame = myPanel.getChart().getSelectedKeyFrame();
    keyFrame.setValue(attributeName, Float.toString(value));
  }

  /**
   * Set multiple values atomically on the currently selected keyframe
   * @param model
   * @param values
   */
  public void setKeyframeAttributes(@NotNull HashMap<String, String> values) {
    MotionSceneModel.KeyFrame keyFrame = myPanel.getChart().getSelectedKeyFrame();
    keyFrame.setValues(values);
  }

  // TODO: merge with the above parse function
  @Nullable
  XmlFile getTransitionFile(@NotNull NlComponent component) {
    // get the parent if need be
    if (!NlComponentHelperKt.isOrHasSuperclass(component, SdkConstants.MOTION_LAYOUT)) {
      component = component.getParent();
      if (component == null || !NlComponentHelperKt.isOrHasSuperclass(component, SdkConstants.MOTION_LAYOUT)) {
        return null;
      }
    }
    String file = component.getAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_TRANSITION);
    if (file == null) {
      return null;
    }
    int index = file.lastIndexOf("@xml/");
    String fileName = file.substring(index + 5);
    if (fileName == null || fileName.isEmpty()) {
      return null;
    }
    Project project = component.getModel().getProject();
    AndroidFacet facet = component.getModel().getFacet();
    List<VirtualFile> resourcesXML = AndroidResourceUtil.getResourceSubdirs(ResourceFolderType.XML, facet.getAllResourceDirectories());
    if (resourcesXML.isEmpty()) {
      return null;
    }
    VirtualFile directory = resourcesXML.get(0);
    VirtualFile virtualFile = directory.findFileByRelativePath(fileName + ".xml");

    return (XmlFile)AndroidPsiUtils.getPsiFileSafely(project, virtualFile);
  }

  @Nullable
  List<XmlTag> getKeyframes(XmlFile file, String componentId) {
    XmlTag[] children = file.getRootTag().findSubTags("KeyFrames");
    List<XmlTag> found = new ArrayList();
    for (int i = 0; i < children.length; i++) {
      XmlTag[] keyframes = children[i].getSubTags();
      for (int j = 0; j < keyframes.length; j++) {
        XmlTag keyframe = keyframes[j];
        XmlAttribute attribute = keyframe.getAttribute("motion:target");
        if (attribute != null) {
          String keyframeTarget = attribute.getValue();
          int index = keyframeTarget.indexOf('/');
          if (index != -1) {
            keyframeTarget = keyframeTarget.substring(index + 1);
          }
          if (componentId.equalsIgnoreCase(keyframeTarget)) {
            found.add(keyframe);
          }
        }
      }
    }
    return found;
  }

  @Nullable
  XmlTag getConstraintSet(XmlFile file, String constraintSetId) {
    XmlTag[] children = file.getRootTag().findSubTags("ConstraintSet");
    for (int i = 0; i < children.length; i++) {
      XmlAttribute attribute = children[i].getAttribute("android:id");
      if (attribute != null) {
        if (attribute.getValue().equalsIgnoreCase(constraintSetId)) {
          return children[i];
        }
      }
    }
    return null;
  }

  @Nullable
  XmlTag getConstrainView(XmlTag constraintSet, String id) {
    XmlTag[] children = constraintSet.getSubTags();
    for (int i = 0; i < children.length; i++) {
      XmlAttribute attribute = children[i].getAttribute("android:id");
      if (attribute != null) {
        String value = attribute.getValue();
        int index = value.lastIndexOf("id/");
        value = value.substring(index + 3);
        if (value != null && value.equalsIgnoreCase(id)) {
          return children[i];
        }
      }
    }
    return null;
  }

  public void setInTransition(@NotNull NlComponent component, boolean inTransition) {
    if (!NlComponentHelperKt.isOrHasSuperclass(component, SdkConstants.MOTION_LAYOUT)) {
      component = component.getParent();
      if (component == null || !NlComponentHelperKt.isOrHasSuperclass(component, SdkConstants.MOTION_LAYOUT)) {
        return;
      }
    }
    NlModel myModel = component.getModel();
    NlComponent finalComponent = component;
    new WriteCommandAction(myModel.getProject(), "Set In Transition", myModel.getFile()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        finalComponent.setAttribute(SdkConstants.AUTO_URI, "applyTransition", Boolean.toString(inTransition));
      }
    }.execute();
    NlModel model = component.getModel();
    model.notifyModified(NlModel.ChangeType.EDIT);
  }

  public void setMotionLayoutAttributePanel(MotionLayoutAttributePanel panel) {
    myMotionLayoutAttributePanel = panel;
  }

  @Override
  public void addTimelineListener(@NotNull TimelineListener listener) {
    myTimelineListeners.add(listener);
  }

  @Override
  public void removeTimeLineListener(@NotNull TimelineListener listener) {
    myTimelineListeners.remove(listener);
  }
}
