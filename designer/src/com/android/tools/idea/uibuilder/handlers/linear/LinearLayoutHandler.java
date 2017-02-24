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
package com.android.tools.idea.uibuilder.handlers.linear;

import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.api.actions.DirectViewAction;
import com.android.tools.idea.uibuilder.api.actions.ViewAction;
import com.android.tools.idea.uibuilder.api.actions.ViewActionPresentation;
import com.android.tools.idea.uibuilder.api.actions.ViewActionSeparator;
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.FillPolicy;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.SegmentType;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.target.Target;
import com.android.tools.idea.uibuilder.surface.Interaction;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.ImmutableList;
import icons.AndroidDesignerIcons;
import icons.AndroidIcons;
import org.intellij.lang.annotations.JdkConstants.InputEventMask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.utils.XmlUtils.formatFloatAttribute;

/**
 * Handler for the {@code <LinearLayout>} layout
 */
public class LinearLayoutHandler extends ViewGroupHandler {

  @NotNull
  @Override
  public String getTitleAttributes(@NotNull NlComponent component) {
    if (!component.getTagName().equals(LINEAR_LAYOUT)) {
      return super.getTitleAttributes(component);
    }
    return isVertical(component) ? "(vertical)" : "(horizontal)";
  }

  @Override
  @NotNull
  public Icon getIcon(@NotNull NlComponent component) {
    if (!component.getTagName().equals(LINEAR_LAYOUT)) {
      return super.getIcon(component);
    }
    return isVertical(component) ? AndroidIcons.Views.VerticalLinearLayout : AndroidIcons.Views.LinearLayout;
  }

  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(ATTR_ORIENTATION);
  }

  /**
   * Returns true if the given node represents a vertical linear layout.
   *
   * @param component the node to check layout orientation for
   * @return true if the layout is in vertical mode, otherwise false
   */
  protected boolean isVertical(@NotNull NlComponent component) {
    // Horizontal is the default, so if no value is specified it is horizontal.
    String orientation = component.getAttribute(ANDROID_URI, ATTR_ORIENTATION);
    return VALUE_VERTICAL.equals(orientation);
  }

  @Override
  @Nullable
  public DragHandler createDragHandler(@NotNull ViewEditor editor,
                                       @NotNull SceneComponent layout,
                                       @NotNull List<SceneComponent> components,
                                       @NotNull DragType type) {
    if (layout.getDrawWidth() == 0 || layout.getDrawHeight() == 0) {
      return null;
    }
    return new LinearDragHandler(editor, layout, components, type, this);
  }

  @Override
  public void onChildInserted(@NotNull NlComponent layout, @NotNull NlComponent newChild, @NotNull InsertType insertType) {
    if (insertType == InsertType.MOVE_WITHIN) {
      // Don't adjust widths/heights/weights when just moving within a single
      // LinearLayout
      return;
    }

    // Attempt to set fill-properties on newly added views such that for example,
    // in a vertical layout, a text field defaults to filling horizontally, but not
    // vertically.
    ViewHandler viewHandler = newChild.getViewHandler();
    if (viewHandler != null) {
      boolean vertical = isVertical(layout);
      FillPolicy fill = viewHandler.getFillPolicy();
      String fillParent = VALUE_MATCH_PARENT;
      if (fill.fillHorizontally(vertical)) {
        newChild.setAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, fillParent);
      }
      else if (!vertical && fill == FillPolicy.WIDTH_IN_VERTICAL) {
        // In a horizontal layout, make views that would fill horizontally in a
        // vertical layout have a non-zero weight instead. This will make the item
        // fill but only enough to allow other views to be shown as well.
        // (However, for drags within the same layout we do not touch
        // the weight, since it might already have been tweaked to a particular
        // value)
        newChild.setAttribute(ANDROID_URI, ATTR_LAYOUT_WEIGHT, VALUE_1);
      }
      if (fill.fillVertically(vertical)) {
        newChild.setAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, fillParent);
      }
    }

    // If you insert into a layout that already is using layout weights,
    // and all the layout weights are the same (nonzero) value, then use
    // the same weight for this new layout as well. Also duplicate the 0dip/0px/0dp
    // sizes, if used.
    boolean duplicateWeight = true;
    boolean duplicate0dip = true;
    String sameWeight = null;
    String sizeAttribute = isVertical(layout) ? ATTR_LAYOUT_HEIGHT : ATTR_LAYOUT_WIDTH;
    for (NlComponent target : layout.getChildren()) {
      if (target == newChild) {
        continue;
      }
      String weight = target.getAttribute(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
      if (weight == null || weight.length() == 0) {
        duplicateWeight = false;
        break;
      }
      else if (sameWeight != null && !sameWeight.equals(weight)) {
        duplicateWeight = false;
      }
      else {
        sameWeight = weight;
      }
      String size = target.getAttribute(ANDROID_URI, sizeAttribute);
      if (size != null && !size.startsWith("0")) { //$NON-NLS-1$
        duplicate0dip = false;
        break;
      }
    }
    if (duplicateWeight && sameWeight != null) {
      newChild.setAttribute(ANDROID_URI, ATTR_LAYOUT_WEIGHT, sameWeight);
      if (duplicate0dip) {
        newChild.setAttribute(ANDROID_URI, sizeAttribute, VALUE_ZERO_DP);
      }
    }
  }

  @Override
  @Nullable
  public ResizeHandler createResizeHandler(@NotNull ViewEditor editor,
                                           @NotNull NlComponent component,
                                           @Nullable SegmentType horizontalEdgeType,
                                           @Nullable SegmentType verticalEdgeType) {
    return new LinearResizeHandler(editor, this, component, horizontalEdgeType, verticalEdgeType);
  }

  /**
   * Returns the layout weight of of the given child of a LinearLayout, or 0.0 if it
   * does not define a weight
   */
  static float getWeight(@NotNull NlComponent linearLayoutChild) {
    String weight = linearLayoutChild.getAttribute(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
    if (weight != null && weight.length() > 0) {
      try {
        return Float.parseFloat(weight);
      }
      catch (NumberFormatException ignore) {
      }
    }

    return 0.0f;
  }

  /**
   * Returns the sum of all the layout weights of the children in the given LinearLayout
   *
   * @param linearLayout the layout to compute the total sum for
   * @return the total sum of all the layout weights in the given layout
   */
  static float getWeightSum(@NotNull NlComponent linearLayout) {
    String weightSum = linearLayout.getAttribute(ANDROID_URI, ATTR_WEIGHT_SUM);
    float sum;
    if (weightSum != null) {
      // Distribute
      try {
        sum = Float.parseFloat(weightSum);
        return sum;
      }
      catch (NumberFormatException nfe) {
        // Just keep using the default
      }
    }

    return getSumOfWeights(linearLayout);
  }

  private static float getSumOfWeights(@NotNull NlComponent linearLayout) {
    float sum = 0.0f;
    for (NlComponent child : linearLayout.getChildren()) {
      sum += getWeight(child);
    }

    return sum;
  }

  private void clearWeights(@NotNull NlComponent component, @NotNull List<NlComponent> selectedChildren) {
    // Clear attributes
    String sizeAttribute = isVertical(component) ? ATTR_LAYOUT_HEIGHT : ATTR_LAYOUT_WIDTH;
    for (NlComponent selected : selectedChildren) {
      selected.setAttribute(ANDROID_URI, ATTR_LAYOUT_WEIGHT, null);
      String size = selected.getAttribute(ANDROID_URI, sizeAttribute);
      if (size != null && size.startsWith("0")) {
        selected.setAttribute(ANDROID_URI, sizeAttribute, VALUE_WRAP_CONTENT);
      }
    }
  }

  private void distributeWeights(@NotNull NlComponent component, @NotNull List<NlComponent> selectedChildren) {
    // Any XML to get weight sum?
    String weightSum = component.getAttribute(ANDROID_URI, ATTR_WEIGHT_SUM);
    double sum = -1.0;
    if (weightSum != null && !weightSum.isEmpty()) {
      // Distribute
      try {
        sum = Double.parseDouble(weightSum);
      }
      catch (NumberFormatException nfe) {
        // Just keep using the default
      }
    }
    int numTargets = selectedChildren.size();
    double share;
    if (sum <= 0.0) {
      // The sum will be computed from the children, so just
      // use arbitrary amount
      share = 1.0;
    }
    else {
      share = sum / numTargets;
    }
    String value = formatFloatAttribute((float)share);
    String sizeAttribute = isVertical(component) ? ATTR_LAYOUT_HEIGHT : ATTR_LAYOUT_WIDTH;
    for (NlComponent selected : selectedChildren) {
      selected.setAttribute(ANDROID_URI, ATTR_LAYOUT_WEIGHT, value);

      // Also set the width/height to 0dp to ensure actual equal
      // size (without this, only the remaining space is
      // distributed)
      if (VALUE_WRAP_CONTENT.equals(selected.getAttribute(ANDROID_URI, sizeAttribute))) {
        selected.setAttribute(ANDROID_URI, sizeAttribute, VALUE_ZERO_DP);
      }
    }
  }

  @Override
  public void addToolbarActions(@NotNull List<ViewAction> actions) {
    int rank = 0;
    actions.add(new ToggleOrientationAction().setRank(rank += 20));
    actions.add(new BaselineAction().setRank(rank += 20));
    actions.add(new DistributeWeightsAction().setRank(rank += 20));
    actions.add(new DominateWeightsAction().setRank(rank += 20));
    actions.add(new ClearWeightsAction().setRank(rank += 20));
    actions.add(new ViewActionSeparator().setRank(rank += 20));
    addDefaultViewActions(actions, rank);
  }

  @Override
  public void addPopupMenuActions(@NotNull List<ViewAction> actions) {
    addToolbarActionsToMenu("LinearLayout", actions);
  }

  private static class ToggleOrientationAction extends DirectViewAction {
    @Override
    public void perform(@NotNull ViewEditor editor, @NotNull ViewHandler handler, @NotNull NlComponent component,
                        @NotNull List<NlComponent> selectedChildren,
                        @InputEventMask int modifiers) {
      assert handler instanceof LinearLayoutHandler;
      LinearLayoutHandler linearLayoutHandler = (LinearLayoutHandler)handler;
      boolean isHorizontal = !linearLayoutHandler.isVertical(component);
      String value = isHorizontal ? VALUE_VERTICAL : null; // null: horizontal is the default
      component.setAttribute(ANDROID_URI, ATTR_ORIENTATION, value);
    }

    @Override
    public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren,
                                   @InputEventMask int modifiers) {
      assert handler instanceof LinearLayoutHandler;
      LinearLayoutHandler linearLayoutHandler = (LinearLayoutHandler)handler;
      boolean vertical = linearLayoutHandler.isVertical(component);

      presentation.setLabel("Convert orientation to " + (!vertical ? VALUE_VERTICAL : VALUE_HORIZONTAL));
      Icon icon = vertical ? AndroidDesignerIcons.SwitchVerticalLinear : AndroidDesignerIcons.SwitchHorizontalLinear;
      presentation.setIcon(icon);
    }
  }

  private static class DistributeWeightsAction extends DirectViewAction {
    public DistributeWeightsAction() {
      super(AndroidDesignerIcons.DistributeWeights, "Distribute Weights Evenly");
    }

    @Override
    public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren,
                                   @InputEventMask int modifiers) {
      presentation.setVisible(selectedChildren.size() > 1);
    }

    @Override
    public void perform(@NotNull ViewEditor editor, @NotNull ViewHandler handler, @NotNull NlComponent component,
                        @NotNull List<NlComponent> selectedChildren, @InputEventMask int modifiers) {


      assert handler instanceof LinearLayoutHandler;
      LinearLayoutHandler linearLayoutHandler = (LinearLayoutHandler)handler;
      linearLayoutHandler.distributeWeights(component, selectedChildren);
    }
  }

  private static class DominateWeightsAction extends DirectViewAction {
    public DominateWeightsAction() {
      super(AndroidDesignerIcons.DominateWeight, "Assign All Weight");
    }

    @Override
    public void perform(@NotNull ViewEditor editor, @NotNull ViewHandler handler, @NotNull NlComponent component,
                        @NotNull List<NlComponent> selectedChildren, @InputEventMask int modifiers) {

      assert handler instanceof LinearLayoutHandler;
      LinearLayoutHandler linearLayoutHandler = (LinearLayoutHandler)handler;
      linearLayoutHandler.distributeWeights(component, selectedChildren);
    }

    @Override
    public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren,
                                   @InputEventMask int modifiers) {
      presentation.setVisible(!selectedChildren.isEmpty());
    }
  }

  private static class ClearWeightsAction extends DirectViewAction {
    public ClearWeightsAction() {
      super(AndroidDesignerIcons.ClearWeights, "Clear All Weights");
    }

    @Override
    public void perform(@NotNull ViewEditor editor, @NotNull ViewHandler handler, @NotNull NlComponent component,
                        @NotNull List<NlComponent> selectedChildren, @InputEventMask int modifiers) {

      assert handler instanceof LinearLayoutHandler;
      LinearLayoutHandler linearLayoutHandler = (LinearLayoutHandler)handler;
      linearLayoutHandler.clearWeights(component, selectedChildren);
    }


    @Override
    public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren,
                                   @InputEventMask int modifiers) {
      presentation.setVisible(!selectedChildren.isEmpty());
    }
  }

  /*------------- NEW ARCHITECTURE TODO remove this -----------*/

  @Nullable
  @Override
  public Interaction createInteraction(@NotNull ScreenView screenView, @NotNull NlComponent layout) {
    return null;
  }

  /**
   * Returns true to handles painting the component
   *
   * @return true if the ViewGroupHandler want to be in charge of painting
   */
  @Override
  public boolean handlesPainting() {
    return true;
  }

  /**
   * Paint the component and its children on the given context
   *
   * @param gc         graphics context
   * @param screenView the current screen view
   * @param component  the component to draw
   * @return true to indicate that we will need to be repainted
   */
  @Override
  public boolean drawGroup(@NotNull Graphics2D gc, @NotNull ScreenView screenView,
                           @NotNull NlComponent component) {
    NlComponent prev = null;
    boolean vertical = isVertical(component);
    AffineTransform tx = gc.getTransform();
    Coordinates.transformGraphics(screenView, gc);
    for (NlComponent child : component.getChildren()) {
      if (prev != null) {
        if (vertical) {
          int middle = (prev.y + prev.h + child.y) / 2;
          NlGraphics.drawLine(NlDrawingStyle.GUIDELINE_DASHED, gc, component.x, middle,
                              component.x + component.w, middle);
        }
        else {
          int middle = (prev.x + prev.w + child.x) / 2;
          NlGraphics.drawLine(NlDrawingStyle.GUIDELINE_DASHED, gc, middle, component.y, middle,
                              component.y + component.h);
        }
      }
      prev = child;
    }
    gc.setTransform(tx);
    return false;
  }

  /**
   * Give a chance to the ViewGroup to add targets to the {@linkplain SceneComponent}
   *
   * @param component the component we'll add targets on
   * @param isParent  is it the parent viewgroup component
   */
  @Override
  @NotNull
  public List<Target> createTargets(@NotNull SceneComponent component, boolean isParent) {
    return super.createTargets(component, isParent);
  }

  /**
   * Let the ViewGroupHandler handle clearing attributes on a given component
   *
   * @param component
   */
  @Override
  public void clearAttributes(NlComponent component) {
    // do nothing
  }
}
