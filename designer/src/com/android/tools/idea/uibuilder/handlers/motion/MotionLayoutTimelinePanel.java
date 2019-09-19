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

import static com.android.tools.idea.uibuilder.handlers.motion.timeline.MotionSceneModel.stripID;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.common.model.ModelListener;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlComponentDelegate;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface;
import com.android.tools.idea.uibuilder.api.AccessorySelectionListener;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionDesignSurfaceEdits;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionLayoutInterface;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.Gantt;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.GanttCommands;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.GanttEventListener;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.MotionSceneModel;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.surface.AccessoryPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Timeline Accessory Panel for MotionLayout editing
 */
class MotionLayoutTimelinePanel implements AccessoryPanelInterface, GanttEventListener, ModelListener,
                                           MotionLayoutInterface, MotionDesignSurfaceEdits {
  public static final boolean DEBUG = false;

  private final ViewGroupHandler.AccessoryPanelVisibility myVisibilityCallback;
  private final DesignSurface mySurface;
  private final List<AccessorySelectionListener> myListeners;
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
  private NlModel myModel;
  private MotionSceneModel myMotionSceneModel;
  private Object myLastSelectedAccessory = new Object();

  public State getCurrentState() {
    return myCurrentState;
  }

  @Override
  public boolean showPopupMenuActions() {
    return (myCurrentState == State.TL_START || myCurrentState == State.TL_END);
  }

  public void setTimelineProgress(float progress) {
    myPanel.setProgress(progress);
  }

  @Override
  public boolean handlesWriteForComponent(String id) {
    SmartPsiElementPointer<XmlTag> constraint = getSelectedConstraint();
    if (constraint != null) {
      String constraintId = constraint.getElement().getAttribute("android:id").getValue();
      return id.equals(stripID(constraintId));
    }
    return false;
  }

  enum State {TL_UNKNOWN, TL_START, TL_PLAY, TL_PAUSE, TL_TRANSITION, TL_END}

  private State myCurrentState = State.TL_UNKNOWN;

  public static final String TIMELINE = "Timeline";

  public MotionLayoutTimelinePanel(@NotNull DesignSurface surface,
                                   @NotNull NlComponent parent,
                                   @NotNull ViewGroupHandler.AccessoryPanelVisibility visibility) {
    mySurface = surface;
    myVisibilityCallback = visibility;
    myListeners = new ArrayList<>();

    myMotionLayoutComponentHelper = new MotionLayoutComponentHelper(parent);
    parent.putClientProperty(TIMELINE, this);
    updateModel(parent.getModel());
    mySurface.getSelectionModel().addListener((model, selection) -> handleSelectionChanged(model, selection));
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
    return mySelection != null ? myPanel.getSelectedKey(mySelection.getId()) : null;
  }

  public MotionSceneModel.ConstraintView getSelectedConstraintView() {
    return mySelection != null ? myPanel.getSelectedConstraintView(mySelection.getId()) : null;
  }

  @Override
  public SmartPsiElementPointer<XmlTag> getSelectedConstraint() {
    return myPanel.getChart().getSelectedConstraint();
  }

  @Override
  public String getSelectedConstraintSet() {
    return null;
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

  private void handleSelectionChanged(@NotNull SelectionModel model, @NotNull List<NlComponent> selection) {
    if (myPanel != null) {
      myPanel.handleSelectionChanged(model, selection);
    }
  }

  @Override
  public void updateAccessoryPanelWithSelection(@NotNull AccessoryPanel.Type type,
                                                @NotNull List<NlComponent> selection) {
    myCurrentState = State.TL_UNKNOWN;

    if (selection.isEmpty()) {
      mySelection = null;
      return;
    }

    NlComponent component = selection.get(0);

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

//    if (getSelectedAccessory() == null) {
      fireSelectionChanged(selection);
//    }
  }

  public void updateState() {
    if (myCurrentState == State.TL_UNKNOWN) {
      float position = myPanel.getChart().getProgress();

      SceneComponent root = mySurface.getScene().getRoot();
      if (root != null) {
        root.updateTargets();
      }
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
        setState(State.TL_START);
        setProgress(0);
        break;
      case PLAY:
        setState(State.TL_PLAY);
        myDirectionBackward = false;
        break;
      case PAUSE:
        setState(State.TL_PAUSE);
        myDirectionBackward = false;
        break;
      case TRANSITION:
        float position = myPanel.getChart().getProgress();
        setProgress(position);
        break;
      case END:
        setState(State.TL_END);
        setProgress(1);
        break;
      default:
    }
    MotionSceneModel.KeyFrame keyFrame = getSelectedKeyframe();
    if (keyFrame != null) {
      setProgress(keyFrame.getFramePosition()/100f);
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

    List<VirtualFile> resourceFolders = ResourceFolderManager.getInstance(facet).getFolders();
    List<VirtualFile> resourcesXML = AndroidResourceUtil.getResourceSubdirs(ResourceFolderType.XML, resourceFolders);
    if (resourcesXML.isEmpty()) {
      return;
    }
    VirtualFile directory = resourcesXML.get(0);
    VirtualFile virtualFile = directory.findFileByRelativePath(fileName + ".xml");

    XmlFile xmlFile = (XmlFile)AndroidPsiUtils.getPsiFileSafely(project, virtualFile);

    MotionSceneModel motionSceneModel = MotionSceneModel.parse(component, project, virtualFile, xmlFile);
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
    // Move the handling onto the event dispatch thread in case this notification is sent from a different thread:
    ApplicationManager.getApplication().invokeLater(() -> {
      loadMotionScene();
      if (myMotionLayoutAttributePanel != null) {
        // make sure this happens after our update.
        myMotionLayoutAttributePanel.updateAfterModelDerivedDataChanged();
      }
    });
  }

  @Override
  public void setProgress(float percent) {
    if (!myMotionLayoutComponentHelper.setProgress(percent)) {
      myMotionLayoutComponentHelper = new MotionLayoutComponentHelper(myMotionLayout);
    }
    fireSelectionChanged();
  }

  private void setState(State state) {
    if (myCurrentState == state || myInStateChange) {
      return;
    }
    myInStateChange = true;
    if (DEBUG) {
      System.out.println("=========== MotionLayoutTimelinePanel. setState("+state.name()+")");
    }
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
    if (myCurrentState == State.TL_PLAY) {
      if (myPositionTimer != null) {
        myPositionTimer.stop();
      }
    }
  }

  @Override
  @Nullable
  public Object getSelectedAccessory() {
    if (DEBUG) {
      Debug.println("getSelectedAccessory ");
    }

    MotionSceneModel.KeyFrame keyframe = getSelectedKeyframe();
    if (keyframe != null) {
      return keyframe.getTag();
    }
    MotionSceneModel.ConstraintView cv = getSelectedConstraintView();
    if (cv != null) {
      return cv.getTag();
    }
    return null;
  }

  @Override
  @Nullable
  public Object getSelectedAccessoryType() {
    return null;
  }

  @Override
  public void addListener(@NotNull AccessorySelectionListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeListener(@NotNull AccessorySelectionListener listener) {
    myListeners.remove(listener);
  }

  private void fireSelectionChanged(@NotNull List<NlComponent> components) {
    List<AccessorySelectionListener> copy = new ArrayList<>(myListeners);
    copy.forEach(listener -> listener.selectionChanged(this, components));
  }

  private void fireSelectionChanged() {
    Object newValue = getSelectedAccessory();
    if (myLastSelectedAccessory == newValue) {
      // Avoid sending change requests when there are no changes.
      return;
    }
    List<NlComponent> selection = mySelection != null ? Collections.singletonList(mySelection) : Collections.emptyList();
    fireSelectionChanged(selection);
    myLastSelectedAccessory = newValue;
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
        if (myCurrentState == State.TL_PAUSE) {
          setState(State.TL_PLAY);
        }
        break;
      case PLAY_ACTION:
      case SLOW_MOTION:
        if (myCurrentState == State.TL_PLAY) {
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
    String selectedElementName = myPanel.getChart().getSelectedKeyView();
    if (selectedElementName != null && myMotionLayout != null) {
      List<NlComponent> selection = getSelectionFrom(myMotionLayout, selectedElementName);
      SwingUtilities.invokeLater(() ->
      mySurface.getSelectionModel().setSelection(selection));
    }

    SmartPsiElementPointer<XmlTag> constraint = myPanel.getChart().getSelectedConstraint();
    if (constraint != null) {
      XmlTag constraintSet = constraint.getElement().getParentTag();
      String id = stripID(constraintSet.getAttributeValue("id", SdkConstants.ANDROID_URI));
      if (id != null) {
        if (id.equalsIgnoreCase("start")) {
          setState(State.TL_START);
        } else if (id.equalsIgnoreCase("end")) {
          setState(State.TL_END);
        }
      }
    }
  }

  private List<NlComponent> getSelectionFrom(@NotNull NlComponent component, @NotNull String id) {
    if (id.equals(component.getId())) {
      return Collections.singletonList(component);
    }
    for (NlComponent child : component.getChildren()) {
      if (id.equals(child.getId())) {
        return Collections.singletonList(child);
      }
    }
    return Collections.emptyList();
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
  public void setKeyframeAttribute(@NotNull AttrName attributeName, float value) {
    MotionSceneModel.KeyFrame keyFrame = myPanel.getChart().getSelectedKeyFrame();
    if (keyFrame != null) {
      keyFrame.setValue(attributeName, Float.toString(value));
    }
  }

  /**
   * Set multiple values atomically on the currently selected keyframe
   * @param model
   * @param values
   */
  public void setKeyframeAttributes(@NotNull HashMap<AttrName, String> values) {
    MotionSceneModel.KeyFrame keyFrame = myPanel.getChart().getSelectedKeyFrame();
    if (keyFrame != null) {
      keyFrame.setValues(values);
    }
  }

  // TODO: merge with the above parse function
  @Override
  @Nullable
  public XmlFile getTransitionFile(@NotNull NlComponent component) {
    // get the parent if need be
    if (!NlComponentHelperKt.isOrHasSuperclass(component, SdkConstants.MOTION_LAYOUT)) {
      component = component.getParent();
      if (component == null || !NlComponentHelperKt.isOrHasSuperclass(component, SdkConstants.MOTION_LAYOUT)) {
        return null;
      }
    }
    String file = component.getAttribute(SdkConstants.AUTO_URI, "layoutDescription");
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
    List<VirtualFile> resourceFolders = ResourceFolderManager.getInstance(facet).getFolders();
    List<VirtualFile> resourcesXML = AndroidResourceUtil.getResourceSubdirs(ResourceFolderType.XML, resourceFolders);
    if (resourcesXML.isEmpty()) {
      return null;
    }
    VirtualFile directory = resourcesXML.get(0);
    VirtualFile virtualFile = directory.findFileByRelativePath(fileName + ".xml");

    return (XmlFile)AndroidPsiUtils.getPsiFileSafely(project, virtualFile);
  }

  @Override
  @Nullable
  public List<XmlTag> getKeyframes(XmlFile file, String componentId) {
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

  @Override
  @Nullable
  public XmlTag getConstraintSet(XmlFile file, String constraintSetId) {
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

  @Override
  @Nullable
  public XmlTag getConstrainView(XmlTag constraintSet, String id) {
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
}
