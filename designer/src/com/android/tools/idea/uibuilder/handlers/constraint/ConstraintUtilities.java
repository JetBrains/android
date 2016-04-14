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

import com.android.SdkConstants;
import com.android.tools.sherpa.structure.WidgetCompanion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate;
import com.android.tools.idea.uibuilder.model.Insets;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.sherpa.drawing.decorator.WidgetDecorator;
import com.android.tools.sherpa.structure.WidgetsScene;
import com.google.tnt.solver.widgets.ConstraintAnchor;
import com.google.tnt.solver.widgets.ConstraintWidget;
import com.google.tnt.solver.widgets.ConstraintWidgetContainer;
import com.google.tnt.solver.widgets.WidgetContainer;
import com.intellij.openapi.projectRoots.Sdk;

/**
 * Utility functions managing translation of constants from the solver to the NlModel attributes
 */
public class ConstraintUtilities {

  /**
   * Return the corresponding strength attribute for the given anchor
   *
   * @param anchor the anchor we want to use
   * @return the strength attribute
   */
  @Nullable
  static String getConnectionAttributeStrength(@Nullable ConstraintAnchor anchor) {
    if (anchor != null) {
      switch (anchor.getType()) {
        case LEFT: {
          return SdkConstants.ATTR_LAYOUT_LEFT_STRENGTH;
        }
        case RIGHT: {
          return SdkConstants.ATTR_LAYOUT_RIGHT_STRENGTH;
        }
        case TOP: {
          return SdkConstants.ATTR_LAYOUT_TOP_STRENGTH;
        }
        case BOTTOM: {
          return SdkConstants.ATTR_LAYOUT_BOTTOM_STRENGTH;
        }
      }
    }
    return null;
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
          return SdkConstants.ATTR_LAYOUT_LEFT_MARGIN;
        }
        case TOP: {
          return SdkConstants.ATTR_LAYOUT_TOP_MARGIN;
        }
        case RIGHT: {
          return SdkConstants.ATTR_LAYOUT_RIGHT_MARGIN;
        }
        case BOTTOM: {
          return SdkConstants.ATTR_LAYOUT_BOTTOM_MARGIN;
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
    switch (anchorType) {
      case LEFT: {
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_STRENGTH, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_MARGIN, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_CREATOR, null);
        break;
      }
      case TOP: {
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_STRENGTH, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_MARGIN, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_CREATOR, null);
        break;
      }
      case RIGHT: {
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_STRENGTH, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_MARGIN, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_CREATOR, null);
        break;
      }
      case BOTTOM: {
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_STRENGTH, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_MARGIN, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_CREATOR, null);
        break;
      }
      case BASELINE: {
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BASELINE_CREATOR, null);
      }
      break;
    }
  }

  /**
   * Reset the vertical alignment of the component
   *
   * @param component the component we work on
   * @param alignment the type of alignment
   */
  public static void resetVerticalAlignment(@NotNull NlComponent component, int alignment) {
    if (alignment == -1 || alignment == 1) {
      component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_STRENGTH, null);
      component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_STRENGTH, null);
    }
    else if (alignment == 0) {
      component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_STRENGTH, "strong");
      component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_STRENGTH, "weak");
    }
    else if (alignment == 2) {
      component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_STRENGTH, "weak");
      component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_STRENGTH, "strong");
    }
  }

  /**
   * Reset the horizontal alignment of the component
   *
   * @param component the component we work on
   * @param alignment the type of alignment
   */
  public static void resetHorizontalAlignment(@NotNull NlComponent component, int alignment) {
    if (alignment == -1 || alignment == 1) {
      component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_STRENGTH, null);
      component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_STRENGTH, null);
    }
    else if (alignment == 0) {
      component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_STRENGTH, "strong");
      component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_STRENGTH, "weak");
    }
    else if (alignment == 2) {
      component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_STRENGTH, "weak");
      component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_STRENGTH, "strong");
    }
  }

  /**
   * Set the position of the component
   *
   * @param component the component we work on
   * @param x         x position (in Dp)
   * @param y         y position (in Dp)
   */
  public static void setEditorPosition(@NotNull NlComponent component,
                                       @AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    String sX = String.format(SdkConstants.VALUE_N_DP, x);
    String sY = String.format(SdkConstants.VALUE_N_DP, y);
    String attributeX = SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X;
    String attributeY = SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y;
    component.setAttribute(SdkConstants.SHERPA_URI, attributeX, sX);
    component.setAttribute(SdkConstants.SHERPA_URI, attributeY, sY);
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
        String margin = String.format(SdkConstants.VALUE_N_DP, anchor.getMargin());
        component.setAttribute(SdkConstants.SHERPA_URI, marginAttribute, margin);
      }
      component.setAttribute(SdkConstants.SHERPA_URI, getConnectionAttributeCreator(anchor), "" + anchor.getConnectionCreator());
    }
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
      } break;
      case WRAP_CONTENT: {
        width = SdkConstants.VALUE_WRAP_CONTENT;
      } break;
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
      } break;
      case WRAP_CONTENT: {
        height = SdkConstants.VALUE_WRAP_CONTENT;
      } break;
      default:
        height = String.format(SdkConstants.VALUE_N_DP, widget.getHeight());
    }
    component.setAttribute(SdkConstants.ANDROID_URI,
                           SdkConstants.ATTR_LAYOUT_HEIGHT,
                           height);
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
   * Set Left Margin on a constraint widget if defined
   *
   * @param left      the left attribute (we'll only set the margin if the attribute exists)
   * @param component the component we are looking at
   * @param widget    the constraint widget we set the margin on
   */
  static void setLeftMargin(@Nullable String left, @NotNull NlComponent component, @NotNull ConstraintWidget widget) {
    if (left != null) {
      String margin = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_MARGIN);
      if (margin != null) {
        int dp = NlComponent.extractDp(margin);
        widget.getAnchor(ConstraintAnchor.Type.LEFT).setMargin(dp);
      }
    }
  }

  /**
   * Set Right Margin on a constraint widget if defined
   *
   * @param right     the right attribute (we'll only set the margin if the attribute exists)
   * @param component the component we are looking at
   * @param widget    the constraint widget we set the margin on
   */
  static void setRightMargin(@Nullable String right, @NotNull NlComponent component, @NotNull ConstraintWidget widget) {
    if (right != null) {
      String margin = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_MARGIN);
      if (margin != null) {
        int dp = NlComponent.extractDp(margin);
        widget.getAnchor(ConstraintAnchor.Type.RIGHT).setMargin(dp);
      }
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
      String margin = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_MARGIN);
      if (margin != null) {
        int dp = NlComponent.extractDp(margin);
        widget.getAnchor(ConstraintAnchor.Type.TOP).setMargin(dp);
      }
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
      String margin = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_MARGIN);
      if (margin != null) {
        int dp = NlComponent.extractDp(margin);
        widget.getAnchor(ConstraintAnchor.Type.BOTTOM).setMargin(dp);
      }
    }
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
  static void setTarget(@NotNull NlModel model, @NotNull WidgetsScene widgetsScene, @Nullable String targetID, @Nullable ConstraintWidget widgetSrc,
                        @NotNull ConstraintAnchor.Type constraintA, @NotNull ConstraintAnchor.Type constraintB) {
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
      ConstraintWidget widget = widgetsScene.getWidget(componentFound.getTag());
      if (widgetSrc != null && widget != null) {
        int connectionCreator = 0;
        WidgetCompanion companion = (WidgetCompanion)widgetSrc.getCompanionWidget();
        NlComponent component = (NlComponent)companion.getWidgetModel();
        String creatorAttribute = getConnectionAttributeCreator(widgetSrc.getAnchor(constraintA));
        String creatorValue = component.getAttribute(SdkConstants.SHERPA_URI, creatorAttribute);
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
   * @param id the id to find
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
   * @param constraintModel
   * @param widget          constraint widget
   * @param component       the model component
   */
  static void updateWidget(@NotNull ConstraintModel constraintModel, @Nullable ConstraintWidget widget, @Nullable NlComponent component) {
    if (component == null || widget == null) {
      return;
    }
    widget.setDebugName(component.getId());
    WidgetsScene scene = constraintModel.getScene();
    Insets padding = component.getPadding();
    if (widget instanceof ConstraintWidgetContainer) {
      widget.setDimension(constraintModel.pxToDp(component.w - padding.width()),
                          constraintModel.pxToDp(component.h - padding.height()));
    } else {
      widget.setDimension(constraintModel.pxToDp(component.w),
                          constraintModel.pxToDp(component.h));
    }
    NlComponent parent = component.getParent();
    NlModel model = component.getModel();
    if (parent != null) {
      ConstraintWidget parentWidget = scene.getWidget(parent.getTag());
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
      widget.setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.WRAP_CONTENT);
      widget.setMinWidth(widget.getWidth());
    }
    else {
      widget.setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.FIXED);
    }
    String layout_height = component.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);
    if ((component.h == 0) || (layout_height != null && (layout_height.equalsIgnoreCase("0") || layout_height.equalsIgnoreCase("0dp")))) {
      widget.setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.ANY);
    }
    else if (layout_height != null && layout_height.equalsIgnoreCase(SdkConstants.VALUE_WRAP_CONTENT)) {
      widget.setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.WRAP_CONTENT);
      widget.setMinHeight(widget.getHeight());
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
    if (widget.getParent() instanceof WidgetContainer) {
      WidgetContainer parentContainer = (WidgetContainer)widget.getParent();
      x -= parentContainer.getDrawX();
      y -= parentContainer.getDrawY();
    }
    widget.setOrigin(x, y);
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
    setLeftMargin(left1, component, widget);
    setTarget(model, scene, left2, widget, ConstraintAnchor.Type.LEFT, ConstraintAnchor.Type.RIGHT);
    setLeftMargin(left2, component, widget);
    setTarget(model, scene, right1, widget, ConstraintAnchor.Type.RIGHT, ConstraintAnchor.Type.LEFT);
    setRightMargin(right1, component, widget);
    setTarget(model, scene, right2, widget, ConstraintAnchor.Type.RIGHT, ConstraintAnchor.Type.RIGHT);
    setRightMargin(right2, component, widget);
    setTarget(model, scene, centerX, widget, ConstraintAnchor.Type.CENTER_X, ConstraintAnchor.Type.CENTER_X);
    setLeftMargin(centerX, component, widget);
    setRightMargin(centerX, component, widget);

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
    setLeftMargin(centerY, component, widget);
    setRightMargin(centerY, component, widget);

    setStrength(SdkConstants.ATTR_LAYOUT_LEFT_STRENGTH, ConstraintAnchor.Type.LEFT, component, widget);
    setStrength(SdkConstants.ATTR_LAYOUT_RIGHT_STRENGTH, ConstraintAnchor.Type.RIGHT, component, widget);
    setStrength(SdkConstants.ATTR_LAYOUT_TOP_STRENGTH, ConstraintAnchor.Type.TOP, component, widget);
    setStrength(SdkConstants.ATTR_LAYOUT_BOTTOM_STRENGTH, ConstraintAnchor.Type.BOTTOM, component, widget);
  }

}
