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

import android.support.constraint.solver.widgets.ConstraintAnchor;
import android.support.constraint.solver.widgets.ConstraintWidget;
import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.analytics.NlUsageTrackerManager;
import com.android.tools.idea.uibuilder.analytics.NlUsageTracker;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.api.actions.*;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.scene.*;
import com.android.tools.idea.uibuilder.scene.target.AnchorTarget;
import com.android.tools.idea.uibuilder.scene.target.DragTarget;
import com.android.tools.idea.uibuilder.scene.target.ResizeTarget;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.Interaction;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.sherpa.drawing.WidgetDraw;
import com.android.tools.sherpa.drawing.decorator.WidgetDecorator;
import com.android.tools.sherpa.interaction.MouseInteraction;
import com.android.tools.sherpa.scout.Scout;
import com.android.tools.sherpa.structure.Selection;
import com.android.tools.sherpa.structure.WidgetsScene;
import com.google.common.collect.Lists;
import com.google.wireless.android.sdk.stats.LayoutEditorEvent;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.intellij.lang.annotations.JdkConstants.InputEventMask;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static com.android.SdkConstants.CONSTRAINT_LAYOUT_LIB_ARTIFACT;

/**
 * Handles interactions for the ConstraintLayout viewgroups
 */
public class ConstraintLayoutHandler extends ViewGroupHandler {
  private static final String PREFERENCE_KEY_PREFIX = "ConstraintLayoutPreference";
  /**
   * Preference key (used with {@link PropertiesComponent}) for auto connect mode
   */
  public static final String AUTO_CONNECT_PREF_KEY = PREFERENCE_KEY_PREFIX + "AutoConnect";
  /**
   * Preference key (used with {@link PropertiesComponent}) for show all constraints mode
   */
  public static final String SHOW_CONSTRAINTS_PREF_KEY = PREFERENCE_KEY_PREFIX + "ShowAllConstraints";

  private boolean myShowAllConstraints = true;

  ArrayList<ViewAction> myActions = new ArrayList<>();
  ArrayList<ViewAction> myPopupActions = new ArrayList<>();
  ArrayList<ViewAction> myControlActions = new ArrayList<>();

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
  public void addToolbarActions(@NotNull List<ViewAction> actions) {
    myActions.clear();
    myControlActions.clear();

    actions.add((new ToggleConstraintModeAction()));
    actions.add((new ToggleAutoConnectAction()));
    actions.add((new ViewActionSeparator()));
    actions.add(new ClearConstraintsAction());
    actions.add((new InferAction()));
    actions.add((new ViewActionSeparator()));
    actions.add((new MarginSelector()));

    // TODO Decide if we want lock actions.add(new LockConstraints());

    actions.add(new NestedViewActionMenu("Pack", AndroidIcons.SherpaIcons.PackSelectionVerticallyB, Lists.newArrayList(

      Lists.newArrayList(
        new AlignAction(Scout.Arrange.HorizontalPack,
                        AndroidIcons.SherpaIcons.PackSelectionHorizontally,
                        "Pack Horizontally"),
        new AlignAction(Scout.Arrange.VerticalPack,
                        AndroidIcons.SherpaIcons.PackSelectionVertically,
                        "Pack Vertically"),
        new AlignAction(Scout.Arrange.ExpandHorizontally,
                        AndroidIcons.SherpaIcons.HorizontalExpand,
                        "Expand Horizontally"),
        new AlignAction(Scout.Arrange.ExpandVertically,
                        AndroidIcons.SherpaIcons.VerticalExpand,
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
      Lists.newArrayList(
        new AlignAction(Scout.Arrange.AlignHorizontallyLeft,
                        AndroidIcons.SherpaIcons.LeftAligned, AndroidIcons.SherpaIcons.LeftAlignedB,
                        "Align Left Edges"),
        new AlignAction(Scout.Arrange.AlignHorizontallyCenter,
                        AndroidIcons.SherpaIcons.CenterAligned, AndroidIcons.SherpaIcons.CenterAlignedB,
                        "Align Horizontal Centers"),
        new AlignAction(Scout.Arrange.AlignHorizontallyRight,
                        AndroidIcons.SherpaIcons.RightAligned, AndroidIcons.SherpaIcons.RightAlignedB,
                        "Align Right Edges")
      ),
      Lists.newArrayList(
        new AlignAction(Scout.Arrange.AlignVerticallyTop,
                        AndroidIcons.SherpaIcons.TopAlign, AndroidIcons.SherpaIcons.TopAlignB,
                        "Align Top Edges"),
        new AlignAction(Scout.Arrange.AlignVerticallyMiddle,
                        AndroidIcons.SherpaIcons.MiddleAlign, AndroidIcons.SherpaIcons.MiddleAlignB,
                        "Align Vertical Centers"),
        new AlignAction(Scout.Arrange.AlignVerticallyBottom,
                        AndroidIcons.SherpaIcons.BottomAlign, AndroidIcons.SherpaIcons.BottomAlignB,
                        "Align Bottom Edges"),
        new AlignAction(Scout.Arrange.AlignBaseline,
                        AndroidIcons.SherpaIcons.BaselineAlign, AndroidIcons.SherpaIcons.BaselineAlignB,
                        "Align Baselines")
      ),
      Lists.newArrayList(new ViewActionSeparator()),
      Lists.newArrayList(
        new AlignAction(Scout.Arrange.CenterHorizontally,
                        AndroidIcons.SherpaIcons.HorizontalCenter,
                        AndroidIcons.SherpaIcons.HorizontalCenterB,
                        "Center Horizontally"),
        new AlignAction(Scout.Arrange.CenterVertically,
                        AndroidIcons.SherpaIcons.VerticalCenter,
                        AndroidIcons.SherpaIcons.VerticalCenterB,
                        "Center Vertically"),
        new AlignAction(Scout.Arrange.CenterHorizontallyInParent,
                        AndroidIcons.SherpaIcons.HorizontalCenterParent,
                        AndroidIcons.SherpaIcons.HorizontalCenterParentB,
                        "Center Horizontally in Parent"),
        new AlignAction(Scout.Arrange.CenterVerticallyInParent,
                        AndroidIcons.SherpaIcons.VerticalCenterParent,
                        AndroidIcons.SherpaIcons.VerticalCenterParent,
                        "Center Vertically in Parent")
      )
    )));

    actions.add(new NestedViewActionMenu("Guidelines", AndroidIcons.SherpaIcons.GuidelineVertical, Lists.<List<ViewAction>>newArrayList(
      Lists.newArrayList(
        new AddElementAction(AddElementAction.VERTICAL_GUIDELINE,
                             AndroidIcons.SherpaIcons.GuidelineVertical,
                             "Add Vertical Guideline"),
        new AddElementAction(AddElementAction.HORIZONTAL_GUIDELINE,
                             AndroidIcons.SherpaIcons.GuidelineHorizontal,
                             "Add Horizontal Guideline")
      ))
    ));
  }

  @Override
  public void addPopupMenuActions(@NotNull List<ViewAction> actions) {
    actions.add(new ClearConstraintsAction());
    // Just dumps all the toolbar actions in the context menu under a menu item called "Constraint Layout"
    String str;
    ViewAction action;

    str = "Pack Horizontally";
    actions.add(action = new AlignAction(Scout.Arrange.HorizontalPack,
                                         AndroidIcons.SherpaIcons.PackSelectionHorizontally, str));
    myPopupActions.add(action);

    str = "Pack Vertically";
    actions.add(action = new AlignAction(Scout.Arrange.VerticalPack,
                                         AndroidIcons.SherpaIcons.PackSelectionVertically, str));
    myPopupActions.add(action);

    str = "Expand Horizontally";
    actions.add(action = new AlignAction(Scout.Arrange.ExpandHorizontally,
                                         AndroidIcons.SherpaIcons.HorizontalExpand, str));
    myPopupActions.add(action);

    str = "Expand Vertically";
    actions.add(action = new AlignAction(Scout.Arrange.ExpandVertically,
                                         AndroidIcons.SherpaIcons.VerticalExpand, str));
    myPopupActions.add(action);

    actions.add((new ViewActionSeparator()));

    str = "Align Left Edges";
    actions.add(action = new AlignAction(Scout.Arrange.AlignHorizontallyLeft,
                                         AndroidIcons.SherpaIcons.LeftAligned, AndroidIcons.SherpaIcons.LeftAlignedB, str));
    myPopupActions.add(action);

    str = "Align Horizontal Centers";
    actions.add(action = new AlignAction(Scout.Arrange.AlignHorizontallyCenter,
                                         AndroidIcons.SherpaIcons.CenterAligned, AndroidIcons.SherpaIcons.CenterAlignedB, str));
    myPopupActions.add(action);

    str = "Align Right Edges";
    actions.add(action = new AlignAction(Scout.Arrange.AlignHorizontallyRight,
                                         AndroidIcons.SherpaIcons.RightAligned, AndroidIcons.SherpaIcons.RightAlignedB, str));
    myPopupActions.add(action);

    str = "Align Top Edges";
    actions.add(action = new AlignAction(Scout.Arrange.AlignVerticallyTop,
                                         AndroidIcons.SherpaIcons.TopAlign, AndroidIcons.SherpaIcons.TopAlignB, str));
    myPopupActions.add(action);

    str = "Align Vertical Centers";
    actions.add(action = new AlignAction(Scout.Arrange.AlignVerticallyMiddle,
                                         AndroidIcons.SherpaIcons.MiddleAlign, AndroidIcons.SherpaIcons.MiddleAlignB, str));
    myPopupActions.add(action);

    str = "Align Bottom Edges";
    actions.add(action = new AlignAction(Scout.Arrange.AlignVerticallyBottom,
                                         AndroidIcons.SherpaIcons.BottomAlign, AndroidIcons.SherpaIcons.BottomAlignB, str));
    myPopupActions.add(action);

    str = "Align Baselines";
    actions.add(action = new AlignAction(Scout.Arrange.AlignBaseline,
                                         AndroidIcons.SherpaIcons.BaselineAlign, AndroidIcons.SherpaIcons.BottomAlignB, str));
    myPopupActions.add(action);

    actions.add((new ViewActionSeparator()));

    str = "Center Horizontally";
    actions.add(action = new AlignAction(Scout.Arrange.CenterHorizontally,
                                         AndroidIcons.SherpaIcons.HorizontalCenter, AndroidIcons.SherpaIcons.HorizontalCenterB, str));
    myPopupActions.add(action);

    str = "Center Vertically";
    actions.add(action = new AlignAction(Scout.Arrange.CenterVertically,
                                         AndroidIcons.SherpaIcons.VerticalCenter, AndroidIcons.SherpaIcons.VerticalCenterB, str));
    myPopupActions.add(action);

    str = "Center Horizontally in Parent";
    actions.add(action = new AlignAction(Scout.Arrange.CenterHorizontallyInParent,
                                         AndroidIcons.SherpaIcons.HorizontalCenterParent, AndroidIcons.SherpaIcons.HorizontalCenterParentB,
                                         str));
    myPopupActions.add(action);

    str = "Center Vertically in Parent";
    actions.add(action = new AlignAction(Scout.Arrange.CenterVerticallyInParent,
                                         AndroidIcons.SherpaIcons.VerticalCenterParent, AndroidIcons.SherpaIcons.VerticalCenterParentB,
                                         str));
    myPopupActions.add(action);
    addToolbarActionsToMenu("Constraint Layout", actions);

    str = "Add Vertical Guideline";
    actions.add(action = new AddElementAction(AddElementAction.VERTICAL_GUIDELINE, AndroidIcons.SherpaIcons.GuidelineVertical, str));
    myPopupActions.add(action);

    str = "Add Horizontal Guideline";
    actions.add(action = new AddElementAction(AddElementAction.HORIZONTAL_GUIDELINE, AndroidIcons.SherpaIcons.GuidelineHorizontal, str));
    myPopupActions.add(action);
  }

  interface Enableable {
    void enable(Selection selection);
  }

  /**
   * This updates what is grayed out
   *
   * @param selection
   */
  public void updateActions(Selection selection) {
    if (myActions == null) {
      return;
    }
    for (ViewAction action : myActions) {
      if (action instanceof Enableable) {
        Enableable e = (Enableable)action;
        e.enable(selection);
      }
    }

    for (ViewAction action : myPopupActions) {
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
    if (!ConstraintModel.USE_SOLVER) {
      return new SceneInteraction(screenView, component);
    }
    return new ConstraintInteraction(screenView, component);
  }

  /**
   * Add resize and anchor targets on the given component
   *
   * @param component the component we'll add targets on
   * @param isParent
   */
  @Override
  public void addTargets(@NotNull SceneComponent component, boolean isParent) {
    if (!isParent) {
      component.addTarget(new DragTarget());
    }
    component.addTarget(new ResizeTarget(ResizeTarget.Type.LEFT_TOP));
    component.addTarget(new ResizeTarget(ResizeTarget.Type.LEFT_BOTTOM));
    component.addTarget(new ResizeTarget(ResizeTarget.Type.RIGHT_TOP));
    component.addTarget(new ResizeTarget(ResizeTarget.Type.RIGHT_BOTTOM));
    component.addTarget(new AnchorTarget(AnchorTarget.Type.LEFT));
    component.addTarget(new AnchorTarget(AnchorTarget.Type.TOP));
    component.addTarget(new AnchorTarget(AnchorTarget.Type.RIGHT));
    component.addTarget(new AnchorTarget(AnchorTarget.Type.BOTTOM));
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
                                       @NotNull NlComponent layout,
                                       @NotNull java.util.List<NlComponent> components,
                                       @NotNull DragType type) {
    return new ConstraintDragHandler(editor, this, layout, components, type);
  }

  /**
   * Update the mouse cursor if the (x, y) coordinates hit a resize handle or constraint handle
   *
   * @param screenView the ScreenView we are working on
   * @param x          the current x mouse coordinate
   * @param y          the current y mouse coordinate
   * @return true if we modified the cursor
   */
  @Override
  public boolean updateCursor(@NotNull ScreenView screenView,
                              @AndroidCoordinate int x, @AndroidCoordinate int y) {
    if (!ConstraintModel.USE_SOLVER) {
      Scene scene = screenView.getScene();
      int dpX = Coordinates.pxToDp(screenView, x);
      int dpY = Coordinates.pxToDp(screenView, y);
      scene.mouseHover(dpX, dpY);
      screenView.getSurface().repaint();
    } else {
      DrawConstraintModel drawConstraintModel = ConstraintModel.getDrawConstraintModel(screenView);

      drawConstraintModel.mouseMoved(x, y);
      int cursor = drawConstraintModel.getMouseInteraction().getMouseCursor();

      // Set the mouse cursor
      // TODO: we should only update if we are above a component we manage, not simply all component that
      // is a child of this viewgroup
      screenView.getSurface().setCursor(Cursor.getPredefinedCursor(cursor));
    }
    return true;
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
    ConstraintModel constraintModel = ConstraintModel.getConstraintModel(screenView.getModel());
    DrawConstraintModel drawConstraintModel = ConstraintModel.getDrawConstraintModel(screenView);
    updateActions(constraintModel.getSelection());
    ConstraintWidget widget = constraintModel.getScene().getWidget(component);
    return drawConstraintModel.paint(gc, widget,
                                     Coordinates.getSwingDimension(screenView, component.w),
                                     Coordinates.getSwingDimension(screenView, component.h),
                                     myShowAllConstraints);
  }

  private static class ToggleAutoConnectAction extends ToggleViewAction implements Enableable {
    public ToggleAutoConnectAction() {
      super(AndroidIcons.SherpaIcons.AutoConnectOff, AndroidIcons.SherpaIcons.AutoConnect, "Turn On Autoconnect", "Turn Off Autoconnect");
    }

    @Override
    public void enable(Selection selection) {

    }

    @Override
    public boolean isSelected(@NotNull ViewEditor editor,
                              @NotNull ViewHandler handler,
                              @NotNull NlComponent parent,
                              @NotNull List<NlComponent> selectedChildren) {
      return ConstraintModel.isAutoConnect();
    }

    @Override
    public void setSelected(@NotNull ViewEditor editor,
                            @NotNull ViewHandler handler,
                            @NotNull NlComponent parent,
                            @NotNull List<NlComponent> selectedChildren,
                            boolean selected) {
      NlUsageTrackerManager.getInstance(((ViewEditorImpl)editor).getScreenView().getSurface())
        .logAction(selected
                   ? LayoutEditorEvent.LayoutEditorEventType.TURN_ON_AUTOCONNECT
                   : LayoutEditorEvent.LayoutEditorEventType.TURN_OFF_AUTOCONNECT);
      ConstraintModel.setAutoConnect(selected);
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
      ConstraintModel model = ConstraintModel.getConstraintModel(editor.getModel());
      if (model == null) {
        return;
      }
      NlUsageTrackerManager.getInstance(((ViewEditorImpl)editor).getScreenView().getSurface())
        .logAction(LayoutEditorEvent.LayoutEditorEventType.CLEAR_ALL_CONSTRAINTS);
      WidgetsScene scene = model.getScene();
      scene.clearAllConstraints();
      model.saveToXML(true);
    }

    @Override
    public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren,
                                   @InputEventMask int modifiers) {
      presentation.setIcon(AndroidIcons.SherpaIcons.DeleteConstraint);
      presentation.setLabel("Clear All Constraints");
    }
  }

  private static class LockConstraints extends DirectViewAction {
    @Override
    public void perform(@NotNull ViewEditor editor,
                        @NotNull ViewHandler handler,
                        @NotNull NlComponent component,
                        @NotNull List<NlComponent> selectedChildren,
                        @InputEventMask int modifiers) {
      ConstraintModel model = ConstraintModel.getConstraintModel(editor.getModel());
      if (model == null) {
        return;
      }
      if (model.getSelection().getWidgets().size() == 1) {
        WidgetsScene scene = model.getScene();
        scene.toggleLockConstraints((model.getSelection().getWidgets().get(0)));
      }
    }

    @Override
    public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren,
                                   @InputEventMask int modifiers) {
      presentation.setIcon(AndroidIcons.SherpaIcons.LockConstraints);
      presentation.setLabel("Locks auto inferred constraints");
    }
  }

  private static class InferAction extends DirectViewAction {
    @Override
    public void perform(@NotNull ViewEditor editor,
                        @NotNull ViewHandler handler,
                        @NotNull NlComponent component,
                        @NotNull List<NlComponent> selectedChildren,
                        @InputEventMask int modifiers) {
      ConstraintModel model = ConstraintModel.getConstraintModel(editor.getModel());
      if (model == null) {
        return;
      }
      NlUsageTrackerManager.getInstance(((ViewEditorImpl)editor).getScreenView().getSurface())
        .logAction(LayoutEditorEvent.LayoutEditorEventType.INFER_CONSTRAINS);
      WidgetsScene scene = model.getScene();
      try {
        Scout.inferConstraints(scene);
      }
      catch (Exception e) {
        // TODO show dialog the inference failed
        Logger.getInstance(ConstraintLayoutHandler.class).warn("Error in inferring constraints", e);
      }
      model.saveToXML(true);
      model.setNeedsAnimateConstraints(ConstraintAnchor.SCOUT_CREATOR);
    }

    @Override
    public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren,
                                   @InputEventMask int modifiers) {
      presentation.setIcon(AndroidIcons.SherpaIcons.Inference);
      presentation.setLabel("Infer Constraints");
    }
  }

  private class ToggleConstraintModeAction extends ToggleViewAction {
    public ToggleConstraintModeAction() {
      super(AndroidIcons.SherpaIcons.Hide, AndroidIcons.SherpaIcons.Unhide, "Show Constraints", "Hide Constraints");
      myShowAllConstraints = PropertiesComponent.getInstance().getBoolean(SHOW_CONSTRAINTS_PREF_KEY);
    }

    @Override
    public boolean isSelected(@NotNull ViewEditor editor,
                              @NotNull ViewHandler handler,
                              @NotNull NlComponent parent,
                              @NotNull List<NlComponent> selectedChildren) {
      return myShowAllConstraints;
    }

    @Override
    public void setSelected(@NotNull ViewEditor editor,
                            @NotNull ViewHandler handler,
                            @NotNull NlComponent parent,
                            @NotNull List<NlComponent> selectedChildren,
                            boolean selected) {
      myShowAllConstraints = selected;
      NlUsageTrackerManager.getInstance(((ViewEditorImpl)editor).getScreenView().getSurface())
        .logAction(
          selected ? LayoutEditorEvent.LayoutEditorEventType.SHOW_CONSTRAINTS : LayoutEditorEvent.LayoutEditorEventType.HIDE_CONSTRAINTS);
      PropertiesComponent.getInstance().setValue(SHOW_CONSTRAINTS_PREF_KEY, myShowAllConstraints);
    }
  }

  static class ControlIcon implements Icon {
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
        g.setColor(new Color(0x03a9f4));
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
      while (parent != null && !parent.isOrHasSuperclass(SdkConstants.CONSTRAINT_LAYOUT)) {
        parent = parent.getParent();
      }
      if (parent != null) {
        NlComponent guideline = parent.createChild(editor, SdkConstants.CONSTRAINT_LAYOUT_GUIDELINE, null, InsertType.CREATE);
        guideline.ensureId();
        guideline.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN, "20dp");
        NlUsageTracker tracker = NlUsageTrackerManager.getInstance(((ViewEditorImpl)editor).getScreenView().getSurface());
        if (myType == HORIZONTAL_GUIDELINE) {
          tracker.logAction(LayoutEditorEvent.LayoutEditorEventType.ADD_HORIZONTAL_GUIDELINE);
          guideline.setAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_ORIENTATION,
                                 SdkConstants.ATTR_GUIDELINE_ORIENTATION_HORIZONTAL);
        }
        else {
          tracker.logAction(LayoutEditorEvent.LayoutEditorEventType.ADD_VERTICAL_GUIDELINE);
          guideline.setAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_ORIENTATION,
                                 SdkConstants.ATTR_GUIDELINE_ORIENTATION_VERTICAL);
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
      presentation.setVisible(true);
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

    private boolean isEnabled(int count) {
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
        case ExpandVertically:
        case ExpandHorizontally:
        case CenterHorizontallyInParent:
        case CenterVerticallyInParent:
        case CenterVertically:
        case CenterHorizontally:
          return count >= 1;
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
      ConstraintModel model = ConstraintModel.getConstraintModel(editor.getModel());
      if (model == null) {
        return;
      }
      NlUsageTrackerManager.getInstance(((ViewEditorImpl)editor).getScreenView().getSurface())
        .logAction(LayoutEditorEvent.LayoutEditorEventType.ALIGN);
      modifiers &= InputEvent.CTRL_MASK;
      Scout.arrangeWidgets(myActionType, model.getSelection().getWidgets(), modifiers == 0 || ConstraintModel.isAutoConnect());
      model.saveToXML(true);
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
        if (ConstraintModel.isAutoConnect() || (InputEvent.CTRL_MASK & modifiers) == 0) {
          icon = myConstrainIcon;
        }
      }
      presentation.setEnabled(isEnabled(selectedChildren.size()));
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
      MouseInteraction.setMargin(myMarginPopup.getValue());
    }

    private void updateIcon() {
      final int margin = myMarginPopup.getValue();
      if (myMarginIconValue != margin) {
        myMarginIconValue = margin;
        myMarginIcon = new Icon() {
          @Override
          public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setFont(g.getFont().deriveFont(Font.BOLD, 12));
            String m = Integer.toString(margin);
            FontMetrics metrics = g.getFontMetrics();
            int strWidth = metrics.stringWidth(m);

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
      ConstraintModel model = ConstraintModel.getConstraintModel(editor.getModel());
      if (model == null) {
        return;
      }
      DesignSurface surface = ((ViewEditorImpl)editor).getScreenView().getSurface();
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
        ((ControlIcon)myMarginIcon).setHighlight(ConstraintModel.isAutoConnect() || (InputEvent.CTRL_MASK & modifiers) == 0);
      }
      presentation.setIcon(myMarginIcon);
    }
  }

}
