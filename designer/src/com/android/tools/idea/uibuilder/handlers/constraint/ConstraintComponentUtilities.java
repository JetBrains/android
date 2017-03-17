/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.constraint;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.repository.GradleVersion;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.AnchorTarget;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.target.Target;
import com.android.tools.idea.uibuilder.scout.Direction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.lang.Float;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawGuidelineCycle.*;

/**
 * Encapsulate basic querys on a ConstraintLayout component
 */
@SuppressWarnings("ForLoopReplaceableByForEach")
public final class ConstraintComponentUtilities {

  public static final HashMap<String, String> ourReciprocalAttributes;
  public static final HashMap<String, String> ourMapMarginAttributes;
  public static final HashMap<String, AnchorTarget.Type> ourMapSideToOriginAnchors;
  public static final HashMap<String, AnchorTarget.Type> ourMapSideToTargetAnchors;
  public static final ArrayList<String> ourLeftAttributes;
  public static final ArrayList<String> ourTopAttributes;
  public static final ArrayList<String> ourRightAttributes;
  public static final ArrayList<String> ourBottomAttributes;
  public static final ArrayList<String> ourBaselineAttributes;
  public static final ArrayList<String> ourMarginAttributes;
  public static final ArrayList<String> ourHorizontalAttributes;
  public static final ArrayList<String> ourVerticalAttributes;
  public static final ArrayList<String> ourCreatorAttributes;
  public static final String[] ourConstraintLayoutAttributesToClear = {
    ATTR_LAYOUT_LEFT_TO_LEFT_OF,
    ATTR_LAYOUT_LEFT_TO_RIGHT_OF,
    ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
    ATTR_LAYOUT_RIGHT_TO_RIGHT_OF,
    ATTR_LAYOUT_TOP_TO_TOP_OF,
    ATTR_LAYOUT_TOP_TO_BOTTOM_OF,
    ATTR_LAYOUT_BOTTOM_TO_TOP_OF,
    ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF,
    ATTR_LAYOUT_BASELINE_TO_BASELINE_OF,
    ATTR_LAYOUT_START_TO_END_OF,
    ATTR_LAYOUT_START_TO_START_OF,
    ATTR_LAYOUT_END_TO_START_OF,
    ATTR_LAYOUT_END_TO_END_OF,
    ATTR_LAYOUT_GONE_MARGIN_LEFT,
    ATTR_LAYOUT_GONE_MARGIN_TOP,
    ATTR_LAYOUT_GONE_MARGIN_RIGHT,
    ATTR_LAYOUT_GONE_MARGIN_BOTTOM,
    ATTR_LAYOUT_GONE_MARGIN_START,
    ATTR_LAYOUT_GONE_MARGIN_END,
    ATTR_LAYOUT_HORIZONTAL_BIAS,
    ATTR_LAYOUT_VERTICAL_BIAS,
    ATTR_LAYOUT_WIDTH_DEFAULT,
    ATTR_LAYOUT_HEIGHT_DEFAULT,
    ATTR_LAYOUT_WIDTH_MIN,
    ATTR_LAYOUT_WIDTH_MAX,
    ATTR_LAYOUT_HEIGHT_MIN,
    ATTR_LAYOUT_HEIGHT_MAX,
    ATTR_LAYOUT_LEFT_CREATOR,
    ATTR_LAYOUT_TOP_CREATOR,
    ATTR_LAYOUT_RIGHT_CREATOR,
    ATTR_LAYOUT_BOTTOM_CREATOR,
    ATTR_LAYOUT_BASELINE_CREATOR,
    ATTR_LAYOUT_DIMENSION_RATIO,
    ATTR_LAYOUT_HORIZONTAL_WEIGHT,
    ATTR_LAYOUT_VERTICAL_WEIGHT,
    ATTR_LAYOUT_HORIZONTAL_CHAIN_STYLE,
    ATTR_LAYOUT_VERTICAL_CHAIN_STYLE,
  };
  public static final String[] ourLayoutAttributesToClear = {
    ATTR_LAYOUT_MARGIN,
    ATTR_LAYOUT_MARGIN_LEFT,
    ATTR_LAYOUT_MARGIN_START,
    ATTR_LAYOUT_MARGIN_RIGHT,
    ATTR_LAYOUT_MARGIN_END,
    ATTR_LAYOUT_MARGIN_TOP,
    ATTR_LAYOUT_MARGIN_BOTTOM,
    ATTR_LAYOUT_MARGIN_START,
  };

  static {
    ourReciprocalAttributes = new HashMap<>();
    ourReciprocalAttributes.put(ATTR_LAYOUT_LEFT_TO_LEFT_OF, ATTR_LAYOUT_LEFT_TO_RIGHT_OF);
    ourReciprocalAttributes.put(ATTR_LAYOUT_LEFT_TO_RIGHT_OF, ATTR_LAYOUT_LEFT_TO_LEFT_OF);
    ourReciprocalAttributes.put(ATTR_LAYOUT_RIGHT_TO_LEFT_OF, ATTR_LAYOUT_RIGHT_TO_RIGHT_OF);
    ourReciprocalAttributes.put(ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, ATTR_LAYOUT_RIGHT_TO_LEFT_OF);
    ourReciprocalAttributes.put(ATTR_LAYOUT_TOP_TO_TOP_OF, ATTR_LAYOUT_TOP_TO_BOTTOM_OF);
    ourReciprocalAttributes.put(ATTR_LAYOUT_TOP_TO_BOTTOM_OF, ATTR_LAYOUT_TOP_TO_TOP_OF);
    ourReciprocalAttributes.put(ATTR_LAYOUT_BOTTOM_TO_TOP_OF, ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF);
    ourReciprocalAttributes.put(ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, ATTR_LAYOUT_BOTTOM_TO_TOP_OF);

    ourMapMarginAttributes = new HashMap<>();
    ourMapMarginAttributes.put(ATTR_LAYOUT_LEFT_TO_LEFT_OF, ATTR_LAYOUT_MARGIN_LEFT);
    ourMapMarginAttributes.put(ATTR_LAYOUT_LEFT_TO_RIGHT_OF, ATTR_LAYOUT_MARGIN_LEFT);
    ourMapMarginAttributes.put(ATTR_LAYOUT_RIGHT_TO_LEFT_OF, ATTR_LAYOUT_MARGIN_RIGHT);
    ourMapMarginAttributes.put(ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, ATTR_LAYOUT_MARGIN_RIGHT);
    ourMapMarginAttributes.put(ATTR_LAYOUT_TOP_TO_TOP_OF, ATTR_LAYOUT_MARGIN_TOP);
    ourMapMarginAttributes.put(ATTR_LAYOUT_TOP_TO_BOTTOM_OF, ATTR_LAYOUT_MARGIN_TOP);
    ourMapMarginAttributes.put(ATTR_LAYOUT_BOTTOM_TO_TOP_OF, ATTR_LAYOUT_MARGIN_BOTTOM);
    ourMapMarginAttributes.put(ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, ATTR_LAYOUT_MARGIN_BOTTOM);

    ourMapSideToOriginAnchors = new HashMap<>();
    ourMapSideToOriginAnchors.put(ATTR_LAYOUT_LEFT_TO_LEFT_OF, AnchorTarget.Type.LEFT);
    ourMapSideToOriginAnchors.put(ATTR_LAYOUT_LEFT_TO_RIGHT_OF, AnchorTarget.Type.LEFT);
    ourMapSideToOriginAnchors.put(ATTR_LAYOUT_RIGHT_TO_LEFT_OF, AnchorTarget.Type.RIGHT);
    ourMapSideToOriginAnchors.put(ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, AnchorTarget.Type.RIGHT);
    ourMapSideToOriginAnchors.put(ATTR_LAYOUT_TOP_TO_TOP_OF, AnchorTarget.Type.TOP);
    ourMapSideToOriginAnchors.put(ATTR_LAYOUT_TOP_TO_BOTTOM_OF, AnchorTarget.Type.TOP);
    ourMapSideToOriginAnchors.put(ATTR_LAYOUT_BOTTOM_TO_TOP_OF, AnchorTarget.Type.BOTTOM);
    ourMapSideToOriginAnchors.put(ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, AnchorTarget.Type.BOTTOM);
    ourMapSideToOriginAnchors.put(ATTR_LAYOUT_BASELINE_TO_BASELINE_OF, AnchorTarget.Type.BASELINE);

    ourMapSideToTargetAnchors = new HashMap<>();
    ourMapSideToTargetAnchors.put(ATTR_LAYOUT_LEFT_TO_LEFT_OF, AnchorTarget.Type.LEFT);
    ourMapSideToTargetAnchors.put(ATTR_LAYOUT_LEFT_TO_RIGHT_OF, AnchorTarget.Type.RIGHT);
    ourMapSideToTargetAnchors.put(ATTR_LAYOUT_RIGHT_TO_LEFT_OF, AnchorTarget.Type.LEFT);
    ourMapSideToTargetAnchors.put(ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, AnchorTarget.Type.RIGHT);
    ourMapSideToTargetAnchors.put(ATTR_LAYOUT_TOP_TO_TOP_OF, AnchorTarget.Type.TOP);
    ourMapSideToTargetAnchors.put(ATTR_LAYOUT_TOP_TO_BOTTOM_OF, AnchorTarget.Type.BOTTOM);
    ourMapSideToTargetAnchors.put(ATTR_LAYOUT_BOTTOM_TO_TOP_OF, AnchorTarget.Type.TOP);
    ourMapSideToTargetAnchors.put(ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, AnchorTarget.Type.BOTTOM);
    ourMapSideToTargetAnchors.put(ATTR_LAYOUT_BASELINE_TO_BASELINE_OF, AnchorTarget.Type.BASELINE);

    ourLeftAttributes = new ArrayList<>();
    ourLeftAttributes.add(ATTR_LAYOUT_LEFT_TO_LEFT_OF);
    ourLeftAttributes.add(ATTR_LAYOUT_LEFT_TO_RIGHT_OF);

    ourTopAttributes = new ArrayList<>();
    ourTopAttributes.add(ATTR_LAYOUT_TOP_TO_TOP_OF);
    ourTopAttributes.add(ATTR_LAYOUT_TOP_TO_BOTTOM_OF);

    ourRightAttributes = new ArrayList<>();
    ourRightAttributes.add(ATTR_LAYOUT_RIGHT_TO_LEFT_OF);
    ourRightAttributes.add(ATTR_LAYOUT_RIGHT_TO_RIGHT_OF);

    ourBottomAttributes = new ArrayList<>();
    ourBottomAttributes.add(ATTR_LAYOUT_BOTTOM_TO_TOP_OF);
    ourBottomAttributes.add(ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF);

    ourBaselineAttributes = new ArrayList<>();
    ourBaselineAttributes.add(ATTR_LAYOUT_BASELINE_TO_BASELINE_OF);

    ourMarginAttributes = new ArrayList<>();
    ourMarginAttributes.add(ATTR_LAYOUT_MARGIN);
    ourMarginAttributes.add(ATTR_LAYOUT_MARGIN_LEFT);
    // ourMarginAttributes.add(SdkConstants.ATTR_LAYOUT_MARGIN_START);
    ourMarginAttributes.add(ATTR_LAYOUT_MARGIN_RIGHT);
    // ourMarginAttributes.add(SdkConstants.ATTR_LAYOUT_MARGIN_END);
    ourMarginAttributes.add(ATTR_LAYOUT_MARGIN_TOP);
    ourMarginAttributes.add(ATTR_LAYOUT_MARGIN_BOTTOM);

    ourHorizontalAttributes = new ArrayList<>();
    ourHorizontalAttributes.add(ATTR_LAYOUT_LEFT_TO_LEFT_OF);
    ourHorizontalAttributes.add(ATTR_LAYOUT_LEFT_TO_RIGHT_OF);
    ourHorizontalAttributes.add(ATTR_LAYOUT_RIGHT_TO_LEFT_OF);
    ourHorizontalAttributes.add(ATTR_LAYOUT_RIGHT_TO_RIGHT_OF);

    ourVerticalAttributes = new ArrayList<>();
    ourVerticalAttributes.add(ATTR_LAYOUT_TOP_TO_TOP_OF);
    ourVerticalAttributes.add(ATTR_LAYOUT_TOP_TO_BOTTOM_OF);
    ourVerticalAttributes.add(ATTR_LAYOUT_BOTTOM_TO_TOP_OF);
    ourVerticalAttributes.add(ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF);
    ourVerticalAttributes.add(ATTR_LAYOUT_BASELINE_TO_BASELINE_OF);

    ourCreatorAttributes = new ArrayList<>();
    ourCreatorAttributes.add(ATTR_LAYOUT_LEFT_CREATOR);
    ourCreatorAttributes.add(ATTR_LAYOUT_TOP_CREATOR);
    ourCreatorAttributes.add(ATTR_LAYOUT_RIGHT_CREATOR);
    ourCreatorAttributes.add(ATTR_LAYOUT_BOTTOM_CREATOR);
    ourCreatorAttributes.add(ATTR_LAYOUT_BASELINE_CREATOR);
    ourCreatorAttributes.add(ATTR_LAYOUT_CENTER_CREATOR);
    ourCreatorAttributes.add(ATTR_LAYOUT_CENTER_X_CREATOR);
    ourCreatorAttributes.add(ATTR_LAYOUT_CENTER_Y_CREATOR);
  }

  /**
   * Given a NlComponent and an attribute, return the corresponding AnchorTarget
   *
   * @param scene
   * @param targetComponent
   * @param attribute
   * @return
   */
  public static AnchorTarget getOriginAnchor(Scene scene, NlComponent targetComponent, String attribute) {
    AnchorTarget.Type type = ourMapSideToOriginAnchors.get(attribute);
    SceneComponent component = scene.getSceneComponent(targetComponent);
    if (component != null) {
      return getAnchorTarget(component, type);
    }
    return null;
  }

  /**
   * Given a NlComponent and an attribute, return the corresponding AnchorTarget
   *
   * @param scene
   * @param targetComponent
   * @param attribute
   * @return
   */
  public static AnchorTarget getTargetAnchor(Scene scene, NlComponent targetComponent, String attribute) {
    AnchorTarget.Type type = ourMapSideToTargetAnchors.get(attribute);
    SceneComponent component = scene.getSceneComponent(targetComponent);
    if (component != null) {
      return getAnchorTarget(component, type);
    }
    return null;
  }

  public static AnchorTarget getAnchorTarget(@NotNull SceneComponent component, @NotNull AnchorTarget.Type type) {
    for (Target target : component.getTargets()) {
      if (target instanceof AnchorTarget) {
        if (((AnchorTarget) target).getType() == type) {
          return (AnchorTarget) target;
        }
      }
    }
    return null;
  }

  private static boolean hasConstraints(NlComponent component, String uri, ArrayList<String> constraints) {
    int count = constraints.size();
    for (int i = 0; i < count; i++) {
      if (component.getLiveAttribute(uri, constraints.get(i)) != null) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasHorizontalConstraints(NlComponent component) {
    return hasConstraints(component, SHERPA_URI, ourHorizontalAttributes);
  }

  public static boolean hasVerticalConstraints(NlComponent component) {
    return hasConstraints(component, SHERPA_URI, ourVerticalAttributes);
  }

  /**
   * Return a dp value correctly resolved. This is only intended for generic
   * dimensions (number + unit). Do not use this if the string can contain
   * wrap_content or match_parent. See {@link #getLayoutDimensionDpValue(NlComponent, String)}.
   *
   * @param component the component we are looking at
   * @param value     the attribute value we want to parse
   * @return the value of the attribute in Dp, or zero if impossible to resolve
   */
  public static int getDpValue(@NotNull NlComponent component, String value) {
    if (value != null) {
      Configuration configuration = component.getModel().getConfiguration();
      ResourceResolver resourceResolver = configuration.getResourceResolver();
      if (resourceResolver != null) {
        Integer px = ViewEditor.resolveDimensionPixelSize(resourceResolver, value, configuration);
        return px == null ? 0 : (int)(0.5f + px / (configuration.getDensity().getDpiValue() / 160.0f));
      }
    }
    return 0;
  }

  public static int getGuidelineMode(SceneComponent component) {
    NlComponent nlComponent = component.getNlComponent();
    String begin = nlComponent.getLiveAttribute(SHERPA_URI, LAYOUT_CONSTRAINT_GUIDE_BEGIN);
    String end = nlComponent.getLiveAttribute(SHERPA_URI, LAYOUT_CONSTRAINT_GUIDE_END);

    if (begin != null) {
      return BEGIN;
    }
    else if (end != null) {
      return END;
    }
    else {
      return PERCENT;
    }
  }

  public static void clearAttributes(NlComponent component) {
    AttributesTransaction transaction = component.startAttributeTransaction();
    clearAllAttributes(component, transaction);
    transaction.apply();

    NlModel nlModel = component.getModel();
    Project project = nlModel.getProject();
    XmlFile file = nlModel.getFile();

    String label = "Cleared all constraints";
    WriteCommandAction action = new WriteCommandAction(project, label, file) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        transaction.commit();
      }
    };
    action.execute();
  }

  public static void setDpAttribute(String uri, String attribute, AttributesTransaction transaction, int value) {
    String position = String.format(VALUE_N_DP, value);
    transaction.setAttribute(uri, attribute, position);
  }

  public static void clearAttributes(String uri, ArrayList<String> attributes, AttributesTransaction transaction) {
    int count = attributes.size();
    for (int i = 0; i < count; i++) {
      String attribute = attributes.get(i);
      transaction.setAttribute(uri, attribute, null);
    }
  }

  public static void clearAttributes(String uri,  String[] attributes, AttributesTransaction transaction) {
     for (int i = 0; i < attributes.length; i++) {
      transaction.setAttribute(uri, attributes[i], null);
    }
  }
  public static void clearConnections(NlComponent component, ArrayList<String> attributes, AttributesTransaction transaction) {
    int count = attributes.size();
    for (int i = 0; i < count; i++) {
      String attribute = attributes.get(i);
      transaction.setAttribute(SHERPA_URI, attribute, null);
    }
    if (attributes == ourLeftAttributes) {
      transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_LEFT, null);
      // transaction.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_START, null);
      transaction.setAttribute(SHERPA_URI, ATTR_LAYOUT_HORIZONTAL_BIAS, null);
    }
    else if (attributes == ourRightAttributes) {
      transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_RIGHT, null);
      // transaction.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_END, null);
      transaction.setAttribute(SHERPA_URI, ATTR_LAYOUT_HORIZONTAL_BIAS, null);
    }
    else if (attributes == ourTopAttributes) {
      transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_TOP, null);
      transaction.setAttribute(SHERPA_URI, ATTR_LAYOUT_VERTICAL_BIAS, null);
    }
    else if (attributes == ourBottomAttributes) {
      transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_BOTTOM, null);
      transaction.setAttribute(SHERPA_URI, ATTR_LAYOUT_VERTICAL_BIAS, null);
    }
    if (!hasHorizontalConstraints(component)) {
      int offsetX = 0;
      NlComponent parent = component.getParent();
      if (parent != null) {
        offsetX = component.x - parent.x;
        // convert px to dp
        float dpiFactor = component.getModel().getConfiguration().getDensity().getDpiValue() / 160f;
        offsetX = (int)(0.5f + offsetX / dpiFactor);
      }
      setDpAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X, transaction, offsetX);
    }
    if (!hasVerticalConstraints(component)) {
      int offsetY = 0;
      NlComponent parent = component.getParent();
      if (parent != null) {
        offsetY = component.y - parent.y;
        // convert px to dp
        float dpiFactor = component.getModel().getConfiguration().getDensity().getDpiValue() / 160f;
        offsetY = (int)(0.5f + offsetY / dpiFactor);
      }
      setDpAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, transaction, offsetY);
    }
  }

  private static void clearAllAttributes(NlComponent component, AttributesTransaction transaction) {
    clearAttributes(SdkConstants.SHERPA_URI, ourConstraintLayoutAttributesToClear, transaction);
    clearAttributes(SdkConstants.ANDROID_URI, ourLayoutAttributesToClear, transaction);
    int offsetX = Coordinates.pxToDp(component.getModel(), component.x - (component.isRoot() ? 0 : component.getParent().x));
    int offsetY = Coordinates.pxToDp(component.getModel(), component.y - (component.isRoot() ? 0 : component.getParent().y));
    setDpAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X, transaction, offsetX);
    setDpAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, transaction, offsetY);
  }

  public static void updateOnDelete(NlComponent component, String targetId) {
    AttributesTransaction transaction = null;
    transaction = updateOnDelete(component, ourLeftAttributes, transaction, targetId);
    transaction = updateOnDelete(component, ourTopAttributes, transaction, targetId);
    transaction = updateOnDelete(component, ourRightAttributes, transaction, targetId);
    transaction = updateOnDelete(component, ourBottomAttributes, transaction, targetId);
    transaction = updateOnDelete(component, ourBaselineAttributes, transaction, targetId);

    if (transaction != null) {
      transaction.apply();
      NlModel nlModel = component.getModel();
      Project project = nlModel.getProject();
      XmlFile file = nlModel.getFile();

      String label = "Remove constraints pointing to a deleted component";
      AttributesTransaction finalTransaction = transaction;
      WriteCommandAction action = new WriteCommandAction(project, label, file) {
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          finalTransaction.commit();
        }
      };
      action.execute();
    }
  }

  public static AttributesTransaction updateOnDelete(NlComponent component,
                                                     ArrayList<String> attributes,
                                                     AttributesTransaction transaction,
                                                     String targetId) {
    if (isConnectedTo(component, attributes, targetId)) {
      if (transaction == null) {
        transaction = component.startAttributeTransaction();
      }
      clearConnections(component, attributes, transaction);
    }
    return transaction;
  }

  public static boolean isConnectedTo(NlComponent component, ArrayList<String> attributes, String targetId) {
    int count = attributes.size();
    for (int i = 0; i < count; i++) {
      String attribute = attributes.get(i);
      String target = component.getLiveAttribute(SHERPA_URI, attribute);
      target = NlComponent.extractId(target);
      if (target != null && target.equalsIgnoreCase(targetId)) {
        return true;
      }
    }
    return false;
  }

  public static void ensureHorizontalPosition(NlComponent component, AttributesTransaction transaction) {
    if (hasHorizontalConstraints(component)) {
      return;
    }
    int dx = component.x - (component.getParent() != null ? component.getParent().x : 0);
    if (dx > 0) {
      float dipValue = component.getModel().getConfiguration().getDensity().getDpiValue() / 160f;
      String position = String.format(VALUE_N_DP, ((int)(0.5f + dx / dipValue)));
      transaction.setAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X, position);
    }
  }

  public static void ensureVerticalPosition(NlComponent component, AttributesTransaction transaction) {
    if (hasVerticalConstraints(component)) {
      return;
    }
    int dy = component.y - (component.getParent() != null ? component.getParent().y : 0);
    if (dy > 0) {
      float dipValue = component.getModel().getConfiguration().getDensity().getDpiValue() / 160f;
      String position = String.format(VALUE_N_DP, ((int)(0.5f + dy / dipValue)));
      transaction.setAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, position);
    }
  }


  public static boolean isConstraintModelGreaterThan(NlModel model, int major, int ... version) {
    String constraint_artifact = SdkConstants.CONSTRAINT_LAYOUT_LIB_GROUP_ID + ":" + SdkConstants.CONSTRAINT_LAYOUT_LIB_ARTIFACT_ID;
    GradleVersion v = model.getModuleDependencyVersion(constraint_artifact);
    return (versionGreaterThan(v, major,
                               (version.length>0)?version[0]:-1,
                               (version.length>1)?version[1]:-1, 0, 0));
  }

  /**
   * Are we past a version used to implement a conditional change for other releases
   * results when alpha and beta both > 0 is undefined
   *
   * @param v
   * @param major
   * @param minor
   * @param micro
   * @param beta version of beta to check 0 if not a version of beta
   * @param alpha version of alpha to check 0 if not a version of alpha
   * @return
   */
  private static boolean versionGreaterThan(GradleVersion v, int major, int minor, int micro, int beta, int alpha) {
    if (v == null) { // if you could not get the version assume it is the latest
      return true;
    }
    if (v.getMajor() != major) {
      return v.getMajor() > major;
    }
    if (v.getMinor() != minor) {
      return (v.getMinor() > minor);
    }
    if (micro == -1) { // minor version needed to be bigger
      return false;
    }
    if (v.getMicro() != micro) {
      return (v.getMicro() > micro);
    }
    if (alpha > 0) {
      if ("alpha".equals(v.getPreviewType())) {
        return (v.getPreview() > alpha);
      }
      else { // expecting alpha but out of beta
        return true;
      }
    }
    if (beta > 0) {
      if ("beta".equals(v.getPreviewType())) {
        return (v.getPreview() > beta);
      }
      else { // expecting beta but out of beta
        return true;
      }
    }
    return false;
  }
  /////////////////////////////////////////////////////////////////////////////
  // Utility methods for Scout
  /////////////////////////////////////////////////////////////////////////////

  public static int getDpX(@NotNull NlComponent component) {
    float dpiFactor = component.getModel().getConfiguration().getDensity().getDpiValue() / 160f;
    return (int)(0.5f + component.x / dpiFactor);
  }

  public static boolean hasAttributes(@NotNull NlComponent component, String uri, ArrayList<String> attributes) {
    int count = attributes.size();
    for (int i = 0; i < count; i++) {
      String attribute = attributes.get(i);
      if (component.getLiveAttribute(uri, attribute) != null) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasAttributes(@NotNull AttributesTransaction transaction, String uri, ArrayList<String> attributes) {
    int count = attributes.size();
    for (int i = 0; i < count; i++) {
      String attribute = attributes.get(i);
      if (transaction.getAttribute(uri, attribute) != null) {
        return true;
      }
    }
    return false;
  }

  public static String getConnectionId(@NotNull NlComponent component, String uri, ArrayList<String> attributes) {
    int count = attributes.size();
    for (int i = 0; i < count; i++) {
      String attribute = component.getLiveAttribute(uri, attributes.get(i));
      if (attribute != null) {
        return NlComponent.extractId(attribute);
      }
    }
    return null;
  }

  public static boolean hasLeft(@NotNull AttributesTransaction transaction) {
    return hasAttributes(transaction, SHERPA_URI, ourLeftAttributes);
  }

  public static boolean hasTop(@NotNull AttributesTransaction transaction) {
    return hasAttributes(transaction, SHERPA_URI, ourTopAttributes);
  }

  public static boolean hasRight(@NotNull AttributesTransaction transaction) {
    return hasAttributes(transaction, SHERPA_URI, ourRightAttributes);
  }

  public static boolean hasBottom(@NotNull AttributesTransaction transaction) {
    return hasAttributes(transaction, SHERPA_URI, ourBottomAttributes);
  }

  public static void cleanup(@NotNull AttributesTransaction transaction, @NotNull SceneComponent component) {
    boolean hasLeft = hasLeft(transaction);
    boolean hasRight = hasRight(transaction);
    boolean hasTop = hasTop(transaction);
    boolean hasBottom = hasBottom(transaction);
    boolean hasBaseline = transaction.getAttribute(SHERPA_URI, ATTR_LAYOUT_BASELINE_TO_BASELINE_OF) != null;

    if (!hasLeft) {
      transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_LEFT, null);
      // transaction.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_START, null); // TODO: handles RTL correctly
      transaction.setAttribute(SHERPA_URI, ATTR_LAYOUT_HORIZONTAL_BIAS, null);
    }
    if (!hasRight) {
      transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_RIGHT, null);
      // transaction.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_END, null); // TODO: handles RTL correctly
      transaction.setAttribute(SHERPA_URI, ATTR_LAYOUT_HORIZONTAL_BIAS, null);
    }
    if (!hasLeft && !hasRight) {
      if (transaction.getAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X) == null) {
        setDpAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X, transaction, component.getOffsetParentX());
      }
    }
    else {
      transaction.setAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X, null);
    }
    if (!hasTop) {
      transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_TOP, null);
      transaction.setAttribute(SHERPA_URI, ATTR_LAYOUT_VERTICAL_BIAS, null);
    }
    if (!hasBottom) {
      transaction.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_BOTTOM, null);
      transaction.setAttribute(SHERPA_URI, ATTR_LAYOUT_VERTICAL_BIAS, null);
    }
    if (!hasTop && !hasBottom && !hasBaseline) {
      if (transaction.getAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y) == null) {
        setDpAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, transaction, component.getOffsetParentY());
      }
    }
    else {
      transaction.setAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, null);
    }
    if (!component.allowsFixedPosition()) {
      transaction.setAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X, null);
      transaction.setAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, null);
    }
  }

  public static SceneComponent findChainHead(SceneComponent component, ArrayList<String> sideA, ArrayList<String> sideB) {
    while (true) {
      NlComponent nlComponent = component.getNlComponent();
      String attributeA = getConnectionId(nlComponent, SHERPA_URI, sideA);
      if (attributeA == null) {
        return component;
      }
      SceneComponent target = component.getScene().getSceneComponent(attributeA);
      if (target == null) {
        return component;
      }
      String attributeB = getConnectionId(target.getNlComponent(), SHERPA_URI, sideB);
      if (attributeB == null) {
        return component;
      }
      if (attributeB.equalsIgnoreCase(nlComponent.getId())) {
        component = target;
      }
      else {
        return component;
      }
    }
  }

  public static boolean isInChain(ArrayList<String> sideA, ArrayList<String> sideB, SceneComponent component) {
    String attributeA = getConnectionId(component.getNlComponent(), SHERPA_URI, sideA);
    if (attributeA != null) {
      SceneComponent target = component.getScene().getSceneComponent(attributeA);
      if (target != null) {
        String attributeB = getConnectionId(target.getNlComponent(), SHERPA_URI, sideB);
        if (attributeB != null) {
          if (attributeB.equalsIgnoreCase(component.getNlComponent().getId())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static void cycleChainStyle(@NotNull SceneComponent chainHeadComponent,
                                     @NotNull String orientationStyle,
                                     @NotNull SceneComponent component) {
    NlComponent chainHead = chainHeadComponent.getNlComponent();
    String chainStyle = chainHead.getLiveAttribute(SHERPA_URI, orientationStyle);
    if (chainStyle != null) {
      if (chainStyle.equalsIgnoreCase(ATTR_LAYOUT_CHAIN_SPREAD)) {
        chainStyle = ATTR_LAYOUT_CHAIN_SPREAD_INSIDE;
      }
      else if (chainStyle.equalsIgnoreCase(ATTR_LAYOUT_CHAIN_SPREAD_INSIDE)) {
        chainStyle = ATTR_LAYOUT_CHAIN_PACKED;
      }
      else if (chainStyle.equalsIgnoreCase(ATTR_LAYOUT_CHAIN_PACKED)) {
        chainStyle = ATTR_LAYOUT_CHAIN_SPREAD;
      }
    }
    else {
      chainStyle = ATTR_LAYOUT_CHAIN_SPREAD;
    }
    AttributesTransaction transaction = chainHead.startAttributeTransaction();
    transaction.setAttribute(SHERPA_URI, orientationStyle, chainStyle);
    transaction.apply();

    NlModel nlModel = chainHead.getModel();
    Project project = nlModel.getProject();
    XmlFile file = nlModel.getFile();

    String label = "Cycle chain style";
    WriteCommandAction action = new WriteCommandAction(project, label, file) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        transaction.commit();
      }
    };
    action.execute();

    component.getScene().needsRebuildList();
  }


  public static int getDpY(@NotNull NlComponent component) {
    float dpiFactor = component.getModel().getConfiguration().getDensity().getDpiValue() / 160f;
    return (int)(0.5f + component.y / dpiFactor);
  }

  public static int getDpWidth(@NotNull NlComponent component) {
    float dpiFactor = component.getModel().getConfiguration().getDensity().getDpiValue() / 160f;
    return (int)(0.5f + component.w / dpiFactor);
  }

  public static int getDpHeight(@NotNull NlComponent component) {
    float dpiFactor = component.getModel().getConfiguration().getDensity().getDpiValue() / 160f;
    return (int)(0.5f + component.h / dpiFactor);
  }

  public static int getDpBaseline(@NotNull NlComponent component) {
    float dpiFactor = component.getModel().getConfiguration().getDensity().getDpiValue() / 160f;
    return (int)(0.5f + component.getBaseline() / dpiFactor);
  }

  public static boolean hasBaseline(@NotNull NlComponent component) {
    return component.getBaseline() > 0;
  }

  /**
   * Test for guideline or BARRIER
   * @param component
   * @return
   */
  public static boolean isLine(@NotNull NlComponent component) {
    ViewInfo viewInfo = component.viewInfo;
    if (viewInfo == null) {
      return false;
    }
    if (component.isOrHasSuperclass(CONSTRAINT_LAYOUT_GUIDELINE)) {
      return true;
    }
    return component.isOrHasSuperclass(CONSTRAINT_LAYOUT_BARRIER);
  }

  public static boolean isHorizontalGuideline(@NotNull NlComponent component) {
    if (component.viewInfo != null && component.viewInfo.getClassName().equalsIgnoreCase(CONSTRAINT_LAYOUT_GUIDELINE)) {
      String orientation = component.getAttribute(SHERPA_URI, ATTR_ORIENTATION);
      if (orientation != null && orientation.equalsIgnoreCase(ATTR_GUIDELINE_ORIENTATION_HORIZONTAL)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isVerticalGuideline(@NotNull NlComponent component) {
    if (component.viewInfo != null && component.viewInfo.getClassName().equalsIgnoreCase(CONSTRAINT_LAYOUT_GUIDELINE)) {
      String orientation = component.getAttribute(SHERPA_URI, ATTR_ORIENTATION);
      if (orientation != null && orientation.equalsIgnoreCase(ATTR_GUIDELINE_ORIENTATION_VERTICAL)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isHorizontalResizable(@NotNull NlComponent component) {
    String dimension = component.getAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH);
    if (dimension == null) {
      return false;
    }
    if (dimension.equalsIgnoreCase(VALUE_MATCH_CONSTRAINT)) {
      return true;
    }
    return false;
  }

  public static boolean isVerticalResizable(@NotNull NlComponent component) {
    String dimension = component.getAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
    if (dimension == null) {
      return false;
    }
    if (dimension.equalsIgnoreCase(VALUE_MATCH_CONSTRAINT)) {
      return true;
    }
    return false;
  }

  public static boolean hasUserResizedHorizontally(@NotNull NlComponent component) {
    String dimension = component.getAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH);
    if (dimension.equalsIgnoreCase(VALUE_WRAP_CONTENT)) {
      return false;
    }

    return true;
  }

  public static boolean hasUserResizedVertically(@NotNull NlComponent component) {
    String dimension = component.getAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

    if (dimension.equalsIgnoreCase(VALUE_WRAP_CONTENT)) {
      return false;
    }

    return true;
  }

  public static int getMargin(@NotNull NlComponent component, String margin_attr) {
    int margin = 0;

    String marginString = component.getLiveAttribute(NS_RESOURCES, margin_attr);
    if (marginString == null) {
      if (ATTR_LAYOUT_MARGIN_LEFT.equalsIgnoreCase(margin_attr)) { // left check if it is start
        marginString = component.getLiveAttribute(NS_RESOURCES, ATTR_LAYOUT_MARGIN_START);
      }
      else { // right check if it is end
        marginString = component.getLiveAttribute(NS_RESOURCES, ATTR_LAYOUT_MARGIN_END);
      }
    }
    if (marginString != null) {
      if (marginString.startsWith("@")) {
        // TODO handle isMarginReference = true;
      }
    }
    float dpiFactor = component.getModel().getConfiguration().getDensity().getDpiValue() / 160f;
    return (int)(0.5f + margin / dpiFactor);
  }

  public static void setScoutAbsoluteDpX(@NotNull NlComponent component, @AndroidDpCoordinate int dp) {
    setScoutAttributeValue(component, TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X, dp);
  }

  public static void setScoutAbsoluteDpY(@NotNull NlComponent component, @AndroidDpCoordinate int dp) {
    setScoutAttributeValue(component, TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, dp);
  }

  public static void setScoutAbsoluteDpWidth(@NotNull NlComponent component, @AndroidDpCoordinate int dp) {
    setScoutAttributeValue(component, ANDROID_URI, ATTR_LAYOUT_WIDTH, dp);
  }

  public static void setScoutAbsoluteDpHeight(@NotNull NlComponent component, @AndroidDpCoordinate int dp) {
    setScoutAttributeValue(component, ANDROID_URI, ATTR_LAYOUT_HEIGHT, dp);
  }

  public static void setScoutVerticalBiasPercent(@NotNull NlComponent component, @AndroidDpCoordinate float value) {
    AttributesTransaction transaction = component.startAttributeTransaction();
    transaction.setAttribute(SHERPA_URI, ATTR_LAYOUT_HORIZONTAL_BIAS, Float.toString(value));
    transaction.apply();
  }

  public static void setScoutHorizontalBiasPercent(@NotNull NlComponent component, @AndroidDpCoordinate float value) {
    AttributesTransaction transaction = component.startAttributeTransaction();
    transaction.setAttribute(SHERPA_URI, ATTR_LAYOUT_HORIZONTAL_BIAS, Float.toString(value));
    transaction.apply();
  }

  public static void setScoutAttributeValue(@NotNull NlComponent component, @NotNull String uri,
                                            @NotNull String attribute, @AndroidDpCoordinate int dp) {
    if (dp <= 0) {
      return;
    }
    String position = String.format(VALUE_N_DP, dp);
    AttributesTransaction transaction = component.startAttributeTransaction();
    transaction.setAttribute(uri, attribute, position);
    transaction.apply();
  }

  public static void scoutClearAttributes(@NotNull NlComponent component, ArrayList<String> attributes) {
    AttributesTransaction transaction = component.startAttributeTransaction();
    clearConnections(component, attributes, transaction);
    transaction.apply();
  }

  public static void setScoutAttributeValue(@NotNull NlComponent component, @NotNull String uri,
                                            @NotNull String attribute, @NotNull String value) {
    AttributesTransaction transaction = component.startAttributeTransaction();
    transaction.setAttribute(uri, attribute, value);
    transaction.apply();
  }

  public static boolean isConstraintLayout(@NotNull NlComponent component) {
    return component.isOrHasSuperclass(CONSTRAINT_LAYOUT)
           || component.getTag().getName().equals(CONSTRAINT_LAYOUT); // used during layout conversion
  }

  // ordered the same as Direction enum
  public static String[][] ATTRIB_MATRIX = {
    {ATTR_LAYOUT_TOP_TO_TOP_OF, ATTR_LAYOUT_TOP_TO_BOTTOM_OF, null, null, null},
    {ATTR_LAYOUT_BOTTOM_TO_TOP_OF, ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, null, null, null},
    {null, null, ATTR_LAYOUT_LEFT_TO_LEFT_OF, ATTR_LAYOUT_LEFT_TO_RIGHT_OF, null},
    {null, null, ATTR_LAYOUT_RIGHT_TO_LEFT_OF, ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, null},
    {null, null, null, null, ATTR_LAYOUT_BASELINE_TO_BASELINE_OF}
  };
  public static String[][] ATTRIB_CLEAR = {
    {ATTR_LAYOUT_TOP_TO_TOP_OF, ATTR_LAYOUT_TOP_TO_BOTTOM_OF, ATTR_LAYOUT_BASELINE_TO_BASELINE_OF},
    {ATTR_LAYOUT_BOTTOM_TO_TOP_OF, ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, ATTR_LAYOUT_BASELINE_TO_BASELINE_OF},
    {ATTR_LAYOUT_LEFT_TO_LEFT_OF, ATTR_LAYOUT_LEFT_TO_RIGHT_OF},
    {ATTR_LAYOUT_RIGHT_TO_LEFT_OF, ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, ATTR_LAYOUT_TO_START_OF},
    {ATTR_LAYOUT_BASELINE_TO_BASELINE_OF}
  };

  public static String[] ATTRIB_MARGIN = {
    ATTR_LAYOUT_MARGIN_TOP,
    ATTR_LAYOUT_MARGIN_BOTTOM,
    ATTR_LAYOUT_MARGIN_LEFT,
    ATTR_LAYOUT_MARGIN_RIGHT
  };

  public static void scoutConnect(NlComponent source, Direction sourceDirection, NlComponent target, Direction targetDirection, int margin) {
    int srcIndex = sourceDirection.ordinal();
    String attrib = ATTRIB_MATRIX[srcIndex][targetDirection.ordinal()];
    if (attrib == null) {
      System.err.println("cannot connect " + sourceDirection + " to " + targetDirection);
    }
    ArrayList<String> list = new ArrayList<String>();
    for (int i = 0; i < ATTRIB_CLEAR[srcIndex].length; i++) {
      String clr_attr = ATTRIB_CLEAR[srcIndex][i];
      if (!attrib.equals(clr_attr)) {
        list.add(clr_attr);
      }
    }
    final AttributesTransaction transaction = source.startAttributeTransaction();
    clearAttributes(SHERPA_URI, list, transaction);
    String targetId = null;
    if (target == source.getParent()) {
      targetId = ATTR_PARENT;
    }
    else {
      targetId = NEW_ID_PREFIX + target.ensureLiveId();
    }
    transaction.setAttribute(SHERPA_URI, attrib, targetId);
    if ((srcIndex <= Direction.BASELINE.ordinal()) && (margin > 0)) {
      transaction.setAttribute(ANDROID_URI, ATTRIB_MARGIN[srcIndex], margin + "dp");
    }
    transaction.apply();
  }
}
