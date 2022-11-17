/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.editor.targets;

import static com.android.ide.common.resources.ResourcesUtil.stripPrefixFromId;
import static com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneUtils.MOTION_LAYOUT_PROPERTIES;
import static icons.StudioIcons.LayoutEditor.Toolbar.BASELINE_ALIGNED_CONSTRAINT;
import static icons.StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_BOTTOM_TO_BOTTOM;
import static icons.StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_BOTTOM_TO_TOP;
import static icons.StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_END_TO_END;
import static icons.StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_END_TO_START;
import static icons.StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_START_TO_END;
import static icons.StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_START_TO_START;
import static icons.StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_TOP_TO_BOTTOM;
import static icons.StudioIcons.LayoutEditor.Toolbar.CONSTRAIN_TOP_TO_TOP;

import com.android.AndroidXConstants;
import com.android.SdkConstants;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlAttributesHolder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneComponentHelperKt;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.ScenePicker;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.target.AnchorTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.uibuilder.handlers.constraint.ComponentModification;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawAnchor;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.GuidelineAnchorTarget;
import com.android.tools.idea.uibuilder.handlers.motion.MotionLayoutComponentHelper;
import com.android.tools.idea.uibuilder.handlers.motion.MotionUtils;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionAttributes;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scene.decorator.DecoratorUtilities;
import com.android.tools.idea.uibuilder.scout.Scout;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.ui.PopupMenuListenerAdapter;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.swing.Icon;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.PopupMenuEvent;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implements a target anchor for the MotionLayout.
 *
 * TODO: we might want to refactor with ConstraintAnchorTarget
 */
public class MotionLayoutAnchorTarget extends AnchorTarget {

  private boolean myRenderingTemporaryConnection = false;
  @AndroidDpCoordinate private int myConnectedX = -1;
  @AndroidDpCoordinate private int myConnectedY = -1;

  private final HashMap<String, String> mPreviousAttributes = new HashMap<>();

  /////////////////////////////////////////////////////////////////////////////
  //region Constructor
  /////////////////////////////////////////////////////////////////////////////

  public MotionLayoutAnchorTarget(@NotNull Type type, boolean isEdge) {
    super(type, isEdge);
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Accessors
  /////////////////////////////////////////////////////////////////////////////

  public boolean isHorizontalAnchor() {
    return myType == Type.LEFT || myType == Type.RIGHT;
  }

  public boolean isVerticalAnchor() {
    return myType == Type.TOP || myType == Type.BOTTOM;
  }

  public boolean isBaselineAnchor() {
    return myType == Type.BASELINE;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Display
  /////////////////////////////////////////////////////////////////////////////

  public String getConnectionId(@NotNull NlAttributesHolder component, String uri, ArrayList<String> attributes) {
    int count = attributes.size();
    for (int i = 0; i < count; i++) {
      String attribute = component.getAttribute(uri, attributes.get(i));
      if (attribute != null) {
        return NlComponent.extractId(attribute);
      }
    }
    return null;
  }

  private boolean isConnected(MotionLayoutAnchorTarget target) {
    if (target == null) {
      return false;
    }
    if (!isConnected()) {
      return false;
    }

    ComponentModification component = new ComponentModification(myComponent.getNlComponent(), "read");
    String attribute = null;
    //noinspection EnumSwitchStatementWhichMissesCases
    switch (myType) {
      case LEFT: {
        attribute = getConnectionId(component, SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourLeftAttributes);
        break;
      }
      case RIGHT: {
        attribute = getConnectionId(component, SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourRightAttributes);
        break;
      }
      case TOP: {
        attribute = getConnectionId(component, SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourTopAttributes);
        break;
      }
      case BOTTOM: {
        attribute = getConnectionId(component, SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourBottomAttributes);
        break;
      }
      case BASELINE: {
        attribute = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF);
        if (attribute != null) {
          attribute = NlComponent.extractId(attribute);
        }
        break;
      }
    }
    if (attribute == null) {
      return false;
    }
    return attribute.equalsIgnoreCase(target.getComponent().getId());
  }

  @Override
  protected boolean isConnected() {
    ComponentModification component = new ComponentModification(myComponent.getNlComponent(), "read");
    return MotionUtils.isAnchorConnected(myType, component, useRtlAttributes(), isRtl());
  }

  @Override
  public boolean canDisconnect() {
    return myComponent.getScene().isCtrlMetaDown();
  }

  @SuppressWarnings("UseJBColor")
  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    if (myIsEdge) {
      return;
    }

    if (myComponent.getNlComponent().getParent() != null) {
      MotionLayoutComponentHelper motionLayout = MotionLayoutComponentHelper.create(myComponent.getParent().getNlComponent());
      if (motionLayout.isInTransition()) {
        return;
      }
    }

    super.render(list, sceneContext);

    if (!myRenderingTemporaryConnection) {
      if (myLastX != -1 && myLastY != -1) {
        if ((myConnectedX == -1 && myConnectedY == -1)
            || !(myLastX == myConnectedX && myLastY == myConnectedY)) {
          float x = getCenterX();
          float y = getCenterY();
          list.addConnection(sceneContext, x, y, myLastX, myLastY, myType.ordinal());
        }
      }
    }
  }

  @VisibleForTesting
  @Override
  public boolean isEnabled() {
    if (!super.isEnabled()) {
      return false;
    }
    if (myComponent.getScene().getSelection().size() > 1) {
      return false;
    }
    if (myComponent.isSelected()) {
      boolean hasBaselineConnection = isBaselineConnected();
      if (getType() == Type.BASELINE) {
        // only show baseline anchor as needed
        return myComponent.canShowBaseline() || hasBaselineConnection;
      }
      else {
        // if the baseline is showing, hide the rest of the anchors
        return (!hasBaselineConnection && !myComponent.canShowBaseline()) || (hasBaselineConnection && isHorizontalAnchor());
      }
    }

    Scene.FilterType filerType = myComponent.getScene().getFilterType();
    boolean matchesFilter;
    switch (filerType) {
      case BASELINE_ANCHOR:
        matchesFilter = isBaselineAnchor();
        break;
      case VERTICAL_ANCHOR:
        matchesFilter = isVerticalAnchor();
        break;
      case HORIZONTAL_ANCHOR:
        matchesFilter = isHorizontalAnchor();
        break;
      case ANCHOR:
        matchesFilter = true;
        break;
      default:
        matchesFilter = false;
        break;
    }
    Integer state =  myType.getMask();
    boolean tryingToConnect = state != null && (state & myType.getMask()) != 0 && isTargeted();
    return  matchesFilter || tryingToConnect;
  }

  @Override
  public boolean isConnectible(@NotNull AnchorTarget dest) {
    if (!(dest instanceof MotionLayoutAnchorTarget)) {
      return false;
    }
    MotionLayoutAnchorTarget constraintAnchorDest = (MotionLayoutAnchorTarget) dest;
    if (isVerticalAnchor() && !constraintAnchorDest.isVerticalAnchor()) {
      return false;
    }
    if (isHorizontalAnchor() && !constraintAnchorDest.isHorizontalAnchor()) {
      return false;
    }
    if (constraintAnchorDest.isEdge() && !(dest instanceof GuidelineAnchorTarget)) {
      return myComponent.getParent() == constraintAnchorDest.myComponent;
    }
    else {
      return myComponent.getParent() == constraintAnchorDest.myComponent.getParent();
    }
  }

  private boolean isBaselineConnected() {
    return myComponent.getAuthoritativeNlComponent()
                      .getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF) != null;
  }

  @NotNull
  @Override
  protected DrawAnchor.Mode getDrawMode() {
    Integer state = DecoratorUtilities.getTryingToConnectState(myComponent.getNlComponent());
    boolean doing_connection = state != null;
    state = myType.getMask();
    // While creating a constraint, this anchor is a valid target for the connection.
    boolean can_connect = state != null && (state & myType.getMask()) != 0;
    // There is a connection being created in the space of this anchor.
    boolean is_connected = isConnected();
    int drawState =
      (can_connect ? 1 : 0) | (mIsOver ? 2 : 0) | (is_connected ? 4 : 0) | (doing_connection ? 8 : 0) | (myComponent.isSelected() ? 16 : 0);

    DrawAnchor.Mode[] modeTable = {
      DrawAnchor.Mode.DO_NOT_DRAW, //
      DrawAnchor.Mode.NORMAL,      // can_connect
      DrawAnchor.Mode.DO_NOT_DRAW, // mIsOver
      DrawAnchor.Mode.NORMAL,      // can_connect & mIsOver
      DrawAnchor.Mode.DO_NOT_DRAW, // is_connected
      DrawAnchor.Mode.NORMAL,      // is_connected & can_connect
      DrawAnchor.Mode.DO_NOT_DRAW, // is_connected & mIsOver
      DrawAnchor.Mode.NORMAL,      // is_connected & can_connect & mIsOver
      DrawAnchor.Mode.DO_NOT_DRAW, // doing_connection
      DrawAnchor.Mode.NORMAL,      // doing_connection & can_connect
      DrawAnchor.Mode.DO_NOT_DRAW, // doing_connection & mIsOver
      DrawAnchor.Mode.OVER,        // doing_connection & can_connect & mIsOver
      DrawAnchor.Mode.DO_NOT_DRAW, // doing_connection & is_connected &
      DrawAnchor.Mode.NORMAL,      // doing_connection & is_connected & can_connect
      DrawAnchor.Mode.DO_NOT_DRAW, // doing_connection & is_connected & mIsOver
      DrawAnchor.Mode.OVER,        // doing_connection & is_connected & can_connect & mIsOver
      DrawAnchor.Mode.NORMAL,      // isSelected
      DrawAnchor.Mode.NORMAL,      // isSelected & can_connect
      DrawAnchor.Mode.OVER,        // isSelected & mIsOver
      DrawAnchor.Mode.NORMAL,      // isSelected & can_connect & mIsOver
      DrawAnchor.Mode.NORMAL,      // isSelected & is_connected
      DrawAnchor.Mode.NORMAL,      // isSelected & is_connected & can_connect
      DrawAnchor.Mode.DELETE,      // isSelected & is_connected & mIsOver
      DrawAnchor.Mode.DELETE,        // isSelected & is_connected & can_connect & mIsOver
      DrawAnchor.Mode.NORMAL,      // isSelected & doing_connection
      DrawAnchor.Mode.NORMAL,      // isSelected & doing_connection & can_connect
      DrawAnchor.Mode.NORMAL,      // isSelected & doing_connection & mIsOver
      DrawAnchor.Mode.OVER,        // isSelected & doing_connection & can_connect & mIsOver
      DrawAnchor.Mode.NORMAL,      // isSelected & doing_connection & is_connected &
      DrawAnchor.Mode.NORMAL,      // isSelected & doing_connection & is_connected & can_connect
      DrawAnchor.Mode.NORMAL,      // isSelected & doing_connection & is_connected & mIsOver
      DrawAnchor.Mode.OVER,        // isSelected & doing_connection & is_connected & can_connect & mIsOver
    };
    return modeTable[drawState];
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Utilities
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Clear the attributes related to this Anchor type
   */
  private void clearMe(@NotNull NlAttributesHolder transaction) {
    ConstraintComponentUtilities.clearAnchor(myType, transaction, useRtlAttributes(), isRtl());
  }

  /**
   * Store the existing attributes in mPreviousAttributes
   */
  private void rememberPreviousAttributeFromNlComponent(@NotNull String uri, @NotNull ArrayList<String> attributes) {
    NlComponent component = myComponent.getAuthoritativeNlComponent();
    for (String attribute : attributes) {
      mPreviousAttributes.put(attribute, component.getLiveAttribute(uri, attribute));
    }
  }

  private boolean useRtlAttributes() {
    return myComponent.useRtlAttributes();
  }

  private boolean isRtl() {
    return myComponent.getScene().isInRTL();
  }

  /**
   * Return the correct attribute string given our type and the target type
   */
  private String getAttribute(@NotNull Target target) {
    if (!(target instanceof MotionLayoutAnchorTarget)) {
      return null;
    }
    MotionLayoutAnchorTarget anchorTarget = (MotionLayoutAnchorTarget)target;
    return ConstraintComponentUtilities.getAttribute(myType, anchorTarget.myType, useRtlAttributes(), isRtl());
  }

  /**
   * Revert to the original (on mouse down) state.
   */
  private void revertToPreviousState() {
    NlComponent component = myComponent.getAuthoritativeNlComponent();
    ComponentModification modification = new ComponentModification(component, "Revert");
    MotionUtils.fillComponentModification(modification);
    modification.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF, null);
    for (String key : mPreviousAttributes.keySet()) {
      if (key.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X) ||
          key.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y)) {
        modification.setAttribute(SdkConstants.TOOLS_URI, key, mPreviousAttributes.get(key));
      }
      else if (key.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_MARGIN_TOP)
               || key.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM)
               || key.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_MARGIN_LEFT)
               || key.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT)
               || key.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_MARGIN_START)
               || key.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_MARGIN_END)) {
        modification.setAttribute(SdkConstants.ANDROID_URI, key, mPreviousAttributes.get(key));
      }
      else {
        modification.setAttribute(SdkConstants.SHERPA_URI, key, mPreviousAttributes.get(key));
      }
    }
    MotionUtils.apply(modification);
    myComponent.getScene().markNeedsLayout(Scene.ANIMATED_LAYOUT);
  }

  /**
   * Connect the anchor to the given target. Applied immediately in memory.
   */
  private ComponentModification connectMe(NlComponent component, String attribute, NlComponent targetComponent) {
    ComponentModification modification = new ComponentModification(component, "Connect Constraint");
    MotionUtils.fillComponentModification(modification);

    String targetId;
    NlComponent parent = component.getParent();
    assert parent != null;
    if (NlComponentHelperKt.isOrHasSuperclass(parent, AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_CONSTRAINTS)) {
      parent = parent.getParent();
    }
    if (targetComponent == parent) {
      targetId = SdkConstants.ATTR_PARENT;
    }
    else {
      targetId = SdkConstants.NEW_ID_PREFIX + NlComponentHelperKt.ensureLiveId(targetComponent);
    }
    modification.setAttribute(SdkConstants.SHERPA_URI, attribute, targetId);
    if (myType == Type.BASELINE) {
      ConstraintComponentUtilities.clearAttributes(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourTopAttributes, modification);
      ConstraintComponentUtilities.clearAttributes(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourBottomAttributes, modification);
      modification.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, null);
      modification.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_TOP, null);
      modification.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM, null);
    }
    else if (ConstraintComponentUtilities.ourReciprocalAttributes.get(attribute) != null) {
      modification.setAttribute(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourReciprocalAttributes.get(attribute), null);
    }

    // If the other side is already connected to the same component, the user is trying to
    // center this component to targetComponent, so we remove the margin.
    String otherSideAttr = ConstraintComponentUtilities.ourOtherSideAttributes.get(attribute);
    String otherSideAttrValue = otherSideAttr != null ? modification.getAttribute(SdkConstants.SHERPA_URI, otherSideAttr) : null;
    if (isOppositeSideConnectedToSameTarget(targetId, otherSideAttrValue)) {
      removeOppositeSideMargin(modification, otherSideAttr);
    }
    else if (ConstraintComponentUtilities.ourMapMarginAttributes.get(attribute) != null) {
      Scene scene = myComponent.getScene();
      int marginValue = getDistance(attribute, targetComponent, scene);
      if (!scene.isCtrlMetaDown()) {
        if (marginValue < 0) {
          marginValue = 0;
        }
        else {
          marginValue = Scout.getMargin();
        }
      }
      else {
        marginValue = Math.max(marginValue, 0);
      }
      String margin;
      if (Scout.getMarginResource() == null) {
        margin = String.format(Locale.US, SdkConstants.VALUE_N_DP, marginValue);
      } else {
        margin = Scout.getMarginResource();
      }
      String attr = ConstraintComponentUtilities.ourMapMarginAttributes.get(attribute);
      modification.setAttribute(SdkConstants.ANDROID_URI, attr, margin);
      if (SdkConstants.ATTR_LAYOUT_MARGIN_END.equals(attr)) {
        modification.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, margin);
      }
      else if (SdkConstants.ATTR_LAYOUT_MARGIN_START.equals(attr)) {
        modification.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, margin);
      }
      scene.needsRebuildList();
      myConnectedX = myLastX;
      myConnectedY = myLastY;
    }
    ConstraintComponentUtilities.cleanup(modification, myComponent.getNlComponent());
    MotionUtils.apply(modification);
    myComponent.getScene().markNeedsLayout(Scene.ANIMATED_LAYOUT);
    myRenderingTemporaryConnection = true;
    return modification;
  }

  private void removeOppositeSideMargin(@NotNull NlAttributesHolder attributes, @NotNull String otherSideAttr) {
    String otherSideMargin = ConstraintComponentUtilities.ourMapMarginAttributes.get(otherSideAttr);
    if (otherSideMargin == null) {
      return;
    }
    String alternateAttr;

    boolean rtl = isRtl();
    switch (otherSideMargin) {
      case SdkConstants.ATTR_LAYOUT_MARGIN_LEFT:
        alternateAttr = rtl ? SdkConstants.ATTR_LAYOUT_MARGIN_END : SdkConstants.ATTR_LAYOUT_MARGIN_START;
        break;
      case SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT:
        alternateAttr = rtl ? SdkConstants.ATTR_LAYOUT_MARGIN_START : SdkConstants.ATTR_LAYOUT_MARGIN_END;
        break;
      case SdkConstants.ATTR_LAYOUT_MARGIN_START:
        alternateAttr = rtl ? SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT : SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
        break;
      case SdkConstants.ATTR_LAYOUT_MARGIN_END:
        alternateAttr = rtl ? SdkConstants.ATTR_LAYOUT_MARGIN_LEFT : SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
        break;
      default:
        alternateAttr = null;
    }

    attributes.setAttribute(SdkConstants.ANDROID_URI, otherSideMargin, null);
    if (alternateAttr != null) {
      attributes.setAttribute(SdkConstants.ANDROID_URI, alternateAttr, null);
    }
  }

  private static boolean isOppositeSideConnectedToSameTarget(@NotNull String targetId, @Nullable String otherSideAttrValue) {
    return otherSideAttrValue != null && stripPrefixFromId(otherSideAttrValue).equals(stripPrefixFromId(targetId));
  }

  /**
   * Given a NlComponent and an attribute, return the corresponding ConstraintAnchorTarget
   */
  public static MotionLayoutAnchorTarget getTargetAnchor(Scene scene,
                                                       NlComponent targetComponent,
                                                       String attribute,
                                                       boolean supportsRtl,
                                                       boolean isInRtl) {
    SceneComponent component = scene.getSceneComponent(targetComponent);
    if (component == null) {
      return null;
    }
    if (supportsRtl) {
      if (isInRtl) {
        return getAnchorTarget(component, ConstraintComponentUtilities.ourRTLMapSideToTargetAnchors.get(attribute));
      }
      else {
        return getAnchorTarget(component, ConstraintComponentUtilities.ourLTRMapSideToTargetAnchors.get(attribute));
      }
    }
    return getAnchorTarget(component, ConstraintComponentUtilities.ourLTRMapSideToTargetAnchors.get(attribute));
  }

  public static MotionLayoutAnchorTarget getAnchorTarget(@NotNull SceneComponent component, @NotNull AnchorTarget.Type type) {
    for (Target target : component.getTargets()) {
      if (target instanceof MotionLayoutAnchorTarget) {
        if (((MotionLayoutAnchorTarget)target).getType() == type) {
          return (MotionLayoutAnchorTarget)target;
        }
      }
    }
    return null;
  }

  private int getDistance(String attribute, NlComponent targetComponent, Scene scene) {
    int marginValue;
    MotionLayoutAnchorTarget
      targetAnchor = getTargetAnchor(scene, targetComponent, attribute, useRtlAttributes(), isRtl());
    if (targetAnchor == null) {
      return 0;
    }
    switch (myType) {
      case LEFT: {
        switch (targetAnchor.getType()) {
          case LEFT:
          case RIGHT: {
            marginValue = (int)(getCenterX() - targetAnchor.getCenterX());
          }
          break;
          default:
            marginValue = 0;
        }
      }
      break;
      case RIGHT: {
        switch (targetAnchor.getType()) {
          case LEFT:
          case RIGHT: {
            marginValue = (int)(targetAnchor.getCenterX() - getCenterX());
          }
          break;
          default:
            marginValue = 0;
        }
      }
      break;
      case TOP: {
        switch (targetAnchor.getType()) {
          case TOP:
          case BOTTOM: {
            marginValue = (int)(getCenterY() - targetAnchor.getCenterY());
          }
          break;
          default:
            marginValue = 0;
        }
      }
      break;
      case BOTTOM: {
        switch (targetAnchor.getType()) {
          case TOP:
          case BOTTOM: {
            marginValue = (int)(targetAnchor.getCenterY() - getCenterY());
          }
          break;
          default:
            marginValue = 0;
        }
      }
      break;
      default:
        marginValue = 0;
    }
    return marginValue;
  }

  /**
   * Disconnect the anchor
   *
   * @param component
   */
  private void disconnectMe(NlComponent component) {
    ComponentModification modification = new ComponentModification(component, "Constraint Disconnected");
    MotionUtils.fillComponentModification(modification);
    clearMe(modification);
    ConstraintComponentUtilities.cleanup(modification, myComponent.getNlComponent());
    MotionUtils.commit(modification);
    myComponent.getScene().markNeedsLayout(Scene.ANIMATED_LAYOUT);
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Mouse Handling
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public int getPreferenceLevel() {
    return Target.ANCHOR_LEVEL;
  }

  @Override
  public void mouseDown(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    super.mouseDown(x, y);

    Scene scene = myComponent.getScene();
    if (isHorizontalAnchor()) {
      scene.setFilterType(Scene.FilterType.HORIZONTAL_ANCHOR);
    }
    else {
      scene.setFilterType(Scene.FilterType.VERTICAL_ANCHOR);
    }
    if (getType() == Type.BASELINE) {
      scene.setFilterType(Scene.FilterType.BASELINE_ANCHOR);
    }

    myConnectedX = -1;
    myConnectedY = -1;

    mPreviousAttributes.clear();

    NlComponent component = myComponent.getNlComponent();
    NlComponent motionLayoutComponent = MotionUtils.getMotionLayoutAncestor(component);
    if (motionLayoutComponent == null) {
      fillInPreviousAttributesFromNlComponent();
    } else {
      MotionLayoutComponentHelper motionLayout = MotionLayoutComponentHelper.create(motionLayoutComponent);
      String state = motionLayout.getState();
      if (state != null) {
        fillInPreviousAttributes();
      }
      else {
        fillInPreviousAttributesFromNlComponent();
      }
    }
  }

  /**
   * Store the existing attributes in mPreviousAttributes
   */
  private void rememberPreviousAttribute(HashMap<String, MotionAttributes.DefinedAttribute> definedAttributes, @NotNull String uri, @NotNull ArrayList<String> attributes) {
    for (String attribute : attributes) {
      saveAttribute(definedAttributes, attribute);
    }
  }

  private void saveAttribute(HashMap<String, MotionAttributes.DefinedAttribute> definedAttributes, String key) {
    MotionAttributes.DefinedAttribute attribute = definedAttributes.get(key);
    if (attribute != null) {
      mPreviousAttributes.put(key, attribute.getValue());
    }
  }

  private void fillInPreviousAttributes() {
    Object properties = myComponent.getNlComponent().getClientProperty(MOTION_LAYOUT_PROPERTIES);
    if (properties != null && properties instanceof MotionAttributes) {
      MotionAttributes attrs = (MotionAttributes)properties;
      HashMap<String, MotionAttributes.DefinedAttribute> definedAttributes = attrs.getAttrMap();

      saveAttribute(definedAttributes, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X);
      saveAttribute(definedAttributes, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y);
      saveAttribute(definedAttributes, SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF);

      //noinspection EnumSwitchStatementWhichMissesCases
      switch (myType) {
        case LEFT:
        case RIGHT: {
          rememberPreviousAttribute(definedAttributes, SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourLeftAttributes);
          rememberPreviousAttribute(definedAttributes, SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourRightAttributes);
          rememberPreviousAttribute(definedAttributes, SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourStartAttributes);
          rememberPreviousAttribute(definedAttributes, SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourEndAttributes);
          saveAttribute(definedAttributes, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT);
          saveAttribute(definedAttributes, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
          saveAttribute(definedAttributes, SdkConstants.ATTR_LAYOUT_MARGIN_START);
          saveAttribute(definedAttributes, SdkConstants.ATTR_LAYOUT_MARGIN_END);
          saveAttribute(definedAttributes, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS);
        }
        break;
        case TOP: {
          rememberPreviousAttribute(definedAttributes, SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourTopAttributes);
          saveAttribute(definedAttributes, SdkConstants.ATTR_LAYOUT_MARGIN_TOP);
          saveAttribute(definedAttributes, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS);
        }
        break;
        case BOTTOM: {
          rememberPreviousAttribute(definedAttributes, SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourBottomAttributes);
          saveAttribute(definedAttributes, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM);
          saveAttribute(definedAttributes, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS);
        }
        break;
        case BASELINE: {
          rememberPreviousAttribute(definedAttributes, SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourTopAttributes);
          rememberPreviousAttribute(definedAttributes, SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourBottomAttributes);
          saveAttribute(definedAttributes, SdkConstants.ATTR_LAYOUT_MARGIN_TOP);
          saveAttribute(definedAttributes, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM);
          saveAttribute(definedAttributes, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS);
        }
        break;
      }
    }
  }

  private void fillInPreviousAttributesFromNlComponent() {
    NlComponent component = myComponent.getAuthoritativeNlComponent();
    mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X,
                            component.getLiveAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X));
    mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y,
                            component.getLiveAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y));
    mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF,
                            component.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF));
    //noinspection EnumSwitchStatementWhichMissesCases
    switch (myType) {
      case LEFT:
      case RIGHT: {
        rememberPreviousAttributeFromNlComponent(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourLeftAttributes);
        rememberPreviousAttributeFromNlComponent(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourRightAttributes);
        rememberPreviousAttributeFromNlComponent(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourStartAttributes);
        rememberPreviousAttributeFromNlComponent(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourEndAttributes);
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
                                component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT));
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
                                component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT));
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_MARGIN_START,
                                component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_START));
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_MARGIN_END,
                                component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_END));
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS,
                                component.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS));
      }
      break;
      case TOP: {
        rememberPreviousAttributeFromNlComponent(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourTopAttributes);
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
                                component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_TOP));
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS,
                                component.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS));
      }
      break;
      case BOTTOM: {
        rememberPreviousAttributeFromNlComponent(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourBottomAttributes);
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM,
                                component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM));
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS,
                                component.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS));
      }
      break;
      case BASELINE: {
        rememberPreviousAttributeFromNlComponent(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourTopAttributes);
        rememberPreviousAttributeFromNlComponent(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourBottomAttributes);
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
                                component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_TOP));
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM,
                                component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM));
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS,
                                component.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS));
      }
      break;
    }
  }

  /**
   * On mouse drag, we can connect (in memory) to existing targets, or revert to the
   * original state that we captured on mouseDown.
   */
  @Override
  public void mouseDrag(@AndroidDpCoordinate int x,
                        @AndroidDpCoordinate int y,
                        @NotNull List<Target> closestTargets,
                        @NotNull SceneContext sceneContext) {
    super.mouseDrag(x, y, closestTargets, sceneContext);

    MotionLayoutAnchorTarget targetAnchor = getClosestConnectibleTarget(closestTargets);

    if (!myIsDragging) {
      myIsDragging = true;
      DecoratorUtilities.setTryingToConnectState(myComponent.getNlComponent(), myType, true);
    }

    if (targetAnchor != null) {
      NlComponent component = myComponent.getAuthoritativeNlComponent();
      String attribute = getAttribute(targetAnchor);
      if (attribute != null) {
        if (targetAnchor.myComponent != myComponent && !targetAnchor.isConnected(this)) {
          if (myComponent.getParent() != targetAnchor.myComponent) {
            Integer state = targetAnchor.myType.getMask();
            if (state == null) {
              return;
            }
            int mask = state & targetAnchor.myType.getMask();
            if (mask == 0) {
              return;
            }
          }

          NlComponent targetComponent = targetAnchor.myComponent.getAuthoritativeNlComponent();
          connectMe(component, attribute, targetComponent);
          return;
        }
      }
    }
    if (myRenderingTemporaryConnection) {
      revertToPreviousState();
      myRenderingTemporaryConnection = false;
      return;
    }

    myComponent.getScene().needsRebuildList();
  }

  @Nullable
  private MotionLayoutAnchorTarget getClosestConnectibleTarget(@NotNull List<Target> closestTargets) {
    MotionLayoutAnchorTarget closest = null;
    for (Target target : closestTargets) {
      if (target == this || !(target instanceof MotionLayoutAnchorTarget)) {
        continue;
      }
      MotionLayoutAnchorTarget anchorTarget = (MotionLayoutAnchorTarget) target;
      if (isConnectible(anchorTarget)) {
        if (closest == null || closest.getComponent().getDepth() < anchorTarget.getComponent().getDepth()) {
          closest = anchorTarget;
        }
      }
    }
    return closest;
  }

  /**
   * On mouseRelease, we can either disconnect the current anchor (if the mouse release is on ourselves)
   * or connect the anchor to a given target. Modifications are applied first in memory then committed
   * to the XML model.
   */
  @Override
  public void mouseRelease(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @NotNull List<Target> closestTargets) {
    super.mouseRelease(x, y, closestTargets);

    try {
      MotionLayoutAnchorTarget closestTarget;
      if (myComponent.getScene().isCtrlMetaDown() && closestTargets.contains(this)) {
        closestTarget = this;
      }
      else {
        closestTarget = getClosestConnectibleTarget(closestTargets);
      }
      if (closestTarget != null && !closestTarget.isConnected(this)) {
        NlComponent component = myComponent.getAuthoritativeNlComponent();
        if (closestTarget == this) {
          if (canDisconnect()) {
            disconnectMe(component);
          }
        }
        else {
          String attribute = getAttribute(closestTarget);
          if (attribute != null) {
            if (closestTarget.myComponent == myComponent) {
              return;
            }
            if (myComponent.getParent() != closestTarget.myComponent) {
              Integer state = closestTarget.myType.getMask();
              if (state == null) {
                return;
              }
              int mask = state & closestTarget.myType.getMask();
              if (mask == 0) {
                return;
              }
            }
            NlComponent targetComponent = closestTarget.myComponent.getAuthoritativeNlComponent();
            ComponentModification modification = connectMe(component, attribute, targetComponent);
            MotionUtils.commit(modification);
            myComponent.getScene().markNeedsLayout(Scene.ANIMATED_LAYOUT);
          }
        }
      }
      else {
        Collection<SceneComponent> components = myComponent.getScene().getSceneComponents();
        Rectangle rectangle = new Rectangle();
        ArrayList<NlComponent> list = new ArrayList<>();
        list.add(myComponent.getAuthoritativeNlComponent());
        list.add(myComponent.getAuthoritativeNlComponent());
        ArrayList<SceneComponent> allItems = new ArrayList<>();
        for (SceneComponent component : components) {
          rectangle.width = component.getDrawWidth();
          rectangle.height = component.getDrawHeight();
          rectangle.x = component.getDrawX();
          rectangle.y = component.getDrawY();
          if (rectangle.contains(x, y) && SceneComponentHelperKt.isSibling(myComponent, component)) {
            allItems.add(component);
          }
        }
        if (!allItems.isEmpty()) {
          Type typeToConnect = myType;
          JBPopupMenu menu = new JBPopupMenu("Connect to:");
          if (isRtl()) {
            if (typeToConnect == Type.LEFT) {
              typeToConnect = Type.RIGHT;
            }
            else if (typeToConnect == Type.RIGHT) {
              typeToConnect = Type.LEFT;
            }
          }
          for (SceneComponent component : allItems) {
            list.set(1, component.getAuthoritativeNlComponent());
            switch (typeToConnect) {
              case LEFT:
                addConnectMenu(list, allItems, component, menu, Scout.Connect.ConnectStartToStart, "Start ", " start", CONSTRAIN_START_TO_START);
                addConnectMenu(list, allItems, component, menu, Scout.Connect.ConnectStartToEnd, "Start ", " end", CONSTRAIN_START_TO_END);
                break;
              case RIGHT:
                addConnectMenu(list, allItems, component, menu, Scout.Connect.ConnectEndToStart, "End ", " start", CONSTRAIN_END_TO_START);
                addConnectMenu(list, allItems, component, menu, Scout.Connect.ConnectEndToEnd, "End ", " end", CONSTRAIN_END_TO_END);
                break;
              case TOP:
                addConnectMenu(list, allItems, component, menu, Scout.Connect.ConnectTopToTop, "Top ", " top", CONSTRAIN_TOP_TO_TOP);
                addConnectMenu(list, allItems, component, menu, Scout.Connect.ConnectTopToBottom, "Top ", " bottom", CONSTRAIN_TOP_TO_BOTTOM);
                break;
              case BOTTOM:
                addConnectMenu(list, allItems, component, menu, Scout.Connect.ConnectBottomToTop, "Bottom ", " top", CONSTRAIN_BOTTOM_TO_TOP);
                addConnectMenu(list, allItems, component, menu, Scout.Connect.ConnectBottomToBottom, "Bottom ", " bottom",
                               CONSTRAIN_BOTTOM_TO_BOTTOM);
                break;
              case BASELINE:
                addConnectMenu(list, allItems, component, menu, Scout.Connect.ConnectBaseLineToBaseLine, "Baseline ", " baseline",
                               BASELINE_ALIGNED_CONSTRAINT);
                break;
            }
          }

          SceneView view = myComponent.getScene().getSceneManager().getSceneView();
          int swingX = Coordinates.getSwingXDip(view, x);
          int swingY = Coordinates.getSwingYDip(view, y);

          // Finish previous dragging setup.
          myIsDragging = false;
          DecoratorUtilities.setTryingToConnectState(myComponent.getAuthoritativeNlComponent(), myType, false);

          List<NlComponent> allItemsNlComponents =
            allItems.stream().map(item -> item.getAuthoritativeNlComponent()).collect(Collectors.toCollection(ArrayList::new));
          menu.addPopupMenuListener(new PopupMenuListenerAdapter() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
              super.popupMenuWillBecomeVisible(e);
              DecoratorUtilities.setTryingToConnectState(myComponent.getAuthoritativeNlComponent(), allItemsNlComponents, myType, true);
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
              super.popupMenuWillBecomeInvisible(e);
              DecoratorUtilities.setTryingToConnectState(myComponent.getAuthoritativeNlComponent(), allItemsNlComponents, myType, false);
              myComponent.getScene().setFilterType(Scene.FilterType.NONE);
            }
          });
          if (menu.getComponentCount() > 0) {
            menu.show(myComponent.getScene().getDesignSurface().getPreferredFocusedComponent(), swingX, swingY);
          }
        }
      }
    }
    finally {
      if (myIsDragging) {
        myIsDragging = false;
        DecoratorUtilities.setTryingToConnectState(myComponent.getNlComponent(), myType, false);
        myComponent.getScene().needsRebuildList();
      }
    }
  }

  @Override
  public void mouseCancel() {
    super.mouseCancel();
    DecoratorUtilities.setTryingToConnectState(myComponent.getNlComponent(), myType, false);
    revertToPreviousState();
  }

  @Override
  public void addHit(@NotNull SceneContext transform,
                     @NotNull ScenePicker picker,
                     @JdkConstants.InputEventMask int modifiersEx) {
    if (!myIsEdge) {
      // This anchor is not the edge of root ConstraintLayout. Consider as normal size.
      super.addHit(transform, picker, modifiersEx);
      return;
    }

    // This anchor belongs to the root ConstraintLayout, the hittable area should be extended.
    int x1 = 0;
    int x2 = 0;
    int y1 = 0;
    int y2 = 0;

    int centerX = transform.getSwingXDip(getCenterX());
    int centerY = transform.getSwingYDip(getCenterY());
    int componentWidth = transform.getSwingDimensionDip(myComponent.getDrawWidth());
    int componentHeight = transform.getSwingDimensionDip(myComponent.getDrawHeight());

    Rectangle renderableArea = transform.getRenderableBounds();

    switch (myType) {
      case LEFT:
        // Extends the hittable area in the left side.
        x1 = Math.min(centerX - ANCHOR_SIZE, (int) renderableArea.getX());
        x2 = centerX + ANCHOR_SIZE;
        y1 = centerY - componentHeight / 2;
        y2 = centerY + componentHeight / 2;
        break;
      case RIGHT:
        // Extends the hittable area in the right side.
        x1 = centerX - ANCHOR_SIZE;
        x2 = Math.max(centerX + ANCHOR_SIZE, (int) (renderableArea.getX() + renderableArea.getWidth()));
        y1 = centerY - componentHeight / 2;
        y2 = centerY + componentHeight / 2;
        break;
      case TOP:
        // Extends the hittable area in the top side.
        x1 = centerX - componentWidth / 2;
        x2 = centerX + componentWidth / 2;
        y1 = Math.min(centerY - ANCHOR_SIZE, (int) renderableArea.getY());
        y2 = centerY + ANCHOR_SIZE;
        break;
      case BOTTOM:
        // Extends the hittable area in the bottom side.
        x1 = centerX - componentWidth / 2;
        x2 = centerX + componentWidth / 2;
        y1 = centerY - ANCHOR_SIZE;
        y2 = Math.max(centerY + ANCHOR_SIZE, (int) (renderableArea.getY() + renderableArea.getHeight()));
        break;
      case BASELINE:
        Logger.getInstance(getClass()).warn("The baseline anchor should be a edge anchor.");
        break;
    }

    picker.addRect(this, 0, x1, y1, x2, y2);
  }

  /** Append to the existing tooltip a hint to delete constraints. */
  @Override
  public String getToolTipText() {
    StringBuilder builder = new StringBuilder();
    builder.append(super.getToolTipText());
    if (isConnected()) {
      builder.append(" (")
        .append(AdtUiUtils.getActionKeyText())
        .append("+Click)");
    }
    return builder.toString();
  }

  /**
   * adds a connection to the connection menu list
   *
   * @param list
   * @param component
   * @param menu
   * @param type
   * @param from
   * @param to
   * @param icon
   */
  private void addConnectMenu(ArrayList<NlComponent> list,
                              List<SceneComponent> allItems,
                              SceneComponent component,
                              JBPopupMenu menu,
                              Scout.Connect type,
                              String from,
                              String to,
                              Icon icon) {
     menu.add(new ConnectMenu(allItems, myComponent, from, component, to, icon, type, isRtl()));
  }

  static class ConnectMenu extends JBMenuItem implements ActionListener, ChangeListener {
    SceneComponent mySrc;
    SceneComponent myDest;
    Scout.Connect myType;
    @Nullable AnchorTarget myDestTarget;

    List<SceneComponent> mAllItems;

    public ConnectMenu(List<SceneComponent> allItems,
                       SceneComponent src,
                       String from,
                       SceneComponent dest, String text, Icon icon, Scout.Connect type, boolean isRtl) {
      super(from + "to " + dest.getId() + text, icon);
      mAllItems = allItems;
      mySrc = src;
      myDest = dest;
      myType = type;
      for (Target target : myDest.getTargets()) {
        if (target instanceof AnchorTarget && ((AnchorTarget)target).getType() == myType.getDstAnchorType(isRtl)) {
          myDestTarget = (AnchorTarget)target;
        }
      }
      addActionListener(this);
      addChangeListener(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      List<NlComponent> list = Arrays.asList(mySrc.getAuthoritativeNlComponent(), myDest.getAuthoritativeNlComponent());
      Scout.connect(list, myType, false, true);
      NlDesignSurface designSurface = (NlDesignSurface)mySrc.getScene().getDesignSurface();
      designSurface.forceLayersPaint(true);
    }


    @Override
    public void stateChanged(ChangeEvent e) {
      for (SceneComponent item : mAllItems) {
        item.setDrawState(SceneComponent.DrawState.NORMAL);
      }
      if (myDestTarget != null) {
        myDestTarget.setMouseHovered(isSelected() || isArmed());
      }
      myDest.setDrawState(isSelected() || isArmed() ? SceneComponent.DrawState.HOVER : SceneComponent.DrawState.NORMAL);
      myDest.getScene().needsRebuildList();
      myDest.getScene().repaint();
    }
  }
  //endregion
  /////////////////////////////////////////////////////////////////////////////
}
