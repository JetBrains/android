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
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.sherpa.drawing.decorator.WidgetDecorator;
import com.android.tools.sherpa.structure.WidgetsScene;
import com.google.tnt.solver.widgets.ConstraintAnchor;
import com.google.tnt.solver.widgets.ConstraintWidget;
import com.google.tnt.solver.widgets.WidgetContainer;

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
   * Reset potential attributes related to the given anchor type
   *
   * @param component  the component we work on
   * @param anchorType the anchor type
   */
  public static void resetAnchor(@NonNull NlComponent component, @NonNull ConstraintAnchor.Type anchorType) {
    switch (anchorType) {
      case LEFT: {
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_STRENGTH, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_MARGIN, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF, null);
        break;
      }
      case TOP: {
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_STRENGTH, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_MARGIN, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF, null);
        break;
      }
      case RIGHT: {
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_STRENGTH, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_MARGIN, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, null);
        break;
      }
      case BOTTOM: {
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_STRENGTH, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_MARGIN, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF, null);
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, null);
        break;
      }
      case BASELINE: {
        component.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF, null);
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
  public static void resetVerticalAlignment(@NonNull NlComponent component, int alignment) {
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
  public static void resetHorizontalAlignment(@NonNull NlComponent component, int alignment) {
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
  public static void setEditorPosition(@NonNull NlComponent component, int x, int y) {
    if (component.getParent() == null) {
      return;
    }
    String sX = x + "dp";
    String sY = y + "dp";
    String attributeX = SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X;
    String attributeY = SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y;
    // TODO: the namespace sherpa isn't created after drop, need to investigate why
    component.setAttribute(SdkConstants.SHERPA_URI, attributeX, sX);
    component.setAttribute(SdkConstants.SHERPA_URI, attributeY, sY);
  }

  /**
   * Update the component given a new anchor
   *
   * @param component the component we work on
   * @param anchor    the anchor we want to update from
   */
  static void updateConnection(@NonNull NlComponent component, @NonNull ConstraintAnchor anchor) {
    resetAnchor(component, anchor.getType());
    String attribute = getConnectionAttribute(anchor, anchor.getTarget());
    String marginAttribute = getConnectionAttributeMargin(anchor);
    if (anchor.isConnected()) {
      ConstraintWidget target = anchor.getTarget().getOwner();
      WidgetDecorator decorator = (WidgetDecorator)target.getCompanionWidget();
      NlComponent targetComponent = (NlComponent)decorator.getCompanionObject();
      String targetId = "@+id/" + targetComponent.getId();
      component.setAttribute(SdkConstants.SHERPA_URI, attribute, targetId);
      String margin = anchor.getMargin() + "dp";
      component.setAttribute(SdkConstants.SHERPA_URI, marginAttribute, margin);
    }
  }

  /**
   * Set the constraint strength if defined
   *
   * @param component the component we are looking at
   * @param widget    the constraint widget we set the strength on
   */
  static void setStrength(@NonNull String attribute, @NonNull ConstraintAnchor.Type type,
                          @NonNull NlComponent component, @NonNull ConstraintWidget widget) {
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
  static void setLeftMargin(@Nullable String left, @NonNull NlComponent component, @NonNull ConstraintWidget widget) {
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
  static void setRightMargin(@Nullable String right, @NonNull NlComponent component, @NonNull ConstraintWidget widget) {
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
  static void setTopMargin(@Nullable String top, @NonNull NlComponent component, @NonNull ConstraintWidget widget) {
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
  static void setBottomMargin(@Nullable String bottom, @NonNull NlComponent component, @NonNull ConstraintWidget widget) {
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
   * @param widgetsScene the current widgets scene
   * @param targetID     ID of our target
   * @param widgetSrc    our source widget
   * @param constraintA  the source anchor type
   * @param constraintB  the target anchor type
   */
  static void setTarget(@NonNull WidgetsScene widgetsScene, @Nullable String targetID, @Nullable ConstraintWidget widgetSrc,
                        @NonNull ConstraintAnchor.Type constraintA, @NonNull ConstraintAnchor.Type constraintB) {
    if (targetID == null) {
      return;
    }
    ConstraintWidget widget = widgetsScene.getWidget(NlComponent.extractId(targetID));
    if (widgetSrc != null && widget != null) {
      widgetSrc.connect(constraintA, widget, constraintB);
    }
  }

  /**
   * Set a margin on a connected anchor
   *
   * @param widget the widget we operate on
   * @param margin the margin to set
   * @param type   the type of the anchor
   */
  private static void setMargin(@NonNull ConstraintWidget widget, int margin, @NonNull ConstraintAnchor.Type type) {
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
  static void updateWidget(@NonNull ConstraintModel constraintModel, @Nullable ConstraintWidget widget, @Nullable NlComponent component) {
    if (component == null || widget == null) {
      return;
    }
    WidgetsScene scene = constraintModel.getScene();
    widget.setDimension(constraintModel.pxToDp(component.w), constraintModel.pxToDp(component.h));
    NlComponent parent = component.getParent();
    if (parent != null) {
      ConstraintWidget parentWidget = null;
      if (parent.getId() == null) {
        for (ConstraintWidget w : scene.getWidgets()) {
          WidgetDecorator decorator = (WidgetDecorator)w.getCompanionWidget();
          NlComponent c = (NlComponent)decorator.getCompanionObject();
          if (parent == c) {
            parentWidget = w;
            break;
          }
        }
      }
      else {
        parentWidget = scene.getWidget(parent.getId());
      }
      if (parentWidget instanceof WidgetContainer) {
        WidgetContainer parentContainerWidget = (WidgetContainer)parentWidget;
        if (widget.getParent() != parentContainerWidget) {
          parentContainerWidget.add(widget);
        }
      }
    }

    // FIXME: need to agree on the correct magic value for this rather than simply using zero.
    String layout_width = component.getAttribute(SdkConstants.ANDROID_URI, "layout_width");
    if ((component.w == 0) || (layout_width != null && (layout_width.equalsIgnoreCase("0") || layout_width.equalsIgnoreCase("0dp")))) {
      widget.setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.ANY);
    }
    else {
      widget.setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.FIXED);
    }
    String layout_height = component.getAttribute(SdkConstants.ANDROID_URI, "layout_height");
    if ((component.h == 0) || (layout_height != null && (layout_height.equalsIgnoreCase("0") || layout_height.equalsIgnoreCase("0dp")))) {
      widget.setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.ANY);
    }
    else {
      widget.setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.FIXED);
    }

    int x = constraintModel.pxToDp(component.x);
    int y = constraintModel.pxToDp(component.y);
    if (widget.getParent() instanceof WidgetContainer) {
      WidgetContainer parentContainer = (WidgetContainer)widget.getParent();
      NlComponent parentComponent = (NlComponent)((WidgetDecorator)parentContainer.getCompanionWidget()).getCompanionObject();
      x -= constraintModel.pxToDp(parentComponent.x);
      y -= constraintModel.pxToDp(parentComponent.y);
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

    setTarget(scene, left1, widget, ConstraintAnchor.Type.LEFT, ConstraintAnchor.Type.LEFT);
    setLeftMargin(left1, component, widget);
    setTarget(scene, left2, widget, ConstraintAnchor.Type.LEFT, ConstraintAnchor.Type.RIGHT);
    setLeftMargin(left2, component, widget);
    setTarget(scene, right1, widget, ConstraintAnchor.Type.RIGHT, ConstraintAnchor.Type.LEFT);
    setRightMargin(right1, component, widget);
    setTarget(scene, right2, widget, ConstraintAnchor.Type.RIGHT, ConstraintAnchor.Type.RIGHT);
    setRightMargin(right2, component, widget);
    setTarget(scene, centerX, widget, ConstraintAnchor.Type.CENTER_X, ConstraintAnchor.Type.CENTER_X);
    setLeftMargin(centerX, component, widget);
    setRightMargin(centerX, component, widget);

    setTarget(scene, top1, widget, ConstraintAnchor.Type.TOP, ConstraintAnchor.Type.TOP);
    setTopMargin(top1, component, widget);
    setTarget(scene, top2, widget, ConstraintAnchor.Type.TOP, ConstraintAnchor.Type.BOTTOM);
    setTopMargin(top2, component, widget);
    setTarget(scene, bottom1, widget, ConstraintAnchor.Type.BOTTOM, ConstraintAnchor.Type.TOP);
    setBottomMargin(bottom1, component, widget);
    setTarget(scene, bottom2, widget, ConstraintAnchor.Type.BOTTOM, ConstraintAnchor.Type.BOTTOM);
    setBottomMargin(bottom2, component, widget);
    setTarget(scene, baseline, widget, ConstraintAnchor.Type.BASELINE, ConstraintAnchor.Type.BASELINE);
    setTarget(scene, centerY, widget, ConstraintAnchor.Type.CENTER_Y, ConstraintAnchor.Type.CENTER_Y);
    setLeftMargin(centerY, component, widget);
    setRightMargin(centerY, component, widget);

    setStrength(SdkConstants.ATTR_LAYOUT_LEFT_STRENGTH, ConstraintAnchor.Type.LEFT, component, widget);
    setStrength(SdkConstants.ATTR_LAYOUT_RIGHT_STRENGTH, ConstraintAnchor.Type.RIGHT, component, widget);
    setStrength(SdkConstants.ATTR_LAYOUT_TOP_STRENGTH, ConstraintAnchor.Type.TOP, component, widget);
    setStrength(SdkConstants.ATTR_LAYOUT_BOTTOM_STRENGTH, ConstraintAnchor.Type.BOTTOM, component, widget);
  }
}
