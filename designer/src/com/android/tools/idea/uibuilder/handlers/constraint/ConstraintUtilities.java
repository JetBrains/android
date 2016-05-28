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
import com.android.ide.common.resources.ResourceResolver;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.refactoring.rtl.RtlSupportProcessor;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.sherpa.drawing.decorator.TextWidget;
import com.android.tools.sherpa.drawing.decorator.WidgetDecorator;
import com.android.tools.sherpa.interaction.WidgetInteractionTargets;
import com.android.tools.sherpa.structure.WidgetCompanion;
import com.android.tools.sherpa.structure.WidgetsScene;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;

/**
 * Utility functions managing translation of constants from the solver to the NlModel attributes
 */
public class ConstraintUtilities {

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
   * @param component  the component we work on
   * @param anchorType the anchor type
   */
  public static void resetAnchor(@NotNull NlComponent component, @NotNull ConstraintAnchor.Type anchorType) {
    //noinspection EnumSwitchStatementWhichMissesCases
    switch (anchorType) {
      case LEFT: {
        // TODO: don't reset start, reset the correct margin
        component.setAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_START, null);
        component.setAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF, null);
        component.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_LEFT_CREATOR, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS, null);
        break;
      }
      case TOP: {
        component.setAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_TOP, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF, null);
        component.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_TOP_CREATOR, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, null);
        break;
      }
      case RIGHT: {
        // TODO: don't reset end, reset the correct margin
        component.setAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_END, null);
        component.setAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, null);
        component.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_RIGHT_CREATOR, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS, null);
        break;
      }
      case BOTTOM: {
        component.setAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, null);
        component.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_CREATOR, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, null);
        break;
      }
      case BASELINE: {
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF, null);
        component.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_BASELINE_CREATOR, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, null);
      }
      break;
    }
  }

  /**
   * Set the position of the component
   *
   * @param widget    the constraint widget we work on
   * @param component the associated component we work on
   * @param x         x position (in Dp)
   * @param y         y position (in Dp)
   */
  public static void setEditorPosition(@Nullable ConstraintWidget widget, @NotNull NlComponent component,
                                       @AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    String attributeX = SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X;
    String attributeY = SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y;
    if (widget != null && hasHorizontalConstraints(widget)) {
      component.setAttribute(SdkConstants.TOOLS_URI, attributeX, null);
    } else {
      String sX = String.format(SdkConstants.VALUE_N_DP, x);
      component.setAttribute(SdkConstants.TOOLS_URI, attributeX, sX);
    }
    if (widget != null && hasVerticalConstraints(widget)) {
      component.setAttribute(SdkConstants.TOOLS_URI, attributeY, null);
    } else {
      String sY = String.format(SdkConstants.VALUE_N_DP, y);
      component.setAttribute(SdkConstants.TOOLS_URI, attributeY, sY);
    }
  }

  /**
   * Clear all editor absolute positions
   * @param component
   */
  public static void clearEditorPosition(@NotNull NlComponent component) {
    component.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, null);
    component.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, null);
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
   * Update the component given a new anchor
   *
   * @param component the component we work on
   * @param anchor    the anchor we want to update from
   */
  static void setConnection(@NotNull NlComponent component, @NotNull ConstraintAnchor anchor) {
    resetAnchor(component, anchor.getType());

    String attribute = getConnectionAttribute(anchor, anchor.getTarget());
    String marginAttribute = getConnectionAttributeMargin(anchor);

    if (anchor.isConnected() && attribute != null) {
      ConstraintWidget target = anchor.getTarget().getOwner();
      WidgetCompanion companion = (WidgetCompanion)target.getCompanionWidget();
      NlComponent targetComponent = (NlComponent)companion.getWidgetModel();
      String targetId = SdkConstants.NEW_ID_PREFIX + targetComponent.ensureId();

      component.setAttribute(SdkConstants.SHERPA_URI, attribute, targetId);

      if (marginAttribute != null && anchor.getMargin() > 0) {
        NlModel model = targetComponent.getModel();
        String margin = String.format(SdkConstants.VALUE_N_DP, anchor.getMargin());

        if (isRtlMargin(marginAttribute) && supportsStartEnd(model)) {
          if (requiresRightLeft(model)) {
            component.setAttribute(SdkConstants.NS_RESOURCES, marginAttribute, margin);
          }
          TextDirection direction = TextDirection.fromConfiguration(model.getConfiguration());
          String rtlAttribute = getRtlMarginAttribute(marginAttribute, direction);
          component.setAttribute(SdkConstants.NS_RESOURCES, rtlAttribute, margin);
        } else {
          component.setAttribute(SdkConstants.NS_RESOURCES, marginAttribute, margin);
        }
      }

      String attributeCreator = getConnectionAttributeCreator(anchor);
      if (anchor.getConnectionCreator() != 0) {
        component.setAttribute(SdkConstants.TOOLS_URI,
                               attributeCreator, String.valueOf(anchor.getConnectionCreator()));
      } else {
        component.setAttribute(SdkConstants.TOOLS_URI,
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
    } else if (SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT.equals(attribute)) {
      return direction.getAttrMarginRight();
    }
    return direction.getAttrMarginLeft();
  }

  /**
   * Update the component given a new dimension
   *
   * @param component the component we work on
   * @param widget    the widget we use as a model
   */
  public static void setDimension(@NotNull NlComponent component, @NotNull ConstraintWidget widget) {
    String width;
    switch (widget.getHorizontalDimensionBehaviour()) {
      case ANY: {
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
    component.setAttribute(SdkConstants.ANDROID_URI,
                           SdkConstants.ATTR_LAYOUT_WIDTH,
                           width);
    String height;
    switch (widget.getVerticalDimensionBehaviour()) {
      case ANY: {
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
    component.setAttribute(SdkConstants.ANDROID_URI,
                           SdkConstants.ATTR_LAYOUT_HEIGHT,
                           height);
  }

  /**
   * Update the component horizontal bias
   *
   * @param component the component we work on
   * @param widget    the widget we use as a model
   */
  static void setHorizontalBias(@NotNull NlComponent component, @NotNull ConstraintWidget widget) {
    float bias = widget.getHorizontalBiasPercent();
    if (bias != 0.5f) {
      component.setAttribute(SdkConstants.SHERPA_URI,
                             SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS, String.valueOf(bias));
    }
  }

  /**
   * Update the component horizontal bias
   *
   * @param component the component we work on
   * @param widget    the widget we use as a model
   */
  static void setVerticalBias(@NotNull NlComponent component, @NotNull ConstraintWidget widget) {
    float bias = widget.getVerticalBiasPercent();
    if (bias != 0.5f) {
      component.setAttribute(SdkConstants.SHERPA_URI,
                             SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, String.valueOf(bias));
    }
  }

  /**
   * Update the component with the values from a Guideline widget
   *
   * @param component the component we work on
   * @param guideline the widget we use as a model
   */
  static void commitGuideline(@NotNull NlComponent component, @NotNull Guideline guideline) {
    int behaviour = guideline.getRelativeBehaviour();
    component.setAttribute(SdkConstants.SHERPA_URI,
                           SdkConstants.ATTR_GUIDELINE_RELATIVE_BEGIN, null);
    component.setAttribute(SdkConstants.SHERPA_URI,
                           SdkConstants.ATTR_GUIDELINE_RELATIVE_END, null);
    component.setAttribute(SdkConstants.SHERPA_URI,
                           SdkConstants.ATTR_GUIDELINE_RELATIVE_PERCENT, null);
    if (behaviour == Guideline.RELATIVE_PERCENT) {
      String value = String.valueOf(guideline.getRelativePercent());
      component.setAttribute(SdkConstants.SHERPA_URI,
                             SdkConstants.ATTR_GUIDELINE_RELATIVE_PERCENT,
                             value);
    }
    else if (behaviour == Guideline.RELATIVE_BEGIN) {
      String value = String.format(SdkConstants.VALUE_N_DP, guideline.getRelativeBegin());
      component.setAttribute(SdkConstants.SHERPA_URI,
                             SdkConstants.ATTR_GUIDELINE_RELATIVE_BEGIN,
                             value);
    }
    else if (behaviour == Guideline.RELATIVE_END) {
      String value = String.format(SdkConstants.VALUE_N_DP, guideline.getRelativeEnd());
      component.setAttribute(SdkConstants.SHERPA_URI,
                             SdkConstants.ATTR_GUIDELINE_RELATIVE_END,
                             value);
    }
  }

  /**
   * Set the constraint strength if defined
   *
   * @param component the component we are looking at
   * @param widget    the constraint widget we set the strength on
   */
  static void setStrength(@NotNull String attribute, @NotNull ConstraintAnchor.Type type,
                          @NotNull NlComponent component, @NotNull ConstraintWidget widget) {
    String strength = component.getAttribute(SdkConstants.SHERPA_URI, attribute);
    if (strength != null) {
      if (strength.equalsIgnoreCase("weak")) {
        widget.getAnchor(type).setStrength(ConstraintAnchor.Strength.WEAK);
      }
      else if (strength.equalsIgnoreCase("strong")) {
        widget.getAnchor(type).setStrength(ConstraintAnchor.Strength.STRONG);
      }
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
    String biasString = component.getAttribute(SdkConstants.SHERPA_URI, attribute);
    float bias = 0.5f;
    if (biasString != null && biasString.length() > 0) {
      try {
        bias = Float.parseFloat(biasString);
      }
      catch (NumberFormatException e) {
      }
    }
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
    String dimensionRatioString = component.getAttribute(SdkConstants.SHERPA_URI, attribute);
    float dimensionRatio = 0f;
    if (dimensionRatioString == null || dimensionRatioString.length() == 0) {
      widget.setDimensionRatio(dimensionRatio);
      return;
    }

    int colonIndex = dimensionRatioString.indexOf(':');
    if (colonIndex >= 0 && colonIndex < dimensionRatioString.length() - 1) {
      String nominator = dimensionRatioString.substring(0, colonIndex);
      String denominator = dimensionRatioString.substring(colonIndex + 1);
      if (nominator.length() > 0 && denominator.length() > 0) {
        try {
          float nominatorValue = Float.parseFloat(nominator);
          float denominatorValue = Float.parseFloat(denominator);
          if (denominatorValue > 0) {
            dimensionRatio = nominatorValue / denominatorValue;
          }
        }
        catch (NumberFormatException e) {
        }
      }
    }
    widget.setDimensionRatio(dimensionRatio);
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
    String margin = component.getAttribute(SdkConstants.NS_RESOURCES, attr);
    if (margin == null) {
      if (attr == SdkConstants.ATTR_LAYOUT_MARGIN_START) {
        margin = component.getAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT);
      } else if (attr == SdkConstants.ATTR_LAYOUT_MARGIN_END) {
        margin = component.getAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
      }
    }
    if (margin != null) {
      return getDpValue(component, margin);
    }
    return 0;
  }

  /**
   * Return a dp value correctly resolved
   *
   * @param component the component we are looking at
   * @param value     the attribute value we want to parse
   * @return the value of the attribute in Dp, or zero if impossible to resolve
   */
  private static int getDpValue(@NotNull NlComponent component, String value) {
    if (value != null) {
      Configuration configuration = component.getModel().getConfiguration();
      ResourceResolver resourceResolver = configuration.getResourceResolver();
      if (resourceResolver != null) {
        Integer dp = ResourceHelper.resolveDimensionPixelSize(resourceResolver, value, configuration);
        return dp == null ? 0 : (int)(dp / (configuration.getDensity().getDpiValue() / 160.0f));
      }
    }
    return 0;
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
    String id = NlComponent.extractId(targetID);
    if (id == null) {
      return;
    }
    NlComponent componentFound = null;
    for (NlComponent component : model.getComponents()) {
      NlComponent found = getComponentFromId(component, id);
      if (found != null) {
        componentFound = found;
        break;
      }
    }
    if (componentFound != null) {
      ConstraintWidget widget = widgetsScene.getWidget(componentFound);
      if (widgetSrc != null && widget != null) {
        int connectionCreator = 0;
        WidgetCompanion companion = (WidgetCompanion)widgetSrc.getCompanionWidget();
        NlComponent component = (NlComponent)companion.getWidgetModel();
        String creatorAttribute = getConnectionAttributeCreator(widgetSrc.getAnchor(constraintA));
        String creatorValue = component.getAttribute(SdkConstants.TOOLS_URI, creatorAttribute);
        if (creatorValue != null) {
          connectionCreator = Integer.parseInt(creatorValue);
        }
        widgetSrc.connect(constraintA, widget, constraintB, 0, ConstraintAnchor.Strength.STRONG, connectionCreator);
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
   *
   * @param constraintModel the constraint model we are working with
   * @param widget          constraint widget
   * @param component       the model component
   * @param deepUpdate      do a thorough update or not
   */
  static void updateWidget(@NotNull ConstraintModel constraintModel,
                           @Nullable ConstraintWidget widget,
                           @Nullable NlComponent component,
                           boolean deepUpdate) {
    if (component == null || widget == null) {
      return;
    }
    // We only want to update if it's a deep update or if our component's parent ISN'T a ConstraintLayout
    // If our parent is a ConstraintLayout (or we are), it is authoritative, so no need to update anything but a deep update...
    boolean update = deepUpdate
                     || (!(widget.getParent() instanceof ConstraintWidgetContainer)
                          && !(widget instanceof ConstraintWidgetContainer));
    if (!update) {
      WidgetContainer parent = (WidgetContainer)widget.getParent();
      if (parent != null && !(parent instanceof ConstraintWidgetContainer)) {
        widget.setDimension(constraintModel.pxToDp(component.w),
                               constraintModel.pxToDp(component.h));
        int x = constraintModel.pxToDp(component.x) - parent.getDrawX();
        int y = constraintModel.pxToDp(component.y) - parent.getDrawY();
        if (widget.getX() != x || widget.getY() != y) {
          widget.setOrigin(x, y);
          widget.forceUpdateDrawPosition();
        }
      }
      widget.setBaselineDistance(constraintModel.pxToDp(component.getBaseline()));
      return;
    }
    if (!(widget instanceof Guideline)) {
      widget.setVisibility(component.getAndroidViewVisibility());
    }
    widget.setDebugName(component.getId());
    WidgetsScene scene = constraintModel.getScene();
    Insets padding = component.getPadding(true);
    if (widget instanceof ConstraintWidgetContainer) {
      widget.setDimension(constraintModel.pxToDp(component.w - padding.width()),
                          constraintModel.pxToDp(component.h - padding.height()));
    }
    else {
      widget.setDimension(constraintModel.pxToDp(component.w),
                          constraintModel.pxToDp(component.h));
    }
    String absoluteWidth = component.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_WIDTH);
    if (absoluteWidth != null) {
      Configuration configuration = component.getModel().getConfiguration();
      ResourceResolver resourceResolver = configuration.getResourceResolver();
      int size = ResourceHelper.resolveDimensionPixelSize(resourceResolver, absoluteWidth, configuration);
      size = constraintModel.pxToDp(size);
      widget.setWidth(size);
    }

    String absoluteHeight = component.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_HEIGHT);
    if (absoluteHeight != null) {
      Configuration configuration = component.getModel().getConfiguration();
      ResourceResolver resourceResolver = configuration.getResourceResolver();
      int size = ResourceHelper.resolveDimensionPixelSize(resourceResolver, absoluteHeight, configuration);
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

    // FIXME: need to agree on the correct magic value for this rather than simply using zero.
    String layout_width = component.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
    if ((component.w == 0) || (layout_width != null && (layout_width.equalsIgnoreCase("0") || layout_width.equalsIgnoreCase("0dp")))) {
      widget.setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.ANY);
    }
    else if (layout_width != null && layout_width.equalsIgnoreCase(SdkConstants.VALUE_WRAP_CONTENT)) {
      widget.setWrapWidth(widget.getWidth());
      widget.setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.WRAP_CONTENT);
    }
    else if (layout_width != null && layout_width.equalsIgnoreCase(SdkConstants.VALUE_MATCH_PARENT)) {
      widget.setWrapWidth(widget.getWidth());
      widget.setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.ANY);
    }
    else {
      widget.setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.FIXED);
    }
    String layout_height = component.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);
    if ((component.h == 0) || (layout_height != null && (layout_height.equalsIgnoreCase("0") || layout_height.equalsIgnoreCase("0dp")))) {
      widget.setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.ANY);
    }
    else if (layout_height != null && layout_height.equalsIgnoreCase(SdkConstants.VALUE_WRAP_CONTENT)) {
      widget.setWrapHeight(widget.getHeight());
      widget.setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.WRAP_CONTENT);
    }
    else if (layout_height != null && layout_height.equalsIgnoreCase(SdkConstants.VALUE_MATCH_PARENT)) {
      widget.setWrapHeight(widget.getHeight());
      widget.setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.ANY);
    }
    else {
      widget.setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.FIXED);
    }

    int x = constraintModel.pxToDp(component.x);
    int y = constraintModel.pxToDp(component.y);
    if (widget instanceof ConstraintWidgetContainer) {
      x += constraintModel.pxToDp(padding.left);
      y += constraintModel.pxToDp(padding.top);
    }
    WidgetContainer parentContainer = (WidgetContainer)widget.getParent();
    if (parentContainer != null && parentContainer instanceof WidgetContainer) {
      x -= parentContainer.getDrawX();
      y -= parentContainer.getDrawY();
    }

    if (widget.getX() != x || widget.getY() != y) {
      widget.setOrigin(x, y);
      widget.forceUpdateDrawPosition();
    }

    widget.setBaselineDistance(constraintModel.pxToDp(component.getBaseline()));
    widget.resetAnchors();

    String left1 = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF);
    String left2 = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF);
    String right1 = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF);
    String right2 = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF);
    String centerX = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_CENTER_X_TO_CENTER_X_OF);

    String top1 = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF);
    String top2 = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF);
    String bottom1 = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF);
    String bottom2 = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF);
    String baseline = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF);
    String centerY = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_CENTER_Y_TO_CENTER_Y_OF);

    setTarget(model, scene, left1, widget, ConstraintAnchor.Type.LEFT, ConstraintAnchor.Type.LEFT);
    setStartMargin(left1, component, widget);
    setTarget(model, scene, left2, widget, ConstraintAnchor.Type.LEFT, ConstraintAnchor.Type.RIGHT);
    setStartMargin(left2, component, widget);
    setTarget(model, scene, right1, widget, ConstraintAnchor.Type.RIGHT, ConstraintAnchor.Type.LEFT);
    setEndMargin(right1, component, widget);
    setTarget(model, scene, right2, widget, ConstraintAnchor.Type.RIGHT, ConstraintAnchor.Type.RIGHT);
    setEndMargin(right2, component, widget);
    setTarget(model, scene, centerX, widget, ConstraintAnchor.Type.CENTER_X, ConstraintAnchor.Type.CENTER_X);
    setStartMargin(centerX, component, widget);
    setEndMargin(centerX, component, widget);

    setTarget(model, scene, top1, widget, ConstraintAnchor.Type.TOP, ConstraintAnchor.Type.TOP);
    setTopMargin(top1, component, widget);
    setTarget(model, scene, top2, widget, ConstraintAnchor.Type.TOP, ConstraintAnchor.Type.BOTTOM);
    setTopMargin(top2, component, widget);
    setTarget(model, scene, bottom1, widget, ConstraintAnchor.Type.BOTTOM, ConstraintAnchor.Type.TOP);
    setBottomMargin(bottom1, component, widget);
    setTarget(model, scene, bottom2, widget, ConstraintAnchor.Type.BOTTOM, ConstraintAnchor.Type.BOTTOM);
    setBottomMargin(bottom2, component, widget);
    setTarget(model, scene, baseline, widget, ConstraintAnchor.Type.BASELINE, ConstraintAnchor.Type.BASELINE);
    setTarget(model, scene, centerY, widget, ConstraintAnchor.Type.CENTER_Y, ConstraintAnchor.Type.CENTER_Y);
    setTopMargin(centerY, component, widget);
    setBottomMargin(centerY, component, widget);

    setBias(SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS, component, widget);
    setBias(SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, component, widget);

    setDimensionRatio(SdkConstants.ATTR_LAYOUT_DIMENSION_RATIO, component, widget);

    if (widget instanceof Guideline) {
      Guideline guideline = (Guideline)widget;
      setGuideline(component, guideline);
    }

    // Update text decorator
    WidgetCompanion companion = (WidgetCompanion)widget.getCompanionWidget();
    WidgetDecorator decorator = companion.getWidgetDecorator(WidgetDecorator.BLUEPRINT_STYLE);
    if (decorator != null && decorator instanceof TextWidget) {
      TextWidget textWidget = (TextWidget)decorator;
      textWidget.setText(getResolvedText(component));

      Configuration configuration = component.getModel().getConfiguration();
      ResourceResolver resourceResolver = configuration.getResourceResolver();

      Integer size = null;

      if (resourceResolver != null) {
        String textSize = component.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT_SIZE);
        if (textSize != null) {
          size = ResourceHelper.resolveDimensionPixelSize(resourceResolver, textSize, configuration);
        }
      }

      if (size == null) {
        // With the specified string, this method cannot return null
        //noinspection ConstantConditions
        size = ResourceHelper.resolveDimensionPixelSize(resourceResolver, "15sp", configuration);
      }

      // Cannot be null, see previous condition
      //noinspection ConstantConditions
      textWidget.setTextSize(constraintModel.pxToDp(size));
    }
  }

  /**
   * Update the given guideline with the attributes set in the NlComponent
   *
   * @param component the component we get the attributes from
   * @param guideline the guideline widget we want to update with the values
   */
  private static void setGuideline(NlComponent component, Guideline guideline) {
    String relativeBegin = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_GUIDELINE_RELATIVE_BEGIN);
    String relativeEnd = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_GUIDELINE_RELATIVE_END);
    String relativePercent = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_GUIDELINE_RELATIVE_PERCENT);
    if (relativePercent != null && relativePercent.length() > 0) {
      int value = 0;
      try {
        value = Integer.parseInt(relativePercent);
      } catch (NumberFormatException e) {
        // ignore
      }
      guideline.setRelativePercent(value);
    }
    else if (relativeBegin != null && relativeBegin.length() > 0) {
      try {
        int value = getDpValue(component, relativeBegin);
        guideline.setRelativeBegin(value);
      }
      catch (NumberFormatException e) {
      }
    }
    else if (relativeEnd != null && relativeEnd.length() > 0) {
      try {
        int value = getDpValue(component, relativeEnd);
        guideline.setRelativeEnd(value);
      }
      catch (NumberFormatException e) {
      }
    }
    String orientation = component.getAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_ORIENTATION);
    if (orientation != null) {
      int newOrientation = Guideline.HORIZONTAL;
      if (SdkConstants.ATTR_GUIDELINE_ORIENTATION_VERTICAL.equalsIgnoreCase(orientation)) {
        newOrientation = Guideline.VERTICAL;
      }
      if (newOrientation != guideline.getOrientation()) {
        guideline.setOrientation(newOrientation);
        WidgetCompanion companion = (WidgetCompanion)guideline.getCompanionWidget();
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
      return ResourceHelper.resolveStringValue(resourceResolver, text);
    }
    return "";
  }

  @NotNull
  static String getResolvedText(NlComponent component) {
    String text = component.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT);
    if (text != null) {
      if (text.startsWith(SdkConstants.PREFIX_RESOURCE_REF)) {
        return resolveStringResource(component, text);
      }
      return text;
    }
    return "";
  }

  /**
   * Utility function to commit an attribute to the NlModel
   *
   * @param component
   * @param attribute
   * @param value     String or null to clear attribute
   */
  static void saveNlAttribute(NlComponent component, String attribute, final String value) {
    saveNlAttribute(component, SdkConstants.SHERPA_URI, attribute, value);
  }

  /**
   * Utility function to commit an attribute to the NlModel
   *
   * @param component
   * @param nameSpace
   * @param attribute
   * @param value     String or null to clear attribute
   */
  static void saveNlAttribute(NlComponent component, String nameSpace, String attribute, final String value) {
    NlModel nlModel = component.getModel();
    Project project = nlModel.getProject();
    XmlFile file = nlModel.getFile();

    String previousValue = component.getAttribute(nameSpace, attribute);
    if ((value == null && previousValue == null)
        || (value != null && previousValue != null && value.equalsIgnoreCase(previousValue))
        || (value != null && previousValue == null && value.equalsIgnoreCase("0dp"))) {
      // TODO: we should fix why we get there in the first place rather than catching it here.
      // (WidgetConstraintPanel::configureUI configure the margin in the combobox and calls us...)
      return;
    }
    String label = "Constraint";
    WriteCommandAction action = new WriteCommandAction(project, label, file) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        component.setAttribute(nameSpace, attribute, value);
      }
    };
    action.execute();
  }

  /**
   * Utility function to commit to the NlModel the current state of all widgets
   *
   * @param nlModel
   */
  static void saveModelToXML(@NotNull NlModel nlModel) {
    Project project = nlModel.getProject();
    XmlFile file = nlModel.getFile();

    String label = "Constraint";
    WriteCommandAction action = new WriteCommandAction(project, label, file) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        ConstraintModel model = ConstraintModel.getConstraintModel(nlModel);
        Collection<ConstraintWidget> widgets = model.getScene().getWidgets();
        for (ConstraintWidget widget : widgets) {
          commitElement(model, widget);
        }
      }
    };
    action.execute();
  }

  /**
   * Return true if the widget is contained inside a ConstraintLayout
   *
   * @param widget
   * @return
   */
  private static boolean isInConstraintLayout(ConstraintWidget widget) {
    if (widget.getParent() instanceof ConstraintWidgetContainer) {
      return true;
    }
    return false;
  }

  /**
   * Utility function to commit to the NlModel the current state of the given widget
   *
   * @param model  the constraintmodel we are working with
   * @param widget the widget we want to save to the nl model
   */
  static void commitElement(ConstraintModel model, @NotNull ConstraintWidget widget) {
    WidgetCompanion companion = (WidgetCompanion)widget.getCompanionWidget();
    NlComponent component = (NlComponent)companion.getWidgetModel();
    if (model.getDragDropWidget() == widget || isInConstraintLayout(widget)) {
      setEditorPosition(widget, component, widget.getX(), widget.getY());
    } else {
      clearEditorPosition(component);
    }

    if (!widget.isRoot()) {
      setDimension(component, widget);
    }
    for (ConstraintAnchor anchor : widget.getAnchors()) {
      setConnection(component, anchor);
    }
    setHorizontalBias(component, widget);
    setVerticalBias(component, widget);
    if (widget instanceof Guideline) {
      commitGuideline(component, (Guideline)widget);
    }
  }

  /**
   * Utility function to render the current model to layoutlib
   *
   * @param model the ConstraintModel we want to render
   */
  static void renderModel(@NotNull ConstraintModel model) {
    Collection<ConstraintWidget> widgets = model.getScene().getWidgets();
    for (ConstraintWidget widget : widgets) {
      saveWidgetLayoutParams(model, widget);
    }
    model.getNlModel().requestRender(); // let's render asynchronously for now
  }

  /**
   * Apply the current attributes of the given widget to the layoutlib LayoutParams
   *
   * @param model
   * @param widget
   */
  static void saveWidgetLayoutParams(@NotNull ConstraintModel model, @NotNull ConstraintWidget widget) {
    try {
      if (!widget.isRoot()) {
        WidgetCompanion companion = (WidgetCompanion)widget.getCompanionWidget();
        NlComponent component = (NlComponent)companion.getWidgetModel();
        if (component == null || component.viewInfo == null) {
          return;
        }
        Object viewObject = component.viewInfo.getViewObject();
        Object layoutParams = component.viewInfo.getLayoutParamsObject();

        // Save the attributes to the layoutParams instance
        saveEditorPosition(layoutParams, model.dpToPx(widget.getX()), model.dpToPx(widget.getY()));
        saveDimension(model, widget, layoutParams);
        for (ConstraintAnchor anchor : widget.getAnchors()) {
          saveConnection(model, anchor, layoutParams);
        }
        saveBiases(widget, layoutParams);

        if (widget instanceof Guideline) {
          saveGuideline((Guideline)widget, layoutParams);
        }

        // Now trigger a relayout
        Class layoutParamsClass = layoutParams.getClass();
        do {
          layoutParamsClass = layoutParamsClass.getSuperclass();
        } while (!SdkConstants.CLASS_VIEWGROUP_LAYOUTPARAMS.equals(layoutParamsClass.getName()));

        viewObject.getClass().getMethod("setLayoutParams", layoutParamsClass)
          .invoke(viewObject, layoutParams);
      }
    }
    catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      // Ignore
    }
  }

  /**
   * Save the current position to layoutParams
   *
   * @param layoutParams
   * @param x
   * @param y
   */
  static void saveEditorPosition(@NotNull Object layoutParams,
                                 @AndroidCoordinate int x, @AndroidCoordinate int y) {
    try {
      Field editor_absolute_x = layoutParams.getClass().getField("editorAbsoluteX");
      Field editor_absolute_y = layoutParams.getClass().getField("editorAbsoluteY");
      editor_absolute_x.set(layoutParams, x);
      editor_absolute_y.set(layoutParams, y);
    }
    catch (IllegalAccessException | NoSuchFieldException e) {
      // Ignore
    }
  }

  /**
   * Save the current dimension to layoutParams
   *
   * @param model
   * @param widget
   * @param layoutParams
   */
  static void saveDimension(@NotNull ConstraintModel model, @NotNull ConstraintWidget widget, @NotNull Object layoutParams) {
    try {
      Field fieldWidth = layoutParams.getClass().getField("width");
      Field fieldHeight = layoutParams.getClass().getField("height");
      int previousWidth = fieldWidth.getInt(layoutParams);
      int previousHeight = fieldHeight.getInt(layoutParams);
      int newWidth = model.dpToPx(widget.getWidth());
      int newHeight = model.dpToPx(widget.getHeight());
      if (previousWidth != newWidth || previousHeight != newHeight) {
        fieldWidth.set(layoutParams, newWidth);
        fieldHeight.set(layoutParams, newHeight);
      }
    }
    catch (IllegalAccessException | NoSuchFieldException e) {
      // Ignore
    }
  }

  /**
   * Save the current horizontal and vertical biases to layoutParams
   *
   * @param widget
   * @param layoutParams
   */
  static void saveBiases(@NotNull ConstraintWidget widget, @NotNull Object layoutParams) {
    try {
      Field horizontal_bias = layoutParams.getClass().getField("horizontalBias");
      Field vertical_bias = layoutParams.getClass().getField("verticalBias");
      horizontal_bias.set(layoutParams, widget.getHorizontalBiasPercent());
      vertical_bias.set(layoutParams, widget.getVerticalBiasPercent());
    }
    catch (IllegalAccessException | NoSuchFieldException e) {
      // Ignore
    }
  }

  /**
   * Save the current guideline attributes to layoutParams
   *
   * @param guideline
   * @param layoutParams
   */
  static void saveGuideline(@NotNull Guideline guideline, @NotNull Object layoutParams) {
    try {
      int behaviour = guideline.getRelativeBehaviour();
      if (behaviour == Guideline.RELATIVE_BEGIN) {
        Field relativeBegin = layoutParams.getClass().getField("relativeBegin");
        relativeBegin.set(layoutParams, guideline.getRelativeBegin());
      }
      else if (behaviour == Guideline.RELATIVE_END) {
        Field relativeEnd = layoutParams.getClass().getField("relativeEnd");
        relativeEnd.set(layoutParams, guideline.getRelativeEnd());
      }
      else if (behaviour == Guideline.RELATIVE_PERCENT) {
        Field relativePercent = layoutParams.getClass().getField("relativePercent");
        relativePercent.set(layoutParams, guideline.getRelativeEnd());
      }
    }
    catch (IllegalAccessException | NoSuchFieldException e) {
      // Ignore
    }
  }

  /**
   * Save the current connections of this widget to layoutParams
   */
  @SuppressWarnings("EnumSwitchStatementWhichMissesCases")
  static void saveConnection(@NotNull ConstraintModel model, @NotNull ConstraintAnchor anchor, @NotNull Object layoutParams) {
    try {
      resetField(layoutParams, anchor);
      if (anchor.isConnected()) {
        ConstraintWidget target = anchor.getTarget().getOwner();
        WidgetCompanion targetCompanion = (WidgetCompanion)target.getCompanionWidget();
        NlComponent targetComponent = (NlComponent)targetCompanion.getWidgetModel();
        int targetID = targetComponent.getAndroidViewId();
        Field connectionField = getConnectionField(layoutParams, anchor);
        if (connectionField != null) {
          connectionField.set(layoutParams, targetID);
        }
        if (anchor.getMargin() > 0) {
          switch (anchor.getType()) {
            case LEFT: {
              Field field = layoutParams.getClass().getField("leftMargin");
              field.set(layoutParams, model.dpToPx(anchor.getMargin()));
            }
            break;
            case TOP: {
              Field field = layoutParams.getClass().getField("topMargin");
              field.set(layoutParams, model.dpToPx(anchor.getMargin()));
            }
            break;
            case RIGHT: {
              Field field = layoutParams.getClass().getField("rightMargin");
              field.set(layoutParams, model.dpToPx(anchor.getMargin()));
            }
            break;
            case BOTTOM: {
              Field field = layoutParams.getClass().getField("bottomMargin");
              field.set(layoutParams, model.dpToPx(anchor.getMargin()));
            }
            break;
          }
        }
      }
    }
    catch (IllegalAccessException | NoSuchFieldException e) {
      // Ignore
    }
  }

  /**
   * Reset the fields associated to the given anchor in the layoutParams instance
   *
   * @param layoutParams
   * @param anchor
   * @throws NoSuchFieldException
   * @throws IllegalAccessException
   */
  @SuppressWarnings("EnumSwitchStatementWhichMissesCases")
  static void resetField(@NotNull Object layoutParams, @NotNull ConstraintAnchor anchor)
    throws NoSuchFieldException, IllegalAccessException {
    switch (anchor.getType()) {
      case BASELINE: {
        Field field = layoutParams.getClass().getField("baselineToBaseline");
        field.set(layoutParams, -1);
      }
      break;
      case LEFT: {
        Field field = layoutParams.getClass().getField("lefToLeft");
        field.set(layoutParams, -1);
        field = layoutParams.getClass().getField("leftToRight");
        field.set(layoutParams, -1);
        field = layoutParams.getClass().getField("left_margin");
        field.set(layoutParams, -1);
      }
      break;
      case RIGHT: {
        Field field = layoutParams.getClass().getField("rightToLeft");
        field.set(layoutParams, -1);
        field = layoutParams.getClass().getField("rightToRight");
        field.set(layoutParams, -1);
        field = layoutParams.getClass().getField("right_margin");
        field.set(layoutParams, -1);
      }
      break;
      case TOP: {
        Field field = layoutParams.getClass().getField("topToTop");
        field.set(layoutParams, -1);
        field = layoutParams.getClass().getField("topToBottom");
        field.set(layoutParams, -1);
        field = layoutParams.getClass().getField("top_margin");
        field.set(layoutParams, -1);
      }
      break;
      case BOTTOM: {
        Field field = layoutParams.getClass().getField("bottomToTop");
        field.set(layoutParams, -1);
        field = layoutParams.getClass().getField("bottomToBottom");
        field.set(layoutParams, -1);
        field = layoutParams.getClass().getField("bottom_margin");
        field.set(layoutParams, -1);
      }
      break;
    }
  }

  /**
   * Return the field in layoutParams corresponding to the given anchor
   *
   * @param layoutParams
   * @param anchor
   * @return
   * @throws NoSuchFieldException
   */
  @SuppressWarnings("EnumSwitchStatementWhichMissesCases")
  @Nullable
  static Field getConnectionField(@NotNull Object layoutParams, @NotNull ConstraintAnchor anchor)
    throws NoSuchFieldException {
    ConstraintAnchor target = anchor.getTarget();
    if (target != null) {
      switch (anchor.getType()) {
        case BASELINE: {
          if (target.getType() == ConstraintAnchor.Type.BASELINE) {
            return layoutParams.getClass().getField("baselineToBaseline");
          }
          break;
        }
        case LEFT: {
          switch (target.getType()) {
            case LEFT: {
              return layoutParams.getClass().getField("lefToLeft");
            }
            case RIGHT: {
              return layoutParams.getClass().getField("leftToRight");
            }
          }
          break;
        }
        case RIGHT: {
          switch (target.getType()) {
            case LEFT: {
              return layoutParams.getClass().getField("rightToLeft");
            }
            case RIGHT: {
              return layoutParams.getClass().getField("rightToRight");
            }
          }
          break;
        }
        case TOP: {
          switch (target.getType()) {
            case TOP: {
              return layoutParams.getClass().getField("topToTop");
            }
            case BOTTOM: {
              return layoutParams.getClass().getField("topToBottom");
            }
          }
          break;
        }
        case BOTTOM: {
          switch (target.getType()) {
            case TOP: {
              return layoutParams.getClass().getField("bottomToTop");
            }
            case BOTTOM: {
              return layoutParams.getClass().getField("bottomToBottom");
            }
          }
          break;
        }
      }
    }
    return null;
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

  static boolean supportsStartEnd(@NotNull NlModel model) {
    AndroidVersion compileSdkVersion = getCompileSdkVersion(model);
    return (compileSdkVersion == null ||
            compileSdkVersion.isGreaterOrEqualThan(RtlSupportProcessor.RTL_TARGET_SDK_START)
                && getTargetSdkVersion(model).isGreaterOrEqualThan(RtlSupportProcessor.RTL_TARGET_SDK_START));
  }

  static boolean requiresRightLeft(@NotNull NlModel model) {
    return getMinSdkVersion(model).getApiLevel() < RtlSupportProcessor.RTL_TARGET_SDK_START;
  }
}
