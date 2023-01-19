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
import static com.android.SdkConstants.AUTO_URI;
import static com.android.AndroidXConstants.CLASS_CONSTRAINT_LAYOUT;
import static com.android.AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_CONSTRAINTS;
import static com.android.AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_FLOW;
import static com.android.AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_GROUP;
import static com.android.AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_LAYER;
import static com.android.AndroidXConstants.CLASS_MOTION_LAYOUT;
import static com.android.SdkConstants.CLASS_VIEW;
import static com.android.SdkConstants.CLASS_VIEWGROUP;
import static com.android.AndroidXConstants.CONSTRAINT_LAYOUT;
import static com.android.AndroidXConstants.CONSTRAINT_LAYOUT_BARRIER;
import static com.android.AndroidXConstants.CONSTRAINT_LAYOUT_GUIDELINE;
import static com.android.SdkConstants.CONSTRAINT_REFERENCED_IDS;
import static com.android.SdkConstants.GRAVITY_VALUE_BOTTOM;
import static com.android.SdkConstants.GRAVITY_VALUE_TOP;
import static com.android.SdkConstants.ID_PREFIX;
import static com.android.SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN;
import static com.android.SdkConstants.SHERPA_URI;
import static com.android.SdkConstants.TAG;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.SdkConstants.VALUE_WRAP_CONTENT;
import static com.android.tools.idea.uibuilder.api.actions.ViewActionUtils.getViewOptionsAction;
import static icons.StudioIcons.LayoutEditor.Toolbar.BASELINE_ALIGNED_CONSTRAINT;
import static icons.StudioIcons.LayoutEditor.Toolbar.CENTER_HORIZONTAL;
import static icons.StudioIcons.LayoutEditor.Toolbar.CREATE_CONSTRAINTS;
import static icons.StudioIcons.LayoutEditor.Toolbar.CREATE_HORIZ_CHAIN;
import static icons.StudioIcons.LayoutEditor.Toolbar.GUIDELINE_VERTICAL;
import static icons.StudioIcons.LayoutEditor.Toolbar.LEFT_ALIGNED;
import static icons.StudioIcons.LayoutEditor.Toolbar.PACK_HORIZONTAL;

import com.android.ide.common.rendering.api.AttrResourceValueImpl;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.api.DragType;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.command.NlWriteCommandActionUtil;
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
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.ui.resourcechooser.util.ResourceChooserHelperKt;
import com.android.tools.idea.ui.resourcemanager.ResourcePickerDialog;
import com.android.tools.idea.uibuilder.actions.ChainStyleViewActions;
import com.android.tools.idea.uibuilder.actions.ToggleAllLiveRenderingAction;
import com.android.tools.idea.uibuilder.analytics.NlAnalyticsManager;
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
import com.android.tools.idea.uibuilder.handlers.constraint.draw.ConstraintLayoutComponentNotchProvider;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.ConstraintLayoutNotchProvider;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.BarrierAnchorTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.BarrierTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.BaseLineToggleViewAction;
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
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.LafIconLookup;
import icons.StudioIcons;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
import javax.swing.LookAndFeel;
import javax.swing.Timer;
import javax.swing.UIManager;
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

  private final static String ADD_VERTICAL_BARRIER = "Vertical Barrier";
  private final static String ADD_HORIZONTAL_BARRIER = "Horizontal Barrier";
  private final static String ADD_TO_BARRIER = "Barrier";
  private final static String ADD_LAYER = "Layer";
  private final static String ADD_GROUP = "Group";
  private final static String ADD_CONSTRAINTS_SET = "Set of Constraints";
  private final static String ADD_FLOW = "Flow";

  private static HashMap<String, Boolean> ourVisibilityFlags = new HashMap<>();

  // This is used to efficiently test if they are horizontal or vertical.
  private static HashSet<String> ourHorizontalBarriers = new HashSet<>(Arrays.asList(GRAVITY_VALUE_TOP, GRAVITY_VALUE_BOTTOM));

  @NotNull
  private static NlAnalyticsManager getAnalyticsManager(@NotNull ViewEditor editor) {
    return ((NlDesignSurface)editor.getScene().getDesignSurface()).getAnalyticsManager();
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
      new ToggleAllLiveRenderingAction())));
    actions.add(new ToggleAutoConnectAction());
    actions.add(new MarginSelector());
    actions.add(new ViewActionSeparator());
    actions.add(new ClearConstraintsAction());
    actions.add((new InferAction()));
    actions.add((new ViewActionSeparator()));

    // TODO Decide if we want lock actions.add(new LockConstraints());
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
                                     @InputEventMask int modifiersEx) {
        super.updatePresentation(presentation, editor, handler, component, selectedChildren, modifiersEx);
        presentation.setVisible(isConstraintLayoutChild(selectedChildren));
      }
    });

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
                                       @InputEventMask int modifiersEx) {
          super.updatePresentation(presentation, editor, handler, component, selectedChildren, modifiersEx);
          presentation.setVisible(isConstraintLayoutChild(selectedChildren));
        }
      });

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
  private static class DisappearingActionMenu extends ViewActionMenu implements EnabledAction {

    private DisappearingActionMenu(@NotNull String menuName,
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
    actions.add(new DisappearingActionMenu("Constrain", CREATE_CONSTRAINTS, ConstraintViewActions.CONNECT_ACTIONS));
    actions.add(new DisappearingActionMenu("Organize", PACK_HORIZONTAL, ConstraintViewActions.ORGANIZE_ACTIONS));
    actions.add(new DisappearingActionMenu("Align", LEFT_ALIGNED, ConstraintViewActions.ALIGN_ACTIONS));
    actions.add(new DisappearingActionMenu("Chains", CREATE_HORIZ_CHAIN, ConstraintViewActions.CHAIN_ACTIONS));
    actions.add(new DisappearingActionMenu("Center", CENTER_HORIZONTAL, ConstraintViewActions.CENTER_ACTIONS));
    actions.add(new DisappearingActionMenu("Add helpers", GUIDELINE_VERTICAL, ConstraintViewActions.HELPER_ACTIONS));

    actions.add(new ConvertToMotionLayoutComponentsAction());
    return true;
  }

  /**
   * Return a new ConstraintInteraction instance to handle a mouse interaction
   *
   * @param screenView the associated screen view
   * @param x          mouse down (x)
   * @param y          mouse down (y)
   * @param component  the component target of the interaction
   * @return a new instance of ConstraintInteraction
   */
  @Override
  @Nullable
  public Interaction createInteraction(@NotNull ScreenView screenView,
                                       @SwingCoordinate int x,
                                       @SwingCoordinate int y,
                                       @NotNull NlComponent component) {
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
        boolean isHorizontal = (side == null || ourHorizontalBarriers.contains(StringUtil.toLowerCase(side)));
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
   * {@inheritDoc}
   */
  @Override
  public void clearAttributes(@NotNull List<NlComponent> components) {
    if (components.isEmpty()) {
      // Nothing to do
      return;
    }
    // Wrapper by WriteCommandAction so it creates only one undo stack.
    NlWriteCommandActionUtil.run(components, "Cleared all constraints", () -> {
      components.forEach(it -> ConstraintComponentUtilities.clearAttributes(it));
    });
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
  public void onChildRemoved(@NotNull NlComponent layout,
                             @NotNull NlComponent newChild,
                             @NotNull InsertType insertType) {
    for (String attribute : ConstraintComponentUtilities.ourConstraintLayoutAttributesToClear) {
      newChild.removeAttribute(SHERPA_URI, attribute);
    }
    newChild.removeAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X);
    newChild.removeAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y);
    String id = newChild.getId();
    if (id != null) {
      ConstraintHelperHandler.willDelete(layout, id);
    }
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
      getAnalyticsManager(editor).trackClearAllConstraints();

      ViewGroupHandler constraintHandler = (ViewGroupHandler)handler;
      constraintHandler.clearAttributes(component.getChildren());
      // Clear selection.
      editor.getScene().select(Collections.emptyList());
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
      ViewGroupHandler constraintHandler = (ViewGroupHandler)handler;
      constraintHandler.clearAttributes(selectedChildren);
      getAnalyticsManager(editor).trackRemoveConstraint();
      ensureLayersAreShown(editor, 1000);
    }

    @Override
    public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren,
                                   @InputEventMask int modifiersEx) {
      presentation.setIcon(StudioIcons.LayoutEditor.Toolbar.CLEAR_CONSTRAINTS);
      presentation.setLabel("Clear Constraints of Selection");
    }
  }

  private static class ConvertToMotionLayoutComponentsAction extends DirectViewAction {
    @Override
    public boolean affectsUndo() {
      return false;
    }

    @Override
    public void perform(@NotNull ViewEditor editor,
                        @NotNull ViewHandler handler,
                        @NotNull NlComponent component,
                        @NotNull List<NlComponent> selectedChildren,
                        @InputEventMask int modifiers) {
      // Todo we should log
      String cl_name = DependencyManagementUtil.mapAndroidxName(component.getModel().getModule(), CLASS_CONSTRAINT_LAYOUT);
      if (!component.getTagName().equals(cl_name)) {
        NlComponent parent = component.getParent();
        if (parent != null) {
          component = parent;
        }
      }
      final NlComponent componentToConvert = component;
      if (!component.getTagName().equals(cl_name)) {
        Messages.showErrorDialog(editor.getScene().getDesignSurface(),
                                 "You can only convert ConstraintLayout not " + component.getTagName(),
                                 getLabel());
        return;
      }
      if (Messages.showYesNoDialog(editor.getScene().getDesignSurface().getProject(),
                                   "<html>This action will convert your layout into a MotionLayout<br> " +
                                   "and create a separate MotionScene file.</html>",
                                   "Motion Editor", "Convert", "Cancel", null) ==
          Messages.YES) {
        NlWriteCommandActionUtil.run(componentToConvert, "Convert to MotionLayout", () ->
        {
          ScoutMotionConvert.convert(componentToConvert);
        });

        DesignSurface<?> designSurface = editor.getScene().getDesignSurface();

        Timer t = new Timer(300, new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            Collection<SceneComponent> components = designSurface.getScene().getSceneComponents();
            for (SceneComponent sceneComponent : components) {
              NlComponent nlComponent = sceneComponent.getNlComponent();
              if (NlComponentHelperKt.isOrHasSuperclass(nlComponent, CLASS_MOTION_LAYOUT)) {
                designSurface.getSelectionModel().setSelection(Arrays.asList(nlComponent));
                ((Timer)e.getSource()).stop();
                break;
              }
            }
          }
        });
        t.start();
      }
    }

    @Override
    public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren,
                                   @InputEventMask int modifiersEx) {
      presentation.setIcon(StudioIcons.LayoutEditor.Toolbar.CYCLE_CHAIN_SPREAD);
      boolean show = ConstraintComponentUtilities.isConstraintModelGreaterThan(editor, "2.0.0-beta02");
      presentation.setLabel("Convert to MotionLayout");
      presentation.setVisible(show);
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
      getAnalyticsManager(editor).trackInferConstraints();
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
                                   @InputEventMask int modifiersEx) {
      presentation.setIcon(StudioIcons.LayoutEditor.Toolbar.INFER_CONSTRAINTS);
      presentation.setLabel("Infer Constraints");
    }
  }

  private static class ToggleVisibilityAction extends ToggleViewAction {
    String mType;

    private ToggleVisibilityAction(String type, String text, boolean defaultValue) {
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

  /**
   * Returns the list of selected ids in a string formatted:
   * "${id1}, ${id2}, ${id3}".
   * Returns null if there's no selected views.
   */
  @Nullable
  static String getSelectedIds(List<NlComponent> selected) {
    if (selected.isEmpty()) {
      return null;
    }

    StringBuilder builder = new StringBuilder();
    for (NlComponent component : selected) {
      if (NlComponentHelperKt.isOrHasSuperclass(component, CLASS_VIEW) ||
          NlComponentHelperKt.isOrHasSuperclass(component, CLASS_VIEWGROUP)) {
        String id = component.getId();
        if (id != null) {
          builder.append(component.getId()).append(",");
        }
      }
    }
    if (builder.length() == 0) {
      return null;
    }
    return builder.toString().substring(0, builder.length() - 1);
  }

  private static class AddElementAction extends DirectViewAction {
    public static final int HORIZONTAL_GUIDELINE = 0;
    public static final int VERTICAL_GUIDELINE = 1;
    public static final int HORIZONTAL_BARRIER = 2;
    public static final int VERTICAL_BARRIER = 3;
    public static final int GROUP = 4;
    public static final int CONSTRAINT_SET = 5;
    public static final int LAYER = 6;
    public static final int FLOW = 7;

    final int myType;

    private AddElementAction(int type, Icon icon, String text) {
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
              .createChild(parent, useAndroidx ? CONSTRAINT_LAYOUT_GUIDELINE.newName() : CONSTRAINT_LAYOUT_GUIDELINE.oldName(),
                           null, InsertType.CREATE);
            assert guideline != null;
            guideline.ensureId();
            guideline.setAttribute(SHERPA_URI, LAYOUT_CONSTRAINT_GUIDE_BEGIN, "20dp");
            getAnalyticsManager(editor).trackAddHorizontalGuideline();
            guideline.setAttribute(ANDROID_URI, ATTR_ORIENTATION, ATTR_GUIDELINE_ORIENTATION_HORIZONTAL);
          }
          break;
          case VERTICAL_GUIDELINE: {
            NlComponent guideline = NlComponentHelperKt
              .createChild(parent, useAndroidx ? CONSTRAINT_LAYOUT_GUIDELINE.newName() : CONSTRAINT_LAYOUT_GUIDELINE.oldName(),
                           null, InsertType.CREATE);
            assert guideline != null;
            guideline.ensureId();
            guideline.setAttribute(SHERPA_URI, LAYOUT_CONSTRAINT_GUIDE_BEGIN, "20dp");
            getAnalyticsManager(editor).trackAddVerticalGuideline();
            guideline.setAttribute(ANDROID_URI, ATTR_ORIENTATION, ATTR_GUIDELINE_ORIENTATION_VERTICAL);
          }
          break;
          case GROUP: {
            NlComponent group = NlComponentHelperKt
              .createChild(parent, useAndroidx ? CLASS_CONSTRAINT_LAYOUT_GROUP.newName() : CLASS_CONSTRAINT_LAYOUT_GROUP.oldName(),
                           null, InsertType.CREATE);
            assert group != null;
            String referencedIds = getSelectedIds(selectedChildren);
            if (referencedIds != null) {
              group.setAttribute(AUTO_URI, CONSTRAINT_REFERENCED_IDS, referencedIds);
            }
            group.ensureId();
          }
          break;
          case CONSTRAINT_SET: {
            NlComponent constraints =
              NlComponentHelperKt.createChild(parent,useAndroidx
                                                     ? CLASS_CONSTRAINT_LAYOUT_CONSTRAINTS.newName()
                                                     : CLASS_CONSTRAINT_LAYOUT_CONSTRAINTS.oldName(), null, InsertType.CREATE);
            assert constraints != null;
            constraints.ensureId();
            ConstraintReferenceManagement.populateConstraints(constraints);
          }
          break;
          case LAYER: {
            NlComponent layer = NlComponentHelperKt
              .createChild(parent, useAndroidx ? CLASS_CONSTRAINT_LAYOUT_LAYER.newName() : CLASS_CONSTRAINT_LAYOUT_LAYER.oldName(),
                           null, InsertType.CREATE);
            assert layer != null;
            removeAbsolutePositioning(selectedChildren);
            String referencedIds = getSelectedIds(selectedChildren);
            if (referencedIds != null) {
              layer.setAttribute(AUTO_URI, CONSTRAINT_REFERENCED_IDS, referencedIds);
            }
            layer.ensureId();
          }
          break;
          case FLOW: {
            NlComponent layer = NlComponentHelperKt
              .createChild(parent, useAndroidx ? CLASS_CONSTRAINT_LAYOUT_FLOW.newName() : CLASS_CONSTRAINT_LAYOUT_FLOW.oldName(),
                           null, InsertType.CREATE);
            assert layer != null;
            removeAbsolutePositioning(selectedChildren);
            String referencedIds = getSelectedIds(selectedChildren);
            if (referencedIds != null) {
              layer.setAttribute(AUTO_URI, CONSTRAINT_REFERENCED_IDS, referencedIds);
            }
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
                  barrier = child;
                  break;
                }
              }
              if (ConstraintHelperHandler.USE_HELPER_TAGS) {
                if (barrier != null) {
                  for (NlComponent child : selectedChildren) {
                    if (ConstraintComponentUtilities.isLine(child)) {
                      continue;
                    }
                    NlComponent tag = NlComponentHelperKt.createChild(barrier, TAG, null, InsertType.CREATE);
                    if (tag != null) {
                      tag.removeAndroidAttribute(ATTR_LAYOUT_WIDTH);
                      tag.removeAndroidAttribute(ATTR_LAYOUT_HEIGHT);
                      tag.setAttribute(ANDROID_URI, ATTR_ID, ID_PREFIX + child.getId());
                      tag.setAttribute(ANDROID_URI, ATTR_VALUE, VALUE_TRUE);
                    }
                  }
                }
              } // TODO: add views to the barrier when not using the tags approach
              return;
            }

            NlComponent barrier = NlComponentHelperKt
              .createChild(parent, useAndroidx ? CONSTRAINT_LAYOUT_BARRIER.newName() : CONSTRAINT_LAYOUT_BARRIER.oldName(), null,
                           InsertType.CREATE);
            assert barrier != null;

            String referencedIds = getSelectedIds(selectedChildren);
            if (referencedIds != null) {
              barrier.setAttribute(AUTO_URI, CONSTRAINT_REFERENCED_IDS, referencedIds);
            }
            barrier.ensureId();
            barrier.setAttribute(SHERPA_URI, ATTR_BARRIER_DIRECTION, "top");
            // TODO add getAnalyticsManager(editor).trackAddHorizontalBarrier

            if (ConstraintHelperHandler.USE_HELPER_TAGS) {
              if (!selectedChildren.isEmpty()) {
                NlComponent tag = NlComponentHelperKt.createChild(barrier, TAG, null, InsertType.CREATE);
                if (tag != null) {
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
                  barrier = child;
                  break;
                }
              }
              if (ConstraintHelperHandler.USE_HELPER_TAGS) {
                if (barrier != null) {
                  for (NlComponent child : selectedChildren) {
                    if (ConstraintComponentUtilities.isLine(child)) {
                      continue;
                    }
                    NlComponent tag = NlComponentHelperKt.createChild(barrier, TAG, null, InsertType.CREATE);
                    if (tag != null) {
                      tag.removeAndroidAttribute(ATTR_LAYOUT_WIDTH);
                      tag.removeAndroidAttribute(ATTR_LAYOUT_HEIGHT);
                      tag.setAttribute(ANDROID_URI, ATTR_ID, ID_PREFIX + child.getId());
                      tag.setAttribute(ANDROID_URI, ATTR_VALUE, VALUE_TRUE);
                    }
                  }
                }
              } // TODO: add views to the barrier when not using the tags approach
              return;
            }
            NlComponent barrier = NlComponentHelperKt
              .createChild(parent, useAndroidx ? CONSTRAINT_LAYOUT_BARRIER.newName() : CONSTRAINT_LAYOUT_BARRIER.oldName(), null,
                           InsertType.CREATE);
            assert barrier != null;
            String referencedIds = getSelectedIds(selectedChildren);
            if (referencedIds != null) {
              barrier.setAttribute(AUTO_URI, CONSTRAINT_REFERENCED_IDS, referencedIds);
            }
            barrier.ensureId();
            barrier.setAttribute(SHERPA_URI, ATTR_BARRIER_DIRECTION, "left");

            // TODO add getAnalyticsManager(editor).trackAddVerticalBarrier

            if (ConstraintHelperHandler.USE_HELPER_TAGS) {
              if (!selectedChildren.isEmpty()) {

                for (NlComponent child : selectedChildren) {
                  if (ConstraintComponentUtilities.isLine(child)) {
                    continue;
                  }
                  NlComponent tag = NlComponentHelperKt.createChild(barrier, TAG, null, InsertType.CREATE);
                  if (tag != null) {
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
    }

    private void removeAbsolutePositioning(List<NlComponent> selectedChildren) {
      for (NlComponent component : selectedChildren) {
        component.removeAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X);
        component.removeAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y);
      }
    }

    @Override
    public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren,
                                   @InputEventMask int modifiersEx) {
      boolean show = true;
      if (myType == VERTICAL_BARRIER || myType == HORIZONTAL_BARRIER) {
        show = ConstraintComponentUtilities.isConstraintModelGreaterThan(editor, "1.0");
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
        show = ConstraintComponentUtilities.isConstraintModelGreaterThan(editor, "1.0");
      }
      if (myType == CONSTRAINT_SET) {
        show = ConstraintComponentUtilities.isConstraintModelGreaterThan(editor, "1.9");
      }
      if (myType == LAYER) {
        show = ConstraintComponentUtilities.isConstraintModelGreaterThan(editor, "1.9");
      }
      if (myType == FLOW) {
        show = ConstraintComponentUtilities.isConstraintModelGreaterThan(editor, "1.9");
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
      getAnalyticsManager(editor).trackAlign();
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
                                   @InputEventMask int modifiersEx) {

      Icon icon = myAlignIcon;
      if (myConstrainIcon != null) {
        if (ToggleAutoConnectAction.isAutoconnectOn() || (InputEvent.CTRL_MASK & modifiersEx) == 0) {
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
        XmlTag tag = myComponent.getBackend().getTag();
        ResourcePickerDialog dialog = ResourceChooserHelperKt.createResourcePickerDialog(
          PICK_A_DIMENSION,
          String.valueOf(Scout.getMargin()),
          myComponent.getModel().getFacet(),
          types,
          ResourceType.DIMEN,
          true,
          false,
          true,
          tag != null ? tag.getContainingFile().getVirtualFile() : null
        );

        if (myMarginPopup != null) {
          myMarginPopup.cancel();
        }
        if (dialog.showAndGet()) {
          resolveResValue(dialog.getResourceName());
        }
      }

      private void resolveResValue(String resourceRef) {
        ConfigurationManager configurationManager = ConfigurationManager.getOrCreateInstance(myComponent.getModel().getFacet());
        XmlTag tag = myComponent.getBackend().getTag();
        assert tag != null;
        ResourceResolver resolver = configurationManager.getConfiguration(tag.getContainingFile().getVirtualFile()).getResourceResolver();
        ResourceValue unresolved = new AttrResourceValueImpl(ResourceNamespace.RES_AUTO, "dimens", null);
        unresolved.setValue(resourceRef);
        ResourceValue resolvedValue = resolver.resolveResValue(unresolved);
        String marginDp = getMarginInDp(resolvedValue);

        if (marginDp == null) {
          Messages.showWarningDialog(
            "\"" + resourceRef + "\' cannot be used for default margin. Please choose a resource with \"dp\" type instead.",
            "Warning");
          setMargin(null, Scout.DEFAULT_MARGIN);
          return;
        }

        try {
          int marginInInt = Integer.parseInt(marginDp);
          setMargin(resourceRef, marginInInt);
        }
        catch (NumberFormatException nfe) {
          Messages.showWarningDialog(
            "\"" + resourceRef + "\' is not a valid dimension. Please choose a resource with correct dimension value instead.",
            "Warning");
          setMargin(null, Scout.DEFAULT_MARGIN);
          Logger.getInstance(MarginPopup.class).warn("Was unable to resolve the resValue from ResourceDialog.");
        }
      }

      private void setMargin(@Nullable String resName, int value) {
        myMarginPopup = createIfNeeded();
        myMarginPopup.getMargin().setValue(value, resName);
        myMarginPopup.updateText();
        updateIcon();
      }
    };

    @Nullable
    private static String getMarginInDp(ResourceValue resourceValue) {
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
      }
      else if (value.endsWith("dip")) {
        toReturn = value.substring(0, value.length() - 3);
      }
      return toReturn;
    }

    private MarginPopup myMarginPopup;
    private LookAndFeel myLookAndFeel;
    private String myPreviousDisplay;
    private Icon myMarginIcon;
    @Nullable private NlComponent myComponent;
    @Nullable private ActionButton myActionButton;

    private MarginSelector() {
      super(null, "Default Margins"); // tooltip
    }

    private void updateIcon() {
      myMarginPopup = createIfNeeded();
      String previousDisplay = myMarginPopup.getMargin().getDisplayValue();
      if (!previousDisplay.equals(myPreviousDisplay)) {
        myPreviousDisplay = previousDisplay;
        myMarginIcon = new Icon() {
          @Override
          public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(JBColor.foreground());
            g.setFont(g.getFont().deriveFont(Font.PLAIN, JBUI.scaleFontSize(DEFAULT_ICON_FONT_SIZE)));
            String m = myPreviousDisplay;
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
      myMarginPopup = createIfNeeded();
      DesignSurface<?> surface = editor.getScene().getDesignSurface();
      getAnalyticsManager(editor).trackDefaultMargins();
      RelativePoint relativePoint = new RelativePoint(surface, new Point(0, 0));
      JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(myMarginPopup, myMarginPopup.getTextField())
        .setRequestFocus(true)
        .setCancelOnMouseOutCallback((event) -> !withinComponent(event))
        .setCancelOnClickOutside(true)
        .setCancelOnOtherWindowOpen(true)
        .setCancelCallback(() -> {
          myMarginPopup.cancel();
          return Boolean.TRUE;
        })
        .createPopup();
      myMarginPopup.setPopup(popup);
      Disposer.register(popup, this::popupClosed);
      popup.show(relativePoint);
    }

    // Bug: b/131855036
    // The MarginPopup component was created under a certain LookAndFeel.
    // If the LookAndFeel has changed we will have to recreate the UI since the update logic in LafManager is complicated.
    @NotNull
    private MarginPopup createIfNeeded() {
      if (myMarginPopup != null && UIManager.getLookAndFeel() == myLookAndFeel) {
        return myMarginPopup;
      }
      if (myMarginPopup != null) {
        myMarginPopup.removeResourcePickerActionListener(myResourcePickerIconClickListener);
        myMarginPopup.setActionListener(null);
      }
      myMarginPopup = new MarginPopup();
      myLookAndFeel = UIManager.getLookAndFeel();
      myMarginPopup.setActionListener((e) -> Scout.setMargin(myMarginPopup.getMargin().getValue()));
      myMarginPopup.addResourcePickerActionListener(myResourcePickerIconClickListener);
      return myMarginPopup;
    }

    private void popupClosed() {
      if (myMarginPopup != null) {
        myMarginPopup.setPopup(null);
      }
      myActionButton = null;
    }

    // Bugs: b/131775093 & b/123071296
    // This popup must be closed when the mouse is moved outside the area of the popup itself
    // and the ActionButton used to open the popup.
    // If a second popup is opened while this popup is visible we risk the appearance of ghost
    // popups on the screen.
    public boolean withinComponent(@NotNull MouseEvent event) {
      if (myMarginPopup == null || !myMarginPopup.isShowing()) {
        return false;
      }

      // First check if the mouse is hovering over the popup itself:
      Point eventLocation = event.getLocationOnScreen();
      Rectangle popupScreenBounds = new Rectangle(myMarginPopup.getLocationOnScreen(), myMarginPopup.getSize());
      if (popupScreenBounds.contains(eventLocation)) {
        return true;
      }

      // Next check if the mouse is hovering over the action button for this MarginSelector.
      // Hack: We don't have the ActionButton passed to this layer. Current workaround is to:
      // Find the ActionButton from the MouseEvent in case the mouse event came from there.
      if (myActionButton == null) {
        myActionButton = checkIfMouseEventCameFromOurActionButton(event.getSource());
        if (myActionButton == null) {
          return false;
        }
      }

      // Extend the height of the button to the bottom on the popup in case there is a gap
      // between the button and the popup. Note that the popup could be above the button
      // if the toolbar is at the bottom of the screen.
      Rectangle buttonScreenBounds = new Rectangle(myActionButton.getLocationOnScreen(), myActionButton.getSize());
      int extendedHeight = popupScreenBounds.y + popupScreenBounds.height - buttonScreenBounds.y;
      buttonScreenBounds.height = Math.max(buttonScreenBounds.height, extendedHeight);
      return buttonScreenBounds.contains(eventLocation);
    }

    @Nullable
    private ActionButton checkIfMouseEventCameFromOurActionButton(@Nullable Object source) {
      if (!(source instanceof ActionButton)) {
        return null;
      }
      ActionButton button = (ActionButton)source;
      Presentation presentation = button.getAction().getTemplatePresentation();
      return getLabel().equals(presentation.getText()) && Objects.equals(getIcon(), presentation.getIcon()) ? button : null;
    }

    @Override
    public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren,
                                   @InputEventMask int modifiersEx) {
      // TODO: Use AndroidIcons.SherpaIcons.Margin instead?
      updateIcon();
      if (myMarginIcon instanceof ControlIcon) {
        ((ControlIcon)myMarginIcon).setHighlight(ToggleAutoConnectAction.isAutoconnectOn() || (InputEvent.CTRL_MASK & modifiersEx) == 0);
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
  public static void willDelete(NlComponent component, @NotNull Collection<NlComponent> deleted) {
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
  public List<Placeholder> getPlaceholders(@NotNull SceneComponent component, @NotNull List<SceneComponent> draggedComponents) {
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
                      BASELINE_ALIGNED_CONSTRAINT,
                      "Baselines"));

    public static final ImmutableList<ViewAction> ALIGN_ACTIONS = ImmutableList.<ViewAction>builder()
      .addAll(ALIGN_HORIZONTALLY_ACTIONS)
      .addAll(ALIGN_VERTICALLY_ACTIONS)
      .build();

    public static final ImmutableList<ViewAction> CHAIN_ACTIONS = ImmutableList.of(
      new DisappearingActionMenu("Horizontal Chain Style",
                                 StudioIcons.LayoutEditor.Toolbar.CYCLE_CHAIN_SPREAD_INLINE,
                                 ChainStyleViewActions.HORIZONTAL_CHAIN_STYLES),
      new DisappearingActionMenu("Vertical Chain Style",
                                 StudioIcons.LayoutEditor.Toolbar.CYCLE_CHAIN_SPREAD_INLINE,
                                 ChainStyleViewActions.VERTICAL_CHAIN_STYLES),
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
      private final Icon myConnectConstraintIcon;
      private final Icon myConstrainIcon;
      private final String myToolTip;
      private boolean mReverse;
      private boolean mToParent = false;

      ConnectAction(Scout.Connect actionType, Icon connectConstraintIcon, String toolTip, boolean reverse) {
        super(connectConstraintIcon, toolTip);
        myConnectType = actionType;
        myConnectConstraintIcon = connectConstraintIcon;
        myConstrainIcon = null;
        myToolTip = toolTip;
        mReverse = reverse;
      }

      ConnectAction(Scout.Connect actionType, Icon connectConstraintIcon, String toolTip) {
        super(connectConstraintIcon, toolTip);
        myConnectType = actionType;
        myConnectConstraintIcon = connectConstraintIcon;
        myConstrainIcon = null;
        myToolTip = toolTip;
        mToParent = true;
      }


      /**
       * Function is called on right click
       * It is moderately compute intensive. (<10ms)
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
        Scout.connect(selectedChildren, myConnectType, mReverse, true);
        ensureLayersAreShown(editor, 1000);
        ComponentModification modification = new ComponentModification(component, "Connect Constraint");
        component.startAttributeTransaction().applyToModification(modification);
        modification.commit();
        getAnalyticsManager(editor).trackAddConstraint();
      }

      @Override
      public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                     @NotNull ViewEditor editor,
                                     @NotNull ViewHandler handler,
                                     @NotNull NlComponent component,
                                     @NotNull List<NlComponent> selectedChildren,
                                     @InputEventMask int modifiersEx) {


        Icon icon = myConnectConstraintIcon;
        if (myConstrainIcon != null) {
          if (ToggleAutoConnectAction.isAutoconnectOn() || (InputEvent.CTRL_MASK & modifiersEx) == 0) {
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

    private static class ConnectSource extends DisappearingActionMenu {
      int mIndex;

      private ConnectSource(int index,
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
                                     int modifiersEx) {
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

    private static ImmutableList<ViewAction> connectTopVertical(boolean reverse) {
      return ImmutableList.of(
        new ConnectAction(Scout.Connect.ConnectTopToTop,
                          StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_TOP_TO_TOP,
                          "top", reverse),
        new ConnectAction(Scout.Connect.ConnectTopToBottom,
                          StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_TOP_TO_BOTTOM,
                          "bottom", reverse)

      );
    }

    private static ImmutableList<ViewAction> connectStartHorizontal(boolean reverse) {
      return ImmutableList.of(
        new ConnectAction(Scout.Connect.ConnectStartToStart,
                          StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_START_TO_START,
                          "start", reverse),
        new ConnectAction(Scout.Connect.ConnectStartToEnd,
                          StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_START_TO_END,
                          "end", reverse)
      );
    }

    private static ImmutableList<ViewAction> connectBottomVertical(boolean reverse) {
      return ImmutableList.of(
        new ConnectAction(Scout.Connect.ConnectBottomToTop,
                          StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_BOTTOM_TO_TOP,
                          "top", reverse),
        new ConnectAction(Scout.Connect.ConnectBottomToBottom,
                          StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_BOTTOM_TO_BOTTOM,
                          "bottom", reverse)

      );
    }

    private static ImmutableList<ViewAction> connectEndHorizontal(boolean reverse) {
      return ImmutableList.of(
        new ConnectAction(Scout.Connect.ConnectEndToStart,
                          StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_END_TO_START,
                          "start", reverse),
        new ConnectAction(Scout.Connect.ConnectEndToEnd,
                          StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_END_TO_END,
                          "end", reverse)
      );
    }

    private static ImmutableList<ViewAction> connectFrom(boolean reverse) {
      return ImmutableList.of(
        new DisappearingActionMenu("top to", StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_TO_TOP, connectTopVertical(reverse)),
        new DisappearingActionMenu("bottom to", StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_TO_BOTTOM, connectBottomVertical(reverse)),
        new DisappearingActionMenu("start to", StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_TO_START, connectStartHorizontal(reverse)),
        new DisappearingActionMenu("end to", StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_TO_END, connectEndHorizontal(reverse)),
        new ConnectAction(Scout.Connect.ConnectBaseLineToBaseLine, BASELINE_ALIGNED_CONSTRAINT, "to baseline", reverse)
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
                           "Vertical Guideline"),
      new AddElementAction(AddElementAction.HORIZONTAL_GUIDELINE,
                           StudioIcons.LayoutEditor.Toolbar.GUIDELINE_HORIZONTAL,
                           "Horizontal Guideline"),
      new AddElementAction(AddElementAction.VERTICAL_BARRIER,
                           StudioIcons.LayoutEditor.Toolbar.BARRIER_VERTICAL,
                           ADD_VERTICAL_BARRIER),
      new AddElementAction(AddElementAction.HORIZONTAL_BARRIER,
                           StudioIcons.LayoutEditor.Toolbar.BARRIER_HORIZONTAL,
                           ADD_HORIZONTAL_BARRIER),
      new AddElementAction(AddElementAction.GROUP,
                           StudioIcons.LayoutEditor.Palette.GROUP,
                           ADD_GROUP),
      new AddElementAction(AddElementAction.CONSTRAINT_SET,
                           StudioIcons.LayoutEditor.Palette.CONSTRAINT_SET,
                           ADD_CONSTRAINTS_SET),
      new AddElementAction(AddElementAction.LAYER,
                           StudioIcons.LayoutEditor.Palette.LAYER,
                           ADD_LAYER),
      new AddElementAction(AddElementAction.FLOW,
                           StudioIcons.LayoutEditor.Palette.FLOW,
                           ADD_FLOW));
  }
}
