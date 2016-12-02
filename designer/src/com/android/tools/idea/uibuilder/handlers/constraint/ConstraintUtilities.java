/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.support.constraint.solver.widgets.*;
import com.android.SdkConstants;
import com.android.ide.common.repository.GradleVersion;
import com.android.ide.common.resources.ResourceResolver;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.refactoring.rtl.RtlSupportProcessor;
import com.android.tools.idea.uibuilder.actions.ConvertToConstraintLayoutAction;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.sherpa.drawing.decorator.TextWidget;
import com.android.tools.sherpa.drawing.decorator.WidgetDecorator;
import com.android.tools.sherpa.interaction.WidgetInteractionTargets;
import com.android.tools.sherpa.scout.Scout;
import com.android.tools.sherpa.structure.WidgetCompanion;
import com.android.tools.sherpa.structure.WidgetsScene;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import static com.android.tools.idea.res.ResourceHelper.resolveStringValue;

/**
 * Utility functions managing translation of constants from the solver to the NlModel attributes
 */
public class ConstraintUtilities {

  final static int MINIMUM_SIZE = 48; // in dp
  final static int MINIMUM_SIZE_EXPAND = 6; // in dp
  private static HashMap<String, Integer> alignmentMap = new HashMap<>();

  static {
    alignmentMap.put(SdkConstants.TextAlignment.CENTER, TextWidget.TEXT_ALIGNMENT_CENTER);
    alignmentMap.put(SdkConstants.TextAlignment.TEXT_START, TextWidget.TEXT_ALIGNMENT_VIEW_START);
    alignmentMap.put(SdkConstants.TextAlignment.TEXT_END, TextWidget.TEXT_ALIGNMENT_VIEW_END);
    alignmentMap.put(SdkConstants.TextAlignment.VIEW_START, TextWidget.TEXT_ALIGNMENT_VIEW_START);
    alignmentMap.put(SdkConstants.TextAlignment.VIEW_END, TextWidget.TEXT_ALIGNMENT_VIEW_END);
  }

  /**
   * Return the corresponding margin attribute for the given anchor
   *
   * @param anchor the anchor we want to use
   * @return the margin attribute
   */
  @Nullable
  static String getConnectionAttributeMargin(@Nullable ConstraintAnchor anchor) {
    if (anchor != null) {
      switch (anchor.getType()) {
        case LEFT: {
          return SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
        }
        case TOP: {
          return SdkConstants.ATTR_LAYOUT_MARGIN_TOP;
        }
        case RIGHT: {
          return SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
        }
        case BOTTOM: {
          return SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM;
        }
      }
    }
    return null;
  }

  /**
   * Return the corresponding RTL margin attribute for the given anchor
   *
   * @param anchor the anchor we want to use
   * @return the margin attribute
   */
  @Nullable
  static String getConnectionRtlAttributeMargin(@Nullable ConstraintAnchor anchor) {
    if (anchor != null) {
      switch (anchor.getType()) {
        case LEFT: {
          return SdkConstants.ATTR_LAYOUT_MARGIN_START;
        }
        case RIGHT: {
          return SdkConstants.ATTR_LAYOUT_MARGIN_END;
        }
      }
    }
    return null;
  }

  /**
   * Return the corresponding connection attribute given an origin and a target pair of anchors
   *
   * @param origin the anchor the connection starts from
   * @param target the anchor the connection ends with
   * @return the connection attribute
   */
  @SuppressWarnings("EnumSwitchStatementWhichMissesCases")
  @Nullable
  static String getConnectionAttribute(@Nullable ConstraintAnchor origin, @Nullable ConstraintAnchor target) {
    String attribute = null;
    if (origin != null && target != null) {
      switch (origin.getType()) {
        case BASELINE: {
          if (target.getType() == ConstraintAnchor.Type.BASELINE) {
            attribute = SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF;
          }
          break;
        }
        case LEFT: {
          switch (target.getType()) {
            case LEFT: {
              attribute = SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF;
              break;
            }
            case RIGHT: {
              attribute = SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF;
              break;
            }
          }
          break;
        }
        case RIGHT: {
          switch (target.getType()) {
            case LEFT: {
              attribute = SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF;
              break;
            }
            case RIGHT: {
              attribute = SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF;
              break;
            }
          }
          break;
        }
        case TOP: {
          switch (target.getType()) {
            case TOP: {
              attribute = SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF;
              break;
            }
            case BOTTOM: {
              attribute = SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF;
              break;
            }
          }
          break;
        }
        case BOTTOM: {
          switch (target.getType()) {
            case TOP: {
              attribute = SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF;
              break;
            }
            case BOTTOM: {
              attribute = SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF;
              break;
            }
          }
          break;
        }
      }
    }
    return attribute;
  }

  /**
   * Return the corresponding creator attribute for the given anchor
   *
   * @param anchor the anchor we want to use
   * @return the creator attribute
   */
  @Nullable
  static String getConnectionAttributeCreator(@Nullable ConstraintAnchor anchor) {
    if (anchor != null) {
      //noinspection EnumSwitchStatementWhichMissesCases
      switch (anchor.getType()) {
        case LEFT: {
          return SdkConstants.ATTR_LAYOUT_LEFT_CREATOR;
        }
        case TOP: {
          return SdkConstants.ATTR_LAYOUT_TOP_CREATOR;
        }
        case RIGHT: {
          return SdkConstants.ATTR_LAYOUT_RIGHT_CREATOR;
        }
        case BOTTOM: {
          return SdkConstants.ATTR_LAYOUT_BOTTOM_CREATOR;
        }
        case BASELINE: {
          return SdkConstants.ATTR_LAYOUT_BASELINE_CREATOR;
        }
        case CENTER: {
          return SdkConstants.ATTR_LAYOUT_CENTER_CREATOR;
        }
        case CENTER_X: {
          return SdkConstants.ATTR_LAYOUT_CENTER_X_CREATOR;
        }
        case CENTER_Y: {
          return SdkConstants.ATTR_LAYOUT_CENTER_Y_CREATOR;
        }
      }
    }
    return null;
  }

  /**
   * Reset potential attributes related to the given anchor type
   *
   * @param attributes the attributes we work on
   * @param anchorType the anchor type
   */
  public static void resetAnchor(@NotNull NlAttributesHolder attributes, @NotNull ConstraintAnchor.Type anchorType) {
    //noinspection EnumSwitchStatementWhichMissesCases
    switch (anchorType) {
      case LEFT: {
        // TODO: don't reset start, reset the correct margin
        attributes.setAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_START, null);
        attributes.setAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, null);
        attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF, null);
        attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF, null);
        attributes.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_LEFT_CREATOR, null);
        attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS, null);
        break;
      }
      case TOP: {
        attributes.setAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_TOP, null);
        attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, null);
        attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF, null);
        attributes.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_TOP_CREATOR, null);
        attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, null);
        break;
      }
      case RIGHT: {
        // TODO: don't reset end, reset the correct margin
        attributes.setAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_END, null);
        attributes.setAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, null);
        attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF, null);
        attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, null);
        attributes.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_RIGHT_CREATOR, null);
        attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS, null);
        break;
      }
      case BOTTOM: {
        attributes.setAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM, null);
        attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF, null);
        attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, null);
        attributes.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_CREATOR, null);
        attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, null);
        break;
      }
      case BASELINE: {
        attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF, null);
        attributes.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_BASELINE_CREATOR, null);
        attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, null);
      }
      break;
    }
  }

  /**
   * Set the position of the attributes
   *
   * @param widget     the constraint widget we work on
   * @param attributes the associated attributes we work on
   * @param x          x position (in Dp)
   * @param y          y position (in Dp)
   */
  public static void setEditorPosition(@Nullable ConstraintWidget widget, @NotNull NlAttributesHolder attributes,
                                       @AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    String attributeX = SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X;
    String attributeY = SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y;
    if (widget != null && hasHorizontalConstraints(widget)) {
      attributes.setAttribute(SdkConstants.TOOLS_URI, attributeX, null);
    }
    else {
      String sX = String.format(SdkConstants.VALUE_N_DP, x);
      attributes.setAttribute(SdkConstants.TOOLS_URI, attributeX, sX);
    }
    if (widget != null && hasVerticalConstraints(widget)) {
      attributes.setAttribute(SdkConstants.TOOLS_URI, attributeY, null);
    }
    else {
      String sY = String.format(SdkConstants.VALUE_N_DP, y);
      attributes.setAttribute(SdkConstants.TOOLS_URI, attributeY, sY);
    }
  }

  /**
   * Clear all editor absolute positions
   *
   * @param attributes
   */
  public static void clearEditorPosition(@NotNull NlAttributesHolder attributes) {
    attributes.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, null);
    attributes.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, null);
  }

  /**
   * Return true if the widget has horizontal constraints
   *
   * @param widget the widget we work on
   * @return true if horizontal constraints set, false otherwise
   */
  private static boolean hasHorizontalConstraints(@NotNull ConstraintWidget widget) {
    ConstraintAnchor left = widget.getAnchor(ConstraintAnchor.Type.LEFT);
    ConstraintAnchor right = widget.getAnchor(ConstraintAnchor.Type.RIGHT);
    return (left != null && left.isConnected()) || (right != null && right.isConnected());
  }

  /**
   * Return true if the widget has vertical constraints
   *
   * @param widget the widget we work on
   * @return true if vertical constraints set, false otherwise
   */
  private static boolean hasVerticalConstraints(@NotNull ConstraintWidget widget) {
    ConstraintAnchor top = widget.getAnchor(ConstraintAnchor.Type.TOP);
    ConstraintAnchor bottom = widget.getAnchor(ConstraintAnchor.Type.BOTTOM);
    ConstraintAnchor baseline = widget.getAnchor(ConstraintAnchor.Type.BASELINE);
    return (top != null && top.isConnected()) || (bottom != null && bottom.isConnected()) || (baseline != null && baseline.isConnected());
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

  private static boolean useParentReference(NlModel model) {
    String constraint_artifact = SdkConstants.CONSTRAINT_LAYOUT_LIB_GROUP_ID + ":" + SdkConstants.CONSTRAINT_LAYOUT_LIB_ARTIFACT_ID;
    GradleVersion v = model.getModuleDependencyVersion(constraint_artifact);
    return (versionGreaterThan(v, 1, 0, 0, 0, 4));
  }

  private static boolean useGuidelineFloat(NlModel model) {
    String constraint_artifact = SdkConstants.CONSTRAINT_LAYOUT_LIB_GROUP_ID + ":" + SdkConstants.CONSTRAINT_LAYOUT_LIB_ARTIFACT_ID;
    GradleVersion v = model.getModuleDependencyVersion(constraint_artifact);
    return (versionGreaterThan(v, 1, 0, 0, 0, 5));
  }

  /**
   * Update the attributes given a new anchor
   *
   * @param attributes the attributes we work on
   * @param anchor     the anchor we want to update from
   */
  static void setConnection(@NotNull NlAttributesHolder attributes, @NotNull ConstraintAnchor anchor) {
    resetAnchor(attributes, anchor.getType());

    String attribute = getConnectionAttribute(anchor, anchor.getTarget());
    String marginAttribute = getConnectionAttributeMargin(anchor);
    String marginAttributeRtl = getConnectionRtlAttributeMargin(anchor);

    if (anchor.isConnected() && attribute != null) {
      ConstraintWidget owner = anchor.getOwner();
      WidgetCompanion ownerCompanion = (WidgetCompanion)owner.getCompanionWidget();
      ConstraintWidget target = anchor.getTarget().getOwner();
      WidgetCompanion companion = (WidgetCompanion)target.getCompanionWidget();
      NlComponent targetComponent = (NlComponent)companion.getWidgetModel();

      String targetId;

      boolean use_parent_ref = useParentReference(targetComponent.getModel());
      if (owner.getParent() == target && use_parent_ref) {
        targetId = SdkConstants.ATTR_PARENT;
      } else {
        targetId = SdkConstants.NEW_ID_PREFIX + targetComponent.ensureId();
      }

      attributes.setAttribute(SdkConstants.SHERPA_URI, attribute, targetId);

      String margin = ownerCompanion.getWidgetProperties().getMarginValue(anchor);
      String marginRtl = ownerCompanion.getWidgetProperties().getMarginRtlValue(anchor);
      String marginValue = null;

      if (anchor.getMargin() > 0) {
        marginValue = String.format(SdkConstants.VALUE_N_DP, anchor.getMargin());
      }

      // if not a reference we need to update it to the new values

      if (margin != null && !ownerCompanion.getWidgetProperties().isMarginReference(anchor)) {
        margin = marginValue;
      }
      if (marginRtl != null && !ownerCompanion.getWidgetProperties().isMarginRtlReference(anchor)) {
        marginRtl = marginValue;
      }

      NlModel model = targetComponent.getModel();
      if (supportsStartEnd(anchor, model)) { // If we need to set RTL attributes
        if (marginRtl == null) {
          if (margin != null) {
            marginRtl = margin;
          }
          else {
            marginRtl = marginValue;
          }
        }
        if (requiresRightLeft(model)) { // If in addition to RTL attribute we need right/left...
          if (margin == null) {
            margin = marginRtl;
          }
        }
      }
      else if (margin == null) {
        margin = marginValue;
      }

      if (marginAttribute != null && margin != null) {
        attributes.setAttribute(SdkConstants.NS_RESOURCES, marginAttribute, margin);
      }

      if (marginAttributeRtl != null && marginRtl != null) {
        attributes.setAttribute(SdkConstants.NS_RESOURCES, marginAttributeRtl, marginRtl);
      }

      String attributeCreator = getConnectionAttributeCreator(anchor);
      if (anchor.getConnectionCreator() != 0) {
        attributes.setAttribute(SdkConstants.TOOLS_URI,
                                attributeCreator, String.valueOf(anchor.getConnectionCreator()));
      }
      else {
        attributes.setAttribute(SdkConstants.TOOLS_URI,
                                attributeCreator, null);
      }
    }
  }

  /**
   * Returns true if the given attribute is an RTL-affected one
   *
   * @param attribute
   * @return
   */
  static boolean isRtlMargin(String attribute) {
    if (SdkConstants.ATTR_LAYOUT_MARGIN_LEFT.equals(attribute)
        || SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT.equals(attribute)) {
      return true;
    }
    return false;
  }

  @NotNull
  static String getRtlMarginAttribute(String attribute, TextDirection direction) {
    if (SdkConstants.ATTR_LAYOUT_MARGIN_LEFT.equals(attribute)) {
      return direction.getAttrMarginLeft();
    }
    else if (SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT.equals(attribute)) {
      return direction.getAttrMarginRight();
    }
    return direction.getAttrMarginLeft();
  }

  /**
   * Update the attributes given a new dimension
   *  @param attributes the attributes we work on
   * @param widget     the widget we use as a model
   */
  public static void setDimension(@NotNull NlAttributesHolder attributes, @NotNull ConstraintWidget widget) {
    String width;
    switch (widget.getHorizontalDimensionBehaviour()) {
      case MATCH_CONSTRAINT: {
        width = "0dp";
      }
      break;
      case WRAP_CONTENT: {
        width = SdkConstants.VALUE_WRAP_CONTENT;
      }
      break;
      default:
        width = String.format(SdkConstants.VALUE_N_DP, widget.getWidth());
    }
    attributes.setAttribute(SdkConstants.ANDROID_URI,
                            SdkConstants.ATTR_LAYOUT_WIDTH,
                            width);
    String height;
    switch (widget.getVerticalDimensionBehaviour()) {
      case MATCH_CONSTRAINT: {
        height = "0dp";
      }
      break;
      case WRAP_CONTENT: {
        height = SdkConstants.VALUE_WRAP_CONTENT;
      }
      break;
      default:
        height = String.format(SdkConstants.VALUE_N_DP, widget.getHeight());
    }
    attributes.setAttribute(SdkConstants.ANDROID_URI,
                            SdkConstants.ATTR_LAYOUT_HEIGHT,
                            height);
  }

  private static String chainStyleToString(int style) {
    switch (style) {
      case ConstraintWidget.CHAIN_SPREAD:
        return SdkConstants.ATTR_LAYOUT_CHAIN_SPREAD;
      case ConstraintWidget.CHAIN_SPREAD_INSIDE:
        return SdkConstants.ATTR_LAYOUT_CHAIN_SPREAD_INSIDE;
      case ConstraintWidget.CHAIN_PACKED:
        return SdkConstants.ATTR_LAYOUT_CHAIN_PACKED;
    }
    return null;
  }

  /**
   * @param attributes
   * @param widget
   */
  static void setHorizontalChainStyle(@NotNull NlAttributesHolder attributes, @NotNull ConstraintWidget widget) {
    int style = widget.getHorizontalChainStyle();
    if (style == ConstraintWidget.CHAIN_SPREAD) {
      // If it's the default, remove it
      attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_HORIZONTAL_CHAIN_STYLE, null);
    } else {
      attributes.setAttribute(SdkConstants.SHERPA_URI,
                              SdkConstants.ATTR_LAYOUT_HORIZONTAL_CHAIN_STYLE,
                              chainStyleToString(style));
    }
  }

  /**
   * @param attributes
   * @param widget
   */
  static void setVerticalChainStyle(@NotNull NlAttributesHolder attributes, @NotNull ConstraintWidget widget) {
    int style = widget.getVerticalChainStyle();
    if (style == ConstraintWidget.CHAIN_SPREAD) {
      // If it's the default, remove it
      attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_CHAIN_STYLE, null);
    } else {
      attributes.setAttribute(SdkConstants.SHERPA_URI,
                              SdkConstants.ATTR_LAYOUT_VERTICAL_CHAIN_STYLE,
                              chainStyleToString(style));
    }
  }

  /**
   * Update the attributes Dimension Ratio
   *
   * @param attributes the attributes we work on
   * @param widget     the widget we use as a model
   */
  static void setRatio(@NotNull NlAttributesHolder attributes, @NotNull ConstraintWidget widget) {
    WidgetCompanion companion = (WidgetCompanion)widget.getCompanionWidget();
    attributes.setAttribute(SdkConstants.SHERPA_URI,
                            SdkConstants.ATTR_LAYOUT_DIMENSION_RATIO,
                            companion.getWidgetProperties().getDimensionRatio());
  }

  /**
   * Update the attributes horizontal bias
   *
   * @param attributes the attributes we work on
   * @param widget     the widget we use as a model
   */
  static void setHorizontalBias(@NotNull NlAttributesHolder attributes, @NotNull ConstraintWidget widget) {
    float bias = widget.getHorizontalBiasPercent();
    if (bias != 0.5f) {
      attributes.setAttribute(SdkConstants.SHERPA_URI,
                              SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS, String.valueOf(bias));
    }
  }

  /**
   * Update the component horizontal bias
   *  @param component the component we work on
   * @param widget    the widget we use as a model
   */
  static void setVerticalBias(@NotNull NlAttributesHolder component, @NotNull ConstraintWidget widget) {
    float bias = widget.getVerticalBiasPercent();
    if (bias != 0.5f) {
      component.setAttribute(SdkConstants.SHERPA_URI,
                             SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, String.valueOf(bias));
    }
  }

  /**
   * Update the component with the values from a Guideline widget
   *  @param model     the component NlModel
   * @param component the component we work on
   * @param guideline the widget we use as a model
   */
  static void commitGuideline(NlModel model, @NotNull NlAttributesHolder component, @NotNull Guideline guideline) {
    int behaviour = guideline.getRelativeBehaviour();
    WidgetCompanion companion = (WidgetCompanion)guideline.getCompanionWidget();
    component.setAttribute(SdkConstants.SHERPA_URI,
                           SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN, null);
    component.setAttribute(SdkConstants.SHERPA_URI,
                           SdkConstants.LAYOUT_CONSTRAINT_GUIDE_END, null);
    component.setAttribute(SdkConstants.SHERPA_URI,
                           SdkConstants.LAYOUT_CONSTRAINT_GUIDE_PERCENT, null);
    component.setAttribute(SdkConstants.SHERPA_URI,
                           SdkConstants.LAYOUT_CONSTRAINT_DEPRECATED_GUIDE_PERCENT, null);
    String previousValue = companion.getWidgetProperties().getGuidelineAttribute();
    if (previousValue != null && !previousValue.startsWith("@")) {
      previousValue = null;
    }
    String value = previousValue;
    if (behaviour == Guideline.RELATIVE_PERCENT) {
      boolean useFloat = useGuidelineFloat(model);
      if (value == null) {
        float percent = guideline.getRelativePercent();
        if (useFloat) {
          value = String.valueOf(percent);
        } else {
          value = String.valueOf((int) (percent * 100));
        }
      }
      String percentAttribute = useFloat ? SdkConstants.LAYOUT_CONSTRAINT_GUIDE_PERCENT
                                         : SdkConstants.LAYOUT_CONSTRAINT_DEPRECATED_GUIDE_PERCENT;
      component.setAttribute(SdkConstants.SHERPA_URI,
                             percentAttribute,
                             value);
    }
    else if (behaviour == Guideline.RELATIVE_BEGIN) {
      if (value == null) {
        value = String.format(SdkConstants.VALUE_N_DP, guideline.getRelativeBegin());
      }
      component.setAttribute(SdkConstants.SHERPA_URI,
                             SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN,
                             value);
    }
    else if (behaviour == Guideline.RELATIVE_END) {
      if (value == null) {
        value = String.format(SdkConstants.VALUE_N_DP, guideline.getRelativeEnd());
      }
      component.setAttribute(SdkConstants.SHERPA_URI,
                             SdkConstants.LAYOUT_CONSTRAINT_GUIDE_END,
                             value);
    }
  }

  /**
   * Set the horizontal or vertical bias of the widget
   *
   * @param attribute horizontal or vertical bias attribute string
   * @param component the component we are looking at
   * @param widget    the constraint widget we set the bias on
   */
  static void setBias(@NotNull String attribute, @NotNull NlComponent component, @NotNull ConstraintWidget widget) {
    AttributesTransaction attributes = component.startAttributeTransaction();
    String biasString = attributes.getAttribute(SdkConstants.SHERPA_URI, attribute);
    float bias = parseFloat(biasString, 0.5f);
    if (attribute.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS)) {
      widget.setHorizontalBiasPercent(bias);
    }
    else {
      widget.setVerticalBiasPercent(bias);
    }
  }

  /**
   * Set the dimension ratio of the widget
   *
   * @param attribute dimension ratio (xx:xx)
   * @param component the component we are looking at
   * @param widget    the constraint widget we set the bias on
   */
  static void setDimensionRatio(@NotNull String attribute, @NotNull NlComponent component, @NotNull ConstraintWidget widget) {
    AttributesTransaction attributes = component.startAttributeTransaction();
    String dimensionRatioString = attributes.getAttribute(SdkConstants.SHERPA_URI, attribute);
    widget.setDimensionRatio(dimensionRatioString);
  }

  /**
   * Set the ChainPack flag widget
   *
   * @param attribute layout_constraintVertical_chainPacked or layout_constraintHorizontal_chainPacked
   * @param component the component we are looking at
   * @param widget    the constraint widget we set the bias on
   */
  static void setChainStyle(@NotNull String attribute, @NotNull NlComponent component, @NotNull ConstraintWidget widget) {
    String chainStyleString = component.getAttribute(SdkConstants.SHERPA_URI, attribute);
    if (chainStyleString != null) {
      int style = ConstraintWidget.CHAIN_SPREAD;
      if (chainStyleString.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_CHAIN_SPREAD)) {
        style = ConstraintWidget.CHAIN_SPREAD;
      } else if (chainStyleString.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_CHAIN_SPREAD_INSIDE)) {
        style = ConstraintWidget.CHAIN_SPREAD_INSIDE;
      } else if (chainStyleString.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_CHAIN_PACKED)) {
        style = ConstraintWidget.CHAIN_PACKED;
      }
      if (SdkConstants.ATTR_LAYOUT_HORIZONTAL_CHAIN_STYLE.equals(attribute)) {
        widget.setHorizontalChainStyle(style);
      } else {
        widget.setVerticalChainStyle(style);
      }
    }
  }

  /**
   * Set the Weight of a widget
   *
   * @param attribute vertical or horizontal weight attributes
   * @param component the component we are looking at
   * @param widget    the constraint widget we set the bias on
   */
  static void setChainWeight(@NotNull String attribute, @NotNull NlComponent component, @NotNull ConstraintWidget widget) {
    AttributesTransaction attributes = component.startAttributeTransaction();
    String chainWeightString = attributes.getAttribute(SdkConstants.SHERPA_URI, attribute);
    float weight = parseFloat(chainWeightString, 0.f);
    if (SdkConstants.ATTR_LAYOUT_HORIZONTAL_WEIGHT.equals(attribute)) {
      widget.setHorizontalWeight(weight);
    }
    else {
      widget.setVerticalWeight(weight);
    }
  }

  private static float parseFloat(String string, float defaultValue) {
    float ret = defaultValue;
    if (string != null && string.length() > 0) {
      try {
        ret = Float.parseFloat(string);
      }
      catch (NumberFormatException e) {
      }
    }
    return ret;
  }

  /**
   * Set start margin on a constraint widget if defined
   *
   * @param left      the left attribute (we'll only set the margin if the attribute exists)
   * @param component the component we are looking at
   * @param widget    the constraint widget we set the margin on
   */
  static void setStartMargin(@Nullable String left, @NotNull NlComponent component, @NotNull ConstraintWidget widget) {
    if (left != null) {
      int margin = getMargin(component, SdkConstants.ATTR_LAYOUT_MARGIN_START);
      widget.getAnchor(ConstraintAnchor.Type.LEFT).setMargin(margin);
    }
  }

  /**
   * Set end margin on a constraint widget if defined
   *
   * @param right     the right attribute (we'll only set the margin if the attribute exists)
   * @param component the component we are looking at
   * @param widget    the constraint widget we set the margin on
   */
  static void setEndMargin(@Nullable String right, @NotNull NlComponent component, @NotNull ConstraintWidget widget) {
    if (right != null) {
      int margin = getMargin(component, SdkConstants.ATTR_LAYOUT_MARGIN_END);
      widget.getAnchor(ConstraintAnchor.Type.RIGHT).setMargin(margin);
    }
  }

  /**
   * Set Top Margin on a constraint widget if defined
   *
   * @param top       the top attribute (we'll only set the margin if the attribute exists)
   * @param component the component we are looking at
   * @param widget    the constraint widget we set the margin on
   */
  static void setTopMargin(@Nullable String top, @NotNull NlComponent component, @NotNull ConstraintWidget widget) {
    if (top != null) {
      int margin = getMargin(component, SdkConstants.ATTR_LAYOUT_MARGIN_TOP);
      widget.getAnchor(ConstraintAnchor.Type.TOP).setMargin(margin);
    }
  }

  /**
   * Set Bottom Margin on a constraint widget if defined
   *
   * @param bottom    the bottom attribute (we'll only set the margin if the attribute exists)
   * @param component the component we are looking at
   * @param widget    the constraint widget we set the margin on
   */
  static void setBottomMargin(@Nullable String bottom, @NotNull NlComponent component, @NotNull ConstraintWidget widget) {
    if (bottom != null) {
      int margin = getMargin(component, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM);
      widget.getAnchor(ConstraintAnchor.Type.BOTTOM).setMargin(margin);
    }
  }

  /**
   * Gets the specified margin value in dp. If the specified margin is
   * SdkConstants.ATTR_LAYOUT_MARGIN_START or SdkConstants.ATTR_LAYOUT_MARGIN_END
   * and cannot be found, this method falls back to SdkConstants.ATTR_LAYOUT_MARGIN_LEFT
   * or SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT.
   *
   * @param component the component we are looking at
   * @param widget    the margin attribute name
   * @return the margin in dp or 0 if it cannot be found
   */
  static int getMargin(@NotNull NlComponent component, @NotNull String attr) {
    AttributesTransaction attributes = component.startAttributeTransaction();
    String margin = attributes.getAttribute(SdkConstants.NS_RESOURCES, attr);
    if (margin == null) {
      if (attr == SdkConstants.ATTR_LAYOUT_MARGIN_START) {
        margin = attributes.getAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT);
      }
      else if (attr == SdkConstants.ATTR_LAYOUT_MARGIN_END) {
        margin = attributes.getAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
      }
    }
    if (margin != null) {
      return getDpValue(component, margin);
    }
    return 0;
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

  /**
   * Return a dp value correctly resolved. Returns -1 for match_parent or -2 for wrap_content.
   *
   * @param component the component we are looking at
   * @param value     the attribute value we want to parse
   * @return the value of the attribute in Dp, or zero if impossible to resolve
   */
  private static int getLayoutDimensionDpValue(@NotNull NlComponent component, String value) {
    if (SdkConstants.VALUE_WRAP_CONTENT.equalsIgnoreCase(value)) return -2;
    if (SdkConstants.VALUE_MATCH_PARENT.equalsIgnoreCase(value)) return -1;
    return getDpValue(component, value);
  }

  /**
   * Create a new connection on the widget
   *
   * @param model        the current NlModel
   * @param widgetsScene the current widgets scene
   * @param targetID     ID of our target
   * @param widgetSrc    our source widget
   * @param constraintA  the source anchor type
   * @param constraintB  the target anchor type
   */
  static void setTarget(@NotNull NlModel model,
                        @NotNull WidgetsScene widgetsScene,
                        @Nullable String targetID,
                        @Nullable ConstraintWidget widgetSrc,
                        @NotNull ConstraintAnchor.Type constraintA,
                        @NotNull ConstraintAnchor.Type constraintB) {
    if (targetID == null) {
      return;
    }
    NlComponent componentFound = null;
    ConstraintWidget parent = widgetSrc.getParent();
    if (useParentReference(model)
        && targetID.equalsIgnoreCase(SdkConstants.ATTR_PARENT) && parent != null) {
      WidgetCompanion companion = (WidgetCompanion)parent.getCompanionWidget();
      componentFound = (NlComponent)companion.getWidgetModel();
    } else {
      String id = NlComponent.extractId(targetID);
      if (id == null) {
        return;
      }
      for (NlComponent component : model.getComponents()) {
        NlComponent found = getComponentFromId(component, id);
        if (found != null) {
          componentFound = found;
          break;
        }
      }
    }
    if (componentFound != null) {
      ConstraintWidget widget = widgetsScene.getWidget(componentFound);
      if (widgetSrc != null && widget != null) {
        int connectionCreator = 0;
        WidgetCompanion companion = (WidgetCompanion)widgetSrc.getCompanionWidget();
        NlComponent component = (NlComponent)companion.getWidgetModel();
        String creatorAttribute = getConnectionAttributeCreator(widgetSrc.getAnchor(constraintA));
        String creatorValue = component.startAttributeTransaction().getAttribute(SdkConstants.TOOLS_URI, creatorAttribute);
        if (creatorValue != null) {
          try {
            connectionCreator = Integer.parseInt(creatorValue);
          }
          catch (NumberFormatException e) {
            connectionCreator = 0;
          }
        }
        if (constraintA == constraintB && constraintA == ConstraintAnchor.Type.BASELINE) {
          widgetSrc.getAnchor(constraintA).connect(widget.getAnchor(constraintB), 0, -1,
                                                   ConstraintAnchor.Strength.STRONG, connectionCreator, true);
        }
        else {
          widgetSrc.connect(constraintA, widget, constraintB, 0, ConstraintAnchor.Strength.STRONG, connectionCreator);
        }
      }
    }
  }

  /**
   * Utility method looking up a component from its ID
   *
   * @param component component to look in
   * @param id        the id to find
   * @return the component if found, null otherwise
   */
  @Nullable
  static NlComponent getComponentFromId(@NotNull NlComponent component, @NotNull String id) {
    // TODO: move this method to NlModel
    if (useParentReference(component.getModel()) && id.equalsIgnoreCase(SdkConstants.ATTR_PARENT)) {
      return component.getParent();
    }
    if (component.getId() != null && component.getId().equalsIgnoreCase(id)) {
      return component;
    }
    for (NlComponent child : component.getChildren()) {
      NlComponent found = getComponentFromId(child, id);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  /**
   * Set a margin on a connected anchor
   *
   * @param widget the widget we operate on
   * @param margin the margin to set
   * @param type   the type of the anchor
   */
  private static void setMargin(@NotNull ConstraintWidget widget, int margin, @NotNull ConstraintAnchor.Type type) {
    if (widget.getAnchor(type).isConnected()) {
      widget.getAnchor(type).setMargin(margin);
    }
  }

  /**
   * Update the constraint widget with the component information (coming from XML)
   * @param constraintModel the constraint model we are working with
   * @param widget          constraint widget
   * @param component       the model component
   * @return true if need to save the xml
   */
  static boolean updateWidgetFromComponent(@NotNull ConstraintModel constraintModel,
                           @Nullable ConstraintWidget widget,
                           @Nullable NlComponent component) {
    if (component == null || widget == null) {
      return false;
    }

    AttributesTransaction attributes = component.startAttributeTransaction();

    if (!(widget instanceof Guideline)) {
      widget.setVisibility(component.getAndroidViewVisibility());
    }
    widget.setDebugName(component.getId());
    WidgetsScene scene = constraintModel.getScene();
    Insets padding = component.getPadding(true);
    if (widget instanceof ConstraintWidgetContainer) {
      int paddingLeft = constraintModel.pxToDp(padding.left);
      int paddingTop = constraintModel.pxToDp(padding.top);
      int paddingRight = constraintModel.pxToDp(padding.right);
      int paddingBottom = constraintModel.pxToDp(padding.bottom);
      ((ConstraintWidgetContainer)widget).setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
      widget.setDimension(constraintModel.pxToDp(component.w) - paddingLeft - paddingRight,
                          constraintModel.pxToDp(component.h) - paddingTop - paddingBottom);
    }
    else {
      widget.setDimension(constraintModel.pxToDp(component.w),
                          constraintModel.pxToDp(component.h));
    }
    String absoluteWidth = attributes.getAttribute(SdkConstants.TOOLS_URI,
                                                   ConvertToConstraintLayoutAction.ATTR_LAYOUT_CONVERSION_ABSOLUTE_WIDTH);
    if (absoluteWidth != null) {
      Configuration configuration = component.getModel().getConfiguration();
      ResourceResolver resourceResolver = configuration.getResourceResolver();
      int size = ViewEditor.resolveDimensionPixelSize(resourceResolver, absoluteWidth, configuration);
      size = constraintModel.pxToDp(size);
      widget.setWidth(size);
    }

    String absoluteHeight = attributes.getAttribute(SdkConstants.TOOLS_URI,
                                                    ConvertToConstraintLayoutAction.ATTR_LAYOUT_CONVERSION_ABSOLUTE_HEIGHT);
    if (absoluteHeight != null) {
      Configuration configuration = component.getModel().getConfiguration();
      ResourceResolver resourceResolver = configuration.getResourceResolver();
      int size = ViewEditor.resolveDimensionPixelSize(resourceResolver, absoluteHeight, configuration);
      size = constraintModel.pxToDp(size);
      widget.setHeight(size);
    }

    widget.setMinWidth(constraintModel.pxToDp(component.getMinimumWidth()));
    widget.setMinHeight(constraintModel.pxToDp(component.getMinimumHeight()));

    NlComponent parent = component.getParent();
    NlModel model = component.getModel();
    if (parent != null) {
      ConstraintWidget parentWidget = scene.getWidget(parent);
      if (parentWidget instanceof WidgetContainer) {
        WidgetContainer parentContainerWidget = (WidgetContainer)parentWidget;
        if (widget.getParent() != parentContainerWidget) {
          parentContainerWidget.add(widget);
        }
      }
    }

    // First set the origin of the widget

    int x = constraintModel.pxToDp(component.x);
    int y = constraintModel.pxToDp(component.y);
    if (widget instanceof ConstraintWidgetContainer) {
      x += constraintModel.pxToDp(padding.left);
      y += constraintModel.pxToDp(padding.top);
    }
    WidgetContainer parentContainer = (WidgetContainer)widget.getParent();
    if (parentContainer != null) {
      if (!(parentContainer instanceof ConstraintWidgetContainer)) {
        x = constraintModel.pxToDp(component.x - component.getParent().x);
        y = constraintModel.pxToDp(component.y - component.getParent().y);
      }
      else {
        x -= parentContainer.getDrawX();
        y -= parentContainer.getDrawY();
      }
    }

    String absoluteX = attributes.getAttribute(SdkConstants.TOOLS_URI,
                                               ConvertToConstraintLayoutAction.ATTR_LAYOUT_CONVERSION_ABSOLUTE_X);
    if (absoluteX != null) {
      Configuration configuration = component.getModel().getConfiguration();
      ResourceResolver resourceResolver = configuration.getResourceResolver();
      int position = ViewEditor.resolveDimensionPixelSize(resourceResolver, absoluteX, configuration);
      x = constraintModel.pxToDp(position);
    }

    String absoluteY = attributes.getAttribute(SdkConstants.TOOLS_URI,
                                               ConvertToConstraintLayoutAction.ATTR_LAYOUT_CONVERSION_ABSOLUTE_Y);
    if (absoluteY != null) {
      Configuration configuration = component.getModel().getConfiguration();
      ResourceResolver resourceResolver = configuration.getResourceResolver();
      int position = ViewEditor.resolveDimensionPixelSize(resourceResolver, absoluteY, configuration);
      y = constraintModel.pxToDp(position);
    }

    if (widget.getX() != x || widget.getY() != y) {
      widget.setOrigin(x, y);
      widget.forceUpdateDrawPosition();
    }

    boolean overrideDimension = false;

    // FIXME: need to agree on the correct magic value for this rather than simply using zero.
    String layout_width = attributes.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
    if (component.w == 0 || getLayoutDimensionDpValue(component, layout_width) == 0) {
      widget.setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.MATCH_CONSTRAINT);
    }
    else if (layout_width != null && layout_width.equalsIgnoreCase(SdkConstants.VALUE_WRAP_CONTENT)) {
      if (widget.getWidth() < MINIMUM_SIZE && widget instanceof WidgetContainer
          && ((WidgetContainer) widget).getChildren().size() == 0) {
        widget.setWidth(MINIMUM_SIZE);
        widget.setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.FIXED);
        overrideDimension = true;
      } else {
        widget.setWrapWidth(widget.getWidth());
        widget.setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.WRAP_CONTENT);
      }
    }
    else if (layout_width != null && layout_width.equalsIgnoreCase(SdkConstants.VALUE_MATCH_PARENT)) {
      if (isWidgetInsideConstraintLayout(widget)) {
        if (widget.getAnchor(ConstraintAnchor.Type.LEFT).isConnected()
            && widget.getAnchor(ConstraintAnchor.Type.RIGHT).isConnected()) {
          widget.setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.MATCH_CONSTRAINT);
        }
        else {
          widget.setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.FIXED);
          widget.setWidth(MINIMUM_SIZE_EXPAND);
          int height = widget.getHeight();
          ConstraintWidget.DimensionBehaviour verticalBehaviour = widget.getVerticalDimensionBehaviour();
          if (height <= 1 && widget instanceof WidgetContainer) {
            widget.setHeight(MINIMUM_SIZE_EXPAND);
          }
          ArrayList<ConstraintWidget> widgets = new ArrayList<>();
          widgets.add(widget);
          Scout.arrangeWidgets(Scout.Arrange.ExpandHorizontally, widgets, true);
          widget.setHeight(height);
          widget.setVerticalDimensionBehaviour(verticalBehaviour);
          overrideDimension = true;
        }
      }
    }
    else {
      widget.setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.FIXED);
    }
    String layout_height = attributes.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);
    if (component.h == 0 || getLayoutDimensionDpValue(component, layout_height) == 0) {
      widget.setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.MATCH_CONSTRAINT);
    }
    else if (layout_height != null && layout_height.equalsIgnoreCase(SdkConstants.VALUE_WRAP_CONTENT)) {
      if (widget.getHeight() < MINIMUM_SIZE && widget instanceof WidgetContainer
          && ((WidgetContainer) widget).getChildren().size() == 0) {
        widget.setHeight(MINIMUM_SIZE);
        widget.setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.FIXED);
        overrideDimension = true;
      } else {
        widget.setWrapHeight(widget.getHeight());
        widget.setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.WRAP_CONTENT);
      }
    }
    else if (layout_height != null && layout_height.equalsIgnoreCase(SdkConstants.VALUE_MATCH_PARENT)) {
      if (isWidgetInsideConstraintLayout(widget)) {
        if ((widget.getAnchor(ConstraintAnchor.Type.TOP).isConnected()
             && widget.getAnchor(ConstraintAnchor.Type.BOTTOM).isConnected())) {
          widget.setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.MATCH_CONSTRAINT);
        }
        else {
          widget.setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.FIXED);
          widget.setHeight(MINIMUM_SIZE_EXPAND);
          int width = widget.getWidth();
          ConstraintWidget.DimensionBehaviour horizontalBehaviour = widget.getHorizontalDimensionBehaviour();
          if (width <= 1 && widget instanceof WidgetContainer) {
            widget.setWidth(MINIMUM_SIZE_EXPAND);
          }
          ArrayList<ConstraintWidget> widgets = new ArrayList<>();
          widgets.add(widget);
          Scout.arrangeWidgets(Scout.Arrange.ExpandVertically, widgets, true);
          widget.setWidth(width);
          widget.setHorizontalDimensionBehaviour(horizontalBehaviour);
          overrideDimension = true;
        }
      }
    }
    else {
      widget.setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.FIXED);
    }

    widget.setBaselineDistance(constraintModel.pxToDp(component.getBaseline()));
    widget.resetAnchors();

    String left1 = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF);
    String left2 = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF);
    String right1 = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF);
    String right2 = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF);

    String top1 = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF);
    String top2 = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF);
    String bottom1 = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF);
    String bottom2 = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF);
    String baseline = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF);
    String ratio = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_DIMENSION_RATIO);

    WidgetCompanion companion = (WidgetCompanion)widget.getCompanionWidget();
    companion.getWidgetProperties().clear();
    companion.getWidgetProperties().setDimensionRatio(ratio);
    setMarginType(ConstraintAnchor.Type.LEFT, component, widget);
    setMarginType(ConstraintAnchor.Type.RIGHT, component, widget);
    setMarginType(ConstraintAnchor.Type.TOP, component, widget);
    setMarginType(ConstraintAnchor.Type.BOTTOM, component, widget);

    setTarget(model, scene, left1, widget, ConstraintAnchor.Type.LEFT, ConstraintAnchor.Type.LEFT);
    setStartMargin(left1, component, widget);
    setTarget(model, scene, left2, widget, ConstraintAnchor.Type.LEFT, ConstraintAnchor.Type.RIGHT);
    setStartMargin(left2, component, widget);
    setTarget(model, scene, right1, widget, ConstraintAnchor.Type.RIGHT, ConstraintAnchor.Type.LEFT);
    setEndMargin(right1, component, widget);
    setTarget(model, scene, right2, widget, ConstraintAnchor.Type.RIGHT, ConstraintAnchor.Type.RIGHT);
    setEndMargin(right2, component, widget);

    setTarget(model, scene, top1, widget, ConstraintAnchor.Type.TOP, ConstraintAnchor.Type.TOP);
    setTopMargin(top1, component, widget);
    setTarget(model, scene, top2, widget, ConstraintAnchor.Type.TOP, ConstraintAnchor.Type.BOTTOM);
    setTopMargin(top2, component, widget);
    setTarget(model, scene, bottom1, widget, ConstraintAnchor.Type.BOTTOM, ConstraintAnchor.Type.TOP);
    setBottomMargin(bottom1, component, widget);
    setTarget(model, scene, bottom2, widget, ConstraintAnchor.Type.BOTTOM, ConstraintAnchor.Type.BOTTOM);
    setBottomMargin(bottom2, component, widget);
    setTarget(model, scene, baseline, widget, ConstraintAnchor.Type.BASELINE, ConstraintAnchor.Type.BASELINE);

    setBias(SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS, component, widget);
    setBias(SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, component, widget);

    setDimensionRatio(SdkConstants.ATTR_LAYOUT_DIMENSION_RATIO, component, widget);
    setChainStyle(SdkConstants.ATTR_LAYOUT_HORIZONTAL_CHAIN_STYLE, component, widget);
    setChainStyle(SdkConstants.ATTR_LAYOUT_VERTICAL_CHAIN_STYLE, component, widget);
    setChainWeight(SdkConstants.ATTR_LAYOUT_HORIZONTAL_WEIGHT, component, widget);
    setChainWeight(SdkConstants.ATTR_LAYOUT_VERTICAL_WEIGHT, component, widget);


    if (widget instanceof Guideline) {
      Guideline guideline = (Guideline)widget;
      setGuideline(component, guideline);
    }

    // Update text decorator
    WidgetDecorator decorator = companion.getWidgetDecorator(WidgetDecorator.BLUEPRINT_STYLE);
    if (decorator != null && decorator instanceof TextWidget) {
      TextWidget textWidget = (TextWidget)decorator;
      textWidget.setText(getResolvedText(component));

      Configuration configuration = component.getModel().getConfiguration();
      ResourceResolver resourceResolver = configuration.getResourceResolver();

      Integer size = null;

      if (resourceResolver != null) {
        String textSize = attributes.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT_SIZE);
        if (textSize != null) {
          size = ViewEditor.resolveDimensionPixelSize(resourceResolver, textSize, configuration);
        }
      }

      if (size == null) {
        // With the specified string, this method cannot return null
        //noinspection ConstantConditions
        size = ViewEditor.resolveDimensionPixelSize(resourceResolver, "15sp", configuration);
      }
      String alignment = attributes.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT_ALIGNMENT);
      textWidget.setTextAlignment((alignment == null) ? TextWidget.TEXT_ALIGNMENT_VIEW_START : alignmentMap.get(alignment));
      String single = attributes.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SINGLE_LINE);
      textWidget.setSingleLine(Boolean.parseBoolean(single));
      // Cannot be null, see previous condition
      //noinspection ConstantConditions
      textWidget.setTextSize(constraintModel.pxToDp(size));
    }

    return overrideDimension; // if true, need to update the XML
  }

  /**
   * Set the type of margin (if it contains a literal value or a dimens reference)
   *
   * @param attribute
   * @param type
   * @param component
   * @param widget
   */
  private static void setMarginType(ConstraintAnchor.Type type, NlComponent component, ConstraintWidget widget) {
    String margin = null;
    String marginRtl = null;
    switch (type) {
      case LEFT: {
        margin = component.getAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT);
        marginRtl = component.getAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_START);
      }
      break;
      case RIGHT: {
        margin = component.getAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
        marginRtl = component.getAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_END);
      }
      break;
      case TOP: {
        margin = component.getAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_TOP);
      }
      break;
      case BOTTOM: {
        margin = component.getAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM);
      }
      break;
    }
    if (margin == null && marginRtl == null) {
      return;
    }
    ConstraintAnchor anchor = widget.getAnchor(type);
    WidgetCompanion companion = (WidgetCompanion)widget.getCompanionWidget();
    if (margin != null) {
      companion.getWidgetProperties().setMarginReference(anchor, margin);
    }
    if (marginRtl != null) {
      companion.getWidgetProperties().setMarginRtlReference(anchor, marginRtl);
    }
  }

  /**
   * Update the given guideline with the attributes set in the NlComponent
   *
   * @param component the component we get the attributes from
   * @param guideline the guideline widget we want to update with the values
   */
  private static void setGuideline(NlComponent component, Guideline guideline) {
    AttributesTransaction attributes = component.startAttributeTransaction();
    String relativeBegin = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN);
    String relativeEnd = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_END);
    boolean useFloat = useGuidelineFloat(component.getModel());
    String percentAttribute = useFloat ? SdkConstants.LAYOUT_CONSTRAINT_GUIDE_PERCENT
                                       : SdkConstants.LAYOUT_CONSTRAINT_DEPRECATED_GUIDE_PERCENT;

    String relativePercent = attributes.getAttribute(SdkConstants.SHERPA_URI, percentAttribute);
    String oldRelativePercent = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_DEPRECATED_GUIDE_PERCENT);
    WidgetCompanion companion = (WidgetCompanion)guideline.getCompanionWidget();
    if (useFloat && oldRelativePercent != null) {
      // we need to upgrade
      companion.getWidgetProperties().setGuidelineAttribute(relativePercent);
      float value = 0;
      try {
          value = Integer.parseInt(oldRelativePercent) / 100f;
      }
      catch (NumberFormatException e) {
        // ignore
      }
      guideline.setGuidePercent(value);
    } else if (relativePercent != null && relativePercent.length() > 0) {
      companion.getWidgetProperties().setGuidelineAttribute(relativePercent);
      float value = 0;
      try {
        if (useFloat) {
          value = Float.parseFloat(relativePercent);
        } else {
          value = Integer.parseInt(relativePercent) / 100f;
        }
      }
      catch (NumberFormatException e) {
        // ignore
      }
      guideline.setGuidePercent(value);
    }
    else if (relativeBegin != null && relativeBegin.length() > 0) {
      companion.getWidgetProperties().setGuidelineAttribute(relativeBegin);
      try {
        int value = getDpValue(component, relativeBegin);
        guideline.setGuideBegin(value);
      }
      catch (NumberFormatException e) {
        // Ignore
      }
    }
    else if (relativeEnd != null && relativeEnd.length() > 0) {
      companion.getWidgetProperties().setGuidelineAttribute(relativeEnd);
      try {
        int value = getDpValue(component, relativeEnd);
        guideline.setGuideEnd(value);
      }
      catch (NumberFormatException e) {
        // Ignore
      }
    }
    String orientation = attributes.getAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_ORIENTATION);
    if (orientation != null) {
      int newOrientation = Guideline.HORIZONTAL;
      if (SdkConstants.ATTR_GUIDELINE_ORIENTATION_VERTICAL.equalsIgnoreCase(orientation)) {
        newOrientation = Guideline.VERTICAL;
      }
      if (newOrientation != guideline.getOrientation()) {
        guideline.setOrientation(newOrientation);
        WidgetInteractionTargets interactionTargets = companion.getWidgetInteractionTargets();
        interactionTargets.resetConstraintHandles();
      }
    }
  }

  @NotNull
  static String resolveStringResource(@NotNull NlComponent component, @NotNull String text) {
    Configuration configuration = component.getModel().getConfiguration();
    ResourceResolver resourceResolver = configuration.getResourceResolver();
    if (resourceResolver != null) {
      return resolveStringValue(resourceResolver, text);
    }
    return "";
  }

  @NotNull
  static String getResolvedText(@NotNull NlComponent component) {
    String text = component.startAttributeTransaction().getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT);
    if (text != null) {
      if (text.startsWith(SdkConstants.PREFIX_RESOURCE_REF)) {
        return resolveStringResource(component, text);
      }
      return text;
    }
    return "";
  }

  /**
   * Utility function committing the given ConstraintModel to NlComponent,
   * either directly an AttributesTransaction to commit to disk, or via
   * a MemoryAttributesTransaction (which internally use AttributesTransaction
   * to commit, but only via reflection)
   *
   * @param model  the given ConstraintModel
   * @param commit commit to disk or not
   */
  private static void saveXmlWidgets(@NotNull ConstraintModel model, boolean commit) {
    Collection<ConstraintWidget> widgets = model.getScene().getWidgets();
    for (ConstraintWidget widget : widgets) {
      NlComponent component = getValidComponent(model, widget);
      if (component != null) {
        AttributesTransaction transaction = component.startAttributeTransaction();
        updateComponentFromWidget(model, widget, transaction);
        if (commit) {
          assert ApplicationManager.getApplication().isWriteAccessAllowed();
          transaction.commit();
        } else {
          transaction.apply();
        }
      }
    }
  }

  /**
   * Utility function to commit to the NlModel the current state of all widgets
   *
   * @param nlModel
   * @param commit  if false, the changes are only reflected in memory not saved to the XML file.
   */
  static void saveModelToXML(@NotNull NlModel nlModel, boolean commit) {
    ConstraintModel model = ConstraintModel.getConstraintModel(nlModel);
    if (commit) {
      Project project = nlModel.getProject();
      XmlFile file = nlModel.getFile();

      String label = "Constraint";
      WriteCommandAction action = new WriteCommandAction(project, label, file) {
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          saveXmlWidgets(model, commit);
        }
      };
      action.execute();
    }
    else {
      saveXmlWidgets(model, commit);
    }
  }

  /**
   * Rollback any temporary XML changes in the passed {@link NlModel}
   */
  public static void rollbackXMLChanges(@NotNull NlModel nlModel) {
    // TODO: Allow rollback without having to start a new transaction
    nlModel.getComponents().forEach(component -> component.startAttributeTransaction().rollback());
  }

  /**
   * Returns a component paired to the given widget, but only if it is a valid
   * component for us to work on.
   *
   * @param model the model we are working on
   * @param widget the constraint widget paired with the component
   * @return
   */
  static @Nullable NlComponent getValidComponent(@NotNull ConstraintModel model,
                                       @NotNull ConstraintWidget widget) {
    WidgetCompanion companion = (WidgetCompanion)widget.getCompanionWidget();
    NlComponent component = (NlComponent)companion.getWidgetModel();
    boolean isDroppedWidget = (model.getDragDropWidget() == widget);
    boolean isInsideConstraintLayout = isWidgetInsideConstraintLayout(widget);
    if (!isDroppedWidget && (widget.isRoot() || widget.isRootContainer() || !isInsideConstraintLayout)) {
      return null;
    }
    return component;
  }

  /**
   * Update the component attributes from its corresponding ConstraintWidget
   *
   * @param model  the model we are working on
   * @param widget the widget to update from
   * @return an AttributesTransaction
   */
  static @Nullable NlAttributesHolder updateComponentFromWidget(@NotNull ConstraintModel model,
                                                         @NotNull ConstraintWidget widget,
                                                         @NotNull NlAttributesHolder transaction) {
    WidgetCompanion companion = (WidgetCompanion)widget.getCompanionWidget();
    NlComponent component = (NlComponent)companion.getWidgetModel();
    boolean isDroppedWidget = (model.getDragDropWidget() == widget);
    boolean isInsideConstraintLayout = isWidgetInsideConstraintLayout(widget);
    if (!isDroppedWidget && (widget.isRoot() || widget.isRootContainer() || !isInsideConstraintLayout)) {
      return null;
    }

    if (isInsideConstraintLayout || isDroppedWidget) {
      setEditorPosition(widget, transaction, widget.getX(), widget.getY());
    }
    else {
      clearEditorPosition(transaction);
    }

    setDimension(transaction, widget);
    setHorizontalChainStyle(transaction, widget);
    setVerticalChainStyle(transaction, widget);
    for (ConstraintAnchor anchor : widget.getAnchors()) {
      setConnection(transaction, anchor);
    }
    setRatio(transaction, widget);
    setHorizontalBias(transaction, widget);
    setVerticalBias(transaction, widget);
    if (widget instanceof Guideline) {
      commitGuideline(component.getModel(), transaction, (Guideline)widget);
    }
    return transaction;
  }

  /**
   * Returns true if the widget is a direct child of a ConstraintLayout
   * @return
   */
  private static boolean isWidgetInsideConstraintLayout(@NotNull ConstraintWidget widget) {
    ConstraintWidget parent = widget.getParent();
    if (parent == null) {
      return false;
    }
    return parent instanceof ConstraintWidgetContainer;
  }

  /**
   * Utility function to render the current model to layoutlib
   *
   * @param model the ConstraintModel we want to render
   */
  static void renderModel(@NotNull ConstraintModel model) {
    model.getNlModel().requestRender();
  }

  static AndroidVersion getCompileSdkVersion(@NotNull NlModel model) {
    return AndroidModuleInfo.get(model.getFacet()).getBuildSdkVersion();
  }

  static AndroidVersion getMinSdkVersion(@NotNull NlModel model) {
    return AndroidModuleInfo.get(model.getFacet()).getMinSdkVersion();
  }

  static AndroidVersion getTargetSdkVersion(@NotNull NlModel model) {
    return AndroidModuleInfo.get(model.getFacet()).getTargetSdkVersion();
  }

  static boolean supportsStartEnd(ConstraintAnchor anchor, @NotNull NlModel model) {
    if (anchor.getType() != ConstraintAnchor.Type.LEFT
      && anchor.getType() != ConstraintAnchor.Type.RIGHT) {
      return false;
    }
    AndroidVersion compileSdkVersion = getCompileSdkVersion(model);
    return (compileSdkVersion == null ||
            compileSdkVersion.isGreaterOrEqualThan(RtlSupportProcessor.RTL_TARGET_SDK_START)
            && getTargetSdkVersion(model).isGreaterOrEqualThan(RtlSupportProcessor.RTL_TARGET_SDK_START));
  }

  static boolean requiresRightLeft(@NotNull NlModel model) {
    return getMinSdkVersion(model).getApiLevel() < RtlSupportProcessor.RTL_TARGET_SDK_START;
  }

}
