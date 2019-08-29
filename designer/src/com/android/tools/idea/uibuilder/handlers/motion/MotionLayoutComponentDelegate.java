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
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlComponentDelegate;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.parsers.AttributeSnapshot;
import com.android.tools.idea.rendering.parsers.LayoutPullParsers;
import com.android.tools.idea.res.ResourceIdManager;
import com.android.tools.idea.uibuilder.handlers.constraint.ComponentModification;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionDesignSurfaceEdits;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.MotionSceneModel;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.utils.Pair;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.ConstraintSetConstraint;
import static com.android.tools.idea.uibuilder.handlers.motion.timeline.MotionSceneModel.stripID;

public class MotionLayoutComponentDelegate implements NlComponentDelegate {
  private static final boolean USE_CACHE = false;

  private final MotionDesignSurfaceEdits myPanel;

  private static List<String> ourDefaultAttributes = Arrays.asList(
    SdkConstants.ATTR_LAYOUT_WIDTH,
    SdkConstants.ATTR_LAYOUT_HEIGHT,
    SdkConstants.ATTR_ID,
    SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X,
    SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y
  );

  private static List<String> ourInterceptedAttributes = Arrays.asList(
    SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X,
    SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y,
    SdkConstants.ATTR_LAYOUT_WIDTH,
    SdkConstants.ATTR_LAYOUT_HEIGHT,
    SdkConstants.ATTR_LAYOUT_MARGIN,
    SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
    SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
    SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
    SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM,
    SdkConstants.ATTR_LAYOUT_MARGIN_START,
    SdkConstants.ATTR_LAYOUT_MARGIN_END,
    SdkConstants.ATTR_LAYOUT_GONE_MARGIN_LEFT,
    SdkConstants.ATTR_LAYOUT_GONE_MARGIN_RIGHT,
    SdkConstants.ATTR_LAYOUT_GONE_MARGIN_TOP,
    SdkConstants.ATTR_LAYOUT_GONE_MARGIN_BOTTOM,
    SdkConstants.ATTR_LAYOUT_GONE_MARGIN_START,
    SdkConstants.ATTR_LAYOUT_GONE_MARGIN_END,
    SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF,
    SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF,
    SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF,
    SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
    SdkConstants.ATTR_LAYOUT_START_TO_START_OF,
    SdkConstants.ATTR_LAYOUT_START_TO_END_OF,
    SdkConstants.ATTR_LAYOUT_END_TO_END_OF,
    SdkConstants.ATTR_LAYOUT_END_TO_START_OF,
    SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF,
    SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF,
    SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF,
    SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF,
    SdkConstants.ATTR_LAYOUT_HORIZONTAL_CHAIN_STYLE,
    SdkConstants.ATTR_LAYOUT_VERTICAL_CHAIN_STYLE,
    SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS,
    SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS,
    SdkConstants.ATTR_LAYOUT_HORIZONTAL_WEIGHT,
    SdkConstants.ATTR_LAYOUT_VERTICAL_WEIGHT
  );

  private MotionLayoutTimelinePanel.State myLastReadState;

  public MotionLayoutComponentDelegate(@NotNull MotionDesignSurfaceEdits panel) {
    myPanel = panel;
  }

  @Override
  public boolean handlesAttribute(@NotNull NlComponent component, @Nullable String namespace, @NotNull String attribute) {
    if (NlComponentHelperKt.isOrHasSuperclass(component, SdkConstants.CLASS_MOTION_LAYOUT)) {
      return false;
    }
    if (ourInterceptedAttributes.contains(attribute)) {
      return myPanel.handlesWriteForComponent(component.getId());
    }
    return false;
  }

  @Override
  public boolean handlesAttributes(NlComponent component) {
    return true;
  }

  @Override
  public boolean handlesApply(ComponentModification modification) {
    return true;
  }

  @Override
  public boolean handlesCommit(ComponentModification modification) {
    SmartPsiElementPointer<XmlTag> constraint = myPanel.getSelectedConstraint();
    return constraint != null;
  }

  @Nullable
  private XmlTag getConstrainedView(@NotNull NlComponent component) {

    SmartPsiElementPointer<XmlTag> constraint = myPanel.getSelectedConstraint();
    if (constraint == null){
      return null;
    }
    XmlTag tag = constraint.getElement().getParentTag();
    XmlTag[] child =  tag.getSubTags();
    for (int i = 0; i < child.length; i++) {
      XmlTag xmlTag = child[i];
      if (component.getId().equals(stripID(xmlTag.getAttributeValue("id",SdkConstants.ANDROID_URI)))) {
        return xmlTag;
      }
    }
    return null;

  }

  HashMap<NlComponent, HashMap<Pair<String, String>, String> > mAttributesCacheStart = new HashMap<>();
  HashMap<NlComponent, HashMap<Pair<String, String>, String> > mAttributesCacheEnd = new HashMap<>();
  HashMap<NlComponent, List<AttributeSnapshot>> mCachedAttributes = new HashMap<>();

  @Override
  public String getAttribute(@NotNull NlComponent component, @Nullable String namespace, @NotNull String attribute) {
    return getCachedAttribute(mAttributesCacheStart, component, namespace, attribute);
  }

  @Nullable
  private String getCachedAttribute(@NotNull HashMap<NlComponent, HashMap<Pair<String, String>, String> > cache,
                                    @NotNull NlComponent component, @Nullable String namespace, @NotNull String attribute) {
    HashMap<Pair<String, String>, String> cachedComponent = null;
    String cachedAttribute = null;
    Pair<String, String> key = null;
    if (namespace.equalsIgnoreCase(SdkConstants.TOOLS_URI)) {
      namespace = SdkConstants.AUTO_URI;
    }
    if (USE_CACHE) {
      cachedComponent = cache.get(component);
      if (cachedComponent == null) {
        cachedComponent = new HashMap<>();
        cache.put(component, cachedComponent);
      }
      key = Pair.of(namespace, attribute);
      cachedAttribute = cachedComponent.get(key);
    }
    if (cachedAttribute == null) {
      XmlTag constrainedView = getConstrainedView(component);
      if (constrainedView == null) {
        return component.getAttributeImpl(namespace, attribute);
      }
      cachedAttribute = constrainedView.getAttributeValue(attribute, namespace);
      if (USE_CACHE) {
        cachedComponent.put(key, cachedAttribute);
      }
    }
    return cachedAttribute;
  }

  private void clearCache(@NotNull NlComponent component) {
    mCachedAttributes.remove(component);
    mAttributesCacheStart.remove(component);
    mAttributesCacheEnd.remove(component);
  }

  @Override
  public void clearCaches() {
    mCachedAttributes.clear();
    mAttributesCacheStart.clear();
    mAttributesCacheEnd.clear();
  }

  @Override
  public void willRemoveChild(@NotNull NlComponent component) {
    XmlFile file = myPanel.getTransitionFile(component);
    if (file == null) {
      return;
    }
    Project project = component.getModel().getProject();
    new WriteCommandAction(project, "Remove component", file) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {

        XmlTag constraintSet = myPanel.getConstraintSet(file, "@+id/start");
        if (constraintSet != null) {
          XmlTag constrainedView = myPanel.getConstrainView(constraintSet, component.getId());
          if (constrainedView != null) {
            constrainedView.delete();
          }
        }
        constraintSet = myPanel.getConstraintSet(file, "@+id/end");
        if (constraintSet != null) {
          XmlTag constrainedView = myPanel.getConstrainView(constraintSet, component.getId());
          if (constrainedView != null) {
            constrainedView.delete();
          }
        }
        List<XmlTag> keyframes = myPanel.getKeyframes(file, component.getId());
        for (XmlTag keyframe : keyframes) {
          keyframe.delete();
        }
      }
    }.execute();

    // A bit heavy handed, but that's what LayoutLib needs...
    LayoutPullParsers.saveFileIfNecessary(file);

    // Let's warn we edited the model.
    NlModel model = component.getModel();
    model.notifyModified(NlModel.ChangeType.EDIT);
    clearCache(component);
  }

  @Override
  public boolean commitToMotionScene(Pair<String, String> key) {
    // allows more fine-grained commit per attributes if needed
    return true;
  }

  @Override
  public List<AttributeSnapshot> getAttributes(NlComponent component) {
    List<AttributeSnapshot> attributes = null;
    if (USE_CACHE) {
      //if (myPanel.getCurrentState() == myLastReadState) {
      //  attributes = mCachedAttributes.get(component);
      //} else {
      //  mCachedAttributes.clear();
      //}
    }
    if (attributes == null) {
      XmlTag constrainedView = getConstrainedView(component);
      if (constrainedView != null) {
        attributes = AttributeSnapshot.createAttributesForTag(constrainedView);
      }
      if (attributes == null) {
        attributes = new ArrayList<>();
        List<AttributeSnapshot> originalAttributes = component.getAttributesImpl();
        for (AttributeSnapshot attributeSnapshot : originalAttributes) {
          if (ourDefaultAttributes.contains(attributeSnapshot.name)) {
            attributes.add(attributeSnapshot);
          }
        }
      }
      if (USE_CACHE && attributes != null) {
        //mCachedAttributes.put(component, attributes);
        //myLastReadState = myPanel.getCurrentState();
      }
    }
    return attributes;
  }

  @Override
  public void apply(ComponentModification modification) {
    String constraintSetId = null;
    int position = 0;

    SmartPsiElementPointer<XmlTag> constraint = myPanel.getSelectedConstraint();
    if (constraint != null) {
      XmlTag tag = constraint.getElement().getParentTag();
      String id = stripID(tag.getAttributeValue("id", SdkConstants.ANDROID_URI));
      constraintSetId = "@+id/" + id;
      // todo: fix temporary hack, we should compare with the name of the current end constraintset
      if (id.equalsIgnoreCase("end")) {
        position = 1;
      }
    } else {
      return;
    }

    ResourceIdManager manager = ResourceIdManager.get(modification.getComponent().getModel().getModule());
    NlComponent parent = modification.getComponent().getParent();
    ViewInfo info = NlComponentHelperKt.getViewInfo(modification.getComponent());

    if (info == null || (info != null && info.getViewObject() == null)) {
      return;
    }

    MotionLayoutComponentHelper helper = new MotionLayoutComponentHelper(parent);

    final Configuration configuration = modification.getComponent().getModel().getConfiguration();
    final int dpiValue = configuration.getDensity().getDpiValue();
    HashMap<String, String> attributes = new HashMap<>();
    for (Pair<String, String> key : modification.getAttributes().keySet()) {
      String value = modification.getAttributes().get(key);
      if (value != null) {
        if (value.startsWith("@id/") || value.startsWith("@+id/")) {
          value = value.substring(value.indexOf('/') + 1);
          Integer resolved = manager.getCompiledId(new ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, value));
          if (resolved == null) {
            updateIds(modification.getComponent());
            resolved = manager.getOrGenerateId(new ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, value));
          }
          if (resolved != null) {
            value = resolved.toString();
          }
        } else if (value.equalsIgnoreCase("parent")) {
          value = "0";
        }
      }
      attributes.put(key.getSecond(), value);
    }

    helper.setAttributes(dpiValue, constraintSetId, info.getViewObject(), attributes);
    helper.setProgress(position);
  }

  /**
   * Make sure we have usable Ids, even if only temporary
   * @param component
   */
  void updateIds(@NotNull NlComponent component) {
    ResourceIdManager manager = ResourceIdManager.get(component.getModel().getModule());
    updateId(manager, component);
    if (NlComponentHelperKt.isOrHasSuperclass(component, SdkConstants.CLASS_MOTION_LAYOUT)) {
      for (NlComponent child : component.getChildren()) {
        updateId(manager, child);
      }
    }
    component = component.getParent();
    if (component != null && NlComponentHelperKt.isOrHasSuperclass(component, SdkConstants.CLASS_MOTION_LAYOUT)) {
      for (NlComponent child : component.getChildren()) {
        updateId(manager, child);
      }
    }
  }

  private void updateId(@NotNull ResourceIdManager manager, @NotNull NlComponent component) {
    String id = component.getId();
    ResourceReference reference = new ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, id);
    Integer resolved = manager.getCompiledId(reference);
    if (resolved == null) {
      resolved = manager.getOrGenerateId(reference);
      if (resolved != null) {
        ViewInfo view = NlComponentHelperKt.getViewInfo(component);
        if (view != null && view.getViewObject() != null) {
          android.view.View androidView = (android.view.View) view.getViewObject();
          androidView.setId(resolved.intValue());
        }
      }
    }
  }

  @Override
  public void commit(ComponentModification modification) {
    NlComponent component = modification.getComponent();
    Project project = component.getModel().getProject();
    String constraintSetId = myPanel.getSelectedConstraintSet();

    //SmartPsiElementPointer<XmlTag> constraint = myPanel.getSelectedConstraint();
    //if (constraint != null) {
    //  XmlTag tag = constraint.getElement().getParentTag();
    //  String id = stripID(tag.getAttributeValue("id", SdkConstants.ANDROID_URI));
    //  constraintSetId = "@+id/" + id;
    //} else {
    //  return;
    //}

    XmlFile file = myPanel.getTransitionFile(component);
    if (file == null) {
      return;
    }

    String finalConstraintSetId = constraintSetId;
    new WriteCommandAction(project, "Set In Transition", file) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        XmlTag constraintSet = myPanel.getConstraintSet(file, finalConstraintSetId);
        if (constraintSet == null) {
          return;
        }
        XmlTag constrainedView = myPanel.getConstrainView(constraintSet, component.getId());
        if (constrainedView == null) {
          constrainedView = constraintSet.createChildTag(ConstraintSetConstraint, null, null, false);
          constrainedView = constraintSet.addSubTag(constrainedView, false);
          String componentId = "@+id/" + component.getId();
          constrainedView.setAttribute("id", SdkConstants.ANDROID_URI, componentId);
          constrainedView.setAttribute(SdkConstants.ATTR_LAYOUT_WIDTH, SdkConstants.ANDROID_URI,
                                       component.getAttributeImpl(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH));
          constrainedView.setAttribute(SdkConstants.ATTR_LAYOUT_HEIGHT, SdkConstants.ANDROID_URI,
                                       component.getAttributeImpl(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT));
          modification.commitTo(constrainedView);
        } else {
          modification.commitTo(constrainedView);
        }
      }
    }.execute();

    // Let's warn we edited the model.
    MotionSceneModel.saveAndNotify(file, component.getModel());

    clearCache(component);
  }

  @Override
  public void setAttribute(NlComponent component, String namespace, String attribute, String value) {
    ComponentModification modification = new ComponentModification(component, "Set Attribute " + attribute);
    modification.setAttribute(namespace, attribute, value);
    commit(modification);
  }

}
