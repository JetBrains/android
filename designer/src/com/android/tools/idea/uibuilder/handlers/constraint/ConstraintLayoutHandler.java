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
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.api.actions.*;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.surface.Interaction;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.sherpa.drawing.decorator.WidgetDecorator;
import com.android.tools.sherpa.interaction.MouseInteraction;
import com.android.tools.sherpa.scout.Scout;
import com.android.tools.sherpa.structure.Selection;
import com.android.tools.sherpa.structure.WidgetsScene;
import android.support.constraint.solver.widgets.ConstraintAnchor;
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

  private boolean myShowAllConstraints = true;

  ArrayList<ViewAction> myActions = new ArrayList<>();
  ArrayList<ViewAction> myPopupActions = new ArrayList<>();
  ArrayList<ViewAction> myControlActions = new ArrayList<>();

  private boolean mControlIsPressed;

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

  private void controlDownUpdate(boolean key) {
    mControlIsPressed = key;
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
  }

  @Override
  @NotNull
  public String getGradleCoordinate(@NotNull String tagName) {
    return CONSTRAINT_LAYOUT_LIB_ARTIFACT;
  }

  @Override
  public void addToolbarActions(@NotNull List<ViewAction> actions) {
    ViewAction action;
    myActions.clear();
    myControlActions.clear();

    actions.add(new ToggleConstraintModeAction());
    actions.add(new ToggleAutoConnectAction());
    actions.add(new ViewActionSeparator());
    actions.add(new ClearConstraintsAction());
    actions.add((new InferAction()));
    actions.add(new MarginSelector("Click to change default margin"));
    String str;

    str = "Align group horizontally on the left";
    actions.add(action = new AlignAction(Scout.Arrange.AlignHorizontallyLeft,
                                         AndroidIcons.SherpaIcons.LeftAligned, AndroidIcons.SherpaIcons.LeftAlignedB, str));
    myActions.add(action);

    str = "Align group horizontally in the middle";
    actions.add(action = new AlignAction(Scout.Arrange.AlignHorizontallyCenter,
                                         AndroidIcons.SherpaIcons.CenterAligned, AndroidIcons.SherpaIcons.CenterAlignedB, str));
    myActions.add(action);

    str = "Align group horizontally on the right";
    actions.add(action = new AlignAction(Scout.Arrange.AlignHorizontallyRight,
                                         AndroidIcons.SherpaIcons.RightAligned, AndroidIcons.SherpaIcons.RightAlignedB, str));
    myActions.add(action);

    str = "Align group vertically to the top";
    actions.add(action = new AlignAction(Scout.Arrange.AlignVerticallyTop,
                                         AndroidIcons.SherpaIcons.TopAlign, AndroidIcons.SherpaIcons.TopAlignB, str));
    myActions.add(action);

    str = "Align group vertically to the middle";
    actions.add(action = new AlignAction(Scout.Arrange.AlignVerticallyMiddle,
                                         AndroidIcons.SherpaIcons.MiddleAlign, AndroidIcons.SherpaIcons.MiddleAlignB, str));
    myActions.add(action);

    str = "Align group vertically to the bottom";
    actions.add(action = new AlignAction(Scout.Arrange.AlignVerticallyBottom,
                                         AndroidIcons.SherpaIcons.BottomAlign, AndroidIcons.SherpaIcons.BottomAlignB, str));
    myActions.add(action);

    str = "Align group on the baseline";
    actions.add(action = new AlignAction(Scout.Arrange.AlignBaseline,
                                         AndroidIcons.SherpaIcons.BaselineAlign, AndroidIcons.SherpaIcons.BaselineAlignB, str));
    myActions.add(action);

    str = "Distribute group horizontally";
    actions.add(action = new AlignAction(Scout.Arrange.DistributeHorizontally,
                                         AndroidIcons.SherpaIcons.HorizontalDistribute, AndroidIcons.SherpaIcons.HorizontalDistributeB,
                                         str));
    myActions.add(action);

    str = "Distribute group vertically";
    actions.add(action = new AlignAction(Scout.Arrange.DistributeVertically,
                                         AndroidIcons.SherpaIcons.verticallyDistribute, AndroidIcons.SherpaIcons.verticallyDistribute,
                                         str));
    myActions.add(action);

    str = "Center the widget horizontally";
    actions.add(action = new AlignAction(Scout.Arrange.CenterHorizontally,
                                         AndroidIcons.SherpaIcons.HorizontalCenter, AndroidIcons.SherpaIcons.HorizontalCenterB, str));
    myActions.add(action);

    str = "Center the widget vertically";
    actions.add(action = new AlignAction(Scout.Arrange.CenterVertically,
                                         AndroidIcons.SherpaIcons.VerticalCenter, AndroidIcons.SherpaIcons.VerticalCenterB, str));
    myActions.add(action);

    str = "Center the widget horizontally in parent";
    actions.add(action = new AlignAction(Scout.Arrange.CenterHorizontallyInParent,
                                         AndroidIcons.SherpaIcons.HorizontalCenterParent, AndroidIcons.SherpaIcons.HorizontalCenterParentB,
                                         str));
    myActions.add(action);

    str = "Center the widget vertically in parent";
    actions.add(action = new AlignAction(Scout.Arrange.CenterVerticallyInParent,
                                         AndroidIcons.SherpaIcons.VerticalCenterParent, AndroidIcons.SherpaIcons.VerticalCenterParent,
                                         str));
    myActions.add(action);

    str = "Pack selection horizontally";
    actions.add(action = new AlignAction(Scout.Arrange.HorizontalPack,
                                         AndroidIcons.SherpaIcons.PackSelectionHorizontally, str));
    myActions.add(action);

    str = "Pack selection vertically";
    actions.add(action = new AlignAction(Scout.Arrange.VerticalPack,
                                         AndroidIcons.SherpaIcons.PackSelectionVertically, str));
    myActions.add(action);

    str = "Expand horizontally";
    actions.add(action = new AlignAction(Scout.Arrange.ExpandHorizontally,
                                         AndroidIcons.SherpaIcons.HorizontalExpand, str));
    myActions.add(action);

    str = "Expand vertically";
    actions.add(action = new AlignAction(Scout.Arrange.ExpandVertically,
                                         AndroidIcons.SherpaIcons.VerticalExpand, str));
    // TODO Decide if we want lock actions.add(new LockConstraints());
    myActions.add(action);
  }

  @Override
  public void addPopupMenuActions(@NotNull List<ViewAction> actions) {
    actions.add(new ClearConstraintsAction());
    // Just dumps all the toolbar actions in the context menu under a menu item called "Constraint Layout"
    String str;
    ViewAction action;
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

    str = "Distribute group horizontally";
    actions.add(action = new AlignAction(Scout.Arrange.DistributeHorizontally,
                                         AndroidIcons.SherpaIcons.HorizontalDistribute, AndroidIcons.SherpaIcons.HorizontalDistribute,
                                         str));
    myPopupActions.add(action);

    str = "Distribute group vertically";
    actions.add(action = new AlignAction(Scout.Arrange.DistributeVertically,
                                         AndroidIcons.SherpaIcons.verticallyDistribute, AndroidIcons.SherpaIcons.verticallyDistributeB,
                                         str));
    myPopupActions.add(action);

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
    addToolbarActionsToMenu("Constraint Layout", actions);

    myPopupActions.add(action);

    str = "Add Vertical Guideline";
    actions.add(action = new AddElementAction(AddElementAction.VERTICAL_GUIDELINE, AndroidIcons.SherpaIcons.VerticalExpand, str));
    myPopupActions.add(action);

    str = "Add Horizontal Guideline";
    actions.add(action = new AddElementAction(AddElementAction.HORIZONTAL_GUIDELINE, AndroidIcons.SherpaIcons.HorizontalExpand, str));
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
   * @param width      width of the surface
   * @param height     height of the surface
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
      ConstraintModel model = ConstraintModel.getConstraintModel(editor.getModel());
      if (model != null) {
        return model.isAutoConnect();
      }
      return false;
    }

    @Override
    public void setSelected(@NotNull ViewEditor editor,
                            @NotNull ViewHandler handler,
                            @NotNull NlComponent parent,
                            @NotNull List<NlComponent> selectedChildren,
                            boolean selected) {
      ConstraintModel model = ConstraintModel.getConstraintModel(editor.getModel());
      if (model != null) {
        model.setAutoConnect(selected);
      }
    }

    @Override
    public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                   @NotNull ViewEditor editor,
                                   @NotNull ViewHandler handler,
                                   @NotNull NlComponent component,
                                   @NotNull List<NlComponent> selectedChildren,
                                   @InputEventMask int modifiers,
                                   boolean selected) {
      ConstraintModel model = ConstraintModel.getConstraintModel(editor.getModel());
      if (model != null) {
        presentation.setIcon(model.isAutoConnect() ? AndroidIcons.SherpaIcons.AutoConnect : AndroidIcons.SherpaIcons.AutoConnectOff);
      }
    }

    @Override
    public boolean affectsUndo() {
      return false;
    }
  }

  private static class ClearConstraintsAction extends DirectViewAction {
    boolean mEnable = true;

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
      presentation.setEnabled(mEnable);
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
    final Icon myIcon;
    final String myText;

    public AddElementAction(int type, Icon icon, String text) {
      myType = type;
      myIcon = icon;
      myText = text;
    }

    @Override
    public void perform(@NotNull ViewEditor editor,
                        @NotNull ViewHandler handler,
                        @NotNull NlComponent component,
                        @NotNull List<NlComponent> selectedChildren,
                        @InputEventMask int modifiers) {

      NlComponent parent = component;
      while (parent != null && !parent.getTagName().equalsIgnoreCase(SdkConstants.CONSTRAINT_LAYOUT)) {
        parent = parent.getParent();
      }
      if (parent != null) {
        NlComponent guideline = parent.createChild(editor, SdkConstants.CONSTRAINT_LAYOUT_GUIDELINE, null, InsertType.CREATE);
        guideline.ensureId();
        guideline.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_GUIDELINE_RELATIVE_BEGIN, "20dp");
        if (myType == HORIZONTAL_GUIDELINE) {
          guideline.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_GUIDELINE_ORIENTATION, SdkConstants.ATTR_GUIDELINE_ORIENTATION_HORIZONTAL);
        } else {
          guideline.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_GUIDELINE_ORIENTATION, SdkConstants.ATTR_GUIDELINE_ORIENTATION_VERTICAL);
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
      presentation.setIcon(myIcon);
      presentation.setLabel(myText);
    }
  }

  private class AlignAction extends DirectViewAction implements Enableable {
    private final Scout.Arrange myActionType;
    private final Icon myAlignIcon;
    private final Icon myConstrainIcon;
    private final String myToolTip;
    boolean mEnable = true;

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

    @Override
    public void enable(Selection selection) {
      int count = selection.size();
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
          mEnable = count > 1;
          break;
        case ExpandVertically:
        case ExpandHorizontally:
        case CenterHorizontallyInParent:
        case CenterVerticallyInParent:
        case CenterVertically:
        case CenterHorizontally:
          mEnable = count >= 1;
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

      Scout.arrangeWidgets(myActionType, model.getSelection().getWidgets(), mControlIsPressed || model.isAutoConnect());
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
        ConstraintModel model = ConstraintModel.getConstraintModel(editor.getModel());
        if (model.isAutoConnect() || (InputEvent.CTRL_MASK & modifiers) != 0) {
          icon = myConstrainIcon;
        }
      }

      presentation.setVisible(mEnable);
      presentation.setEnabled(mEnable);
      presentation.setIcon(icon);
      presentation.setLabel(myToolTip);
    }
  }

  private static class MarginSelector extends DirectViewAction {
    private final String myToolTip;
    boolean mEnable = true;
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

    MarginSelector(String toolTip) {
      myToolTip = toolTip;
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
        ConstraintModel model = ConstraintModel.getConstraintModel(editor.getModel());
        if (model != null) {
          ((ControlIcon)myAlignIcon).setHighlight(model.isAutoConnect() || (InputEvent.CTRL_MASK & modifiers) != 0);
        }
      }

      presentation.setVisible(mEnable);
      presentation.setEnabled(mEnable);
      presentation.setIcon(myAlignIcon);
      presentation.setLabel(myToolTip);
    }
  }

}
