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
package com.android.tools.idea.uibuilder.handlers.motion.editor;

import static com.android.tools.idea.uibuilder.handlers.motion.timeline.MotionSceneModel.stripID;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlComponentDelegate;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.res.ResourceNotificationManager;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface;
import com.android.tools.idea.uibuilder.api.AccessorySelectionListener;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.handlers.motion.MotionLayoutComponentDelegate;
import com.android.tools.idea.uibuilder.handlers.motion.MotionLayoutComponentHelper;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditor;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Debug;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.surface.AccessoryPanel;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JPanel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This provides the main MotionEditor Panel and interfaces it with the rest of the system.
 */
public class MotionAccessoryPanel implements AccessoryPanelInterface, MotionLayoutInterface, MotionDesignSurfaceEdits {
  private static final boolean DEBUG = false;
  private static final boolean TEMP_HACK_FORCE_APPLY = true;
  private final Project myProject;
  private final DesignSurface myDesignSurface;
  NlComponentTag myMotionLayoutTag;
  NlComponent myMotionLayoutNlComponent;
  MotionSceneTag myMotionScene;
  HashSet<String> myLayoutSelectedId = new HashSet<>();
  ViewGroupHandler.AccessoryPanelVisibility mVisibility;
  MotionEditor mMotionEditor = new MotionEditor();
  public static final String TIMELINE = "Timeline";
  private SmartPsiElementPointer<XmlTag> mSelectedConstraintTag;
  private NlComponentDelegate myNlComponentDelegate = new MotionLayoutComponentDelegate(this);

  MotionLayoutComponentHelper myMotionHelper;
  private String mSelectedConstraintId;

  private final List<AccessorySelectionListener> myListeners;
  private NlComponent mySelection;
  private NlComponent myMotionLayout;
  private MotionEditorSelector.Type mLastSelection = MotionEditorSelector.Type.LAYOUT;
  private MotionSceneTag myLastSelectedTag;

  private void applyMotionSceneValue(boolean apply) {
    if (TEMP_HACK_FORCE_APPLY) {
      if (apply) {
        String applyMotionSceneValue = myMotionLayoutNlComponent.getAttribute(SdkConstants.TOOLS_URI, "applyMotionScene");
        if (applyMotionSceneValue != null && applyMotionSceneValue.equals("false")) {
          WriteCommandAction.runWriteCommandAction(myProject, () -> {
            // let's get rid of it, as it's the default.
            myMotionLayoutTag.mComponent.setAttribute(SdkConstants.TOOLS_URI, "applyMotionScene", null);
          });
        }
      }
      else {
        WriteCommandAction.runWriteCommandAction(myProject, () -> {
          myMotionLayoutTag.mComponent.setAttribute(SdkConstants.TOOLS_URI, "applyMotionScene", "false");
        });
      }
    }
  }

  public MotionAccessoryPanel(@NotNull DesignSurface surface,
                              @NotNull NlComponent parent,
                              @NotNull ViewGroupHandler.AccessoryPanelVisibility visibility) {
    if (DEBUG) {
      Debug.log("MotionAccessoryPanel created ");
    }
    myDesignSurface = surface;
    myProject = surface.getProject();
    myMotionLayoutNlComponent = parent;
    myMotionLayoutTag = new NlComponentTag(parent, null);
    mVisibility = visibility;
    myMotionHelper = new MotionLayoutComponentHelper(myMotionLayoutNlComponent);
    myListeners = new ArrayList<>();

    myDesignSurface.getSelectionModel().addListener((model, selection) -> handleSelectionChanged(model, selection));
    mMotionEditor.addSelectionListener(new MotionEditorSelector.Listener() {
      @Override
      public void selectionChanged(MotionEditorSelector.Type selection, MTag[] tag) {
        if (DEBUG) {
          Debug.log("Selection changed " + selection);
        }
        mSelectedConstraintTag = null;
        mLastSelection = selection;
        myLastSelectedTag = computeSelectedTagForPropertyPanel(tag);
        switch (selection) {
          case CONSTRAINT_SET: {
            String id = tag[0].getAttributeValue("id");
            if (DEBUG) {
              Debug.log("id of constraint set " + id);
            }
            if (id != null) {
              mSelectedConstraintId = stripID(id);
              myMotionHelper.setState(mSelectedConstraintId);
            }
            if (TEMP_HACK_FORCE_APPLY) {
              applyMotionSceneValue(true);
            }
          }
          break;
          case TRANSITION:
            String start = stripID(tag[0].getAttributeValue("constraintSetStart"));
            String end = stripID(tag[0].getAttributeValue("constraintSetEnd"));
            myMotionHelper.setTransition(start, end);
            break;
          case LAYOUT: {
            if (TEMP_HACK_FORCE_APPLY) {
              applyMotionSceneValue(false);
            }
            selectOnDesignSurface(tag);
            myMotionHelper.setState(null);
            mSelectedConstraintId = null;
          }
          break;
          case CONSTRAINT: {
            // TODO: This should always be a WrapMotionScene (remove this code when bug is fixed):
            XmlTag xmlTag = null;
            selectOnDesignSurface(tag);
            if (tag[0] instanceof MotionSceneTag) {
              xmlTag = ((MotionSceneTag)tag[0]).getXmlTag();
            }
            else if (tag[0] instanceof NlComponentTag) {
              xmlTag = ((NlComponentTag)tag[0]).mComponent.getTag();
            }
            if (xmlTag == null) {
              return;
            }
            mSelectedConstraintTag = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(xmlTag);
          }
          break;
          case LAYOUT_VIEW:
          case KEY_FRAME_GROUP:
            // The NelePropertiesModel should be handling the properties in these cases...
            break;
        }
        if (!mMotionEditor.isUpdatingModel()) {
          fireSelectionChanged(Collections.singletonList(mySelection));
        }
      }
    });
    mMotionEditor.addTimeLineListener(new MotionEditorSelector.TimeLineListener() {
      @Override
      public void command(MotionEditorSelector.TimeLineCmd cmd, float pos) {
        switch (cmd) {
          case MOTION_PROGRESS:
            myMotionHelper.setProgress(pos);
            break;
          case MOTION_PLAY:
            break;
          case MOTION_STOP:
            break;
        }
      }
    });
    myMotionScene = getMotionScene(myMotionLayoutNlComponent);
    mMotionEditor.setMTag(myMotionScene, myMotionLayoutTag, "", "");
    MTag []cSet = myMotionScene.getChildTags(MotionSceneAttrs.Tags.CONSTRAINTSET);
    if (DEBUG) {
      Debug.log(" select constraint set "+cSet[0].getAttributeValue("id"));
    }
    if (cSet!= null && cSet.length > 0) {
      mMotionEditor.selectTag(cSet[0]);
    }
    parent.putClientProperty(TIMELINE, this);
    if (DEBUG) {
      Debug.log("harness " + parent);
    }
    AndroidFacet facet = parent.getModel().getFacet();
    ResourceNotificationManager.getInstance(myProject).addListener(new ResourceNotificationManager.ResourceChangeListener() {
      @Override
      public void resourcesChanged(@NotNull Set<ResourceNotificationManager.Reason> reason) {
        myMotionScene = getMotionScene(myMotionLayoutNlComponent);
        mMotionEditor.setMTag(myMotionScene, myMotionLayoutTag, "", "");
        fireSelectionChanged(Collections.singletonList(mySelection));
      }
    }, facet, myMotionScene.getXmlTag().getContainingFile().getVirtualFile(), null);
  }

  private void selectOnDesignSurface(MTag[] tag) {
    if (DEBUG) {
      Debug.log("Selection changed ");
    }
    if (true) return;
    ArrayList<NlComponent> list = new ArrayList<>();
    for (int i = 0; i < tag.length; i++) {
      MTag mTag = tag[i];
      if (mTag instanceof NlComponentTag) {
        list.add(((NlComponentTag)mTag).mComponent);
      }
    }

    if (DEBUG) {
      Debug.log(" set section " + tag.length + " " + tag[0].getTagName());
    }
    if (list.size() > 0) {
      myDesignSurface.getSelectionModel().setSelection(list);
    }
    else {
      myDesignSurface.getSelectionModel().setSelection(Arrays.asList(myMotionLayoutNlComponent));
    }
  }

  @Nullable
  private MotionSceneTag computeSelectedTagForPropertyPanel(MTag[] tag) {
    MTag firstTag = tag != null && tag.length > 0 ? tag[0] : null;
    MotionSceneTag sceneTag = firstTag instanceof MotionSceneTag ? (MotionSceneTag)firstTag : null;
    if (sceneTag == null || !sceneTag.getXmlTag().isValid()) {
      return null;
    }
    return sceneTag;
  }

  @Nullable
  @Override
  public Object getSelectedAccessory() {
    return myLastSelectedTag;
  }

  private void handleSelectionChanged(@NotNull SelectionModel model, @NotNull List<NlComponent> selection) {
    // Prevent repeated section of the same objectS
    if (selection.size() == myLayoutSelectedId.size()) {
      int count = 0;
      for (NlComponent component : selection) {
        if (myLayoutSelectedId.contains(component.getId())) {
          count++;
        }
      }
      if (count == selection.size()) {
        return;
      }
    }
    myLayoutSelectedId.clear();
    for (NlComponent component : selection) {
      if (myLayoutSelectedId.add(component.getId())) ;
    }

    if (selection.size() > 0) {
      for (NlComponent component : selection) {
        String tagName = component.getTagName();
        String id = component.getId();
        MTag tag = mMotionEditor.getMeModel().findTag(tagName, id);
        if (tag != null) {
          if (tag instanceof NlComponentTag) {
            mMotionEditor.setSelection(MotionEditorSelector.Type.LAYOUT_VIEW, new MTag[]{tag});
          }
          else {
            mMotionEditor.setSelection(MotionEditorSelector.Type.CONSTRAINT, new MTag[]{tag});
          }
        }
      }
    }
    //    mMotionEditorSelector.notifyListeners(type, tags);
    fireSelectionChanged(selection);
  }

  MotionSceneTag getMotionScene(NlComponent motionLayout) {
    String ref = motionLayout.getAttribute(SdkConstants.AUTO_URI, "layoutDescription");
    int index = ref.lastIndexOf("@xml/");
    String fileName = ref.substring(index + 5);
    if (fileName == null || fileName.isEmpty()) {
      return null;
    }

    // let's open the file
    AndroidFacet facet = motionLayout.getModel().getFacet();

    List<VirtualFile> resourcesXML = AndroidResourceUtil.getResourceSubdirs(ResourceFolderType.XML, ResourceRepositoryManager
      .getModuleResources(facet).getResourceDirs());
    if (resourcesXML.isEmpty()) {
      return null;
    }
    VirtualFile directory = resourcesXML.get(0);
    VirtualFile virtualFile = directory.findFileByRelativePath(fileName + ".xml");

    XmlFile xmlFile = (XmlFile)AndroidPsiUtils.getPsiFileSafely(myProject, virtualFile);

    MotionSceneTag motionSceneModel = MotionSceneTag.parse(motionLayout, myProject, virtualFile, xmlFile);

    return motionSceneModel;
  }

  @NotNull
  @Override
  public JPanel getPanel() {
    return mMotionEditor;
  }

  @NotNull
  @Override
  public JPanel createPanel(AccessoryPanel.Type type) {
    return new JPanel() {{
      setBackground(Color.RED);
    }};
  }

  @Override
  public void updateAccessoryPanelWithSelection(@NotNull AccessoryPanel.Type type, @NotNull List<NlComponent> selection) {

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

    if (!NlComponentHelperKt.isOrHasSuperclass(component, SdkConstants.CLASS_MOTION_LAYOUT)) {
      component = component.getParent();
      if (component != null && !NlComponentHelperKt.isOrHasSuperclass(component, SdkConstants.CLASS_MOTION_LAYOUT)) {
        return; // not found
      }
    }
    // component is a motion layout
    if (myMotionLayout != component) {
      myMotionLayout = component;
    }

    fireSelectionChanged(selection);
  }

  @Override
  public void deactivate() {
    myMotionLayout = null;
  }

  @Override
  public void updateAfterModelDerivedDataChanged() {
    myMotionHelper = new MotionLayoutComponentHelper(myMotionLayoutNlComponent);

    // ok, so I found out why the live edit wasn't working -- actually everything was working, but...
    // live edit works by editing the layoutParams of the view. Which is ok as normally this is
    // indeed what drive the position of a widget.
    // Not so much for MotionLayout! in a transition the position of the widget will depend
    // on the Scene (and progress).
    // So everything was correctly working, updating the layoutparams of the concerned view,
    // but nothing was moving as what we will need to do here is to change the constraintset *live*
    // Additionally we need to correctly reset the state of the motionhelper if we recreate it.
    if (mLastSelection == MotionEditorSelector.Type.LAYOUT) {
      myMotionHelper.setState(null);
      mSelectedConstraintId = null;
    }
    else if (mLastSelection == MotionEditorSelector.Type.CONSTRAINT_SET) {
      myMotionHelper.setState(mSelectedConstraintId);
    }

    // Ok, so to handle the "layout" mode, we need a few things.
    // 1. we need to capture in a constraintset the base layout, because we need to reapply it
    // 2. when "layout" is selected we can turn off the Scene (we do that already) but
    //    we also need to reapply the base layout constraints
    // 3. updating live via layoutparams do work right now, but applying the constraints doesn't
    //    -> I think because while we do correctly deactivate the Scene, at loading time we
    //       apply the start constraintset... so boom. If we captured the scene as a constraintset
    //       we should be able to make that work correctly.
    //    -> temporary solution : when selecting the base layout, write the applyMotionScene = false to the XML
    //       -> TEMP_HACK_FORCE_APPLY = true

    // ok so dragging an object doesn't work, but bias does. I think it's because it somehow use the base layout attributes
    // to decide what's possible.
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


  ////////////////////////////////////////////////////////////////////////////////
  // MotionLayoutInterface
  ////////////////////////////////////////////////////////////////////////////////

  @Override
  public boolean showPopupMenuActions() {
    return false;
  }

  ////////////////////////////////////////////////////////////////////////////////
  // MotionDesignSurfaceEdits
  ////////////////////////////////////////////////////////////////////////////////

  @Override
  public boolean handlesWriteForComponent(String id) {
    boolean handlesWrite = getSelectedConstraintSet() != null;
    return handlesWrite;
    //SmartPsiElementPointer<XmlTag> constraint = getSelectedConstraint();
    //if (constraint != null) {
    //  String constraintId = constraint.getElement().getAttribute("android:id").getValue();
    //  return id.equals(stripID(constraintId));
    //}
    //return false;
  }

  @Override
  public SmartPsiElementPointer<XmlTag> getSelectedConstraint() {
    return mSelectedConstraintTag;
  }

  @Override
  public String getSelectedConstraintSet() {
    return mSelectedConstraintId;
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
    AndroidFacet facet = component.getModel().getFacet();
    List<VirtualFile> resourcesXML = AndroidResourceUtil.getResourceSubdirs(ResourceFolderType.XML, ResourceRepositoryManager
      .getModuleResources(facet).getResourceDirs());
    if (resourcesXML.isEmpty()) {
      return null;
    }
    VirtualFile directory = resourcesXML.get(0);
    VirtualFile virtualFile = directory.findFileByRelativePath(fileName + ".xml");

    return (XmlFile)AndroidPsiUtils.getPsiFileSafely(myProject, virtualFile);
  }

  @Override
  @Nullable
  public XmlTag getConstraintSet(XmlFile file, String constraintSetId) {
    XmlTag[] children = file.getRootTag().findSubTags("ConstraintSet");
    for (int i = 0; i < children.length; i++) {
      XmlAttribute attribute = children[i].getAttribute("android:id");
      if (attribute != null) {
        String childId = stripID(attribute.getValue());
        if (childId.equalsIgnoreCase(constraintSetId)) {
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
}
