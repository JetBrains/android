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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BARRIER_DIRECTION;
import static com.android.SdkConstants.ATTR_GUIDELINE_ORIENTATION_HORIZONTAL;
import static com.android.SdkConstants.ATTR_GUIDELINE_ORIENTATION_VERTICAL;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT_CONSTRAINTSET;
import static com.android.SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X;
import static com.android.SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_MAX_HEIGHT;
import static com.android.SdkConstants.ATTR_MAX_WIDTH;
import static com.android.SdkConstants.ATTR_MIN_HEIGHT;
import static com.android.SdkConstants.ATTR_MIN_WIDTH;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.ATTR_VALUE;
import static com.android.SdkConstants.CLASS_CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.CLASS_CONSTRAINT_LAYOUT_CONSTRAINTS;
import static com.android.SdkConstants.CLASS_CONSTRAINT_LAYOUT_GROUP;
import static com.android.SdkConstants.CLASS_CONSTRAINT_LAYOUT_LAYER;
import static com.android.SdkConstants.CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.CONSTRAINT_LAYOUT_BARRIER;
import static com.android.SdkConstants.CONSTRAINT_LAYOUT_GUIDELINE;
import static com.android.SdkConstants.GRAVITY_VALUE_BOTTOM;
import static com.android.SdkConstants.GRAVITY_VALUE_TOP;
import static com.android.SdkConstants.ID_PREFIX;
import static com.android.SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN;
import static com.android.SdkConstants.SHERPA_URI;
import static com.android.SdkConstants.TAG;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.SdkConstants.VALUE_WRAP_CONTENT;
import static com.android.tools.idea.common.util.ImageUtilKt.iconToImage;
import static com.android.tools.idea.uibuilder.api.actions.ViewActionUtils.getViewOptionsAction;
import static icons.StudioIcons.LayoutEditor.Toolbar.BASELINE_ALIGNED_CONSTRAINT;
import static icons.StudioIcons.LayoutEditor.Toolbar.CENTER_HORIZONTAL;
import static icons.StudioIcons.LayoutEditor.Toolbar.CREATE_CONSTRAINTS;
import static icons.StudioIcons.LayoutEditor.Toolbar.CREATE_HORIZ_CHAIN;
import static icons.StudioIcons.LayoutEditor.Toolbar.LEFT_ALIGNED;
import static icons.StudioIcons.LayoutEditor.Toolbar.PACK_HORIZONTAL;
import static icons.StudioIcons.LayoutEditor.Toolbar.GUIDELINE_VERTICAL;

import com.android.ide.common.rendering.api.AttrResourceValueImpl;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.common.api.DragType;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.model.NlAttributesHolder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.ComponentProvider;
import com.android.tools.idea.common.scene.Placeholder;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.target.AnchorTarget;
import com.android.tools.idea.common.scene.target.LassoTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.Interaction;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog;
import com.android.tools.idea.uibuilder.actions.ToggleLiveRenderingAction;
import com.android.tools.idea.uibuilder.analytics.NlUsageTracker;
import com.android.tools.idea.uibuilder.api.CustomPanel;
import com.android.tools.idea.uibuilder.api.DragHandler;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.actions.DirectViewAction;
import com.android.tools.idea.uibuilder.api.actions.NestedViewActionMenu;
import com.android.tools.idea.uibuilder.api.actions.ToggleAutoConnectAction;
import com.android.tools.idea.uibuilder.api.actions.ToggleViewAction;
import com.android.tools.idea.uibuilder.api.actions.ViewAction;
import com.android.tools.idea.uibuilder.api.actions.ViewActionMenu;
import com.android.tools.idea.uibuilder.api.actions.ViewActionPresentation;
import com.android.tools.idea.uibuilder.api.actions.ViewActionSeparator;
import com.android.tools.idea.uibuilder.graphics.NlIcon;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.ConstraintLayoutComponentNotchProvider;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.ConstraintLayoutNotchProvider;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.WidgetDraw;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.decorator.WidgetDecorator;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.BarrierAnchorTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.BarrierTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.BaseLineToggleViewAction;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.ChainCycleViewAction;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.ConstraintAnchorTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.ConstraintDragTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.ConstraintResizeTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.GuidelineAnchorTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.GuidelineCycleTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.GuidelineTarget;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget;
import com.android.tools.idea.uibuilder.scout.Scout;
import com.android.tools.idea.uibuilder.scout.ScoutMotionConvert;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.idea.util.DependencyManagementUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.wireless.android.sdk.stats.LayoutEditorEvent;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.LafIconLookup;
import icons.AndroidIcons;
import icons.StudioIcons;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.Icon;
import javax.swing.Timer;
import org.intellij.lang.annotations.JdkConstants.InputEventMask;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handles interactions for the ConstraintLayout
 */
public class ConstraintLayoutHandler extends ViewGroupHandler implements ComponentProvider {

  private static final String PREFERENCE_KEY_PREFIX = "ConstraintLayoutPreference";
  /**
   * Preference key (used with {@link PropertiesComponent}) for show all constraints mode
   */
  public static final String SHOW_CONSTRAINTS_PREF_KEY = PREFERENCE_KEY_PREFIX + "ShowAllConstraints";
  public static final String SHOW_MARGINS_PREF_KEY = PREFERENCE_KEY_PREFIX + "ShowMargins";
  public static final String FADE_UNSELECTED_VIEWS = PREFERENCE_KEY_PREFIX + "FadeUnselected";

  private static final NlIcon BASELINE_ICON =
    new NlIcon(StudioIcons.LayoutEditor.Toolbar.BASELINE_ALIGNED, StudioIcons.LayoutEditor.Toolbar.BASELINE_ALIGNED_CONSTRAINT);

  private final static String ADD_VERTICAL_BARRIER = "Add Vertical Barrier";
  private final static String ADD_HORIZONTAL_BARRIER = "Add Horizontal Barrier";
  private final static String ADD_TO_BARRIER = "Add to Barrier";
  private final static String ADD_LAYER = "Add Layer";
  private final static String ADD_GROUP = "Add Group";
  private final static String ADD_CONSTRAINTS_SET = "Add Set of Constraints";

  private static HashMap<String, Boolean> ourVisibilityFlags = new HashMap<>();

  // This is used to efficiently test if they are horizontal or vertical.
  private static HashSet<String> ourHorizontalBarriers = new HashSet<>(Arrays.asList(GRAVITY_VALUE_TOP, GRAVITY_VALUE_BOTTOM));

  /**
   * Base constructor
   */
  public ConstraintLayoutHandler() {
    loadWidgetDecoratorImages();
  }

  private static void loadWidgetDecoratorImages() {
    if (WidgetDecorator.sLockImageIcon == null) {
      WidgetDecorator.sLockImageIcon = iconToImage(StudioIcons.LayoutEditor.Toolbar.LOCK);
    }
    if (WidgetDecorator.sUnlockImageIcon == null) {
      WidgetDecorator.sUnlockImageIcon = iconToImage(StudioIcons.LayoutEditor.Toolbar.UNLOCK);
    }
    if (WidgetDecorator.sDeleteConnectionsImageIcon == null) {
      WidgetDecorator.sDeleteConnectionsImageIcon = iconToImage(StudioIcons.LayoutEditor.Toolbar.CLEAR_CONSTRAINTS);
    }
    if (WidgetDecorator.sPackChainImageIcon == null) {
      WidgetDecorator.sPackChainImageIcon = iconToImage(StudioIcons.LayoutEditor.Toolbar.CYCLE_CHAIN_PACKED);
    }
    if (WidgetDraw.sGuidelineArrowLeft == null) {
      WidgetDraw.sGuidelineArrowLeft = iconToImage(StudioIcons.LayoutEditor.Toolbar.ARROW_LEFT);
    }
    if (WidgetDraw.sGuidelineArrowRight == null) {
      WidgetDraw.sGuidelineArrowRight = iconToImage(StudioIcons.LayoutEditor.Toolbar.ARROW_RIGHT);
    }
    if (WidgetDraw.sGuidelineArrowUp == null) {
      WidgetDraw.sGuidelineArrowUp = iconToImage(StudioIcons.LayoutEditor.Toolbar.ARROW_UP);
    }
    if (WidgetDraw.sGuidelineArrowDown == null) {
      WidgetDraw.sGuidelineArrowDown = iconToImage(StudioIcons.LayoutEditor.Toolbar.ARROW_DOWN);
    }
    if (WidgetDraw.sGuidelinePercent == null) {
      WidgetDraw.sGuidelinePercent = iconToImage(StudioIcons.LayoutEditor.Toolbar.PERCENT);
    }
  }

  @Override
  @NotNull
  public Map<String, Map<String, String>> getEnumPropertyValues(@SuppressWarnings("unused") @NotNull NlComponent component) {
    Map<String, String> values = ImmutableMap.of("0dp", "0dp (match constraint)", VALUE_WRAP_CONTENT, VALUE_WRAP_CONTENT);
    return ImmutableMap.of(ATTR_LAYOUT_WIDTH, values,
                           ATTR_LAYOUT_HEIGHT, values);
  }

  @Override
  @NotNull
  public CustomPanel getLayoutCustomPanel() {
    return new WidgetConstraintPanel(ImmutableList.of());
  }

  @Override
  public void addToolbarActions(@NotNull List<ViewAction> actions) {
    actions.add(getViewOptionsAction(ImmutableList.of(
      new ToggleVisibilityAction(SHOW_CONSTRAINTS_PREF_KEY, "Show All Constraints", false),
      new ToggleVisibilityAction(SHOW_MARGINS_PREF_KEY, "Show Margins", true),
      new ToggleVisibilityAction(FADE_UNSELECTED_VIEWS, "Fade Unselected Views ", false),
      new ToggleLiveRenderingAction())));
    actions.add(new ToggleAutoConnectAction());
    actions.add(new MarginSelector());
    actions.add(new ViewActionSeparator());
    actions.add(new ClearConstraintsAction());
    actions.add((new InferAction()));
    if (StudioFlags.ENABLE_NEW_SCOUT.get()) {
      actions.add((new ScoutAction()));
    }
    actions.add((new ViewActionSeparator()));

    // TODO Decide if we want lock actions.add(new LockConstraints());
    // noinspection unchecked
    actions.add(new NestedViewActionMenu("Pack", StudioIcons.LayoutEditor.Toolbar.PACK_VERTICAL, Lists.<List<ViewAction>>newArrayList(
      Lists.newArrayList(
        new AlignAction(Scout.Arrange.HorizontalPack,
                        PACK_HORIZONTAL,
                        "Pack Horizontally"),
        new AlignAction(Scout.Arrange.VerticalPack,
                        StudioIcons.LayoutEditor.Toolbar.PACK_VERTICAL,
                        "Pack Vertically"),
        new AlignAction(Scout.Arrange.ExpandHorizontally,
                        StudioIcons.LayoutEditor.Toolbar.EXPAND_HORIZONTAL,
                        "Expand Horizontally"),
        new AlignAction(Scout.Arrange.ExpandVertically,
                        StudioIcons.LayoutEditor.Toolbar.EXPAND_VERTICAL,
                        "Expand Vertically"),
        new ViewActionSeparator(),
        new AlignAction(Scout.Arrange.DistributeHorizontally,
                        StudioIcons.LayoutEditor.Toolbar.DISTRIBUTE_HORIZONTAL,
                        StudioIcons.LayoutEditor.Toolbar.DISTRIBUTE_HORIZONTAL_CONSTRAINT,
                        "Distribute Horizontally"),
        new AlignAction(Scout.Arrange.DistributeVertically,
                        StudioIcons.LayoutEditor.Toolbar.DISTRIBUTE_VERTICAL,
                        StudioIcons.LayoutEditor.Toolbar.DISTRIBUTE_VERTICAL_CONSTRAINT,
                        "Distribute Vertically")
      )
    )) {
      @Override
      public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                     @NotNull ViewEditor editor,
                                     @NotNull ViewHandler handler,
                                     @NotNull NlComponent component,
                                     @NotNull List<NlComponent> selectedChildren,
                                     @InputEventMask int modifiers) {
        super.updatePresentation(presentation, editor, handler, component, selectedChildren, modifiers);
        presentation.setVisible(isConstraintLayoutChild(selectedChildren));
      }
    });

    // noinspection unchecked
    actions
      .add(new NestedViewActionMenu("Align", StudioIcons.LayoutEditor.Toolbar.LEFT_ALIGNED_CONSTRAINT, Lists.<List<ViewAction>>newArrayList(
        Stream.of(ConstraintViewActions.ALIGN_HORIZONTALLY_ACTIONS,
                  ConstraintViewActions.ALIGN_VERTICALLY_ACTIONS,
                  ImmutableList.of(new ViewActionSeparator()),
                  ConstraintViewActions.CENTER_ACTIONS)
          .flatMap(list -> list.stream())
          .collect(Collectors.toList()))) {
        @Override
        public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                       @NotNull ViewEditor editor,
                                       @NotNull ViewHandler handler,
                                       @NotNull NlComponent component,
                                       @NotNull List<NlComponent> selectedChildren,
                                       @InputEventMask int modifiers) {
          super.updatePresentation(presentation, editor, handler, component, selectedChildren, modifiers);
          presentation.setVisible(isConstraintLayoutChild(selectedChildren));
        }
      });

    // noinspection unchecked
    actions
      .add(new NestedViewActionMenu("Guidelines", GUIDELINE_VERTICAL, Lists.<List<ViewAction>>newArrayList(
        ConstraintViewActions.HELPER_ACTIONS)));

    ViewActionSeparator.setupFollowingActions(actions);
  }

  private static boolean isConstraintLayoutChild(List<NlComponent> children) {
    for (NlComponent child : children) {
      NlComponent parent = child.getParent();
      if (parent == null) { // can be null when an element has just been deleted
        continue;
      }

      if (NlComponentHelperKt.isOrHasSuperclass(parent, CONSTRAINT_LAYOUT)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Class to support sub-menus that vanish if all items are disabled
   */
  static class DisappearingActionMenu extends ViewActionMenu implements EnabledAction {

    public DisappearingActionMenu(@NotNull String menuName,
                                  @Nullable Icon icon,
                                  @NotNull List<ViewAction> actions) {
      super(menuName, icon, actions);
    }

    @Override
    public boolean isEnabled(List<NlComponent> selected) {
      for (ViewAction action : myActions) {
        if (action instanceof EnabledAction) {
          if (((EnabledAction)action).isEnabled(selected)) {
            return true;
          }
        }
        else {
          return true;
        }
      }
      return false;
    }
  }

  @Override
  public boolean addPopupMenuActions(@NotNull SceneComponent component, @NotNull List<ViewAction> actions) {
    actions.add(new BaseLineToggleViewAction());
    actions.add(new ClearConstraintsSelectedComponentsAction());
    actions.add(new ChainCycleViewAction());

    actions.add(new DisappearingActionMenu("Constrain", CREATE_CONSTRAINTS, ConstraintViewActions.CONNECT_ACTIONS));
    actions.add(new DisappearingActionMenu("Organize", PACK_HORIZONTAL, ConstraintViewActions.ORGANIZE_ACTIONS));
    actions.add(new DisappearingActionMenu("Align", LEFT_ALIGNED, ConstraintViewActions.ALIGN_ACTIONS));
    actions.add(new DisappearingActionMenu("Chains", CREATE_HORIZ_CHAIN, ConstraintViewActions.CHAIN_ACTIONS));
    actions.add(new DisappearingActionMenu("Center", CENTER_HORIZONTAL, ConstraintViewActions.CENTER_ACTIONS));
    actions.add(new DisappearingActionMenu("Helpers", GUIDELINE_VERTICAL, ConstraintViewActions.HELPER_ACTIONS));

    if (StudioFlags.NELE_MOTION_LAYOUT_EDITOR.get()) {
      actions.add(new ConvertToMotionLayoutComponentsAction());
    }
    return true;
  }

  /**
   * Return a new ConstraintInteraction instance to handle a mouse interaction
   *
   * @param screenView the associated screen view
   * @param component  the component we belong to
   * @return a new instance of ConstraintInteraction
   */
  @Override
  public Interaction createInteraction(@NotNull ScreenView screenView, @NotNull NlComponent component) {
    return new ConstraintSceneInteraction(screenView, component);
  }

  @NotNull
  @Override
  public List<Target> createTargets(@NotNull SceneComponent sceneComponent) {
    sceneComponent.setNotchProvider(new ConstraintLayoutNotchProvider());

    return ImmutableList.of(
      new LassoTarget(),
      new ConstraintAnchorTarget(AnchorTarget.Type.LEFT, true),
      new ConstraintAnchorTarget(AnchorTarget.Type.TOP, true),
      new ConstraintAnchorTarget(AnchorTarget.Type.RIGHT, true),
      new ConstraintAnchorTarget(AnchorTarget.Type.BOTTOM, true)
    );
  }

  @NotNull
  @Override
  public List<Target> createChildTargets(@NotNull SceneComponent parentComponent, @NotNull SceneComponent childComponent) {
    ImmutableList.Builder<Target> listBuilder = new ImmutableList.Builder<>();

    NlComponent nlComponent = childComponent.getAuthoritativeNlComponent();
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
        boolean isHorizontal = (side == null || ourHorizontalBarriers.contains(side.toLowerCase()));
        listBuilder
          .add(new BarrierAnchorTarget(isHorizontal ? AnchorTarget.Type.TOP : AnchorTarget.Type.RIGHT, BarrierTarget.parseDirection(side)))
          .add(new BarrierTarget(BarrierTarget.parseDirection(side)));
        return listBuilder.build();
      }
    }

    childComponent.setComponentProvider(this);
    childComponent.setNotchProvider(new ConstraintLayoutComponentNotchProvider());

    listBuilder.add(
      new ConstraintDragTarget(),
      new ConstraintResizeTarget(ResizeBaseTarget.Type.LEFT_TOP),
      new ConstraintResizeTarget(ResizeBaseTarget.Type.LEFT_BOTTOM),
      new ConstraintResizeTarget(ResizeBaseTarget.Type.RIGHT_TOP),
      new ConstraintResizeTarget(ResizeBaseTarget.Type.RIGHT_BOTTOM),
      new ConstraintAnchorTarget(AnchorTarget.Type.LEFT, false),
      new ConstraintAnchorTarget(AnchorTarget.Type.TOP, false),
      new ConstraintAnchorTarget(AnchorTarget.Type.RIGHT, false),
      new ConstraintAnchorTarget(AnchorTarget.Type.BOTTOM, false)
    );

    int baseline = NlComponentHelperKt.getBaseline(childComponent.getNlComponent());
    ViewInfo info = NlComponentHelperKt.getViewInfo(childComponent.getNlComponent());
    if (baseline <= 0 && info != null) {
      baseline = info.getBaseLine();
    }
    if (baseline > 0) {
      listBuilder.add(new ConstraintAnchorTarget(AnchorTarget.Type.BASELINE, false));
    }

    return listBuilder.build();
  }

  @Override
  public void cleanUpAttributes(@NotNull NlComponent component, @NotNull NlAttributesHolder attributes) {
    ConstraintComponentUtilities.cleanup(attributes, component);
  }

  /**
   * Let the ViewGroupHandler handle clearing attributes on a given component
   *
   * @param component
   */
  @Override
  public void clearAttributes(@NotNull NlComponent component) {
    ConstraintComponentUtilities.clearAttributes(component);
  }

  /**
   * Return a drag handle to handle drag and drop interaction
   *
   * @param editor     the associated IDE editor
   * @param layout     the layout being dragged over/into
   * @param components the components being dragged
   * @param type       the <b>initial</b> type of drag, which can change along the way
   * @return instance of a ConstraintDragHandler
   */
  @Override
  public DragHandler createDragHandler(@NotNull ViewEditor editor,
                                       @NotNull SceneComponent layout,
                                       @NotNull List<NlComponent> components,
                                       @NotNull DragType type) {
    return new ConstraintDragHandler(editor, this, layout, components, type);
  }

  @Override
  public void onChildRemoved(@NotNull ViewEditor editor,
                             @NotNull NlComponent layout,
                             @NotNull NlComponent newChild,
                             @NotNull InsertType insertType) {
    for (String attribute : ConstraintComponentUtilities.ourConstraintLayoutAttributesToClear) {
      newChild.removeAttribute(SHERPA_URI, attribute);
    }
    newChild.removeAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X);
    newChild.removeAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y);
  }

  /**
   * Return true to be in charge of the painting
   *
   * @return true
   */
  @Override
  public boolean handlesPainting() {
    return true;
  }

  private static class ClearConstraintsAction extends DirectViewAction {

    private static final String MESSAGE_DELETE_CONSTRAINT = "Delete all the constraints in the current layout?";

    private ClearConstraintsAction() {
      super(StudioIcons.LayoutEditor.Toolbar.CLEAR_CONSTRAINTS, "Clear All Constraints");
    }

    @Override
    @Nullable
    public String getConfirmationMessage() {
      return MESSAGE_DELETE_CONSTRAINT;
    }

    @Override
    public void perform(@NotNull ViewEditor editor,
                        @NotNull ViewHandler handler,
                        @NotNull NlComponent component,
                        @NotNull List<NlComponent> selectedChildren,
                        @InputEventMask int modifiers) {
      NlUsageTracker.getInstance(editor.getScene().getDesignSurface())
        .logAction(LayoutEditorEvent.LayoutEditorEventType.CLEAR_ALL_CONSTRAINTS);

      editor.getScene().clearAllConstraints();
      ensureLayersAreShown(editor, 1000);
    }
  }

  private static class ClearConstraintsSelectedComponentsAction extends DirectViewAction {
    @Override
    public void perform(@NotNull ViewEditor editor,
                        @NotNull ViewHandler handler,
                        @NotNull NlComponent component,
                        @NotNull List<NlComponent> selectedChildren,
                        @InputEventMask int modifiers) {
      for (NlComponent child : selectedChildren) {
        ConstraintComponentUtilities.clearAttributes(child);
      }
      ensureLayersAreShown(editor, 1000);
    }

    @Override
    public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren,
                                   @InputEventMask int modifiers) {
      presentation.setIcon(StudioIcons.LayoutEditor.Toolbar.CLEAR_CONSTRAINTS);
      presentation.setLabel("Clear Constraints of Selection");
    }
  }


  private static class ConvertToMotionLayoutComponentsAction extends DirectViewAction {
    @Override
    public void perform(@NotNull ViewEditor editor,
                        @NotNull ViewHandler handler,
                        @NotNull NlComponent component,
                        @NotNull List<NlComponent> selectedChildren,
                        @InputEventMask int modifiers) {
      // Todo we should log
      String cl_name = DependencyManagementUtil.mapAndroidxName(component.getModel().getModule(), CLASS_CONSTRAINT_LAYOUT);
      if (!component.getTagDeprecated().getName().equals(cl_name)) {
        NlComponent parent = component.getParent();
        if (parent != null) {
          component = parent;
        }
      }
      if (!component.getTagDeprecated().getName().equals(cl_name)) {
        Messages.showErrorDialog(editor.getScene().getDesignSurface(), "You can only convert ConstraintLayout not "+component.getTagDeprecated().getName(), getLabel());
        return;
      }
      if (Messages.showYesNoDialog(editor.getScene().getDesignSurface(), "Convert to MotionLayout?", getLabel(),null) ==
          Messages.YES) {

        ScoutMotionConvert.convert(component);
      }
    }

    @Override
    public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren,
                                   @InputEventMask int modifiers) {
      presentation.setIcon(StudioIcons.LayoutEditor.Toolbar.CYCLE_CHAIN_SPREAD);
      presentation.setLabel("Convert to MotionLayout");
    }
  }

  /**
   * Make sure to have the SceneLayer on the DesignSurface
   * are fully painted for the given duration
   *
   * @param editor   the ViewEditor holding the DesignSurface
   * @param duration how long to paint the SceneLayers, in ms
   */
  @SuppressWarnings("SameParameterValue") // For duration being always == 1000
  private static void ensureLayersAreShown(@NotNull ViewEditor editor, int duration) {
    NlDesignSurface designSurface = (NlDesignSurface)editor.getScene().getDesignSurface();
    designSurface.forceLayersPaint(true);
    designSurface.repaint();
    Timer timer = new Timer(duration, actionEvent -> designSurface.forceLayersPaint(false));
    timer.setRepeats(false);
    timer.start();
  }

  private static class InferAction extends DirectViewAction {
    @Override
    public void perform(@NotNull ViewEditor editor,
                        @NotNull ViewHandler handler,
                        @NotNull NlComponent component,
                        @NotNull List<NlComponent> selectedChildren,
                        @InputEventMask int modifiers) {
      NlUsageTracker.getInstance(editor.getScene().getDesignSurface())
                           .logAction(LayoutEditorEvent.LayoutEditorEventType.INFER_CONSTRAINS);
      try {
        Scout.inferConstraintsAndCommit(component);
        ensureLayersAreShown(editor, 1000);
      }
      catch (Exception e) {
        // TODO show dialog the inference failed
        Logger.getInstance(ConstraintLayoutHandler.class).warn("Error in inferring constraints", e);
      }
    }

    @Override
    public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren,
                                   @InputEventMask int modifiers) {
      presentation.setIcon(StudioIcons.LayoutEditor.Toolbar.INFER_CONSTRAINTS);
      presentation.setLabel("Infer Constraints");
    }
  }

  private static class ScoutAction extends DirectViewAction {
    @Override
    public void perform(@NotNull ViewEditor editor,
                        @NotNull ViewHandler handler,
                        @NotNull NlComponent component,
                        @NotNull List<NlComponent> selectedChildren,
                        @InputEventMask int modifiers) {
      NlUsageTracker.getInstance(editor.getScene().getDesignSurface())
                           .logAction(LayoutEditorEvent.LayoutEditorEventType.INFER_CONSTRAINS);
      try {
        Scout.findConstraintSetAndCommit(component);
        ensureLayersAreShown(editor, 1000);
      }
      catch (Exception e) {
        // TODO show dialog the inference failed
        Logger.getInstance(ConstraintLayoutHandler.class).warn("Error in inferring constraints", e);
      }
    }

    @Override
    public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren,
                                   @InputEventMask int modifiers) {
      presentation.setIcon(StudioIcons.LayoutEditor.Toolbar.INFER_CONSTRAINTS);
      presentation.setLabel("Infer Constraints (new)");
    }
  }

  private static class ToggleVisibilityAction extends ToggleViewAction {
    String mType;

    public ToggleVisibilityAction(String type, String text, boolean defaultValue) {
      // TODO: Set icon when updating action. Otherwise, when changing theme, icon will remain with outdated theme.
      super(null, LafIconLookup.getIcon("checkmark"), text, text);
      mType = type;
      ourVisibilityFlags.put(mType, PropertiesComponent.getInstance().getBoolean(type, defaultValue));
    }

    @Override
    public boolean isSelected(@NotNull ViewEditor editor,
                              @NotNull ViewHandler handler,
                              @NotNull NlComponent parent,
                              @NotNull List<NlComponent> selectedChildren) {
      return ourVisibilityFlags.get(mType);
    }

    @Override
    public void setSelected(@NotNull ViewEditor editor,
                            @NotNull ViewHandler handler,
                            @NotNull NlComponent parent,
                            @NotNull List<NlComponent> selectedChildren,
                            boolean selected) {
      ourVisibilityFlags.put(mType, selected);

      PropertiesComponent.getInstance().setValue(mType, selected);
      ensureLayersAreShown(editor, 1000);
    }
  }

  public static boolean getVisualProperty(String prop) {
    if (ourVisibilityFlags.containsKey(prop)) {
      return ourVisibilityFlags.get(prop);
    }
    boolean selected = PropertiesComponent.getInstance().getBoolean(prop);
    ourVisibilityFlags.put(prop, selected);
    return selected;
  }

  /**
   * Used in testing
   */
  public static void forceDefaultVisualProperties() {
    ourVisibilityFlags.put(SHOW_CONSTRAINTS_PREF_KEY, true);
    ourVisibilityFlags.put(SHOW_MARGINS_PREF_KEY, true);
    ourVisibilityFlags.put(FADE_UNSELECTED_VIEWS, false);
  }

  static class ControlIcon implements Icon {
    @SuppressWarnings("UseJBColor")
    public static final Color HIGHLIGHT_COLOR = new Color(0x03a9f4);
    Icon myIcon;
    boolean myHighlight;

    ControlIcon(Icon icon) {
      myIcon = icon;
    }

    public void setHighlight(boolean mHighlight) {
      this.myHighlight = mHighlight;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {

      myIcon.paintIcon(c, g, x, y);
      if (myHighlight) {
        g.setColor(HIGHLIGHT_COLOR);
        g.fillRect(x, y + getIconHeight() - 2, getIconWidth(), 2);
      }
    }

    @Override
    public int getIconWidth() {
      return myIcon.getIconWidth();
    }

    @Override
    public int getIconHeight() {
      return myIcon.getIconHeight();
    }
  }

  private static class AddElementAction extends DirectViewAction {
    public static final int HORIZONTAL_GUIDELINE = 0;
    public static final int VERTICAL_GUIDELINE = 1;
    public static final int HORIZONTAL_BARRIER = 2;
    public static final int VERTICAL_BARRIER = 3;
    public static final int GROUP = 4;
    public static final int CONSTRAINT_SET = 5;
    public static final int LAYER = 6;

    final int myType;

    public AddElementAction(int type, Icon icon, String text) {
      super(icon, text);
      myType = type;
    }

    @Override
    public void perform(@NotNull ViewEditor editor,
                        @NotNull ViewHandler handler,
                        @NotNull NlComponent component,
                        @NotNull List<NlComponent> selectedChildren,
                        @InputEventMask int modifiers) {
      NlComponent parent = component;
      while (parent != null && !NlComponentHelperKt.isOrHasSuperclass(parent, CONSTRAINT_LAYOUT)) {
        parent = parent.getParent();
      }
      if (parent != null) {
        boolean useAndroidx = NlComponentHelperKt.isOrHasAndroidxSuperclass(parent);
        ensureLayersAreShown(editor, 1000);
        switch (myType) {
          case HORIZONTAL_GUIDELINE: {
            NlComponent guideline = NlComponentHelperKt
              .createChild(parent, editor, useAndroidx ? CONSTRAINT_LAYOUT_GUIDELINE.newName() : CONSTRAINT_LAYOUT_GUIDELINE.oldName(),
                           null, InsertType.CREATE);
            assert guideline != null;
            guideline.ensureId();
            guideline.setAttribute(SHERPA_URI, LAYOUT_CONSTRAINT_GUIDE_BEGIN, "20dp");
            NlUsageTracker tracker = NlUsageTracker.getInstance(editor.getScene().getDesignSurface());
            tracker.logAction(LayoutEditorEvent.LayoutEditorEventType.ADD_HORIZONTAL_GUIDELINE);
            guideline.setAttribute(ANDROID_URI, ATTR_ORIENTATION,
                                   ATTR_GUIDELINE_ORIENTATION_HORIZONTAL);
          }
          break;
          case VERTICAL_GUIDELINE: {
            NlComponent guideline = NlComponentHelperKt
              .createChild(parent, editor, useAndroidx ? CONSTRAINT_LAYOUT_GUIDELINE.newName() : CONSTRAINT_LAYOUT_GUIDELINE.oldName(),
                           null, InsertType.CREATE);
            assert guideline != null;
            guideline.ensureId();
            guideline.setAttribute(SHERPA_URI, LAYOUT_CONSTRAINT_GUIDE_BEGIN, "20dp");
            NlUsageTracker tracker = NlUsageTracker.getInstance(editor.getScene().getDesignSurface());

            tracker.logAction(LayoutEditorEvent.LayoutEditorEventType.ADD_VERTICAL_GUIDELINE);
            guideline.setAttribute(ANDROID_URI, ATTR_ORIENTATION,
                                   ATTR_GUIDELINE_ORIENTATION_VERTICAL);
          }
          break;
          case GROUP: {
            NlComponent group = NlComponentHelperKt
              .createChild(parent, editor, useAndroidx ? CLASS_CONSTRAINT_LAYOUT_GROUP.newName() : CLASS_CONSTRAINT_LAYOUT_GROUP.oldName(),
                           null, InsertType.CREATE);
            assert group != null;
            group.ensureId();
          }
          break;
          case CONSTRAINT_SET: {
            NlComponent constraints =
              NlComponentHelperKt.createChild(parent, editor, useAndroidx
                                                              ? CLASS_CONSTRAINT_LAYOUT_CONSTRAINTS.newName()
                                                              : CLASS_CONSTRAINT_LAYOUT_CONSTRAINTS.oldName(), null, InsertType.CREATE);
            assert constraints != null;
            constraints.ensureId();
            ConstraintReferenceManagement.populateConstraints(constraints);
          }
          break;
          case LAYER: {
            NlComponent layer = NlComponentHelperKt
              .createChild(parent, editor, useAndroidx ? CLASS_CONSTRAINT_LAYOUT_LAYER.newName() : CLASS_CONSTRAINT_LAYOUT_LAYER.oldName(),
                           null, InsertType.CREATE);
            assert layer != null;
            layer.ensureId();
          }
          break;
          case HORIZONTAL_BARRIER: {
            int barriers = 0;
            int other = 0;
            for (NlComponent child : selectedChildren) {
              if (NlComponentHelperKt.isOrHasSuperclass(child, CONSTRAINT_LAYOUT_BARRIER)) {
                barriers++;
              }
              if (!ConstraintComponentUtilities.isLine(child)) {
                other++;
              }
            }
            if (barriers == 1 && other > 0) {
              NlComponent barrier = null;
              for (NlComponent child : selectedChildren) {
                if (NlComponentHelperKt.isOrHasSuperclass(child, CONSTRAINT_LAYOUT_BARRIER)) {
                  break;
                }
              }
              if (ConstraintHelperHandler.USE_HELPER_TAGS) {
                if (barrier != null) {
                  for (NlComponent child : selectedChildren) {
                    if (ConstraintComponentUtilities.isLine(child)) {
                      continue;
                    }
                    NlComponent tag = NlComponentHelperKt.createChild(barrier, editor, TAG, null, InsertType.CREATE);
                    tag.removeAndroidAttribute(ATTR_LAYOUT_WIDTH);
                    tag.removeAndroidAttribute(ATTR_LAYOUT_HEIGHT);
                    tag.setAttribute(ANDROID_URI, ATTR_ID, ID_PREFIX + child.getId());
                    tag.setAttribute(ANDROID_URI, ATTR_VALUE, VALUE_TRUE);
                  }
                }
              } // TODO: add views to the barrier when not using the tags approach
              return;
            }

            NlComponent barrier = NlComponentHelperKt
              .createChild(parent, editor, useAndroidx ? CONSTRAINT_LAYOUT_BARRIER.newName() : CONSTRAINT_LAYOUT_BARRIER.oldName(), null,
                           InsertType.CREATE);
            assert barrier != null;
            barrier.ensureId();
            barrier.setAttribute(SHERPA_URI, ATTR_BARRIER_DIRECTION, "top");

            // NlUsageTracker tracker = NlUsageTrackerImpl.getInstance(editor.getScene().getDesignSurface());
            // TODO add tracker.logAction(LayoutEditorEvent.LayoutEditorEventType.ADD_HORIZONTAL_BARRIER);

            if (ConstraintHelperHandler.USE_HELPER_TAGS) {
              if (!selectedChildren.isEmpty()) {
                NlComponent tag = NlComponentHelperKt.createChild(barrier, editor, TAG, null, InsertType.CREATE);
                tag.removeAndroidAttribute(ATTR_LAYOUT_WIDTH);
                tag.removeAndroidAttribute(ATTR_LAYOUT_HEIGHT);
                for (NlComponent child : selectedChildren) {
                  if (ConstraintComponentUtilities.isLine(child)) {
                    continue;
                  }
                  tag.setAttribute(ANDROID_URI, ATTR_ID, ID_PREFIX + child.getId());
                  tag.setAttribute(ANDROID_URI, ATTR_VALUE, VALUE_TRUE);
                }
              }
            }
          }
          break;
          case VERTICAL_BARRIER: {
            int barriers = 0;
            int other = 0;
            for (NlComponent child : selectedChildren) {
              if (NlComponentHelperKt.isOrHasSuperclass(child, CONSTRAINT_LAYOUT_BARRIER)) {
                barriers++;
              }
              if (!ConstraintComponentUtilities.isLine(child)) {
                other++;
              }
            }
            if (barriers == 1 && other > 0) {
              NlComponent barrier = null;
              for (NlComponent child : selectedChildren) {
                if (NlComponentHelperKt.isOrHasSuperclass(child, CONSTRAINT_LAYOUT_BARRIER)) {
                  break;
                }
              }
              if (ConstraintHelperHandler.USE_HELPER_TAGS) {
                if (barrier != null) {
                  for (NlComponent child : selectedChildren) {
                    if (ConstraintComponentUtilities.isLine(child)) {
                      continue;
                    }
                    NlComponent tag = NlComponentHelperKt.createChild(barrier, editor, TAG, null, InsertType.CREATE);
                    tag.removeAndroidAttribute(ATTR_LAYOUT_WIDTH);
                    tag.removeAndroidAttribute(ATTR_LAYOUT_HEIGHT);
                    tag.setAttribute(ANDROID_URI, ATTR_ID, ID_PREFIX + child.getId());
                    tag.setAttribute(ANDROID_URI, ATTR_VALUE, VALUE_TRUE);
                  }
                }
              } // TODO: add views to the barrier when not using the tags approach
              return;
            }
            NlComponent barrier = NlComponentHelperKt
              .createChild(parent, editor, useAndroidx ? CONSTRAINT_LAYOUT_BARRIER.newName() : CONSTRAINT_LAYOUT_BARRIER.oldName(), null,
                           InsertType.CREATE);
            assert barrier != null;
            barrier.ensureId();
            barrier.setAttribute(SHERPA_URI, ATTR_BARRIER_DIRECTION, "left");

            // NlUsageTracker tracker = NlUsageTrackerImpl.getInstance(editor.getScene().getDesignSurface());
            // TODO add tracker.logAction(LayoutEditorEvent.LayoutEditorEventType.ADD_VERTICAL_BARRIER);

            if (ConstraintHelperHandler.USE_HELPER_TAGS) {
              if (!selectedChildren.isEmpty()) {

                for (NlComponent child : selectedChildren) {
                  if (ConstraintComponentUtilities.isLine(child)) {
                    continue;
                  }
                  NlComponent tag = NlComponentHelperKt.createChild(barrier, editor, TAG, null, InsertType.CREATE);
                  tag.removeAndroidAttribute(ATTR_LAYOUT_WIDTH);
                  tag.removeAndroidAttribute(ATTR_LAYOUT_HEIGHT);
                  tag.setAttribute(ANDROID_URI, ATTR_ID, ID_PREFIX + child.getId());
                  tag.setAttribute(ANDROID_URI, ATTR_VALUE, VALUE_TRUE);
                }
              }
            }
          }
        }
      }
    }

    @Override
    public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren,
                                   @InputEventMask int modifiers) {
      boolean show = true;
      if (myType == VERTICAL_BARRIER || myType == HORIZONTAL_BARRIER) {
        show = ConstraintComponentUtilities.isConstraintModelGreaterThan(editor, 1, 0);
        if (show) {
          int barriers = 0;
          int other = 0;
          for (NlComponent child : selectedChildren) {
            if (NlComponentHelperKt.isOrHasSuperclass(child, CONSTRAINT_LAYOUT_BARRIER)) {
              barriers++;
            }
            if (!ConstraintComponentUtilities.isLine(child)) {
              other++;
            }
          }
          if (barriers == 1 && other > 0) {
            presentation.setLabel(ADD_TO_BARRIER);
          }
          else {
            presentation.setLabel(myType == VERTICAL_BARRIER ? ADD_VERTICAL_BARRIER : ADD_HORIZONTAL_BARRIER);
          }
        }
      }
      if (myType == GROUP) {
        show = ConstraintComponentUtilities.isConstraintModelGreaterThan(editor, 1, 0);
      }
      if (myType == CONSTRAINT_SET) {
        show = ConstraintComponentUtilities.isConstraintModelGreaterThan(editor, 1, 9);
      }
      if (myType == LAYER) {
        show = ConstraintComponentUtilities.isConstraintModelGreaterThan(editor, 1, 9);
      }

      presentation.setVisible(show);
      presentation.setEnabled(true);
    }
  }

  interface EnabledAction {
    boolean isEnabled(List<NlComponent> selected);
  }

  private static class AlignAction extends DirectViewAction implements EnabledAction {
    private final Scout.Arrange myActionType;
    private final Icon myAlignIcon;
    private final Icon myConstrainIcon;
    private final String myToolTip;

    AlignAction(Scout.Arrange actionType, Icon alignIcon, String toolTip) {
      super(alignIcon, toolTip);
      myActionType = actionType;
      myAlignIcon = alignIcon;
      myConstrainIcon = null;
      myToolTip = toolTip;
    }

    AlignAction(Scout.Arrange actionType, Icon alignIcon, Icon constrainIcon, String toolTip) {
      super(alignIcon, toolTip);
      myActionType = actionType;
      myAlignIcon = alignIcon;
      myConstrainIcon = constrainIcon;
      myToolTip = toolTip;
    }

    /**
     * Function is called on right click
     * It is moderatly compute intensive. (<10ms)
     *
     * @param selected
     * @return
     */
    @Override
    public boolean isEnabled(List<NlComponent> selected) {
      int count = selected.size();
      switch (myActionType) {
        case AlignVerticallyTop:
        case AlignVerticallyMiddle:
        case AlignVerticallyBottom:
        case AlignHorizontallyLeft:
        case AlignHorizontallyCenter:
        case AlignHorizontallyRight:
        case DistributeVertically:
        case DistributeHorizontally:
        case VerticalPack:
        case HorizontalPack:
        case AlignBaseline:
          return count > 1;
        case CreateHorizontalChain:
          return count > 1 && !Scout.chainCheck(selected, Scout.ChainTest.InHorizontalChain);
        case CreateVerticalChain:
          return count > 1 && !Scout.chainCheck(selected, Scout.ChainTest.InVerticalChain);
        case ExpandVertically:
        case ExpandHorizontally:
        case CenterHorizontallyInParent:
        case CenterVerticallyInParent:
        case CenterVertically:
        case CenterHorizontally:
          return count >= 1;
        case ChainHorizontalMoveLeft:
          return count == 1 && Scout.chainCheck(selected, Scout.ChainTest.InHorizontalChain) &&
                 !Scout.chainCheck(selected, Scout.ChainTest.IsTopOfChain);
        case ChainVerticalMoveDown:
          return count == 1 && Scout.chainCheck(selected, Scout.ChainTest.InVerticalChain) &&
                 !Scout.chainCheck(selected, Scout.ChainTest.IsBottomOfChain);
        case ChainVerticalMoveUp:
          return count == 1 && Scout.chainCheck(selected, Scout.ChainTest.InVerticalChain) &&
                 !Scout.chainCheck(selected, Scout.ChainTest.IsTopOfChain);
        case ChainHorizontalMoveRight:
          return count == 1 && Scout.chainCheck(selected, Scout.ChainTest.InHorizontalChain) &&
                 !Scout.chainCheck(selected, Scout.ChainTest.IsBottomOfChain);
        case ChainHorizontalRemove:
          return count == 1 && Scout.chainCheck(selected, Scout.ChainTest.InHorizontalChain);
        case ChainVerticalRemove:
          return count == 1 && Scout.chainCheck(selected, Scout.ChainTest.InVerticalChain);
        case ChainInsertHorizontal:
          return count == 1 && Scout.chainCheck(selected, Scout.ChainTest.IsNearHorizontalChain);
        case ChainInsertVertical:
          return count == 1 && Scout.chainCheck(selected, Scout.ChainTest.IsNearVerticalChain);
        default:
          return false;
      }
    }

    @Override
    public void perform(@NotNull ViewEditor editor,
                        @NotNull ViewHandler handler,
                        @NotNull NlComponent component,
                        @NotNull List<NlComponent> selectedChildren,
                        @InputEventMask int modifiers) {
      NlUsageTracker.getInstance(editor.getScene().getDesignSurface())
                           .logAction(LayoutEditorEvent.LayoutEditorEventType.ALIGN);
      // noinspection AssignmentToMethodParameter
      modifiers &= InputEvent.CTRL_MASK;
      Scout.arrangeWidgetsAndCommit(myActionType, selectedChildren, modifiers == 0 || ToggleAutoConnectAction.isAutoconnectOn());
      ensureLayersAreShown(editor, 1000);
    }

    @Override
    public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren,
                                   @InputEventMask int modifiers) {

      Icon icon = myAlignIcon;
      if (myConstrainIcon != null) {
        if (ToggleAutoConnectAction.isAutoconnectOn() || (InputEvent.CTRL_MASK & modifiers) == 0) {
          icon = myConstrainIcon;
        }
      }
      presentation.setVisible(isEnabled(selectedChildren));
      presentation.setEnabled(isEnabled(selectedChildren));
      presentation.setIcon(icon);
      presentation.setLabel(myToolTip);
    }
  }

  private static class MarginSelector extends DirectViewAction {

    private static final String PICK_A_DIMENSION = "Pick a Dimension";
    private static final float DEFAULT_ICON_FONT_SIZE = 12f;
    private static final int DEFAULT_ICON_WIDTH = 36;
    private static final int DEFAULT_ICON_HEIGHT = 16;

    private final ActionListener myResourcePickerIconClickListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myComponent == null) {
          return;
        }

        Set<ResourceType> types = EnumSet.of(ResourceType.DIMEN);
        ChooseResourceDialog dialog = ChooseResourceDialog.builder()
          .setModule(myComponent.getModel().getModule())
          .setTypes(types)
          .setCurrentValue(String.valueOf(Scout.getMargin()))
          .setTag(myComponent.getBackend().getTag())
          .setDefaultType(ResourceType.DIMEN)
          .build();
        dialog.setTitle(PICK_A_DIMENSION);

        if (dialog.showAndGet()) {
          resolveResValue(dialog);
        }
      }

      private void resolveResValue(ChooseResourceDialog dialog) {
        ResourceResolver resolver = dialog.getResourceResolver();
        ResourceValue unresolved = new AttrResourceValueImpl(ResourceNamespace.RES_AUTO, "dimens", null);
        unresolved.setValue(dialog.getResourceName());
        ResourceValue value = resolver.resolveResValue(unresolved);
        String marginDp = getMarginInDp(value);

        if (marginDp == null) {
          Messages.showWarningDialog(
            "\"" + dialog.getResourceName() + "\' cannot be used for default margin. Please choose a resource with \"dp\" type instead.",
            "Warning");
          setMargin(null, Scout.DEFAULT_MARGIN);
          return;
        }

        try {
          int marginInInt = Integer.parseInt(marginDp);
          setMargin(dialog.getResourceName(), marginInInt);
        } catch (NumberFormatException nfe) {
          Messages.showWarningDialog(
            "\"" + dialog.getResourceName() + "\' is not a valid dimension. Please choose a resource with correct dimension value instead.",
            "Warning");
          setMargin(null, Scout.DEFAULT_MARGIN);
          Logger.getInstance(MarginPopup.class).warn("Was unable to resolve the resValue from ResourceDialog.");
        }
      }

      private void setMargin(@Nullable String resName, int value) {
        myMarginPopup.getMargin().setValue(value, resName);
        myMarginPopup.updateText();
        updateIcon();
      }
    };

    private @Nullable String getMarginInDp(ResourceValue resourceValue) {
      if (resourceValue == null) {
        return null;
      }
      String value = resourceValue.getValue();
      if (value == null) {
        return null;
      }

      String toReturn = null;
      if (value.endsWith("dp") ||
        value.endsWith("px") ||
        value.endsWith("pt") ||
        value.endsWith("in") ||
        value.endsWith("mm") ||
        value.endsWith("sp")) {
        toReturn = value.substring(0, value.length() - 2);
      } else if (value.endsWith("dip")) {
        toReturn = value.substring(0, value.length() - 3);
      }
      return toReturn;
    }

    MarginPopup myMarginPopup = new MarginPopup(myResourcePickerIconClickListener);
    private String myPreviousDisplay;
    private Icon myMarginIcon;
    @Nullable private NlComponent myComponent;

    public MarginSelector() {
      super(null, "Default Margins"); // tooltip
      myMarginPopup.setActionListener((e) -> setMargin());
    }

    public void setMargin() {
      Scout.setMargin(myMarginPopup.getMargin().getValue());
    }

    private void updateIcon() {
      String previousDisplay = myMarginPopup.getMargin().getDisplayValue();
      if (!previousDisplay.equals(myPreviousDisplay)) {
        myPreviousDisplay = previousDisplay;
        myMarginIcon = new Icon() {
          @Override
          public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(JBColor.foreground());
            g.setFont(g.getFont().deriveFont(Font.PLAIN, JBUI.scaleFontSize(DEFAULT_ICON_FONT_SIZE)));
            String m = previousDisplay;
            FontMetrics metrics = g.getFontMetrics();
            int strWidth = metrics.stringWidth(m);
            ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int stringY = (getIconHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            g.drawString(m, x + (getIconWidth() - strWidth) / 2, y + stringY - 1);
            g.setColor(JBColor.foreground().darker());
            int marginRight = 6;
            g.drawLine(x + 1, y + getIconHeight() - 1, x + getIconWidth() - 1, y + getIconHeight() - 1);
            g.drawLine(x + 1, y + getIconHeight(), x + 1, y + getIconHeight() - marginRight);
            g.drawLine(x + getIconWidth() - 1, y + getIconHeight(), x + getIconWidth() - 1, y + getIconHeight() - marginRight);
          }

          @Override
          public int getIconWidth() {
            return JBUI.scale(DEFAULT_ICON_WIDTH);
          }

          @Override
          public int getIconHeight() {
            return JBUI.scale(DEFAULT_ICON_HEIGHT);
          }
        };
      }
    }

    @Override
    public void perform(@NotNull ViewEditor editor,
                        @NotNull ViewHandler handler,
                        @NotNull NlComponent component,
                        @NotNull List<NlComponent> selectedChildren,
                        @InputEventMask int modifiers) {
      myComponent = component;
      DesignSurface surface = editor.getScene().getDesignSurface();
      NlUsageTracker.getInstance(surface).logAction(LayoutEditorEvent.LayoutEditorEventType.DEFAULT_MARGINS);
      RelativePoint relativePoint = new RelativePoint(surface, new Point(0, 0));
      JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(myMarginPopup, myMarginPopup.getTextField())
        .setRequestFocus(true)
        .createPopup();
      myMarginPopup.setPopup(popup);
      Disposer.register(popup, () -> myMarginPopup.setPopup(null));
      popup.show(relativePoint);
    }

    @Override
    public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren,
                                   @InputEventMask int modifiers) {
      // TODO: Use AndroidIcons.SherpaIcons.Margin instead?
      updateIcon();
      if (myMarginIcon instanceof ControlIcon) {
        ((ControlIcon)myMarginIcon).setHighlight(ToggleAutoConnectAction.isAutoconnectOn() || (InputEvent.CTRL_MASK & modifiers) == 0);
      }
      presentation.setIcon(myMarginIcon);
    }
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
    final int count = parent.getChildCount();
    for (int i = 0; i < count; i++) {
      NlComponent component = parent.getChild(i);
      if (deleted.contains(component)) {
        continue;
      }
      willDelete(component, deleted);
    }
    return false;
  }

  /**
   * Update the given component if one of its constraint points to one of the component deleted
   *
   * @param component the component we want to check
   * @param deleted   the list of components that are deleted
   */
  private static void willDelete(NlComponent component, @NotNull Collection<NlComponent> deleted) {
    for (NlComponent deletedComponent : deleted) {
      String id = deletedComponent.getId();
      ConstraintComponentUtilities.updateOnDelete(component, id);
      NlComponent parent = deletedComponent.getParent();
      if (parent != null && id != null) {
        ConstraintHelperHandler.willDelete(parent, id);
      }
    }
  }

  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(ATTR_MIN_WIDTH, ATTR_MAX_WIDTH, ATTR_MIN_HEIGHT, ATTR_MAX_HEIGHT);
  }

  /**
   * Returns a component provider instance
   *
   * @return the component provider
   */
  @Override
  public ComponentProvider getComponentProvider(@NotNull SceneComponent component) {
    SceneComponent parent = component.getParent();
    if (parent == null) {
      return null;
    }
    NlComponent nlComponent = parent.getNlComponent();
    if (NlComponentHelperKt.isOrHasSuperclass(nlComponent, CLASS_CONSTRAINT_LAYOUT)) {
      String attribute = nlComponent.getLiveAttribute(SHERPA_URI, "constraints");
      if (attribute != null) {
        return this;
      }
    }
    return null;
  }

  /**
   * Returns the NlComponent associated with the given SceneComponent.
   *
   * @param component a given SceneComponent
   * @return the associated NlComponent
   */
  @Override
  public NlComponent getComponent(@NotNull SceneComponent component) {
    SceneComponent parent = component.getParent();
    if (parent == null) {
      return component.getNlComponent();
    }

    NlComponent nlComponent = parent.getNlComponent();
    String attribute = nlComponent.getLiveAttribute(SHERPA_URI, ATTR_LAYOUT_CONSTRAINTSET);
    attribute = NlComponent.extractId(attribute);
    if (attribute == null) {
      return component.getNlComponent();
    }
    // else, let's get the component indicated
    NlComponent constraints = null;
    for (SceneComponent child : parent.getChildren()) {
      String childId = child.getNlComponent().getId();
      if (Objects.equals(attribute, childId)) {
        constraints = child.getNlComponent();
        break;
      }
    }
    if (constraints == null) {
      return component.getNlComponent();
    }
    for (NlComponent child : constraints.getChildren()) {
      String reference = child.getLiveAttribute(ANDROID_URI, ATTR_ID);
      reference = NlComponent.extractId(reference);
      if (reference != null && reference.equals(component.getNlComponent().getId())) {
        return child;
      }
    }
    return component.getNlComponent();
  }

  @Override
  public List<Placeholder> getPlaceholders(@NotNull SceneComponent component) {
    return ImmutableList.of(new ConstraintPlaceholder(component));
  }

  @Override
  public List<ViewAction> getPropertyActions(@NotNull List<NlComponent> components) {
    ImmutableList.Builder<ViewAction> builder = ImmutableList.builder();
    builder.add(new ClearConstraintsAction());

    ViewAction connectAction = new DisappearingActionMenu("Constrain", CREATE_CONSTRAINTS, ConstraintViewActions.CONNECT_ACTIONS);
    builder.add(connectAction);

    if (components.size() > 1) {
      ViewAction alignAction = new DisappearingActionMenu("Align", LEFT_ALIGNED, ConstraintViewActions.ALIGN_ACTIONS);
      builder.add(alignAction);
      ViewAction chainAction = new DisappearingActionMenu("Chains", CREATE_HORIZ_CHAIN, ConstraintViewActions.CHAIN_ACTIONS);
      builder.add(chainAction);
    }

    return builder.build();
  }

  private static class ConstraintViewActions {
    private static final ImmutableList<ViewAction> ALIGN_HORIZONTALLY_ACTIONS = ImmutableList.of(
      new AlignAction(Scout.Arrange.AlignHorizontallyLeft,
                      LEFT_ALIGNED,
                      StudioIcons.LayoutEditor.Toolbar.LEFT_ALIGNED_CONSTRAINT,
                      "Left Edges"),
      new AlignAction(Scout.Arrange.AlignHorizontallyCenter,
                      StudioIcons.LayoutEditor.Toolbar.HORIZONTAL_CENTER_ALIGNED,
                      StudioIcons.LayoutEditor.Toolbar.HORIZONTAL_CENTER_ALIGNED_CONSTRAINT,
                      "Horizontal Centers"),
      new AlignAction(Scout.Arrange.AlignHorizontallyRight,
                      StudioIcons.LayoutEditor.Toolbar.RIGHT_ALIGNED,
                      StudioIcons.LayoutEditor.Toolbar.RIGHT_ALIGNED_CONSTRAINT,
                      "Right Edges"));

    private static final ImmutableList<ViewAction> ALIGN_VERTICALLY_ACTIONS = ImmutableList.of(
      new AlignAction(Scout.Arrange.AlignVerticallyTop,
                      StudioIcons.LayoutEditor.Toolbar.TOP_ALIGNED,
                      StudioIcons.LayoutEditor.Toolbar.TOP_ALIGNED_CONSTRAINT,
                      "Top Edges"),
      new AlignAction(Scout.Arrange.AlignVerticallyMiddle,
                      StudioIcons.LayoutEditor.Toolbar.VERTICAL_CENTER_ALIGNED,
                      StudioIcons.LayoutEditor.Toolbar.VERTICAL_CENTER_ALIGNED_CONSTRAINT,
                      "Vertical Centers"),
      new AlignAction(Scout.Arrange.AlignVerticallyBottom,
                      StudioIcons.LayoutEditor.Toolbar.BOTTOM_ALIGNED,
                      StudioIcons.LayoutEditor.Toolbar.BOTTOM_ALIGNED_CONSTRAINT,
                      "Bottom Edges"),
      new AlignAction(Scout.Arrange.AlignBaseline,
                      StudioIcons.LayoutEditor.Toolbar.BASELINE_ALIGNED,
                      StudioIcons.LayoutEditor.Toolbar.BASELINE_ALIGNED_CONSTRAINT,
                      "Baselines"));

    public static final ImmutableList<ViewAction> ALIGN_ACTIONS = ImmutableList.<ViewAction>builder()
      .addAll(ALIGN_HORIZONTALLY_ACTIONS)
      .addAll(ALIGN_VERTICALLY_ACTIONS)
      .build();

    public static final ImmutableList<ViewAction> CHAIN_ACTIONS = ImmutableList.of(
      new AlignAction(Scout.Arrange.CreateHorizontalChain,
                      CREATE_HORIZ_CHAIN,
                      CREATE_HORIZ_CHAIN,
                      "Create Horizontal Chain"),
      new AlignAction(Scout.Arrange.CreateVerticalChain,
                      StudioIcons.LayoutEditor.Toolbar.CREATE_VERT_CHAIN,
                      StudioIcons.LayoutEditor.Toolbar.CREATE_VERT_CHAIN,
                      "Create Vertical Chain"),
      new AlignAction(Scout.Arrange.ChainInsertHorizontal,
                      StudioIcons.LayoutEditor.Toolbar.INSERT_HORIZ_CHAIN,
                      StudioIcons.LayoutEditor.Toolbar.INSERT_HORIZ_CHAIN,
                      "Insert in Horizontal Chain"),
      new AlignAction(Scout.Arrange.ChainInsertVertical,
                      StudioIcons.LayoutEditor.Toolbar.INSERT_VERT_CHAIN,
                      StudioIcons.LayoutEditor.Toolbar.INSERT_VERT_CHAIN,
                      "Insert in Vertical Chain"),
      new AlignAction(Scout.Arrange.ChainVerticalRemove,
                      StudioIcons.LayoutEditor.Toolbar.REMOVE_FROM_VERT_CHAIN,
                      StudioIcons.LayoutEditor.Toolbar.REMOVE_FROM_VERT_CHAIN,
                      "Remove from Vertical Chain"),
      new AlignAction(Scout.Arrange.ChainHorizontalRemove,
                      StudioIcons.LayoutEditor.Toolbar.REMOVE_FROM_HORIZ_CHAIN,
                      StudioIcons.LayoutEditor.Toolbar.REMOVE_FROM_HORIZ_CHAIN,
                      "Remove from Horizontal Chain"),
      new AlignAction(Scout.Arrange.ChainVerticalMoveUp,
                      StudioIcons.LayoutEditor.Toolbar.MOVE_UP_VERT_CHAIN,
                      StudioIcons.LayoutEditor.Toolbar.MOVE_UP_VERT_CHAIN,
                      "Move Up Vertical Chain"),
      new AlignAction(Scout.Arrange.ChainVerticalMoveDown,
                      StudioIcons.LayoutEditor.Toolbar.MOVE_DOWN_VERT_CHAIN,
                      StudioIcons.LayoutEditor.Toolbar.MOVE_DOWN_VERT_CHAIN,
                      "Move Down Vertical Chain"),
      new AlignAction(Scout.Arrange.ChainHorizontalMoveLeft,
                      StudioIcons.LayoutEditor.Toolbar.MOVE_LEFT_HORIZ_CHAIN,
                      StudioIcons.LayoutEditor.Toolbar.MOVE_LEFT_HORIZ_CHAIN,
                      "Move Left Horizontal Chain"),
      new AlignAction(Scout.Arrange.ChainHorizontalMoveRight,
                      StudioIcons.LayoutEditor.Toolbar.MOVE_RIGHT_HORIZ_CHAIN,
                      StudioIcons.LayoutEditor.Toolbar.MOVE_RIGHT_HORIZ_CHAIN,
                      "Move Right Horizontal Chain")
    );


    private static final ImmutableList<ViewAction> CENTER_ACTIONS = ImmutableList.of(
      new AlignAction(Scout.Arrange.CenterHorizontally,
                      CENTER_HORIZONTAL,
                      StudioIcons.LayoutEditor.Toolbar.CENTER_HORIZONTAL_CONSTRAINT,
                      "Horizontally"),
      new AlignAction(Scout.Arrange.CenterVertically,
                      StudioIcons.LayoutEditor.Toolbar.CENTER_VERTICAL,
                      StudioIcons.LayoutEditor.Toolbar.CENTER_VERTICAL_CONSTRAINT,
                      "Vertically"),
      new AlignAction(Scout.Arrange.CenterHorizontallyInParent,
                      StudioIcons.LayoutEditor.Toolbar.CENTER_HORIZONTAL_PARENT,
                      StudioIcons.LayoutEditor.Toolbar.CENTER_HORIZONTAL_PARENT_CONSTRAINT,
                      "Horizontally in Parent"),
      new AlignAction(Scout.Arrange.CenterVerticallyInParent,
                      StudioIcons.LayoutEditor.Toolbar.CENTER_VERTICAL_PARENT,
                      StudioIcons.LayoutEditor.Toolbar.CENTER_VERTICAL_PARENT_CONSTRAINT,
                      "Vertically in Parent"));

    // ====================================== Connect menu ==================================================

    private static class ConnectAction extends DirectViewAction implements EnabledAction {
      private final Scout.Connect myConnectType;
      private final Icon myAlignIcon;
      private final Icon myConstrainIcon;
      private final String myToolTip;
      private boolean mReverse;
      private boolean mToParent = false;

      ConnectAction(Scout.Connect actionType, Icon alignIcon, String toolTip, boolean reverse) {
        super(alignIcon, toolTip);
        myConnectType = actionType;
        myAlignIcon = alignIcon;
        myConstrainIcon = null;
        myToolTip = toolTip;
        mReverse = reverse;
      }

      ConnectAction(Scout.Connect actionType, Icon alignIcon, String toolTip) {
        super(alignIcon, toolTip);
        myConnectType = actionType;
        myAlignIcon = alignIcon;
        myConstrainIcon = null;
        myToolTip = toolTip;
        mToParent = true;
      }


      /**
       * Function is called on right click
       * It is moderatly compute intensive. (<10ms)
       *
       * @param selected
       * @return
       */
      @Override
      public boolean isEnabled(List<NlComponent> selected) {
        int count = selected.size();
        if (count > 2 || count == 0) {
          return false;
        }
        if (!isConstraintLayoutChild(selected)) {
          return false;
        }
        return Scout.connectCheck(selected, myConnectType, mReverse);
      }

      @Override
      public void perform(@NotNull ViewEditor editor,
                          @NotNull ViewHandler handler,
                          @NotNull NlComponent component,
                          @NotNull List<NlComponent> selectedChildren,
                          @InputEventMask int modifiers) {
        NlUsageTracker.getInstance(editor.getScene().getDesignSurface())
                             .logAction(LayoutEditorEvent.LayoutEditorEventType.ALIGN);
        // noinspection AssignmentToMethodParameter
        modifiers &= InputEvent.CTRL_MASK;
        Scout.connect(selectedChildren, myConnectType, mReverse, true);
        ensureLayersAreShown(editor, 1000);
        ComponentModification modification = new ComponentModification(component, "Connect Constraint");
        component.startAttributeTransaction().applyToModification(modification);
        modification.commit();
      }

      @Override
      public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                     @NotNull ViewEditor editor,
                                     @NotNull ViewHandler handler,
                                     @NotNull NlComponent component,
                                     @NotNull List<NlComponent> selectedChildren,
                                     @InputEventMask int modifiers) {


        Icon icon = myAlignIcon;
        if (myConstrainIcon != null) {
          if (ToggleAutoConnectAction.isAutoconnectOn() || (InputEvent.CTRL_MASK & modifiers) == 0) {
            icon = myConstrainIcon;
          }
        }
        presentation.setVisible(isEnabled(selectedChildren));
        presentation.setEnabled(isEnabled(selectedChildren));
        presentation.setIcon(icon);
        if (mToParent) {
          presentation.setLabel(myToolTip);
        }
        else {
          String name = selectedChildren.get((mReverse) ? 0 : 1).getId();
          if (name == null) {
            name = "(" + selectedChildren.get((mReverse) ? 0 : 1).getTagName() + ")";
          }
          presentation.setLabel(myToolTip + " of " + name);
        }
      }
    }

    //
    private static class ConnectSource extends DisappearingActionMenu {
      int mIndex = 0;

      public ConnectSource(int index,
                           @Nullable Icon icon,
                           @NotNull List<ViewAction> actions) {
        super("", icon, actions);
        mIndex = index;
      }


      @Override
      public boolean isEnabled(List<NlComponent> selected) {
        if (selected.size() != 2) {
          return false;
        }
        return super.isEnabled(selected);
      }

      @Override
      public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                     @NotNull ViewEditor editor,
                                     @NotNull ViewHandler handler,
                                     @NotNull NlComponent component,
                                     @NotNull List<NlComponent> selectedChildren,
                                     int modifiers) {
        String label;
        if (selectedChildren.size() > mIndex) {
          label = selectedChildren.get(mIndex).getId();
          if (label == null) {
            label = selectedChildren.get(mIndex).getTagName();
          }
        }
        else {
          label = getLabel();
        }

        presentation.setLabel(label);
        presentation.setVisible(isEnabled(selectedChildren));
      }
    }

    private static final ImmutableList<ViewAction> connectTopVertical(boolean reverse) {
      return ImmutableList.of(
        new ConnectAction(Scout.Connect.ConnectTopToTop,
                          StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_TOP_TO_TOP,
                          "top", reverse),
        new ConnectAction(Scout.Connect.ConnectTopToBottom,
                          StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_TOP_TO_BOTTOM,
                          "bottom", reverse)

      );
    }

    private static final ImmutableList<ViewAction> connectStartHorizontal(boolean reverse) {
      return ImmutableList.of(
        new ConnectAction(Scout.Connect.ConnectStartToStart,
                          StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_START_TO_START,
                          "start", reverse),
        new ConnectAction(Scout.Connect.ConnectStartToEnd,
                          StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_START_TO_END,
                          "end", reverse)
      );
    }

    private static final ImmutableList<ViewAction> connectBottomVertical(boolean reverse) {
      return ImmutableList.of(
        new ConnectAction(Scout.Connect.ConnectBottomToTop,
                          StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_BOTTOM_TO_TOP,
                          "top", reverse),
        new ConnectAction(Scout.Connect.ConnectBottomToBottom,
                          StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_BOTTOM_TO_BOTTOM,
                          "bottom", reverse)

      );
    }

    private static final ImmutableList<ViewAction> connectEndHorizontal(boolean reverse) {
      return ImmutableList.of(
        new ConnectAction(Scout.Connect.ConnectEndToStart,
                          StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_END_TO_START,
                          "start", reverse),
        new ConnectAction(Scout.Connect.ConnectEndToEnd,
                          StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_END_TO_END,
                          "end", reverse)
      );
    }

    private static final ImmutableList<ViewAction> connectFrom(boolean reverse) {
      return ImmutableList.of(
        new DisappearingActionMenu("top to", StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_TO_TOP,
                                   ConstraintViewActions.connectTopVertical(reverse)),
        new DisappearingActionMenu("bottom to", StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_TO_BOTTOM,
                                   ConstraintViewActions.connectBottomVertical(reverse)),
        new DisappearingActionMenu("start to", StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_TO_START,
                                   ConstraintViewActions.connectStartHorizontal(reverse)),
        new DisappearingActionMenu("end to", StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_TO_END,
                                   ConstraintViewActions.connectEndHorizontal(reverse)),
        new ConnectAction(Scout.Connect.ConnectBaseLineToBaseLine,
                          BASELINE_ALIGNED_CONSTRAINT,
                          "to baseline", reverse)
      );
    }

    public static final ImmutableList<ViewAction> CONNECT_ACTIONS = ImmutableList.of(

      new ConnectAction(Scout.Connect.ConnectToParentTop,
                        StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_TO_TOP,
                        "parent top"),
      new ConnectAction(Scout.Connect.ConnectToParentBottom,
                        StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_TO_BOTTOM,
                        "parent bottom"),
      new ConnectAction(Scout.Connect.ConnectToParentStart,
                        StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_TO_START,
                        "parent start"),
      new ConnectAction(Scout.Connect.ConnectToParentEnd,
                        StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_TO_END,
                        "parent end"),
      new ConnectSource(0, null, connectFrom(false)),
      new ConnectSource(1, null, connectFrom(true))

    );
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //DisappearingActionMenu

    private static final ImmutableList<ViewAction> ORGANIZE_ACTIONS = ImmutableList.of(
      new AlignAction(Scout.Arrange.HorizontalPack,
                      PACK_HORIZONTAL,
                      "Pack Horizontally"),
      new AlignAction(Scout.Arrange.VerticalPack,
                      StudioIcons.LayoutEditor.Toolbar.PACK_VERTICAL,
                      "Pack Vertically"),
      new AlignAction(Scout.Arrange.ExpandHorizontally,
                      StudioIcons.LayoutEditor.Toolbar.EXPAND_HORIZONTAL,
                      "Expand Horizontally"),
      new AlignAction(Scout.Arrange.ExpandVertically,
                      StudioIcons.LayoutEditor.Toolbar.EXPAND_VERTICAL,
                      "Expand Vertically"));

    private static final ImmutableList<ViewAction> HELPER_ACTIONS = ImmutableList.of(
      new AddElementAction(AddElementAction.VERTICAL_GUIDELINE,
                           GUIDELINE_VERTICAL,
                           "Add Vertical Guideline"),
      new AddElementAction(AddElementAction.HORIZONTAL_GUIDELINE,
                           StudioIcons.LayoutEditor.Toolbar.GUIDELINE_HORIZONTAL,
                           "Add Horizontal Guideline"),
      new AddElementAction(AddElementAction.VERTICAL_BARRIER,
                           StudioIcons.LayoutEditor.Toolbar.BARRIER_VERTICAL,
                           ADD_VERTICAL_BARRIER),
      new AddElementAction(AddElementAction.HORIZONTAL_BARRIER,
                           StudioIcons.LayoutEditor.Toolbar.BARRIER_HORIZONTAL,
                           ADD_HORIZONTAL_BARRIER),
      new AddElementAction(AddElementAction.GROUP,
                           // TODO: add new icon to StudioIcons and replace this icon
                           AndroidIcons.SherpaIcons.Layer,
                           ADD_GROUP),
      new AddElementAction(AddElementAction.CONSTRAINT_SET,
                           // TODO: add new icon to StudioIcons and replace this icon
                           AndroidIcons.SherpaIcons.Layer,
                           ADD_CONSTRAINTS_SET),
      new AddElementAction(AddElementAction.LAYER,
                           // TODO: add new icon to StudioIcons and replace this icon
                           AndroidIcons.SherpaIcons.Layer,
                           ADD_LAYER));

    private static final ImmutableList<ViewAction> ALL_POPUP_ACTIONS = ImmutableList.<ViewAction>builder()
      .addAll(ALIGN_HORIZONTALLY_ACTIONS)
      .addAll(ALIGN_VERTICALLY_ACTIONS)
      .addAll(CONNECT_ACTIONS)
      .addAll(ORGANIZE_ACTIONS)
      .addAll(CENTER_ACTIONS)
      .addAll(HELPER_ACTIONS)
      .build();
  }
}
