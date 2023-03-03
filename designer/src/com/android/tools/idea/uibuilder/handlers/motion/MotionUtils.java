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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X;
import static com.android.SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_END;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_START;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_TOP;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.SHERPA_URI;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneUtils.MOTION_LAYOUT_PROPERTIES;

import com.android.AndroidXConstants;
import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.common.command.NlWriteCommandActionUtil;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.NlAttributesHolder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.target.AnchorTarget;
import com.android.tools.idea.res.StudioResourceRepositoryManager;
import com.android.tools.idea.uibuilder.api.ViewHandler;
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
import javax.swing.tree.TreeSelectionModel;
import org.jetbrains.android.facet.AndroidFacet;
import com.android.tools.idea.res.IdeResourcesUtil;
import org.jetbrains.annotations.NotNull;
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

    MotionLayoutComponentHelper motionLayout = MotionLayoutComponentHelper.create(motionLayoutComponent);
    if (motionLayout.isInTransition()) {
      return;
    }

    if (isInBaseState(motionLayout)) {
      component.startAttributeTransaction();
      for (Pair<String, String> attribute : ConstraintComponentUtilities.ourLayoutAttributes) {
        String namespace = attribute.getFirst();
        String attributeName = attribute.getSecond();
        String definedAttribute = component.getLiveAttribute(namespace, attributeName);
        if (definedAttribute != null) {
          modification.setAttribute(namespace, attributeName, definedAttribute);
        }
      }
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
      String namespace = attribute.getFirst();
      String attributeName = attribute.getSecond();
      MotionAttributes.DefinedAttribute definedAttribute = definedAttributes.get(attributeName);
      if (definedAttribute != null) {
        namespace = getFilteredNamespace(namespace, attributeName, false);
        modification.setAttribute(namespace, attributeName, definedAttribute.getValue());
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

    MotionLayoutComponentHelper motionLayout = MotionLayoutComponentHelper.create(motionLayoutComponent);
    if (motionLayout.isInTransition()) {
      return;
    }

    ConstraintComponentUtilities.cleanup(modification, component);
    if (isInBaseState(motionLayout)) {
      AttributesTransaction transaction = fillTransaction(modification);
      NlWriteCommandActionUtil.run(component, modification.getLabel(), transaction::commit);
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

    MotionLayoutComponentHelper motionLayout = MotionLayoutComponentHelper.create(motionLayoutComponent);
    if (motionLayout.isInTransition()) {
      return;
    }

    if (isInBaseState(motionLayout)) {
      AttributesTransaction transaction = fillTransaction(modification);
      transaction.apply();
      motionLayout.updateLiveAttributes(component, modification, motionLayout.getState());
      return;
    }

    // let's apply in memory by updating the constraintset directly
    motionLayout.updateLiveAttributes(component, modification, motionLayout.getState());
  }

  // in this case, we need to apply the memorized attributes by hand -- the internal
  // AttributesTransaction of the component wouldn't have been used yet.
  private static AttributesTransaction fillTransaction(ComponentModification modification) {
    NlComponent component = modification.getComponent();
    AttributesTransaction transaction = component.startAttributeTransaction();
    for (Pair<String, String> key : modification.getAttributes().keySet()) {
      String value = modification.getAttributes().get(key);
      String namespace = key.getFirst();
      String attribute = key.getSecond();
      namespace = getFilteredNamespace(namespace, attribute, true);
      transaction.setAttribute(namespace, attribute, value);
    }
    return transaction;
  }

  private static final HashMap<String, Pair<String, String>> ourFilteredAttributes = new HashMap<>();
  static {
    ourFilteredAttributes.put(ATTR_ID, Pair.of(ANDROID_URI, SHERPA_URI));
    ourFilteredAttributes.put(ATTR_LAYOUT_WIDTH, Pair.of(ANDROID_URI, SHERPA_URI));
    ourFilteredAttributes.put(ATTR_LAYOUT_HEIGHT, Pair.of(ANDROID_URI, SHERPA_URI));
    ourFilteredAttributes.put(ATTR_LAYOUT_MARGIN_START, Pair.of(ANDROID_URI, SHERPA_URI));
    ourFilteredAttributes.put(ATTR_LAYOUT_MARGIN_END, Pair.of(ANDROID_URI, SHERPA_URI));
    ourFilteredAttributes.put(ATTR_LAYOUT_MARGIN_LEFT, Pair.of(ANDROID_URI, SHERPA_URI));
    ourFilteredAttributes.put(ATTR_LAYOUT_MARGIN_RIGHT, Pair.of(ANDROID_URI, SHERPA_URI));
    ourFilteredAttributes.put(ATTR_LAYOUT_MARGIN_TOP, Pair.of(ANDROID_URI, SHERPA_URI));
    ourFilteredAttributes.put(ATTR_LAYOUT_MARGIN_BOTTOM, Pair.of(ANDROID_URI, SHERPA_URI));
    ourFilteredAttributes.put(ATTR_LAYOUT_EDITOR_ABSOLUTE_X, Pair.of(TOOLS_URI, SHERPA_URI));
    ourFilteredAttributes.put(ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, Pair.of(TOOLS_URI, SHERPA_URI));
  }

  /**
   * Depending if we are in the base state or not we need to return a different namespaces for
   * the attributes tracked in ourFilteredAttributes map.
   *
   * @param namespace
   * @param attribute
   * @param isInBaseState
   * @return correct namespace depending on the state.
   */
  private static String getFilteredNamespace(String namespace, String attribute, boolean isInBaseState) {
    Pair<String, String> namespaces = ourFilteredAttributes.get(attribute);
    if (namespaces == null) {
      return namespace;
    }
    return isInBaseState ? namespaces.getFirst() : namespaces.getSecond();
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
  public static @Nullable NlComponent getMotionLayoutAncestor(@NotNull NlComponent component) {
    NlComponent motionLayout = component;
    while (!NlComponentHelperKt.isOrHasSuperclass(motionLayout, AndroidXConstants.MOTION_LAYOUT)) {
      ViewHandler handler = NlComponentHelperKt.getViewHandler(motionLayout);
      if (handler instanceof MotionLayoutHandler) {
        return motionLayout;
      }
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
    if (motionLayout == null) {
      return null;
    }
    String ref = motionLayout.getAttribute(SdkConstants.AUTO_URI, "layoutDescription");
    ResourceUrl url = ref != null ? ResourceUrl.parse(ref) : null;
    String fileName = null;
    if (url != null) {
      fileName = url.name;
    }
    if (fileName == null || fileName.isEmpty()) {
      return null;
    }

    // let's open the file
    Project project = motionLayout.getModel().getProject();
    AndroidFacet facet = motionLayout.getModel().getFacet();

    List<VirtualFile> resourcesXML = IdeResourcesUtil.getResourceSubdirs(ResourceFolderType.XML, StudioResourceRepositoryManager
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

  /**
   * Provides the appropriate tree selection model based on
   * the model, according to motion layout behaviour.
   * @return {@link TreeSelectionModel} appropriate for model.
   */
  public static int getTreeSelectionModel(NlModel model) {
    List<NlComponent> list = model.getComponents();
    if (!list.isEmpty() && getMotionLayoutAncestor(list.get(0)) != null) {
      return TreeSelectionModel.SINGLE_TREE_SELECTION;
    }
    return TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION;
  }
}
