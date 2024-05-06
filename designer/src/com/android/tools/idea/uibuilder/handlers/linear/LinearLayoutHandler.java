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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_GRAVITY;
import static com.android.SdkConstants.ATTR_LAYOUT_GRAVITY;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.ATTR_WEIGHT_SUM;
import static com.android.SdkConstants.GRAVITY_VALUE_BOTTOM;
import static com.android.SdkConstants.GRAVITY_VALUE_CENTER_HORIZONTAL;
import static com.android.SdkConstants.GRAVITY_VALUE_CENTER_VERTICAL;
import static com.android.SdkConstants.GRAVITY_VALUE_END;
import static com.android.SdkConstants.GRAVITY_VALUE_FILL;
import static com.android.SdkConstants.GRAVITY_VALUE_LEFT;
import static com.android.SdkConstants.GRAVITY_VALUE_RIGHT;
import static com.android.SdkConstants.GRAVITY_VALUE_START;
import static com.android.SdkConstants.GRAVITY_VALUE_TOP;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.VALUE_1;
import static com.android.SdkConstants.VALUE_HORIZONTAL;
import static com.android.SdkConstants.VALUE_MATCH_PARENT;
import static com.android.SdkConstants.VALUE_VERTICAL;
import static com.android.SdkConstants.VALUE_WRAP_CONTENT;
import static com.android.SdkConstants.VALUE_ZERO_DP;
import static com.android.tools.idea.uibuilder.api.actions.ViewActionUtils.getToggleSizeActions;
import static com.android.tools.idea.uibuilder.api.actions.ViewActionUtils.getViewOptionsAction;
import static com.android.utils.XmlUtils.formatFloatValue;

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.api.DragType;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.Placeholder;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneInteraction;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.common.surface.Interaction;
import com.android.tools.idea.uibuilder.api.DragHandler;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.actions.ViewAction;
import com.android.tools.idea.uibuilder.api.actions.ViewActionSeparator;
import com.android.tools.idea.uibuilder.handlers.common.CommonDragHandler;
import com.android.tools.idea.uibuilder.handlers.common.ViewGroupPlaceholder;
import com.android.tools.idea.uibuilder.handlers.linear.actions.BaselineAction;
import com.android.tools.idea.uibuilder.handlers.linear.actions.ClearWeightsAction;
import com.android.tools.idea.uibuilder.handlers.linear.actions.DistributeWeightsAction;
import com.android.tools.idea.uibuilder.handlers.linear.actions.ToggleOrientationAction;
import com.android.tools.idea.uibuilder.handlers.linear.targets.LinearResizeTarget;
import com.android.tools.idea.uibuilder.model.FillPolicy;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.ImmutableList;
import icons.StudioIcons;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handler for the {@code <LinearLayout>} layout
 */
public class LinearLayoutHandler extends ViewGroupHandler {

  final HashMap<SceneComponent, SceneComponent> myDraggingComponents = new HashMap<>();

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
    return isVertical(component) ? StudioIcons.LayoutEditor.Palette.LINEAR_LAYOUT_VERT
                                 : StudioIcons.LayoutEditor.Palette.LINEAR_LAYOUT_HORZ;
  }

  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(ATTR_ORIENTATION, ATTR_GRAVITY);
  }

  @Override
  @NotNull
  public List<String> getLayoutInspectorProperties() {
    return ImmutableList.of(ATTR_LAYOUT_WEIGHT);
  }

  /**
   * Returns true if the given node represents a vertical linear layout.
   *
   * @param component the node to check layout orientation for
   * @return true if the layout is in vertical mode, otherwise false
   */
  public boolean isVertical(@NotNull NlComponent component) {
    return VALUE_VERTICAL.equals(component.resolveAttribute(ANDROID_URI, ATTR_ORIENTATION));
  }

  @Override
  @Nullable
  public DragHandler createDragHandler(@NotNull ViewEditor editor,
                                       @NotNull SceneComponent layout,
                                       @NotNull List<NlComponent> components,
                                       @NotNull DragType type) {
    return new CommonDragHandler(editor, this, layout, components, type);
  }

  @Override
  public void onChildInserted(@NotNull NlComponent layout,
                              @NotNull NlComponent newChild,
                              @NotNull InsertType insertType) {
    if (insertType == InsertType.MOVE) {
      // Don't adjust widths/heights/weights when just moving within a single
      // LinearLayout
      return;
    }

    // Attempt to set fill-properties on newly added views such that for example,
    // in a vertical layout, a text field defaults to filling horizontally, but not
    // vertically.
    ViewHandler viewHandler = NlComponentHelperKt.getViewHandler(newChild, () -> {});
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
      if (weight == null || weight.isEmpty()) {
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

  /**
   * Returns the layout weight of of the given child of a LinearLayout, or 0.0 if it
   * does not define a weight
   */
  static float getWeight(@NotNull NlComponent linearLayoutChild) {
    String weight = linearLayoutChild.getAttribute(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
    if (weight != null && !weight.isEmpty()) {
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

  public void clearWeights(@NotNull NlComponent component, @NotNull List<NlComponent> selectedChildren) {
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

  public void distributeWeights(@NotNull NlComponent component, @NotNull List<NlComponent> selectedChildren) {
    if (selectedChildren.isEmpty()) {
      return;
    }

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
    String value = formatFloatValue((float)share);
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
    actions.add(getViewOptionsAction());
    actions.add(new ToggleOrientationAction());
    actions.add(new BaselineAction());
    actions.add(new DistributeWeightsAction());
    actions.add(new ClearWeightsAction());
    actions.add(new ViewActionSeparator());
    actions.addAll(getToggleSizeActions());
  }

  @Override
  public boolean addPopupMenuActions(@NotNull SceneComponent component, @NotNull List<ViewAction> actions) {
    addToolbarActionsToMenu("LinearLayout", actions);
    return true;
  }

  @Override
  @Nullable
  public Interaction createInteraction(@NotNull ScreenView sceneView,
                                       @SwingCoordinate int x,
                                       @SwingCoordinate int y,
                                       @NotNull NlComponent component) {
    return new SceneInteraction(sceneView);
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
   * Creates the {@link LinearResizeTarget}s for the children.
   *
   * @param parentComponent The parent LinearLayout
   * @param childComponent The component we'll add the targets on
   * @return The list of target to add
   */
  @NotNull
  @Override
  public List<Target> createChildTargets(@NotNull SceneComponent parentComponent, @NotNull SceneComponent childComponent) {
    ImmutableList.Builder<Target> listBuilder = ImmutableList.builder();
    createResizeTarget(parentComponent, childComponent, listBuilder);
    return listBuilder.build();
  }

  @Override
  public boolean shouldAddCommonDragTarget(@NotNull SceneComponent component) {
    return true;
  }

  private static void createResizeTarget(@NotNull SceneComponent parentComponent,
                                         @NotNull SceneComponent childComponent,
                                         @NotNull ImmutableList.Builder<Target> listBuilder) {
    String orientation = parentComponent.getNlComponent().getAttribute(ANDROID_URI, ATTR_ORIENTATION);

    boolean showLeft = true;
    boolean showTop = true;
    boolean showBottom = true;
    boolean showRight = true;

    String gravityAttribute = childComponent.getNlComponent().getAttribute(ANDROID_URI, ATTR_LAYOUT_GRAVITY);
    if (gravityAttribute == null || gravityAttribute.contains(GRAVITY_VALUE_FILL)) {
      // LinearLayout works as default case.
      showLeft = false;
      showTop = false;
    }
    else {
      // TODO: handle the start and end case with condition of RTL
      String[] gravities = gravityAttribute.split("\\|");
      for (String gravity : gravities) {
        switch (gravity) {
          case GRAVITY_VALUE_LEFT:
          case GRAVITY_VALUE_START:
            showLeft = false;
            break;
          case GRAVITY_VALUE_TOP:
            showTop = false;
            break;
          case GRAVITY_VALUE_BOTTOM:
            showBottom = false;
            break;
          case GRAVITY_VALUE_RIGHT:
          case GRAVITY_VALUE_END:
            showRight = false;
            break;
          case GRAVITY_VALUE_CENTER_HORIZONTAL:
            if (VALUE_VERTICAL.equals(orientation)) {
              showTop = false;
            }
            break;
          case GRAVITY_VALUE_CENTER_VERTICAL:
            if (VALUE_HORIZONTAL.equals(orientation)) {
              showLeft = false;
            }
            break;
        }
      }
    }

    // edges
    if (showLeft) {
      listBuilder.add(new LinearResizeTarget(ResizeBaseTarget.Type.LEFT));
    }
    if (showTop) {
      listBuilder.add(new LinearResizeTarget(ResizeBaseTarget.Type.TOP));
    }
    if (showBottom) {
      listBuilder.add(new LinearResizeTarget(ResizeBaseTarget.Type.BOTTOM));
    }
    if (showRight) {
      listBuilder.add(new LinearResizeTarget(ResizeBaseTarget.Type.RIGHT));
    }

    // corners
    if (showLeft && showTop) {
      listBuilder.add(new LinearResizeTarget(ResizeBaseTarget.Type.LEFT_TOP));
    }
    if (showLeft && showBottom) {
      listBuilder.add(new LinearResizeTarget(ResizeBaseTarget.Type.LEFT_BOTTOM));
    }
    if (showRight && showTop) {
      listBuilder.add(new LinearResizeTarget(ResizeBaseTarget.Type.RIGHT_TOP));
    }
    if (showRight && showBottom) {
      listBuilder.add(new LinearResizeTarget(ResizeBaseTarget.Type.RIGHT_BOTTOM));
    }
  }

  @Override
  public List<Placeholder> getPlaceholders(@NotNull SceneComponent component, @NotNull List<SceneComponent> draggedComponents) {
    boolean vertical = isVertical(component.getNlComponent());
    List<Placeholder> list = new ArrayList<>();

    if (vertical) {
      int bottomOfChildren = component.getDrawY();

      int left = component.getDrawX();
      int right = component.getDrawX() + component.getDrawWidth();

      for (SceneComponent child : component.getChildren()) {
        list.add(LinearPlaceholderFactory.createVerticalPlaceholder(component, child, child.getDrawY(), left, right));
        bottomOfChildren = Math.max(bottomOfChildren, child.getDrawY() + child.getDrawHeight());
      }
      list.add(LinearPlaceholderFactory.createVerticalPlaceholder(component, null, bottomOfChildren, left, right));
    }
    else {
      // horizontal case
      int rightOfChildren = component.getDrawX();

      int top = component.getDrawY();
      int bottom = component.getDrawY() + component.getDrawHeight();

      for (SceneComponent child : component.getChildren()) {
        list.add(LinearPlaceholderFactory.createHorizontalPlaceholder(component, child, child.getDrawX(), top, bottom));
        rightOfChildren = Math.max(rightOfChildren, child.getDrawX() + child.getDrawWidth());
      }
      list.add(LinearPlaceholderFactory.createHorizontalPlaceholder(component, null, rightOfChildren, top, bottom));
    }
    list.add(new ViewGroupPlaceholder(component));
    return list;
  }
}
