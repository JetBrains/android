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

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.common.analytics.NlUsageTracker;
import com.android.tools.idea.common.analytics.NlUsageTrackerManager;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.ComponentProvider;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.target.ActionTarget;
import com.android.tools.idea.common.scene.target.LassoTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.Interaction;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.api.actions.*;
import com.android.tools.idea.uibuilder.graphics.NlIcon;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.ConstraintLayoutComponentNotchProvider;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.ConstraintLayoutNotchProvider;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.*;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget;
import com.android.tools.idea.uibuilder.scout.Scout;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.sherpa.drawing.WidgetDraw;
import com.android.tools.sherpa.drawing.decorator.WidgetDecorator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.wireless.android.sdk.stats.LayoutEditorEvent;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import icons.StudioIcons;
import org.intellij.lang.annotations.JdkConstants.InputEventMask;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Handles interactions for the ConstraintLayout
 */
public class ConstraintLayoutHandler extends ViewGroupHandler implements ComponentProvider {

  private static final String PREFERENCE_KEY_PREFIX = "ConstraintLayoutPreference";
  /**
   * Preference key (used with {@link PropertiesComponent}) for auto connect mode
   */
  public static final String AUTO_CONNECT_PREF_KEY = PREFERENCE_KEY_PREFIX + "AutoConnect";
  /**
   * Preference key (used with {@link PropertiesComponent}) for show all constraints mode
   */
  public static final String SHOW_CONSTRAINTS_PREF_KEY = PREFERENCE_KEY_PREFIX + "ShowAllConstraints";
  public static final String SHOW_MARGINS_PREF_KEY = PREFERENCE_KEY_PREFIX + "ShowMargins";
  public static final String FADE_UNSELECTED_VIEWS = PREFERENCE_KEY_PREFIX + "FadeUnselected";

  private static final NlIcon BASELINE_ICON =
    new NlIcon(AndroidIcons.SherpaIcons.BaselineColor, AndroidIcons.SherpaIcons.BaselineBlue);

  private static boolean ourAutoConnect;
  private final static String ADD_VERTICAL_BARRIER = "Add Vertical barrier";
  private final static String ADD_HORIZONTAL_BARRIER = "Add Horizontal Barrier";
  private final static String ADD_TO_BARRIER = "Add to Barrier";
  private final static String ADD_LAYER = "Add Layer";
  private final static String ADD_GROUP = "Add Group";
  private final static String ADD_CONSTRAINTS_SET = "Add set of Constraints";
  public static final String EDIT_BASELINE_ACTION_TOOLTIP = "Edit Baseline";

  private static HashMap<String, Boolean> ourVisibilityFlags = new HashMap<>();

  static {
    ourAutoConnect = PropertiesComponent.getInstance().getBoolean(AUTO_CONNECT_PREF_KEY, false);
  }

  // This is used to efficiently test if they are horizontal or vertical.
  static HashSet<String> ourHorizontalBarriers = new HashSet<>(Arrays.asList(GRAVITY_VALUE_TOP, GRAVITY_VALUE_BOTTOM));
  private ArrayList<ViewAction> myActions = new ArrayList<>();

  /**
   * Utility function to convert from an Icon to an Image
   *
   * @param icon
   * @return
   */
  static Image iconToImage(Icon icon) {
    if (icon instanceof ImageIcon) {
      return ((ImageIcon)icon).getImage();
    }
    else {
      int w = icon.getIconWidth();
      int h = icon.getIconHeight();
      BufferedImage image = UIUtil.createImage(w, h, BufferedImage.TYPE_4BYTE_ABGR);
      Graphics2D g = image.createGraphics();
      icon.paintIcon(null, g, 0, 0);
      g.dispose();
      return image;
    }
  }

  /**
   * Base constructor
   */
  public ConstraintLayoutHandler() {
    loadWidgetDecoratorImages();
  }

  private static void loadWidgetDecoratorImages() {
    if (WidgetDecorator.sLockImageIcon == null) {
      WidgetDecorator.sLockImageIcon = iconToImage(AndroidIcons.SherpaIcons.LockConstraints);
    }
    if (WidgetDecorator.sUnlockImageIcon == null) {
      WidgetDecorator.sUnlockImageIcon = iconToImage(AndroidIcons.SherpaIcons.UnlockConstraints);
    }
    if (WidgetDecorator.sDeleteConnectionsImageIcon == null) {
      WidgetDecorator.sDeleteConnectionsImageIcon = iconToImage(AndroidIcons.SherpaIcons.DeleteConstraintB);
    }
    if (WidgetDecorator.sPackChainImageIcon == null) {
      WidgetDecorator.sPackChainImageIcon = iconToImage(AndroidIcons.SherpaIcons.ChainStyle);
    }
    if (WidgetDraw.sGuidelineArrowLeft == null) {
      WidgetDraw.sGuidelineArrowLeft = iconToImage(AndroidIcons.SherpaIcons.ArrowLeft);
    }
    if (WidgetDraw.sGuidelineArrowRight == null) {
      WidgetDraw.sGuidelineArrowRight = iconToImage(AndroidIcons.SherpaIcons.ArrowRight);
    }
    if (WidgetDraw.sGuidelineArrowUp == null) {
      WidgetDraw.sGuidelineArrowUp = iconToImage(AndroidIcons.SherpaIcons.ArrowUp);
    }
    if (WidgetDraw.sGuidelineArrowDown == null) {
      WidgetDraw.sGuidelineArrowDown = iconToImage(AndroidIcons.SherpaIcons.ArrowDown);
    }
    if (WidgetDraw.sGuidelinePercent == null) {
      WidgetDraw.sGuidelinePercent = iconToImage(AndroidIcons.SherpaIcons.Percent);
    }
  }

  @Override
  @NotNull
  public String getGradleCoordinateId(@NotNull String tagName) {
    return CONSTRAINT_LAYOUT_LIB_ARTIFACT;
  }

  @Override
  @NotNull
  public Map<String, Map<String, String>> getEnumPropertyValues(@SuppressWarnings("unused") @NotNull NlComponent component) {
    Map<String, String> values = ImmutableMap.of("0dp", "match_constraint", VALUE_WRAP_CONTENT, VALUE_WRAP_CONTENT);
    return ImmutableMap.of(ATTR_LAYOUT_WIDTH, values,
                           ATTR_LAYOUT_HEIGHT, values);
  }

  @Override
  public void addToolbarActions(@NotNull List<ViewAction> actions) {
    myActions.clear();

    actions.add(new NestedViewActionMenu("Show", StudioIcons.Common.VISIBILITY_INLINE, Lists.<List<ViewAction>>newArrayList(
      Lists.newArrayList(
        new ToggleVisibilityAction(SHOW_CONSTRAINTS_PREF_KEY, "Show Constraints", true),
        new ToggleVisibilityAction(SHOW_MARGINS_PREF_KEY, "Show Margins", true),
        new ToggleVisibilityAction(FADE_UNSELECTED_VIEWS, "Fade Unselected views ", false)
      )
    )));
    actions.add((new ToggleAutoConnectAction()));
    actions.add((new ViewActionSeparator()));
    actions.add(new ClearConstraintsAction());
    actions.add((new InferAction()));
    actions.add((new ViewActionSeparator()));
    actions.add((new MarginSelector()));

    // TODO Decide if we want lock actions.add(new LockConstraints());
    actions.add(new NestedViewActionMenu("Pack", StudioIcons.LayoutEditor.Toolbar.PACK_VERTICAL, Lists.newArrayList(

      Lists.newArrayList(
        new AlignAction(Scout.Arrange.HorizontalPack,
                        StudioIcons.LayoutEditor.Toolbar.PACK_HORIZONTAL,
                        "Pack Horizontally"),
        new AlignAction(Scout.Arrange.VerticalPack,
                        StudioIcons.LayoutEditor.Toolbar.PACK_VERTICAL,
                        "Pack Vertically"),
        new AlignAction(Scout.Arrange.ExpandHorizontally,
                        StudioIcons.LayoutEditor.Toolbar.EXPAND_HORIZONTAL,
                        "Expand Horizontally"),
        new AlignAction(Scout.Arrange.ExpandVertically,
                        StudioIcons.LayoutEditor.Toolbar.EXPAND_VERTICAL,
                        "Expand Vertically")),
      Lists.newArrayList(new ViewActionSeparator()),

      Lists.newArrayList(
        new AlignAction(Scout.Arrange.DistributeHorizontally,
                        AndroidIcons.SherpaIcons.HorizontalDistribute, AndroidIcons.SherpaIcons.HorizontalDistributeB,
                        "Distribute Horizontally"),
        new AlignAction(Scout.Arrange.DistributeVertically,
                        AndroidIcons.SherpaIcons.verticallyDistribute, AndroidIcons.SherpaIcons.verticallyDistribute,
                        "Distribute Vertically")
      )
    )));

    actions.add(new NestedViewActionMenu("Align", AndroidIcons.SherpaIcons.LeftAlignedB, Lists.newArrayList(
      ConstraintViewActions.ALIGN_HORIZONTALLY_ACTIONS,
      ConstraintViewActions.ALIGN_VERTICALLY_ACTIONS,
      Lists.newArrayList(new ViewActionSeparator()),
      ConstraintViewActions.CENTER_ACTIONS)));

    actions.add(new NestedViewActionMenu("Guidelines", StudioIcons.LayoutEditor.Toolbar.VERTICAL_GUIDE, Lists.<List<ViewAction>>newArrayList(
      ConstraintViewActions.HELPER_ACTIONS)));
  }

  @Override
  public void addPopupMenuActions(@NotNull List<ViewAction> actions) {
    actions.add(new ViewActionMenu("Organize", StudioIcons.LayoutEditor.Toolbar.PACK_HORIZONTAL, ConstraintViewActions.ORGANIZE_ACTIONS));
    actions.add(new ViewActionMenu("Align", StudioIcons.LayoutEditor.Toolbar.LEFT_ALIGNED, ConstraintViewActions.ALIGN_ACTIONS));
    actions.add(new ViewActionMenu("Chain", AndroidIcons.SherpaIcons.CreateHorizontalChain, ConstraintViewActions.CHAIN_ACTIONS));
    actions.add(new ViewActionMenu("Center", StudioIcons.LayoutEditor.Toolbar.CENTER_HORIZONTAL, ConstraintViewActions.CENTER_ACTIONS));
    actions.add(new ViewActionMenu("Helpers", StudioIcons.LayoutEditor.Toolbar.VERTICAL_GUIDE, ConstraintViewActions.HELPER_ACTIONS));
  }

  interface Enableable {
    void enable(List<NlComponent> selection);
  }

  /**
   * This updates what is grayed out
   *
   * @param selection
   */
  public void updateActions(List<NlComponent> selection) {
    if (myActions == null) {
      return;
    }
    for (ViewAction action : myActions) {
      if (action instanceof Enableable) {
        Enableable e = (Enableable)action;
        e.enable(selection);
      }
    }

    for (ViewAction action : ConstraintViewActions.ALL_POPUP_ACTIONS) {
      if (action instanceof Enableable) {
        Enableable e = (Enableable)action;
        e.enable(selection);
      }
    }
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

  /**
   * Create resize and anchor targets for the given component
   */
  @Override
  @NotNull
  public List<Target> createTargets(@NotNull SceneComponent component, boolean isParent) {
    List<Target> result = new ArrayList<>();
    boolean showAnchors = !isParent;
    NlComponent nlComponent = component.getAuthoritativeNlComponent();
    ViewInfo vi = NlComponentHelperKt.getViewInfo(nlComponent);
    if (vi != null) {

      if (NlComponentHelperKt.isOrHasSuperclass(nlComponent, CONSTRAINT_LAYOUT_GUIDELINE)) {
        String orientation = nlComponent.getAttribute(ANDROID_URI, ATTR_ORIENTATION);

        boolean isHorizontal = true;
        if (orientation != null && orientation.equalsIgnoreCase(ATTR_GUIDELINE_ORIENTATION_VERTICAL)) {
          isHorizontal = false;
        }
        result.add(new GuidelineTarget(isHorizontal));
        if (isHorizontal) {
          result.add(new GuidelineAnchorTarget(AnchorTarget.Type.TOP, true));
        }
        else {
          result.add(new GuidelineAnchorTarget(AnchorTarget.Type.LEFT, false));
        }
        result.add(new GuidelineCycleTarget(isHorizontal));
        return result;
      }

      if (NlComponentHelperKt.isOrHasSuperclass(nlComponent, CONSTRAINT_LAYOUT_BARRIER)) {
        @NonNls String side = nlComponent.getAttribute(SHERPA_URI, ATTR_BARRIER_DIRECTION);
        boolean isHorizontal = (side == null || ourHorizontalBarriers.contains(side.toLowerCase()));
        result.add(new BarrierAnchorTarget(isHorizontal ? AnchorTarget.Type.TOP : AnchorTarget.Type.RIGHT,
                                           BarrierTarget.parseDirection(side)));
        result.add(new BarrierTarget(BarrierTarget.parseDirection(side)));
        return result;
      }
    }

    if (!isParent) {
      component.setComponentProvider(this);
    }
    if (showAnchors) {
      ConstraintDragTarget dragTarget = new ConstraintDragTarget();
      result.add(dragTarget);
      result.add(new ConstraintResizeTarget(ResizeBaseTarget.Type.LEFT));
      result.add(new ConstraintResizeTarget(ResizeBaseTarget.Type.RIGHT));
      result.add(new ConstraintResizeTarget(ResizeBaseTarget.Type.TOP));
      result.add(new ConstraintResizeTarget(ResizeBaseTarget.Type.BOTTOM));
      result.add(new ConstraintResizeTarget(ResizeBaseTarget.Type.LEFT_TOP));
      result.add(new ConstraintResizeTarget(ResizeBaseTarget.Type.LEFT_BOTTOM));
      result.add(new ConstraintResizeTarget(ResizeBaseTarget.Type.RIGHT_TOP));
      result.add(new ConstraintResizeTarget(ResizeBaseTarget.Type.RIGHT_BOTTOM));
      component.setNotchProvider(new ConstraintLayoutComponentNotchProvider());
    }
    else {
      result.add(new LassoTarget());
      component.setNotchProvider(new ConstraintLayoutNotchProvider());
    }

    result.add(new AnchorTarget(AnchorTarget.Type.LEFT, showAnchors));
    result.add(new AnchorTarget(AnchorTarget.Type.TOP, showAnchors));
    result.add(new AnchorTarget(AnchorTarget.Type.RIGHT, showAnchors));
    result.add(new AnchorTarget(AnchorTarget.Type.BOTTOM, showAnchors));

    if (showAnchors) {
      ActionTarget previousAction = new ClearConstraintsTarget(null);
      result.add(previousAction);

      int baseline = NlComponentHelperKt.getBaseline(component.getNlComponent());
      ViewInfo info = NlComponentHelperKt.getViewInfo(component.getNlComponent());
      if (baseline <= 0 && info != null) {
        baseline = info.getBaseLine();
      }
      if (baseline > 0) {
        result.add(new AnchorTarget(AnchorTarget.Type.BASELINE, true));
        ActionTarget baselineActionTarget =
          new ActionTarget(previousAction, BASELINE_ICON, (SceneComponent c) -> c.setShowBaseline(!c.canShowBaseline())) {
            @NotNull
            @Override
            public String getToolTipText() {
              return EDIT_BASELINE_ACTION_TOOLTIP;
            }
          };
        result.add(baselineActionTarget);
        previousAction = baselineActionTarget;
      }
      ActionTarget chainCycleTarget = new ChainCycleTarget(previousAction, null);
      result.add(chainCycleTarget);
    }
    return result;
  }

  @Override
  public void cleanUpAttributes(@NotNull ViewEditor editor, @NotNull NlComponent child) {
    SceneComponent childSceneComponent = ((ViewEditorImpl)editor).getSceneView().getScene().getSceneComponent(child);

    if (childSceneComponent == null) {
      return;
    }

    AttributesTransaction transaction = child.startAttributeTransaction();
    ConstraintComponentUtilities.cleanup(transaction, childSceneComponent);
    transaction.commit();
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

  /**
   * Return true to be in charge of the painting
   *
   * @return true
   */
  @Override
  public boolean handlesPainting() {
    return true;
  }

  /**
   * Paint the component and its children on the given context
   *
   * @param gc         graphics context
   * @param screenView the current screenview
   * @param component  the component to draw
   * @return true to indicate that we will need to be repainted
   */
  @Override
  public boolean drawGroup(@NotNull Graphics2D gc, @NotNull ScreenView screenView,
                           @NotNull NlComponent component) {
    updateActions(component.getModel().getSelectionModel().getSelection());
    return false;
  }

  private static class ToggleAutoConnectAction extends ToggleViewAction implements Enableable {
    public ToggleAutoConnectAction() {
      super(StudioIcons.LayoutEditor.Toolbar.AUTO_CORRECT_OFF, StudioIcons.LayoutEditor.Toolbar.AUTO_CONNECT, "Turn On Autoconnect", "Turn Off Autoconnect");
    }

    @Override
    public void enable(List<NlComponent> selection) {
      // FIXME Why is this empty ? Can we remove the Enableable interface and all related code?
    }

    @Override
    public boolean isSelected(@NotNull ViewEditor editor,
                              @NotNull ViewHandler handler,
                              @NotNull NlComponent parent,
                              @NotNull List<NlComponent> selectedChildren) {
      return PropertiesComponent.getInstance().getBoolean(AUTO_CONNECT_PREF_KEY, false);
    }

    @Override
    public void setSelected(@NotNull ViewEditor editor,
                            @NotNull ViewHandler handler,
                            @NotNull NlComponent parent,
                            @NotNull List<NlComponent> selectedChildren,
                            boolean selected) {
      NlUsageTrackerManager.getInstance(((ViewEditorImpl)editor).getSceneView().getSurface())
        .logAction(selected
                   ? LayoutEditorEvent.LayoutEditorEventType.TURN_ON_AUTOCONNECT
                   : LayoutEditorEvent.LayoutEditorEventType.TURN_OFF_AUTOCONNECT);
      PropertiesComponent.getInstance().setValue(AUTO_CONNECT_PREF_KEY, selected, false);
    }

    @Override
    public boolean affectsUndo() {
      return false;
    }
  }

  private static class ClearConstraintsAction extends DirectViewAction {
    @Override
    public void perform(@NotNull ViewEditor editor,
                        @NotNull ViewHandler handler,
                        @NotNull NlComponent component,
                        @NotNull List<NlComponent> selectedChildren,
                        @InputEventMask int modifiers) {
      NlUsageTrackerManager.getInstance(((ViewEditorImpl)editor).getSceneView().getSurface())
        .logAction(LayoutEditorEvent.LayoutEditorEventType.CLEAR_ALL_CONSTRAINTS);
      ViewEditorImpl viewEditor = (ViewEditorImpl)editor;
      Scene scene = viewEditor.getSceneView().getScene();
      scene.clearAttributes();
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
      presentation.setLabel("Clear All Constraints");
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
    NlDesignSurface designSurface = (NlDesignSurface)((ViewEditorImpl)editor).getSceneView().getSurface();
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
      NlUsageTrackerManager.getInstance(((ViewEditorImpl)editor).getSceneView().getSurface())
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

  private static class ToggleVisibilityAction extends ToggleViewAction {
    String mType;

    public ToggleVisibilityAction(String type, String text, boolean defaultValue) {
      super(AndroidIcons.SherpaIcons.Unchecked, AndroidIcons.SherpaIcons.Checked, text, text);
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
        ensureLayersAreShown(editor, 1000);
        switch (myType) {
          case HORIZONTAL_GUIDELINE: {
            NlComponent guideline = NlComponentHelperKt.createChild(parent, editor, CONSTRAINT_LAYOUT_GUIDELINE, null, InsertType.CREATE);
            assert guideline != null;
            NlComponentHelperKt.ensureId(guideline);
            guideline.setAttribute(SHERPA_URI, LAYOUT_CONSTRAINT_GUIDE_BEGIN, "20dp");
            NlUsageTracker tracker = NlUsageTrackerManager.getInstance(((ViewEditorImpl)editor).getSceneView().getSurface());
            tracker.logAction(LayoutEditorEvent.LayoutEditorEventType.ADD_HORIZONTAL_GUIDELINE);
            guideline.setAttribute(NS_RESOURCES, ATTR_ORIENTATION,
                                   ATTR_GUIDELINE_ORIENTATION_HORIZONTAL);
          }
          break;
          case VERTICAL_GUIDELINE: {
            NlComponent guideline = NlComponentHelperKt.createChild(parent, editor, CONSTRAINT_LAYOUT_GUIDELINE, null, InsertType.CREATE);
            assert guideline != null;
            NlComponentHelperKt.ensureId(guideline);
            guideline.setAttribute(SHERPA_URI, LAYOUT_CONSTRAINT_GUIDE_BEGIN, "20dp");
            NlUsageTracker tracker = NlUsageTrackerManager.getInstance(((ViewEditorImpl)editor).getSceneView().getSurface());

            tracker.logAction(LayoutEditorEvent.LayoutEditorEventType.ADD_VERTICAL_GUIDELINE);
            guideline.setAttribute(NS_RESOURCES, ATTR_ORIENTATION,
                                   ATTR_GUIDELINE_ORIENTATION_VERTICAL);
          }
          break;
          case GROUP: {
            NlComponent group = NlComponentHelperKt.createChild(parent, editor, CLASS_CONSTRAINT_LAYOUT_GROUP, null, InsertType.CREATE);
            assert group != null;
            NlComponentHelperKt.ensureId(group);
          }
          break;
          case CONSTRAINT_SET: {
            NlComponent constraints =
              NlComponentHelperKt.createChild(parent, editor, CLASS_CONSTRAINT_LAYOUT_CONSTRAINTS, null, InsertType.CREATE);
            assert constraints != null;
            NlComponentHelperKt.ensureId(constraints);
            ConstraintReferenceManagement.populateConstraints(constraints);
          }
          break;
          case LAYER: {
            NlComponent layer = NlComponentHelperKt.createChild(parent, editor, CLASS_CONSTRAINT_LAYOUT_LAYER, null, InsertType.CREATE);
            assert layer != null;
            NlComponentHelperKt.ensureId(layer);
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

            NlComponent barrier = NlComponentHelperKt.createChild(parent, editor, CONSTRAINT_LAYOUT_BARRIER, null, InsertType.CREATE);
            assert barrier != null;
            NlComponentHelperKt.ensureId(barrier);
            barrier.setAttribute(SHERPA_URI, ATTR_BARRIER_DIRECTION, "top");

            // NlUsageTracker tracker = NlUsageTrackerManager.getInstance(((ViewEditorImpl)editor).getSceneView().getSurface());
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
            NlComponent barrier = NlComponentHelperKt.createChild(parent, editor, CONSTRAINT_LAYOUT_BARRIER, null, InsertType.CREATE);
            assert barrier != null;
            NlComponentHelperKt.ensureId(barrier);
            barrier.setAttribute(SHERPA_URI, ATTR_BARRIER_DIRECTION, "left");

            // NlUsageTracker tracker = NlUsageTrackerManager.getInstance(((ViewEditorImpl)editor).getSceneView().getSurface());
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
        show = ConstraintComponentUtilities.isConstraintModelGreaterThan(component.getModel(), 1, 0);
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
        show = ConstraintComponentUtilities.isConstraintModelGreaterThan(component.getModel(), 1, 0);
      }
      if (myType == CONSTRAINT_SET) {
        show = ConstraintComponentUtilities.isConstraintModelGreaterThan(component.getModel(), 1, 9);
      }
      if (myType == LAYER) {
        show = ConstraintComponentUtilities.isConstraintModelGreaterThan(component.getModel(), 1, 9);
      }

      presentation.setVisible(show);
      presentation.setEnabled(true);
    }
  }

  private static class AlignAction extends DirectViewAction {
    private final Scout.Arrange myActionType;
    private final Icon myAlignIcon;
    private final Icon myConstrainIcon;
    private final String myToolTip;

    AlignAction(Scout.Arrange actionType, Icon alignIcon, String toolTip) {
      myActionType = actionType;
      myAlignIcon = alignIcon;
      myConstrainIcon = null;
      myToolTip = toolTip;
    }

    AlignAction(Scout.Arrange actionType, Icon alignIcon, Icon constrainIcon, String toolTip) {
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
    private boolean isEnabled(List<NlComponent> selected) {
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
          return count > 1 &&  !Scout.chainCheck(selected, Scout.ChainTest.InHorizontalChain);
        case CreateVerticalChain:
          return count > 1 &&  !Scout.chainCheck(selected, Scout.ChainTest.InVerticalChain);
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
        case  ChainInsertVertical:
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
      NlUsageTrackerManager.getInstance(((ViewEditorImpl)editor).getSceneView().getSurface())
        .logAction(LayoutEditorEvent.LayoutEditorEventType.ALIGN);
      modifiers &= InputEvent.CTRL_MASK;
      Scout.arrangeWidgets(myActionType, selectedChildren, modifiers == 0 || ourAutoConnect);
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
        if (ourAutoConnect || (InputEvent.CTRL_MASK & modifiers) == 0) {
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
    MarginPopup myMarginPopup = new MarginPopup();
    private int myMarginIconValue;
    private Icon myMarginIcon;

    public MarginSelector() {
      setLabel("Default Margins"); // tooltip
      myMarginPopup.setActionListener((e) -> setMargin());
    }

    public void setMargin() {
      Scout.setMargin(myMarginPopup.getValue());
    }

    private void updateIcon() {
      final int margin = myMarginPopup.getValue();
      if (myMarginIconValue != margin) {
        myMarginIconValue = margin;
        myMarginIcon = new Icon() {
          @Override
          public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(JBColor.foreground());
            g.setFont(g.getFont().deriveFont(Font.BOLD, 12));
            String m = Integer.toString(margin);
            FontMetrics metrics = g.getFontMetrics();
            int strWidth = metrics.stringWidth(m);
            ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int stringY = (getIconHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            g.drawString(m, x + (getIconWidth() - strWidth) / 2, y + stringY);
          }

          @Override
          public int getIconWidth() {
            return 16;
          }

          @Override
          public int getIconHeight() {
            return 16;
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
      DesignSurface surface = ((ViewEditorImpl)editor).getSceneView().getSurface();
      NlUsageTrackerManager.getInstance(surface).logAction(LayoutEditorEvent.LayoutEditorEventType.DEFAULT_MARGINS);
      RelativePoint relativePoint = new RelativePoint(surface, new Point(0, 0));
      JBPopupFactory.getInstance().createComponentPopupBuilder(myMarginPopup, myMarginPopup.getTextField())
        .setRequestFocus(true)
        .createPopup().show(relativePoint);
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
        ((ControlIcon)myMarginIcon).setHighlight(ourAutoConnect || (InputEvent.CTRL_MASK & modifiers) == 0);
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

  private static class ConstraintViewActions {
    private static final ImmutableList<ViewAction> ALIGN_HORIZONTALLY_ACTIONS = ImmutableList.of(
      new AlignAction(Scout.Arrange.AlignHorizontallyLeft,
                      StudioIcons.LayoutEditor.Toolbar.LEFT_ALIGNED,
                      AndroidIcons.SherpaIcons.LeftAlignedB,
                      "Align Left Edges"),
      new AlignAction(Scout.Arrange.AlignHorizontallyCenter,
                      StudioIcons.LayoutEditor.Toolbar.HORIZONTAL_CENTER_ALIGNED,
                      AndroidIcons.SherpaIcons.CenterAlignedB,
                      "Align Horizontal Centers"),
      new AlignAction(Scout.Arrange.AlignHorizontallyRight,
                      StudioIcons.LayoutEditor.Toolbar.RIGHT_ALIGNED,
                      AndroidIcons.SherpaIcons.RightAlignedB,
                      "Align Right Edges"));

    private static final ImmutableList<ViewAction> ALIGN_VERTICALLY_ACTIONS = ImmutableList.of(
      new AlignAction(Scout.Arrange.AlignVerticallyTop,
                      StudioIcons.LayoutEditor.Toolbar.TOP_ALIGNED,
                      AndroidIcons.SherpaIcons.TopAlignB,
                      "Align Top Edges"),
      new AlignAction(Scout.Arrange.AlignVerticallyMiddle,
                      StudioIcons.LayoutEditor.Toolbar.VERTICAL_CENTER_ALIGNED,
                      AndroidIcons.SherpaIcons.MiddleAlignB,
                      "Align Vertical Centers"),
      new AlignAction(Scout.Arrange.AlignVerticallyBottom,
                      StudioIcons.LayoutEditor.Toolbar.BOTTOM_ALIGNED,
                      AndroidIcons.SherpaIcons.BottomAlignB,
                      "Align Bottom Edges"),
      new AlignAction(Scout.Arrange.AlignBaseline,
                      StudioIcons.LayoutEditor.Toolbar.BASELINE_ALIGNED,
                      AndroidIcons.SherpaIcons.BaselineAlignB, "Align Baselines"));


    private static final ImmutableList<ViewAction> ALIGN_ACTIONS = ImmutableList.<ViewAction>builder()
      .addAll(ALIGN_HORIZONTALLY_ACTIONS)
      .addAll(ALIGN_VERTICALLY_ACTIONS)
      .build();

    private static final ImmutableList<ViewAction> CHAIN_ACTIONS = ImmutableList.of(

      new AlignAction(Scout.Arrange.CreateHorizontalChain,
                      AndroidIcons.SherpaIcons.CreateHorizontalChain,
                      AndroidIcons.SherpaIcons.CreateHorizontalChain,
                      "Create Horizontal Chain"),
      new AlignAction(Scout.Arrange.CreateVerticalChain,
                      AndroidIcons.SherpaIcons.CreateVerticalChain,
                      AndroidIcons.SherpaIcons.CreateVerticalChain,
                      "Create Vertical Chain"),
      new AlignAction(Scout.Arrange.ChainInsertHorizontal,
                      AndroidIcons.SherpaIcons.CreateHorizontalChain,
                      AndroidIcons.SherpaIcons.CreateHorizontalChain,
                      "Insert in Horizontal Chain"),
      new AlignAction(Scout.Arrange.ChainInsertVertical,
                      AndroidIcons.SherpaIcons.CreateVerticalChain,
                      AndroidIcons.SherpaIcons.CreateVerticalChain,
                      "Insert in  Vertical Chain"),
      new AlignAction(Scout.Arrange.ChainVerticalRemove,
                      AndroidIcons.SherpaIcons.CreateVerticalChain,
                      AndroidIcons.SherpaIcons.CreateVerticalChain,
                      "remove from Vertical Chain"),
      new AlignAction(Scout.Arrange.ChainHorizontalRemove,
                      AndroidIcons.SherpaIcons.CreateVerticalChain,
                      AndroidIcons.SherpaIcons.CreateVerticalChain,
                      "remove from Horizontal Chain"),
      new AlignAction(Scout.Arrange.ChainVerticalMoveUp,
                      AndroidIcons.SherpaIcons.CreateVerticalChain,
                      AndroidIcons.SherpaIcons.CreateVerticalChain,
                      "move up Vertical Chain"),
      new AlignAction(Scout.Arrange.ChainVerticalMoveDown,
                      AndroidIcons.SherpaIcons.CreateVerticalChain,
                      AndroidIcons.SherpaIcons.CreateVerticalChain,
                      "move down Vertical Chain"),
      new AlignAction(Scout.Arrange.ChainHorizontalMoveLeft,
                      AndroidIcons.SherpaIcons.CreateVerticalChain,
                      AndroidIcons.SherpaIcons.CreateVerticalChain,
                      "move left Horizontal Chain"),
      new AlignAction(Scout.Arrange.ChainHorizontalMoveRight,
                      AndroidIcons.SherpaIcons.CreateVerticalChain,
                      AndroidIcons.SherpaIcons.CreateVerticalChain,
                      "move right Horizontal Chain")
    );


    private static final ImmutableList<ViewAction> CENTER_ACTIONS = ImmutableList.of(
      new AlignAction(Scout.Arrange.CenterHorizontally,
                      StudioIcons.LayoutEditor.Toolbar.CENTER_HORIZONTAL,
                      AndroidIcons.SherpaIcons.HorizontalCenterB,
                      "Center Horizontally"),
      new AlignAction(Scout.Arrange.CenterVertically,
                      StudioIcons.LayoutEditor.Toolbar.CENTER_VERTICAL,
                      AndroidIcons.SherpaIcons.VerticalCenterB,
                      "Center Vertically"),
      new AlignAction(Scout.Arrange.CenterHorizontallyInParent,
                      StudioIcons.LayoutEditor.Toolbar.CENTER_HORIZONTAL_PARENT,
                      AndroidIcons.SherpaIcons.HorizontalCenterParentB,
                      "Center Horizontally in Parent"),
      new AlignAction(Scout.Arrange.CenterVerticallyInParent,
                      StudioIcons.LayoutEditor.Toolbar.CENTER_VERTICAL_PARENT,
                      AndroidIcons.SherpaIcons.VerticalCenterParentB,
                      "Center Vertically in Parent"));

    private static final ImmutableList<ViewAction> ORGANIZE_ACTIONS = ImmutableList.of(
      new AlignAction(Scout.Arrange.HorizontalPack,
                      StudioIcons.LayoutEditor.Toolbar.PACK_HORIZONTAL,
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
                           StudioIcons.LayoutEditor.Toolbar.VERTICAL_GUIDE,
                           "Add Vertical Guideline"),
      new AddElementAction(AddElementAction.HORIZONTAL_GUIDELINE,
                           StudioIcons.LayoutEditor.Toolbar.HORIZONTAL_GUIDE,
                           "Add Horizontal Guideline"),
      new AddElementAction(AddElementAction.VERTICAL_BARRIER,
                           AndroidIcons.SherpaIcons.BarrierVertical,
                           ADD_VERTICAL_BARRIER),
      new AddElementAction(AddElementAction.HORIZONTAL_BARRIER,
                           AndroidIcons.SherpaIcons.BarrierHorizontal,
                           ADD_HORIZONTAL_BARRIER),
      new AddElementAction(AddElementAction.GROUP,
                           AndroidIcons.SherpaIcons.Layer,
                           ADD_GROUP),
      new AddElementAction(AddElementAction.CONSTRAINT_SET,
                           AndroidIcons.SherpaIcons.Layer,
                           ADD_CONSTRAINTS_SET),
      new AddElementAction(AddElementAction.LAYER,
                           AndroidIcons.SherpaIcons.Layer,
                           ADD_LAYER));

    private static final ImmutableList<ViewAction> ALL_POPUP_ACTIONS = ImmutableList.<ViewAction>builder()
      .addAll(ALIGN_HORIZONTALLY_ACTIONS)
      .addAll(ALIGN_VERTICALLY_ACTIONS)
      .addAll(ORGANIZE_ACTIONS)
      .addAll(CENTER_ACTIONS)
      .addAll(HELPER_ACTIONS)
      .build();
  }
}
