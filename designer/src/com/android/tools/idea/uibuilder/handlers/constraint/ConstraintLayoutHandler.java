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
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.uibuilder.Features;
import com.android.tools.idea.uibuilder.analytics.NlUsageTracker;
import com.android.tools.idea.uibuilder.analytics.NlUsageTrackerManager;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.api.actions.*;
import com.android.tools.idea.uibuilder.editor.LayoutNavigationManager;
import com.android.tools.idea.uibuilder.editor.NlEditor;
import com.android.tools.idea.uibuilder.graphics.NlIcon;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.*;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.scene.*;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.ConstraintLayoutComponentNotchProvider;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.ConstraintLayoutNotchProvider;
import com.android.tools.idea.uibuilder.scene.target.*;
import com.android.tools.idea.uibuilder.structure.NlDropListener;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.Interaction;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.sherpa.drawing.WidgetDraw;
import com.android.tools.sherpa.drawing.decorator.WidgetDecorator;
import com.android.tools.idea.uibuilder.scout.Scout;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.wireless.android.sdk.stats.LayoutEditorEvent;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.intellij.lang.annotations.JdkConstants.InputEventMask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.editor.NlEditorProvider.DESIGNER_ID;

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

  private static final NlIcon BASELINE_ICON =
    new NlIcon(AndroidIcons.SherpaIcons.BaselineColor, AndroidIcons.SherpaIcons.BaselineBlue);
  private static final NlIcon NAVIGATE_TO_ICON =
    new NlIcon(AndroidIcons.SherpaIcons.ArrowRight, AndroidIcons.SherpaIcons.ArrowRight);

  private boolean myShowAllConstraints = true;
  private static boolean ourAutoConnect;
  private final static String ADD_VERTICAL_BARRIER = "Add Vertical barrier";
  private final static String ADD_HORIZONTAL_BARRIER = "Add Horizontal Barrier";
  private final static String ADD_TO_BARRIER = "Add to Barrier";
  private final static String ADD_LAYER = "Add Layer";

  static {
    ourAutoConnect = PropertiesComponent.getInstance().getBoolean(ConstraintLayoutHandler.AUTO_CONNECT_PREF_KEY, false);
  }

  // This is used to efficiently test if they are horizontal or vertical.
  static HashSet<String> ourHorizontalBarriers = new HashSet<String>(Arrays.asList(GRAVITY_VALUE_TOP, GRAVITY_VALUE_BOTTOM));
  ArrayList<ViewAction> myActions = new ArrayList<>();
  ArrayList<ViewAction> myPopupActions = new ArrayList<>();
  ArrayList<ViewAction> myControlActions = new ArrayList<>();

  private JLabel breadcrumb = new JLabel("Navigated from ");

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
                             "Add Horizontal Guideline"),
        new AddElementAction(AddElementAction.VERTICAL_BARRIER,
                             AndroidIcons.SherpaIcons.BarrierVertical,
                             ADD_VERTICAL_BARRIER),
        new AddElementAction(AddElementAction.HORIZONTAL_BARRIER,
                             AndroidIcons.SherpaIcons.BarrierHorizontal,
                             ADD_HORIZONTAL_BARRIER),
        new AddElementAction(AddElementAction.LAYER,
                             AndroidIcons.SherpaIcons.Layer,
                             ADD_LAYER)
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

    str = ADD_VERTICAL_BARRIER;
    actions.add(action = new AddElementAction(AddElementAction.VERTICAL_BARRIER, AndroidIcons.SherpaIcons.BarrierVertical, str));
    myPopupActions.add(action);

    str = ADD_HORIZONTAL_BARRIER;
    actions.add(action = new AddElementAction(AddElementAction.HORIZONTAL_BARRIER, AndroidIcons.SherpaIcons.BarrierHorizontal, str));
    myPopupActions.add(action);

    str = ADD_LAYER;
    actions.add(action = new AddElementAction(AddElementAction.LAYER, AndroidIcons.SherpaIcons.Layer, str));
    myPopupActions.add(action);
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
    return new SceneInteraction(screenView);
  }

  /**
   * Create resize and anchor targets for the given component
   */
  @Override
  @NotNull
  public List<Target> createTargets(@NotNull SceneComponent component, boolean isParent) {
    List<Target> result = new ArrayList<>();
    boolean showAnchors = !isParent;
    NlComponent nlComponent = component.getNlComponent();
    ViewInfo vi = nlComponent.viewInfo;
    if (vi != null) {

      if (nlComponent.isOrHasSuperclass(CONSTRAINT_LAYOUT_GUIDELINE)) {
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

      if (nlComponent.isOrHasSuperclass(CONSTRAINT_LAYOUT_BARRIER)) {
        String side = nlComponent.getAttribute(SHERPA_URI, ATTR_BARRIER_DIRECTION);
        boolean isHorizontal = (side == null || ourHorizontalBarriers.contains(side.toLowerCase()));
        result.add(new BarrierAnchorTarget(isHorizontal ? AnchorTarget.Type.TOP : AnchorTarget.Type.RIGHT, isHorizontal));
        result.add(new BarrierTarget(BarrierTarget.parseDirection(side)));
        return result;
      }
    }

    if (showAnchors) {
      DragTarget dragTarget = new DragTarget();
      result.add(dragTarget);
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

    ActionTarget previousAction = null;
    if (showAnchors) {
      previousAction = new ClearConstraintsTarget(previousAction);
      result.add(previousAction);

      int baseline = component.getNlComponent().getBaseline();
      if (baseline <= 0 && component.getNlComponent().viewInfo != null) {
        baseline = component.getNlComponent().viewInfo.getBaseLine();
      }
      if (baseline > 0) {
        result.add(new AnchorTarget(AnchorTarget.Type.BASELINE, true));
        ActionTarget baselineActionTarget =
          new ActionTarget(previousAction, BASELINE_ICON, (SceneComponent c) -> c.setShowBaseline(!c.canShowBaseline())) {
            @Override
            @Nullable
            public String getToolTipText() {
              return "Edit Baselines";
            }
          };
        result.add(baselineActionTarget);
        previousAction = baselineActionTarget;
      }
      ActionTarget chainCycleTarget = new ChainCycleTarget(previousAction, null);
      result.add(chainCycleTarget);
      previousAction = chainCycleTarget;
    }

    if (Features.INCLUDE_NAVIGATION_ENABLED && VIEW_INCLUDE.equals(nlComponent.getTagName())) {
      ActionTarget navigateTo = new ActionTarget(previousAction, NAVIGATE_TO_ICON, (c) -> {
        XmlAttribute layoutAttribute = nlComponent.getTag().getAttribute(ATTR_LAYOUT);
        if (layoutAttribute != null) {
          XmlAttributeValue value = layoutAttribute.getValueElement();
          PsiReference reference = value != null ? value.getReference() : null;
          PsiElement navigationElement = reference != null ? reference.resolve() : null;
          Project project = nlComponent.getModel().getProject();
          VirtualFile destinationFile = PsiUtilCore.getVirtualFile(navigationElement);
          if (destinationFile != null) {
            FileEditorManager manager = FileEditorManager.getInstance(project);
            VirtualFile currentFile = value.getContainingFile().getVirtualFile();
            FileEditor currentEditor = manager.getSelectedEditor(currentFile);
            boolean isInDesignerMode = currentEditor instanceof NlEditor;

            OpenFileDescriptor openFileDescriptor =
              new OpenFileDescriptor(project, destinationFile);
            manager.openEditor(openFileDescriptor, true);
            manager
              .setSelectedEditor(destinationFile, isInDesignerMode ? DESIGNER_ID : TextEditorProvider.getInstance().getEditorTypeId());

            FileEditor newEditor = manager.getSelectedEditor(destinationFile);
            LayoutNavigationManager.getInstance(project).updateNavigation(currentEditor, currentFile, newEditor, destinationFile);
          }
        }
      });
      result.add(navigateTo);
      previousAction = navigateTo;
    }
    return result;
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
                                       @NotNull List<SceneComponent> components,
                                       @NotNull DragType type) {
    return new SceneDragHandler(editor, this, layout, components, type);
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
      super(AndroidIcons.SherpaIcons.AutoConnectOff, AndroidIcons.SherpaIcons.AutoConnect, "Turn On Autoconnect", "Turn Off Autoconnect");
    }

    @Override
    public void enable(List<NlComponent> selection) {

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
      NlUsageTrackerManager.getInstance(((ViewEditorImpl)editor).getSceneView().getSurface())
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
    public static final int HORIZONTAL_BARRIER = 2;
    public static final int VERTICAL_BARRIER = 3;
    public static final int LAYER = 4;

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

        switch (myType) {
          case HORIZONTAL_GUIDELINE: {
            NlComponent guideline = parent.createChild(editor, SdkConstants.CONSTRAINT_LAYOUT_GUIDELINE, null, InsertType.CREATE);
            guideline.ensureId();
            guideline.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN, "20dp");
            NlUsageTracker tracker = NlUsageTrackerManager.getInstance(((ViewEditorImpl)editor).getSceneView().getSurface());
            tracker.logAction(LayoutEditorEvent.LayoutEditorEventType.ADD_HORIZONTAL_GUIDELINE);
            guideline.setAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_ORIENTATION,
                                   SdkConstants.ATTR_GUIDELINE_ORIENTATION_HORIZONTAL);
          }
          break;
          case VERTICAL_GUIDELINE: {
            NlComponent guideline = parent.createChild(editor, SdkConstants.CONSTRAINT_LAYOUT_GUIDELINE, null, InsertType.CREATE);
            guideline.ensureId();
            guideline.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN, "20dp");
            NlUsageTracker tracker = NlUsageTrackerManager.getInstance(((ViewEditorImpl)editor).getSceneView().getSurface());

            tracker.logAction(LayoutEditorEvent.LayoutEditorEventType.ADD_VERTICAL_GUIDELINE);
            guideline.setAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_ORIENTATION,
                                   SdkConstants.ATTR_GUIDELINE_ORIENTATION_VERTICAL);
          }
          break;
          case LAYER: {
            NlComponent layer = parent.createChild(editor, CLASS_CONSTRAINT_LAYOUT_LAYER, null, InsertType.CREATE);
            layer.ensureId();
          }
          break;
          case HORIZONTAL_BARRIER: {
            int barriers = 0;
            int other = 0;
            for (NlComponent child : selectedChildren) {
              if (child.isOrHasSuperclass(CONSTRAINT_LAYOUT_BARRIER)) {
                barriers++;
              }
              if (!ConstraintComponentUtilities.isLine(child)) {
                other++;
              }
            }
            if (barriers == 1 && other > 0) {
              NlComponent barrier = null;
              for (NlComponent child : selectedChildren) {
                if (child.isOrHasSuperclass(CONSTRAINT_LAYOUT_BARRIER)) {
                  barrier = child;
                  break;
                }
              }
              if (barrier != null) {
                for (NlComponent child : selectedChildren) {
                  if (ConstraintComponentUtilities.isLine(child)) {
                    continue;
                  }
                  NlComponent tag = barrier.createChild(editor, SdkConstants.TAG, null, InsertType.CREATE);
                  tag.removeAndroidAttribute(ATTR_LAYOUT_WIDTH);
                  tag.removeAndroidAttribute(ATTR_LAYOUT_HEIGHT);
                  tag.setAttribute(ANDROID_URI, SdkConstants.ATTR_ID, ID_PREFIX + child.getId());
                  tag.setAttribute(ANDROID_URI,ATTR_VALUE,SdkConstants.VALUE_TRUE);
                }
              }
              return;
            }
            NlComponent barrier = parent.createChild(editor, SdkConstants.CONSTRAINT_LAYOUT_BARRIER, null, InsertType.CREATE);
            barrier.ensureId();
            barrier.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_BARRIER_DIRECTION, "top");
            NlUsageTracker tracker = NlUsageTrackerManager.getInstance(((ViewEditorImpl)editor).getSceneView().getSurface());

            // TODO add tracker.logAction(LayoutEditorEvent.LayoutEditorEventType.ADD_HORIZONTAL_BARRIER);

            if (selectedChildren.size() > 0) {
              NlComponent tag = barrier.createChild(editor, SdkConstants.TAG, null, InsertType.CREATE);
              tag.removeAndroidAttribute(ATTR_LAYOUT_WIDTH);
              tag.removeAndroidAttribute(ATTR_LAYOUT_HEIGHT);
              for (NlComponent child : selectedChildren) {
                if (ConstraintComponentUtilities.isLine(child)) {
                  continue;
                }
                tag.setAttribute(ANDROID_URI, SdkConstants.ATTR_ID, ID_PREFIX + child.getId());
                tag.setAttribute(ANDROID_URI, ATTR_VALUE, SdkConstants.VALUE_TRUE);
              }
            }
          }
          break;
          case VERTICAL_BARRIER: {
            int barriers = 0;
            int other = 0;
            for (NlComponent child : selectedChildren) {
              if (child.isOrHasSuperclass(CONSTRAINT_LAYOUT_BARRIER)) {
                barriers++;
              }
              if (!ConstraintComponentUtilities.isLine(child)) {
                other++;
              }
            }
            if (barriers == 1 && other > 0) {
              NlComponent barrier = null;
              for (NlComponent child : selectedChildren) {
                if (child.isOrHasSuperclass(CONSTRAINT_LAYOUT_BARRIER)) {
                  barrier = child;
                  break;
                }
              }
              if (barrier != null) {
                for (NlComponent child : selectedChildren) {
                  if (ConstraintComponentUtilities.isLine(child)) {
                    continue;
                  }
                  NlComponent tag = barrier.createChild(editor, SdkConstants.TAG, null, InsertType.CREATE);
                  tag.removeAndroidAttribute(ATTR_LAYOUT_WIDTH);
                  tag.removeAndroidAttribute(ATTR_LAYOUT_HEIGHT);
                  tag.setAttribute(ANDROID_URI, SdkConstants.ATTR_ID, ID_PREFIX + child.getId());
                  tag.setAttribute(ANDROID_URI, ATTR_VALUE, SdkConstants.VALUE_TRUE);
                }
              }
              return;
            }
            NlComponent barrier = parent.createChild(editor, SdkConstants.CONSTRAINT_LAYOUT_BARRIER, null, InsertType.CREATE);
            barrier.ensureId();
            barrier.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_BARRIER_DIRECTION, "left");
            NlUsageTracker tracker = NlUsageTrackerManager.getInstance(((ViewEditorImpl)editor).getSceneView().getSurface());
            // TODO add tracker.logAction(LayoutEditorEvent.LayoutEditorEventType.ADD_VERTICAL_BARRIER);

            if (selectedChildren.size() > 0) {

              for (NlComponent child : selectedChildren) {
                if (ConstraintComponentUtilities.isLine(child)) {
                  continue;
                }
                NlComponent tag = barrier.createChild(editor, SdkConstants.TAG, null, InsertType.CREATE);
                tag.removeAndroidAttribute(ATTR_LAYOUT_WIDTH);
                tag.removeAndroidAttribute(ATTR_LAYOUT_HEIGHT);
                tag.setAttribute(ANDROID_URI, SdkConstants.ATTR_ID, ID_PREFIX + child.getId());
                tag.setAttribute(ANDROID_URI, ATTR_VALUE, SdkConstants.VALUE_TRUE);
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
            if (child.isOrHasSuperclass(CONSTRAINT_LAYOUT_BARRIER)) {
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
      if (myType == LAYER) {
        show = ConstraintComponentUtilities.isConstraintModelGreaterThan(component.getModel(), 1, 0);
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
      NlUsageTrackerManager.getInstance(((ViewEditorImpl)editor).getSceneView().getSurface())
        .logAction(LayoutEditorEvent.LayoutEditorEventType.ALIGN);
      modifiers &= InputEvent.CTRL_MASK;
      Scout.arrangeWidgets(myActionType, selectedChildren, modifiers == 0 || ourAutoConnect);
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
  public boolean deleteChildren(@NotNull NlComponent parent, @NotNull List<NlComponent> deleted) {
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
  private static void willDelete(NlComponent component, @NotNull List<NlComponent> deleted) {
    final int count = deleted.size();
    for (int i = 0; i < count; i++) {
      NlComponent deletedComponent = deleted.get(i);
      String id = deletedComponent.getId();
      ConstraintComponentUtilities.updateOnDelete(component, id);
    }
  }

  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(ATTR_MIN_WIDTH, ATTR_MAX_WIDTH, ATTR_MIN_HEIGHT, ATTR_MAX_HEIGHT, ATTR_LAYOUT_CONSTRAINTSET);
  }
}
