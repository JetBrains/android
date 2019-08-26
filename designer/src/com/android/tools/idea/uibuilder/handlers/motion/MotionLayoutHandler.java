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
package com.android.tools.idea.uibuilder.handlers.motion;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BARRIER_DIRECTION;
import static com.android.SdkConstants.ATTR_GUIDELINE_ORIENTATION_VERTICAL;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.ATTR_TRANSITION_SHOW_PATHS;
import static com.android.SdkConstants.CONSTRAINT_LAYOUT_BARRIER;
import static com.android.SdkConstants.CONSTRAINT_LAYOUT_GUIDELINE;
import static com.android.SdkConstants.GRAVITY_VALUE_BOTTOM;
import static com.android.SdkConstants.GRAVITY_VALUE_TOP;
import static com.android.SdkConstants.SHERPA_URI;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.Placeholder;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.target.AnchorTarget;
import com.android.tools.idea.common.scene.target.ComponentAssistantViewAction;
import com.android.tools.idea.common.scene.target.LassoTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.Interaction;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.actions.ViewAction;
import com.android.tools.idea.uibuilder.handlers.assistant.MotionLayoutAssistantPanel;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintPlaceholder;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.ConstraintLayoutComponentNotchProvider;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.ConstraintLayoutNotchProvider;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.BarrierAnchorTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.BarrierTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.ConstraintAnchorTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.ConstraintDragTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.ConstraintResizeTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.GuidelineAnchorTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.GuidelineCycleTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.GuidelineTarget;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionAccessoryPanel;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionAttributePanel;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionLayoutInterface;
import com.android.tools.idea.uibuilder.handlers.motion.editor.targets.MotionLayoutAnchorTarget;
import com.android.tools.idea.uibuilder.handlers.motion.editor.targets.MotionLayoutDragTarget;
import com.android.tools.idea.uibuilder.handlers.motion.editor.targets.MotionLayoutResizeBaseTarget;
import com.android.tools.idea.uibuilder.handlers.motion.editor.targets.MotionLayoutResizeTarget;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.property.assistant.ComponentAssistantFactory;
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget;
import com.android.tools.idea.uibuilder.surface.AccessoryPanel;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MotionLayoutHandler extends ViewGroupHandler /* implements NlComponentDelegate */ {
  private static final boolean DEBUG = false;

  // This is used to efficiently test if they are horizontal or vertical.
  private static HashSet<String> ourHorizontalBarriers = new HashSet<>(Arrays.asList(GRAVITY_VALUE_TOP, GRAVITY_VALUE_BOTTOM));

  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(ATTR_TRANSITION_SHOW_PATHS);
  }

  @Nullable
  private static ComponentAssistantFactory getComponentAssistant(@NotNull DesignSurface surface, @NotNull NlComponent component) {
    if (!StudioFlags.NELE_MOTION_LAYOUT_ANIMATIONS.get() || !SdkConstants.MOTION_LAYOUT.isEquals(component.getTagName())) {
      return null;
    }

    return (context) -> new MotionLayoutAssistantPanel(surface, context.getComponent());
  }

  @NotNull
  @Override
  public List<Target> createTargets(@NotNull SceneComponent sceneComponent) {
    sceneComponent.setNotchProvider(new ConstraintLayoutNotchProvider());
    return ImmutableList.of(
      new LassoTarget(),
      new MotionLayoutAnchorTarget(AnchorTarget.Type.LEFT, true),
      new MotionLayoutAnchorTarget(AnchorTarget.Type.TOP, true),
      new MotionLayoutAnchorTarget(AnchorTarget.Type.RIGHT, true),
      new MotionLayoutAnchorTarget(AnchorTarget.Type.BOTTOM, true)
    );
  }

  @NotNull
  @Override
  public List<Target> createChildTargets(@NotNull SceneComponent parentComponent, @NotNull SceneComponent childComponent) {
    ImmutableList.Builder<Target> listBuilder = new ImmutableList.Builder<>();

    NlComponent nlComponent = childComponent.getAuthoritativeNlComponent();
    nlComponent.setComponentModificationDelegate(new MotionLayoutComponentModificationDelegate());

    ViewInfo vi = NlComponentHelperKt.getViewInfo(nlComponent);
    if (vi != null) {
      if (NlComponentHelperKt.isOrHasSuperclass(nlComponent, CONSTRAINT_LAYOUT_GUIDELINE)) {
        String orientation = nlComponent.getAttribute(ANDROID_URI, ATTR_ORIENTATION);

        boolean isHorizontal = true;
        if (orientation != null && orientation.equalsIgnoreCase(ATTR_GUIDELINE_ORIENTATION_VERTICAL)) {
          isHorizontal = false;
        }

        listBuilder
          .add(new GuidelineTarget(isHorizontal))
          .add(isHorizontal ? new GuidelineAnchorTarget(AnchorTarget.Type.TOP, true)
                            : new GuidelineAnchorTarget(AnchorTarget.Type.LEFT, false))
          .add(new GuidelineCycleTarget(isHorizontal));
        return listBuilder.build();
      }

      if (NlComponentHelperKt.isOrHasSuperclass(nlComponent, CONSTRAINT_LAYOUT_BARRIER)) {
        @NonNls String side = nlComponent.getAttribute(SHERPA_URI, ATTR_BARRIER_DIRECTION);
        boolean isHorizontal = (side == null || ourHorizontalBarriers.contains(side.toLowerCase(Locale.ROOT)));
        listBuilder
          .add(new BarrierAnchorTarget(isHorizontal ? AnchorTarget.Type.TOP : AnchorTarget.Type.RIGHT, BarrierTarget.parseDirection(side)))
          .add(new BarrierTarget(BarrierTarget.parseDirection(side)));
        return listBuilder.build();
      }
    }

    childComponent.setNotchProvider(new ConstraintLayoutComponentNotchProvider());

    listBuilder.add(
      new MotionLayoutDragTarget(),
      new MotionLayoutResizeTarget(MotionLayoutResizeBaseTarget.Type.LEFT_TOP),
      new MotionLayoutResizeTarget(MotionLayoutResizeBaseTarget.Type.LEFT_BOTTOM),
      new MotionLayoutResizeTarget(MotionLayoutResizeBaseTarget.Type.RIGHT_TOP),
      new MotionLayoutResizeTarget(MotionLayoutResizeBaseTarget.Type.RIGHT_BOTTOM),
      new MotionLayoutAnchorTarget(AnchorTarget.Type.LEFT, false),
      new MotionLayoutAnchorTarget(AnchorTarget.Type.TOP, false),
      new MotionLayoutAnchorTarget(AnchorTarget.Type.RIGHT, false),
      new MotionLayoutAnchorTarget(AnchorTarget.Type.BOTTOM, false)
    );

    int baseline = NlComponentHelperKt.getBaseline(childComponent.getNlComponent());
    ViewInfo info = NlComponentHelperKt.getViewInfo(childComponent.getNlComponent());
    if (baseline <= 0 && info != null) {
      baseline = info.getBaseLine();
    }
    if (baseline > 0) {
      listBuilder.add(new MotionLayoutAnchorTarget(AnchorTarget.Type.BASELINE, false));
    }

    return listBuilder.build();
  }

  @Override
  public List<Placeholder> getPlaceholders(@NotNull SceneComponent component) {
    return ImmutableList.of(new MotionLayoutPlaceholder(component));
  }

  @Override
  public boolean addPopupMenuActions(@NotNull SceneComponent component, @NotNull List<ViewAction> actions) {
    CommonActions.getPopupMenuActions(component, actions);

    actions
      .add(new ComponentAssistantViewAction((nlComponent) -> getComponentAssistant(component.getScene().getDesignSurface(), nlComponent)));

    return false;
  }

  @Override
  public boolean needsAccessoryPanel(@NotNull AccessoryPanel.Type type) {
    switch (type) {
      case SOUTH_PANEL:
      case EAST_PANEL:
        return true;
    }
    return false;
  }

  @Override
  @NotNull
  public AccessoryPanelInterface createAccessoryPanel(@NotNull DesignSurface surface,
                                                      @NotNull AccessoryPanel.Type type,
                                                      @NotNull NlComponent parent,
                                                      @NotNull AccessoryPanelVisibility panelVisibility) {
    if (true) {
      switch (type) {
        case SOUTH_PANEL:
          if (DEBUG) {
            Debug.println("SOUTH PANEL");
          }
          return new MotionAccessoryPanel(surface, parent, panelVisibility);
        //return new MotionLayoutTimelinePanel(surface, parent, panelVisibility);
        case EAST_PANEL:
          if (DEBUG) {
            Debug.println("EAST PANEL");
          }
          return new MotionAttributePanel(parent, panelVisibility);
        //return  new MotionLayoutAttributePanel(parent, panelVisibility);
      }
    }
    else {
      switch (type) {
        case SOUTH_PANEL:
          return new MotionAccessoryPanel(surface, parent, panelVisibility);
        case EAST_PANEL:
          return new MotionAccessoryPanel(surface, parent, panelVisibility);
      }
    }
    throw new IllegalArgumentException("Unsupported type");
  }

  @Override
  public Interaction createInteraction(@NotNull ScreenView screenView, @NotNull NlComponent component) {
    return super.createInteraction(screenView, component);
  }

  public static MotionLayoutInterface getTimeline(@NotNull NlComponent component) {
    Object property = component.getClientProperty(MotionLayoutTimelinePanel.TIMELINE);
    if (property == null && component.getParent() != null) {
      // need to grab the timeline from the MotionLayout component...
      // TODO: walk the tree up until we find the MotionLayout?
      property = component.getParent().getClientProperty(MotionLayoutTimelinePanel.TIMELINE);
    }
    if (property == null || !(property instanceof MotionLayoutTimelinePanel)) {
      return null;
    }
    return (MotionLayoutTimelinePanel)property;
  }

  @Override
  public void addToolbarActions(@NotNull List<ViewAction> actions) {
    CommonActions.getToolbarActions(actions);
  }
}
