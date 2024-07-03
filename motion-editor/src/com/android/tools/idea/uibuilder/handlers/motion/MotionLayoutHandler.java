/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.AndroidXConstants;
import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.model.NlAttributesHolder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.Placeholder;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.target.AnchorTarget;
import com.android.tools.idea.common.scene.target.LassoTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.Interaction;
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface;
import com.android.tools.idea.uibuilder.api.CustomPanel;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.actions.ViewAction;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.ConstraintLayoutComponentNotchProvider;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.ConstraintLayoutNotchProvider;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.BarrierAnchorTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.BarrierTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.GuidelineAnchorTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.GuidelineCycleTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.GuidelineTarget;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionAccessoryPanel;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionAttributePanel;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneUtils;
import com.android.tools.idea.uibuilder.handlers.motion.editor.targets.MotionLayoutAnchorTarget;
import com.android.tools.idea.uibuilder.handlers.motion.editor.targets.MotionLayoutResizeBaseTarget;
import com.android.tools.idea.uibuilder.handlers.motion.editor.targets.MotionLayoutResizeTarget;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.surface.AccessoryPanel;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * MotionLayout handler, implements the interactions for MotionLayout components.
 */
public class MotionLayoutHandler extends ViewGroupHandler implements ConstraintLayoutHandler.ConstraintLayoutSupported {
  private static final boolean DEBUG = false;
  // This is used to efficiently test if they are horizontal or vertical.
  private static HashSet<String> ourHorizontalBarriers = new HashSet<>(Arrays.asList(SdkConstants.GRAVITY_VALUE_TOP, SdkConstants.GRAVITY_VALUE_BOTTOM));
  private static String MOTION_ACCESSORY = "MotionLayoutHandler.MotionAccessory";

  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(SdkConstants.ATTR_TRANSITION_SHOW_PATHS);
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
      if (NlComponentHelperKt.isOrHasSuperclass(nlComponent, AndroidXConstants.CONSTRAINT_LAYOUT_GUIDELINE)) {
        String orientation = nlComponent.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ORIENTATION);

        boolean isHorizontal = true;
        if (orientation != null && orientation.equalsIgnoreCase(SdkConstants.ATTR_GUIDELINE_ORIENTATION_VERTICAL)) {
          isHorizontal = false;
        }

        listBuilder
          .add(new GuidelineTarget(isHorizontal))
          .add(isHorizontal ? new GuidelineAnchorTarget(AnchorTarget.Type.TOP, true)
                            : new GuidelineAnchorTarget(AnchorTarget.Type.LEFT, false))
          .add(new GuidelineCycleTarget(isHorizontal));
        return listBuilder.build();
      }

      if (NlComponentHelperKt.isOrHasSuperclass(nlComponent, AndroidXConstants.CONSTRAINT_LAYOUT_BARRIER)) {
        @NonNls String side = nlComponent.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_BARRIER_DIRECTION);
        boolean isHorizontal = (side == null || ourHorizontalBarriers.contains(side.toLowerCase(Locale.ROOT)));
        listBuilder
          .add(new BarrierAnchorTarget(isHorizontal ? AnchorTarget.Type.TOP : AnchorTarget.Type.RIGHT, BarrierTarget.parseDirection(side)))
          .add(new BarrierTarget(BarrierTarget.parseDirection(side)));
        return listBuilder.build();
      }
    }

    childComponent.setNotchProvider(new ConstraintLayoutComponentNotchProvider());

    listBuilder.add(
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
  public boolean shouldAddCommonDragTarget(@NotNull SceneComponent component) {
    return true;
  }

  @Override
  public List<Placeholder> getPlaceholders(@NotNull SceneComponent component, @NotNull List<SceneComponent> draggedComponents) {
    return ImmutableList.of(new MotionLayoutPlaceholder(component));
  }

  @Override
  public boolean addPopupMenuActions(@NotNull SceneComponent component, @NotNull List<ViewAction> actions) {
    CommonActions.getPopupMenuActions(component, actions);

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
  public AccessoryPanelInterface createAccessoryPanel(@NotNull DesignSurface<?> surface,
                                                      @NotNull AccessoryPanel.Type type,
                                                      @NotNull NlComponent parent,
                                                      @NotNull AccessoryPanelVisibility panelVisibility) {
    assert surface instanceof NlDesignSurface : "MotionLayoutHandler needs an NlDesignSurface";
    if (true) {
      switch (type) {
        case SOUTH_PANEL:
          if (DEBUG) {
            Debug.println("SOUTH PANEL");
          }
          MotionAccessoryPanel accessoryPanel = new MotionAccessoryPanel((NlDesignSurface)surface, parent, panelVisibility);
          parent.putClientProperty(MOTION_ACCESSORY, accessoryPanel);
          return accessoryPanel;
        case EAST_PANEL:
          if (DEBUG) {
            Debug.println("EAST PANEL");
          }
          return new MotionAttributePanel(parent, panelVisibility);
      }
    }
    else {
      switch (type) {
        case SOUTH_PANEL:
        case EAST_PANEL:
          MotionAccessoryPanel accessoryPanel = new MotionAccessoryPanel((NlDesignSurface)surface, parent, panelVisibility);
          parent.putClientProperty(MOTION_ACCESSORY, accessoryPanel);
          return accessoryPanel;
      }
    }
    throw new IllegalArgumentException("Unsupported type");
  }

  @Override
  @Nullable
  public Interaction createInteraction(@NotNull ScreenView screenView,
                                       @SwingCoordinate int x,
                                       @SwingCoordinate int y,
                                       @NotNull NlComponent component) {
    MotionLayoutComponentHelper helper = MotionLayoutComponentHelper.create(component);
    if (helper.isInTransition()) {
      if (MotionLayoutSceneInteraction.hitKeyFrame(screenView, x, y, helper, component)) {
        return new MotionLayoutSceneInteraction(screenView, component);
      }
    }
    return null;
  }

  @Override
  public void cleanUpAttributes(@NotNull NlComponent component, @NotNull NlAttributesHolder attributes) {
    ConstraintComponentUtilities.cleanup(attributes, component);
  }

  @Override
  public void onChildInserted(@NotNull NlComponent layout,
                              @NotNull NlComponent newChild,
                              @NotNull InsertType insertType) {
    newChild.ensureId();
    super.onChildInserted(layout, newChild, insertType);
  }

  @Override
  public void addToolbarActions(@NotNull List<ViewAction> actions) {
    CommonActions.getToolbarActions(actions);
  }

  @Override
  @NotNull
  public CustomPanel getLayoutCustomPanel() {
    return new MotionConstraintPanel(ImmutableList.of());
  }

  /**
   * Called when one or more children are about to be deleted by the user.
   *
   * @param parent  the parent of the deleted children (which still contains
   *                the children since this method is called before the deletion
   *                is performed)
   * @param deleted a nonempty list of children about to be deleted
   * @return true if the children have been fully deleted by this participant; false if normal deletion should resume. Note that even though
   * an implementation may return false from this method, that does not mean it did not perform any work. For example, a RelativeLayout
   * handler could remove constraints pointing to now deleted components, but leave the overall deletion of the elements to the core
   * designer.
   */
  @Override
  public boolean deleteChildren(@NotNull NlComponent parent, @NotNull Collection<NlComponent> deleted) {
    MotionAccessoryPanel accessoryPanel = (MotionAccessoryPanel)parent.getClientProperty(MOTION_ACCESSORY);
    final int count = parent.getChildCount();
    for (int i = 0; i < count; i++) {
      NlComponent component = parent.getChild(i);
      if (deleted.contains(component)) {
        String id = component.getId();
        if (id != null && accessoryPanel != null) {
          MotionSceneUtils.deleteRelatedConstraintSets(accessoryPanel.getMotionScene(), id);
        }
        continue;
      }
      ConstraintLayoutHandler.willDelete(component, deleted);
    }
    return false;
  }
}
