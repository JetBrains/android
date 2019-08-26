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

import static com.android.SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF;
import static com.android.SdkConstants.SHERPA_URI;
import static com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneUtils.MOTION_LAYOUT_PROPERTIES;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.common.model.NlAttributesHolder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.target.AnchorTarget;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.uibuilder.handlers.constraint.ComponentModification;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionAttributes;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.Utils;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.utils.Pair;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import java.util.HashMap;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for MotionLayout handler.
 */
public class MotionUtils {

  /**
   * Returns true if the motionLayout is in the base state (i.e base layout)
   * @param motionLayout
   * @return true in base state, false otherwise
   */
  public static boolean isInBaseState(MotionLayoutComponentHelper motionLayout) {
    String state = motionLayout.getState();
    return state == null || state.equals("motion_base");
  }

  /**
   * Initialize a ComponentModification with the correct values from the MotionScene
   *
   * @param modification
   */
  public static void fillComponentModification(ComponentModification modification) {
    NlComponent component = modification.getComponent();

    NlComponent motionLayoutComponent = getMotionLayoutAncestor(component);
    if (motionLayoutComponent == null) {
      modification.directCommit();
      return;
    }

    MotionLayoutComponentHelper motionLayout = new MotionLayoutComponentHelper(motionLayoutComponent);
    if (motionLayout.isInTransition()) {
      return;
    }

    if (isInBaseState(motionLayout)) {
      return;
    }

    // Ok, we are editing a constraintset. Let's apply the correct current states.

    Object properties = component.getClientProperty(MOTION_LAYOUT_PROPERTIES);
    if (properties == null || !(properties instanceof MotionAttributes)) {
      return;
    }

    modification.getAttributes().clear();

    MotionAttributes attrs = (MotionAttributes)properties;
    HashMap<String, MotionAttributes.DefinedAttribute> definedAttributes = attrs.getAttrMap();

    for (Pair<String, String> attribute : ConstraintComponentUtilities.ourLayoutAttributes) {
      String attributeName = attribute.getSecond();
      MotionAttributes.DefinedAttribute definedAttribute = definedAttributes.get(attributeName);
      if (definedAttribute != null) {
        modification.removeAttribute(SdkConstants.TOOLS_URI, attributeName);
        modification.removeAttribute(SdkConstants.SHERPA_URI, attributeName);
        modification.removeAttribute(SdkConstants.ANDROID_URI, attributeName);
        modification.setAttribute(SdkConstants.SHERPA_URI, attributeName, definedAttribute.getValue());
      }
    }
  }

  /**
   * Commit a ComponentModification to the MotionScene
   *
   * @param modification
   */
  public static void commit(ComponentModification modification) {
    NlComponent component = modification.getComponent();
    NlComponent motionLayoutComponent = getMotionLayoutAncestor(component);
    if (motionLayoutComponent == null) {
      modification.commit();
      return;
    }

    MotionLayoutComponentHelper motionLayout = new MotionLayoutComponentHelper(motionLayoutComponent);
    if (motionLayout.isInTransition()) {
      return;
    }

    if (isInBaseState(motionLayout)) {
      modification.directCommit();
      return;
    }

    MTag motionScene = getMotionScene(motionLayoutComponent);
    if (motionScene == null) {
      modification.directCommit();
      return;
    }

    boolean found = false;
    String state = motionLayout.getState();

    // we can decide to either add the constraints to the constraintset if missing,
    // or if false to add them to the layout file.
    final boolean addToConstraintSetIfMissing = true;

    MTag[] cSet = motionScene.getChildTags("ConstraintSet");
    for (int i = 0; i < cSet.length; i++) {
      MTag set = cSet[i];
      String id = set.getAttributeValue("id");
      id = Utils.stripID(id);
      if (id.equalsIgnoreCase(state)) {
        // we are in the correct ConstraintSet
        MTag[] constraints = set.getChildTags(MotionSceneAttrs.Tags.CONSTRAINT);
        for (int j = 0; j < constraints.length; j++) {
          MTag constraint = constraints[j];
          String constraintId = constraint.getAttributeValue("id");
          constraintId = Utils.stripID(constraintId);
          if (constraintId.equalsIgnoreCase(Utils.stripID(component.getId()))) {
            MTag.TagWriter writer = constraint.getTagWriter();
            applyComponentModification(modification, writer);
            writer.commit(modification.getLabel());
            found = true;
          }
        }
        if (!found && addToConstraintSetIfMissing) {
          // missing constraints, let's add them to the ConstraintSet
          MTag.TagWriter constraint = set.getChildTagWriter("Constraint");
          String constraintId = "@+id/" + component.ensureId();
          applyComponentModification(modification, constraint);
          constraint.setAttribute(SdkConstants.SHERPA_URI, "id", constraintId);
          constraint.commit(modification.getLabel());
          found = true;
        }
        break;
      }
    }

    if (!found && !addToConstraintSetIfMissing) {
      // let's add the modification to the layout file instead
      modification.directCommit();
    }

    // Let's warn we edited the model.
    NlModel nlModel = component.getModel();
    nlModel.notifyModified(NlModel.ChangeType.EDIT);
  }

  /**
   * Apply a ComponentModification live on a MotionLayout
   *
   * @param modification
   */
  public static void apply(ComponentModification modification) {
    NlComponent component = modification.getComponent();
    NlComponent motionLayoutComponent = getMotionLayoutAncestor(component);
    if (motionLayoutComponent == null) {
      modification.directApply();
      return;
    }

    MotionLayoutComponentHelper motionLayout = new MotionLayoutComponentHelper(motionLayoutComponent);
    if (motionLayout.isInTransition()) {
      return;
    }

    if (isInBaseState(motionLayout)) {
      modification.directApply();
      return;
    }

    // let's apply in memory by updating the constraintset directly
    motionLayout.updateLiveAttributes(component, modification, motionLayout.getState());
  }

  /**
   * Apply modifications to a tag
   *
   * @param modification
   * @param writer
   */
  static void applyComponentModification(ComponentModification modification, MTag.TagWriter writer) {
    HashMap<Pair<String, String>, String> attributes = modification.getAttributes();
    for (Pair<String, String> key : attributes.keySet()) {
      String value = attributes.get(key);
      writer.setAttribute(key.getFirst(), key.getSecond(), value);
    }
  }

  /**
   * Returns the MotionLayout parent component if it exists
   *
   * @param component
   * @return
   */
  public static @Nullable NlComponent getMotionLayoutAncestor(NlComponent component) {
    NlComponent motionLayout = component;
    while (!NlComponentHelperKt.isOrHasSuperclass(motionLayout, SdkConstants.MOTION_LAYOUT)) {
      motionLayout = motionLayout.getParent();
      if (motionLayout == null) {
        return null;
      }
    }
    return motionLayout;
  }

  /**
   * Returns the corresponding MotionScene of a MotionLayout
   *
   * @param component a MotionLayout or a child of it
   * @return
   */
  public static MotionSceneTag getMotionScene(NlComponent component) {
    NlComponent motionLayout = getMotionLayoutAncestor(component);
    String ref = motionLayout.getAttribute(SdkConstants.AUTO_URI, "layoutDescription");
    int index = ref.lastIndexOf("@xml/");
    String fileName = ref.substring(index + 5);
    if (fileName == null || fileName.isEmpty()) {
      return null;
    }

    // let's open the file
    Project project = motionLayout.getModel().getProject();
    AndroidFacet facet = motionLayout.getModel().getFacet();

    List<VirtualFile> resourcesXML = AndroidResourceUtil.getResourceSubdirs(ResourceFolderType.XML, ResourceRepositoryManager
      .getModuleResources(facet).getResourceDirs());
    if (resourcesXML.isEmpty()) {
      return null;
    }
    VirtualFile directory = resourcesXML.get(0);
    VirtualFile virtualFile = directory.findFileByRelativePath(fileName + ".xml");

    XmlFile xmlFile = (XmlFile)AndroidPsiUtils.getPsiFileSafely(project, virtualFile);

    MotionSceneTag motionSceneModel = MotionSceneTag.parse(motionLayout, project, virtualFile, xmlFile);

    return  motionSceneModel;
  }

  /**
   * is the given anchor type connected for this component
   *
   * @param type             type of the anchor
   * @param component        the component of the anchor
   * @param useRtlAttributes if true, we should use start/end
   * @param isRtl            if true, we are in RTL, otherwise in LTR
   * @return true if the component has an attribute indicated it's connected
   */
  public static boolean isAnchorConnected(AnchorTarget.Type type, NlAttributesHolder component, boolean useRtlAttributes, boolean isRtl) {
    if (type == AnchorTarget.Type.BASELINE) {
      return component.getAttribute(SHERPA_URI, ATTR_LAYOUT_BASELINE_TO_BASELINE_OF) != null;
    }
    boolean isConnected = false;
    Pair pair = ConstraintComponentUtilities.ourPotentialAttributes.get(type);
    if (pair != null) {
      // noinspection ConstantConditions
      isConnected |= component.getAttribute(SHERPA_URI, (String)pair.getFirst()) != null;
      isConnected |= component.getAttribute(SHERPA_URI, (String)pair.getSecond()) != null;
    }
    if (useRtlAttributes) {
      if (isRtl) {
        pair = ConstraintComponentUtilities.ourPotentialRTLAttributes.get(type);
        if (pair != null) {
          isConnected |= component.getAttribute(SHERPA_URI, (String)pair.getFirst()) != null;
          isConnected |= component.getAttribute(SHERPA_URI, (String)pair.getSecond()) != null;
        }
      }
      else {
        pair = ConstraintComponentUtilities.ourPotentialLTRAttributes.get(type);
        if (pair != null) {
          isConnected |= component.getAttribute(SHERPA_URI, (String)pair.getFirst()) != null;
          isConnected |= component.getAttribute(SHERPA_URI, (String)pair.getSecond()) != null;
        }
      }
    }
    return isConnected;
  }
}
