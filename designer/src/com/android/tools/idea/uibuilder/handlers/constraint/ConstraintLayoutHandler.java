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
import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.api.actions.*;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.surface.Interaction;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.sherpa.drawing.WidgetDraw;
import com.android.tools.sherpa.drawing.decorator.WidgetDecorator;
import com.android.tools.sherpa.interaction.MouseInteraction;
import com.android.tools.sherpa.scout.Scout;
import com.android.tools.sherpa.structure.Selection;
import com.android.tools.sherpa.structure.WidgetsScene;
import com.google.common.collect.Lists;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
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
  /** Preference key (used with {@link PropertiesComponent}) for auto connect mode */
  public static final String AUTO_CONNECT_PREF_KEY = PREFERENCE_KEY_PREFIX + "AutoConnect";
  /** Preference key (used with {@link PropertiesComponent}) for show all constraints mode */
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
  public String getGradleCoordinate(@NotNull String tagName) {
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

    actions.add(new NestedViewActionMenu("Pack", AndroidIcons.SherpaIcons.PackSelectionVertically, Lists.newArrayList(

      Lists.newArrayList(
        new AlignAction(Scout.Arrange.HorizontalPack,
                        AndroidIcons.SherpaIcons.PackSelectionHorizontally,
                        "Pack selection horizontally"),
        new AlignAction(Scout.Arrange.VerticalPack,
                        AndroidIcons.SherpaIcons.PackSelectionVertically,
                        "Pack selection vertically"),
        new AlignAction(Scout.Arrange.ExpandHorizontally,
                        AndroidIcons.SherpaIcons.HorizontalExpand,
                        "Expand horizontally"),
        new AlignAction(Scout.Arrange.ExpandVertically,
                        AndroidIcons.SherpaIcons.VerticalExpand,
                        "Expand vertically")),
        Lists.newArrayList(new ViewActionSeparator()),

        Lists.newArrayList(
          new AlignAction(Scout.Arrange.DistributeHorizontally,
                          AndroidIcons.SherpaIcons.HorizontalDistribute, AndroidIcons.SherpaIcons.HorizontalDistributeB,
                          "Distribute group horizontally"),
          new AlignAction(Scout.Arrange.DistributeVertically,
                          AndroidIcons.SherpaIcons.verticallyDistribute, AndroidIcons.SherpaIcons.verticallyDistribute,
                          "Distribute group vertically")
        )
    )));

    actions.add(new NestedViewActionMenu("Alignment", AndroidIcons.SherpaIcons.LeftAlignedB, Lists.newArrayList(
      Lists.newArrayList(
        new AlignAction(Scout.Arrange.AlignHorizontallyLeft,
                        AndroidIcons.SherpaIcons.LeftAligned, AndroidIcons.SherpaIcons.LeftAlignedB,
                        "Align group horizontally on the left"),
        new AlignAction(Scout.Arrange.AlignHorizontallyCenter,
                        AndroidIcons.SherpaIcons.CenterAligned, AndroidIcons.SherpaIcons.CenterAlignedB,
                        "Align group horizontally in the middle"),
        new AlignAction(Scout.Arrange.AlignHorizontallyRight,
                        AndroidIcons.SherpaIcons.RightAligned, AndroidIcons.SherpaIcons.RightAlignedB,
                        "Align group horizontally on the right")
      ),
      Lists.newArrayList(
        new AlignAction(Scout.Arrange.AlignVerticallyTop,
                        AndroidIcons.SherpaIcons.TopAlign, AndroidIcons.SherpaIcons.TopAlignB,
                        "Align group vertically to the top"),
        new AlignAction(Scout.Arrange.AlignVerticallyMiddle,
                        AndroidIcons.SherpaIcons.MiddleAlign, AndroidIcons.SherpaIcons.MiddleAlignB,
                        "Align group vertically to the middle"),
        new AlignAction(Scout.Arrange.AlignVerticallyBottom,
                        AndroidIcons.SherpaIcons.BottomAlign, AndroidIcons.SherpaIcons.BottomAlignB,
                        "Align group vertically to the bottom"),
        new AlignAction(Scout.Arrange.AlignBaseline,
                        AndroidIcons.SherpaIcons.BaselineAlign, AndroidIcons.SherpaIcons.BaselineAlignB,
                        "Align group on the baseline")
      ),
      Lists.newArrayList(new ViewActionSeparator()),
      Lists.newArrayList(
        new AlignAction(Scout.Arrange.CenterHorizontally,
                        AndroidIcons.SherpaIcons.HorizontalCenter,
                        AndroidIcons.SherpaIcons.HorizontalCenterB,
                        "Center the widget horizontally"),
        new AlignAction(Scout.Arrange.CenterVertically,
                        AndroidIcons.SherpaIcons.VerticalCenter,
                        AndroidIcons.SherpaIcons.VerticalCenterB,
                        "Center the widget vertically"),
        new AlignAction(Scout.Arrange.CenterHorizontallyInParent,
                        AndroidIcons.SherpaIcons.HorizontalCenterParent,
                        AndroidIcons.SherpaIcons.HorizontalCenterParentB,
                        "Center the widget horizontally in parent"),
        new AlignAction(Scout.Arrange.CenterVerticallyInParent,
                        AndroidIcons.SherpaIcons.VerticalCenterParent,
                        AndroidIcons.SherpaIcons.VerticalCenterParent,
                        "Center the widget vertically in parent")
      )
    )));

    actions.add(new NestedViewActionMenu("Guides", AndroidIcons.SherpaIcons.GuidelineVertical, Lists.<List<ViewAction>>newArrayList(
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

    str = "Pack selection horizontally";
    actions.add(action = new AlignAction(Scout.Arrange.HorizontalPack,
                                         AndroidIcons.SherpaIcons.PackSelectionHorizontally, str));
    myPopupActions.add(action);

    str = "Pack selection vertically";
    actions.add(action = new AlignAction(Scout.Arrange.VerticalPack,
                                         AndroidIcons.SherpaIcons.PackSelectionVertically, str));
    myPopupActions.add(action);

    str = "Expand horizontally";
    actions.add(action = new AlignAction(Scout.Arrange.ExpandHorizontally,
                                         AndroidIcons.SherpaIcons.HorizontalExpand, str));
    myPopupActions.add(action);

    str = "Expand vertically";
    actions.add(action = new AlignAction(Scout.Arrange.ExpandVertically,
                                         AndroidIcons.SherpaIcons.VerticalExpand, str));
    myPopupActions.add(action);

    actions.add((new ViewActionSeparator()));

    str = "Align group horizontally on the left";
    actions.add(action = new AlignAction(Scout.Arrange.AlignHorizontallyLeft,
                                         AndroidIcons.SherpaIcons.LeftAligned, AndroidIcons.SherpaIcons.LeftAlignedB, str));
    myPopupActions.add(action);

    str = "Align group horizontally in the middle";
    actions.add(action = new AlignAction(Scout.Arrange.AlignHorizontallyCenter,
                                         AndroidIcons.SherpaIcons.CenterAligned, AndroidIcons.SherpaIcons.CenterAlignedB, str));
    myPopupActions.add(action);

    str = "Align group horizontally on the right";
    actions.add(action = new AlignAction(Scout.Arrange.AlignHorizontallyRight,
                                         AndroidIcons.SherpaIcons.RightAligned, AndroidIcons.SherpaIcons.RightAlignedB, str));
    myPopupActions.add(action);

    str = "Align group vertically to the top";
    actions.add(action = new AlignAction(Scout.Arrange.AlignVerticallyTop,
                                         AndroidIcons.SherpaIcons.TopAlign, AndroidIcons.SherpaIcons.TopAlignB, str));
    myPopupActions.add(action);

    str = "Align group vertically to the middle";
    actions.add(action = new AlignAction(Scout.Arrange.AlignVerticallyMiddle,
                                         AndroidIcons.SherpaIcons.MiddleAlign, AndroidIcons.SherpaIcons.MiddleAlignB, str));
    myPopupActions.add(action);

    str = "Align group vertically to the bottom";
    actions.add(action = new AlignAction(Scout.Arrange.AlignVerticallyBottom,
                                         AndroidIcons.SherpaIcons.BottomAlign, AndroidIcons.SherpaIcons.BottomAlignB, str));
    myPopupActions.add(action);

    str = "Align group on the baseline";
    actions.add(action = new AlignAction(Scout.Arrange.AlignBaseline,
                                         AndroidIcons.SherpaIcons.BaselineAlign, AndroidIcons.SherpaIcons.BottomAlignB, str));
    myPopupActions.add(action);

    actions.add((new ViewActionSeparator()));

    str = "Center the widget horizontally";
    actions.add(action = new AlignAction(Scout.Arrange.CenterHorizontally,
                                         AndroidIcons.SherpaIcons.HorizontalCenter, AndroidIcons.SherpaIcons.HorizontalCenterB, str));
    myPopupActions.add(action);

    str = "Center the widget vertically";
    actions.add(action = new AlignAction(Scout.Arrange.CenterVertically,
                                         AndroidIcons.SherpaIcons.VerticalCenter, AndroidIcons.SherpaIcons.VerticalCenterB, str));
    myPopupActions.add(action);

    str = "Center the widget horizontally in parent";
    actions.add(action = new AlignAction(Scout.Arrange.CenterHorizontallyInParent,
                                         AndroidIcons.SherpaIcons.HorizontalCenterParent, AndroidIcons.SherpaIcons.HorizontalCenterParentB,
                                         str));
    myPopupActions.add(action);

    str = "Center the widget vertically in parent";
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
    return new ConstraintInteraction(screenView, component);
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
    DrawConstraintModel drawConstraintModel = ConstraintModel.getDrawConstraintModel(screenView);

    drawConstraintModel.mouseMoved(x, y);
    int cursor = drawConstraintModel.getMouseInteraction().getMouseCursor();

    // Set the mouse cursor
    // TODO: we should only update if we are above a component we manage, not simply all component that
    // is a child of this viewgroup
    screenView.getSurface().setCursor(Cursor.getPredefinedCursor(cursor));
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
    return drawConstraintModel.paint(gc, Coordinates.getSwingDimension(screenView, component.w),
                                     Coordinates.getSwingDimension(screenView, component.h),
                                     myShowAllConstraints);
  }

  private static class ToggleAutoConnectAction extends ToggleViewAction implements Enableable {
    public ToggleAutoConnectAction() {
      super(AndroidIcons.SherpaIcons.AutoConnectOff, AndroidIcons.SherpaIcons.AutoConnect, "Turn on Autoconnect", "Turn off Autoconnect");
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
      ConstraintModel.setAutoConnect(selected);
    }

    @Override
    public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren,
                                   @InputEventMask int modifiers,
                                   boolean selected) {
      presentation.setIcon(ConstraintModel.isAutoConnect() ? AndroidIcons.SherpaIcons.AutoConnect : AndroidIcons.SherpaIcons.AutoConnectOff);
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
      presentation.setLabel("Clear all constraints");
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
      presentation.setLabel("Infer constraints");
    }
  }

  private class ToggleConstraintModeAction extends ToggleViewAction {
    public ToggleConstraintModeAction() {
      super(AndroidIcons.SherpaIcons.Unhide, AndroidIcons.SherpaIcons.Hide, "Show constraints",
            "Show No constraints");
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
      PropertiesComponent.getInstance().setValue(SHOW_CONSTRAINTS_PREF_KEY, myShowAllConstraints);
    }

    @Override
    public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren,
                                   @InputEventMask int modifiers,
                                   boolean selected) {
      presentation.setIcon(myShowAllConstraints ? AndroidIcons.SherpaIcons.Unhide : AndroidIcons.SherpaIcons.Hide);
    }
  }

  static class ControlIcon implements Icon {
    Icon mIcon;
    boolean mHighlight;

    ControlIcon(Icon icon) {
      mIcon = icon;
    }

    public void setHighlight(boolean mHighlight) {
      this.mHighlight = mHighlight;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {

      mIcon.paintIcon(c, g, x, y);
      if (mHighlight) {
        g.setColor(new Color(0x03a9f4));
        g.fillRect(x, y + getIconHeight() - 2, getIconWidth(), 2);
      }
    }

    @Override
    public int getIconWidth() {
      return mIcon.getIconWidth();
    }

    @Override
    public int getIconHeight() {
      return mIcon.getIconHeight();
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
        guideline.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_GUIDELINE_RELATIVE_BEGIN, "20dp");
        if (myType == HORIZONTAL_GUIDELINE) {
          guideline.setAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_ORIENTATION,
                                 SdkConstants.ATTR_GUIDELINE_ORIENTATION_HORIZONTAL);
        }
        else {
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
      modifiers &= InputEvent.CTRL_MASK;
      Scout.arrangeWidgets(myActionType, model.getSelection().getWidgets(), modifiers != 0 || ConstraintModel.isAutoConnect());
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
        if (ConstraintModel.isAutoConnect() || (InputEvent.CTRL_MASK & modifiers) != 0) {
          icon = myConstrainIcon;
        }
      }
      presentation.setEnabled(isEnabled(selectedChildren.size()));
      presentation.setIcon(icon);
      presentation.setLabel(myToolTip);
    }
  }

  private static class MarginSelector extends DirectViewAction {
    String[] mMargins = {"0", "8", "16"};
    int[] mMarginsNumber = {0, 8, 16};
    int mCurrentMargin = 1;

    private final Icon myAlignIcon = new Icon() {
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        g.setFont(g.getFont().deriveFont(Font.BOLD, 12));
        String m = mMargins[mCurrentMargin];
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

    MarginSelector() {
      super(null, "Click to change default margin");
      int m = Scout.getMargin();
      for (int i = 0; i < mMarginsNumber.length; i++) {
        if (m == mMarginsNumber[i]) {
          mCurrentMargin = i;
        }
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
      mCurrentMargin = (mCurrentMargin + 1) % mMargins.length;
      Scout.setMargin(mMarginsNumber[mCurrentMargin]);
      MouseInteraction.setMargin(mMarginsNumber[mCurrentMargin]);
    }

    @Override
    public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren,
                                   @InputEventMask int modifiers) {
      if (myAlignIcon instanceof ControlIcon) {
        ((ControlIcon)myAlignIcon).setHighlight(ConstraintModel.isAutoConnect() || (InputEvent.CTRL_MASK & modifiers) != 0);
      }

      presentation.setIcon(myAlignIcon);
    }
  }

}
