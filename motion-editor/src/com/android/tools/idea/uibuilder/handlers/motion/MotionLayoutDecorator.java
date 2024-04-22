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
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.decorator.SceneDecorator;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.target.AnchorTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintUtilities;
import com.android.tools.idea.uibuilder.handlers.constraint.SecondarySelector;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawAnimatedFrame;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawConnection;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneUtils;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionAttributes;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scene.decorator.DecoratorUtilities;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * This defines the decorator for MotionLayout
 */
public class MotionLayoutDecorator extends SceneDecorator {
  public static final String CONSTRAINT_HOVER = "CONSTRAINT_HOVER";
  private static final boolean ourBlockSelection = false;
  private final static String[] LEFT_DIR = {
    SdkConstants.ATTR_LAYOUT_START_TO_START_OF, SdkConstants.ATTR_LAYOUT_START_TO_END_OF,
    SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF, SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF,
  };
  private final static String[] RIGHT_DIR = {
    SdkConstants.ATTR_LAYOUT_END_TO_END_OF, SdkConstants.ATTR_LAYOUT_END_TO_START_OF,
    SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
  };
  private final static String[] TOP_DIR = {
    SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF
  };
  private final static String[] BOTTOM_DIR = {
    SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF
  };
  private final static String[] LEFT_DIR_RTL = {
    SdkConstants.ATTR_LAYOUT_END_TO_END_OF, SdkConstants.ATTR_LAYOUT_END_TO_START_OF,
    SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF, SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF,
  };
  private final static String[] RIGHT_DIR_RTL = {
    SdkConstants.ATTR_LAYOUT_START_TO_START_OF, SdkConstants.ATTR_LAYOUT_START_TO_END_OF,
    SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
  };

  private final static String[][] ourConnections = {LEFT_DIR, RIGHT_DIR, TOP_DIR, BOTTOM_DIR};
  private final static String[][] ourConnections_rtl = {LEFT_DIR_RTL, RIGHT_DIR_RTL, TOP_DIR, BOTTOM_DIR};

  private final static String BASELINE = "BASELINE";
  private final static String[] BASELINE_DIR = new String[]{SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF};
  private final static String BASELINE_TYPE = "BASELINE_TYPE";

  private final static String[][] MARGIN_ATTR_LTR = {
    {SdkConstants.ATTR_LAYOUT_MARGIN_START, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT},
    {SdkConstants.ATTR_LAYOUT_MARGIN_END, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT},
    {SdkConstants.ATTR_LAYOUT_MARGIN_TOP},
    {SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM},
  };
  private final static String[][] MARGIN_ATTR_RTL = {
    {SdkConstants.ATTR_LAYOUT_MARGIN_END, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT},
    {SdkConstants.ATTR_LAYOUT_MARGIN_START, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT},
    {SdkConstants.ATTR_LAYOUT_MARGIN_TOP},
    {SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM},
  };
  private final static String[] BIAS_ATTR = {
    SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS,
    SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS,
    SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS,
    SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS
  };
  private final static boolean[] FLIP_BIAS = {
    true, false, false, true,
  };

  enum ConnectionType {
    SAME, BACKWARD
  }

  private final static ConnectionType[] DIR_TABLE =
    {ConnectionType.SAME, ConnectionType.BACKWARD, ConnectionType.SAME, ConnectionType.BACKWARD};
  private final static String[] ourDirections = {"LEFT", "RIGHT", "TOP", "BOTTOM"}; // order matches SecondarySelector.Constraint.ordinal()
  private final static String[] ourChainDirections = {"CHAIN_LEFT", "CHAIN_RIGHT", "CHAIN_TOP", "CHAIN_BOTTOM"}; // order matches
  private final static String[] ourDirectionsType = {"LEFT_TYPE", "RIGHT_TYPE", "TOP_TYPE", "BOTTOM_TYPE"}; // order matches
  private final static AnchorTarget.Type[] ourAnchorTypes =
    {AnchorTarget.Type.LEFT, AnchorTarget.Type.RIGHT, AnchorTarget.Type.TOP, AnchorTarget.Type.BOTTOM}; // order matches
  private final static boolean[] isLeftRight = {true, true, false, false}; // order matches
  private final static int[] ourOppositeDirection = {1, 0, 3, 2}; // order matches
  private final static int DRAWPATH_SIZE = 200;
  private final static int MAX_KEY_POSITIONS = 101; // 0-100 inclusive key positions are allowed
  private float[] mPathBuffer = new float[DRAWPATH_SIZE];
  private int[] keyFrameTypes = new int[MAX_KEY_POSITIONS];
  private float[] keyFramePos = new float[MAX_KEY_POSITIONS * 2];

  private static void convert(@NotNull SceneContext sceneContext, Rectangle rect) {
    rect.x = sceneContext.getSwingXDip(rect.x);
    rect.y = sceneContext.getSwingYDip(rect.y);
    rect.width = sceneContext.getSwingDimensionDip(rect.width);
    rect.height = sceneContext.getSwingDimensionDip(rect.height);
  }

  private static void gatherProperties(@NotNull SceneComponent component,
                                       @NotNull SceneComponent child) {
    boolean rtl = component.getScene().isInRTL();
    String[][] connections = ((rtl) ? ourConnections_rtl : ourConnections);

    for (int i = 0; i < ourDirections.length; i++) {
      getConnection(component, child, connections[i], ourDirections[i], ourDirectionsType[i]);
    }
    getConnection(component, child, BASELINE_DIR, BASELINE, BASELINE_TYPE);
  }

  /*
  @Nullable
  public static XmlFile getTransitionFile(@NotNull NlComponent component) {
    // get the parent if need be
    if (!NlComponentHelperKt.isOrHasSuperclass(component, SdkConstants.MOTION_LAYOUT)) {
      component = component.getParent();
      if (component == null || !NlComponentHelperKt.isOrHasSuperclass(component, SdkConstants.MOTION_LAYOUT)) {
        return null;
      }
    }
    String file = component.getAttribute(SdkConstants.AUTO_URI, "layoutDescription");
    if (file == null) {
      return null;
    }
    int index = file.lastIndexOf("@xml/");
    String fileName = file.substring(index + 5);
    if (fileName == null || fileName.isEmpty()) {
      return null;
    }
    Project project = component.getModel().getProject();
    AndroidFacet facet = component.getModel().getFacet();
    List<VirtualFile> resourcesXML = AndroidResourcesIdeUtil.getResourceSubdirs(ResourceFolderType.XML, ResourceRepositoryManager
      .getModuleResources(facet).getResourceDirs());
    if (resourcesXML.isEmpty()) {
      return null;
    }
    VirtualFile directory = resourcesXML.get(0);
    VirtualFile virtualFile = directory.findFileByRelativePath(fileName + ".xml");

    return (XmlFile)AndroidPsiUtils.getPsiFileSafely(project, virtualFile);
  }

  @Nullable
  public static XmlTag getConstraintSet(XmlFile file, String constraintSetId) {
    XmlTag[] children = file.getRootTag().findSubTags("ConstraintSet");
    for (int i = 0; i < children.length; i++) {
      XmlAttribute attribute = children[i].getAttribute("android:id");
      if (attribute != null) {
        String childId = stripID(attribute.getValue());
        if (childId.equalsIgnoreCase(constraintSetId)) {
          return children[i];
        }
      }
    }
    return null;
  }

  @Nullable
  public static XmlTag getConstrainView(XmlTag constraintSet, String id) {
    XmlTag[] children = constraintSet.getSubTags();
    for (int i = 0; i < children.length; i++) {
      XmlAttribute attribute = children[i].getAttribute("android:id");
      if (attribute != null) {
        String value = attribute.getValue();
        int index = value.lastIndexOf("id/");
        value = value.substring(index + 3);
        if (value != null && value.equalsIgnoreCase(id)) {
          return children[i];
        }
      }
    }
    return null;
  }
  */

  /**
   * This caches connections on each child SceneComponent by accessing NLcomponent attributes
   *
   * @param component
   * @param child
   * @param attributes
   * @param dir
   * @param dirType
   */
  private static void getMargins(SceneComponent component, SceneComponent child, String[][] marginNames, String[] marginValues)  {
    MotionLayoutComponentHelper motionLayout = MotionLayoutComponentHelper.create(component.getNlComponent());

    if (motionLayout.isInTransition()) {
      return;
    }

    String id = null;
    ConnectionType type = ConnectionType.SAME;

    if (!MotionUtils.isInBaseState(motionLayout)) {
      Object properties = child.getNlComponent().getClientProperty(MotionSceneUtils.MOTION_LAYOUT_PROPERTIES);
      if (properties instanceof MotionAttributes) {
        MotionAttributes attrs = (MotionAttributes)properties;
        HashMap<String, MotionAttributes.DefinedAttribute> a = attrs.getAttrMap();
        for (int i = 0; i < marginNames.length; i++) {
          String name = null;
          MotionAttributes.DefinedAttribute attribute;
          attribute = a.get(marginNames[i][0]);
          if (attribute == null && marginNames[i].length==2) {
              attribute = a.get(marginNames[i][1]);
            }
          if (attribute != null) {
            marginValues[i]  = attribute.getValue();
          } else  {
            marginValues[i] = null;
          }

        }
      }
    }
    else {
      for (int i = 0; i < marginNames.length; i++) {
          marginValues[i] = child.getAuthoritativeNlComponent().getLiveAttribute(SdkConstants.SHERPA_URI, marginNames[i][0]);
        if (marginValues[i] == null && marginNames[i].length==2) {
          marginValues[i] = child.getAuthoritativeNlComponent().getLiveAttribute(SdkConstants.SHERPA_URI, marginNames[i][1]);
        }

      }
    }

  }


  /**
   * This caches connections on each child SceneComponent by accessing NLcomponent attributes
   *
   * @param component
   * @param child
   * @param attributes
   * @param dir
   * @param dirType
   */
  private static void getConnection(SceneComponent component, SceneComponent child, String[] attributes, String dir, String dirType) {
    MotionLayoutComponentHelper motionLayout = MotionLayoutComponentHelper.create(component.getNlComponent());

    if (motionLayout.isInTransition()) {
      child.myCache.clear();
      return;
    }

    String id = null;
    ConnectionType type = ConnectionType.SAME;

    if (!MotionUtils.isInBaseState(motionLayout)) {
      Object properties = child.getNlComponent().getClientProperty(MotionSceneUtils.MOTION_LAYOUT_PROPERTIES);
      if (properties != null && properties instanceof MotionAttributes) {
        MotionAttributes attrs = (MotionAttributes)properties;
        HashMap<String, MotionAttributes.DefinedAttribute> a = attrs.getAttrMap();
        for (int i = 0; i < attributes.length; i++) {
          MotionAttributes.DefinedAttribute attribute = a.get(attributes[i]);
          if (attribute != null) {
            id = attribute.getValue();
          }
          else {
            id = null;
          }
          type = DIR_TABLE[i];
          if (id != null) {
            break;
          }
        }
      }
    }
    else {
      for (int i = 0; i < attributes.length; i++) {
        id = child.getAuthoritativeNlComponent().getLiveAttribute(SdkConstants.SHERPA_URI, attributes[i]);
        type = DIR_TABLE[i];
        if (id != null) {
          break;
        }
      }
    }

    if (id == null) {
      child.myCache.put(dir, id);
      child.myCache.put(dirType, ConnectionType.SAME);
      return;
    }
    if (id.equalsIgnoreCase(SdkConstants.ATTR_PARENT)) {
      child.myCache.put(dir, component);
      child.myCache.put(dirType, type);
      return;
    }
    String cleanId = NlComponent.extractId(id);
    if (cleanId == null) {
      child.myCache.put(dir, id);
      child.myCache.put(dirType, ConnectionType.SAME);
      return;
    }
    if (cleanId.equals(component.getId())) {
      child.myCache.put(dir, component);
      child.myCache.put(dirType, type);
      return;
    }
    for (SceneComponent con : component.getChildren()) {
      if (cleanId.equals(con.getId())) {
        child.myCache.put(dir, con);
        child.myCache.put(dirType, type);
        return;
      }
    }
    child.myCache.put(dirType, ConnectionType.SAME);
  }

  /**
   * This is responsible for setting the clip and building the list for this component's children
   *
   * @param list
   * @param time
   * @param sceneContext
   * @param component
   */
  @Override
  protected void buildListChildren(@NotNull DisplayList list,
                                   long time,
                                   @NotNull SceneContext sceneContext,
                                   @NotNull SceneComponent component) {
    List<SceneComponent> children = component.getChildren();
    buildListPaths(sceneContext, component, list);
    if (!children.isEmpty()) {
      // Cache connections between children
      for (SceneComponent child : component.getChildren()) {
        gatherProperties(component, child);
      }
      Rectangle rect = new Rectangle();
      component.fillRect(rect);
      list.pushClip(sceneContext, rect);
      Scene scene = component.getScene();

      boolean showAllConstraints = ConstraintLayoutHandler.getVisualProperty(ConstraintLayoutHandler.SHOW_CONSTRAINTS_PREF_KEY);
      List<NlComponent> selection = scene.getSelection();
      for (SceneComponent child : children) {
        child.buildDisplayList(time, list, sceneContext);
        if (sceneContext.showOnlySelection() && !child.isSelected()) {
          continue;
        }
        if ((showAllConstraints && scene.getRoot() == component) || selection.contains(child.getNlComponent())) {
          buildListConnections(list, time, sceneContext, component, child); // draw child connections
        }
      }
      list.popClip();
    }
  }

  private void buildListPaths(SceneContext sceneContext,
                              @NotNull SceneComponent component,
                              @NotNull DisplayList list) {
    MotionLayoutComponentHelper helper = MotionLayoutComponentHelper.create(component.getNlComponent());

    if (helper.isInTransition() && helper.getShowPaths()) {
      List<SceneComponent> children = component.getChildren();
      int size = mPathBuffer.length / 2;
      float x = component.getDrawX();
      float y = component.getDrawY();
      float w = component.getDrawWidth();
      float h = component.getDrawHeight();
      float factor = sceneContext.getSwingDimensionDip(10000) / (float)sceneContext.getSwingDimension(10000);
      h *= factor;
      w *= factor;

      for (SceneComponent child : children) {
        int len = helper.getPath(child.getNlComponent(), mPathBuffer, size);
        if (len > 0) {
          float cbx = x + child.getDrawWidth() * factor / 2;
          float cby = y + child.getDrawHeight() * factor / 2;
          float cbw = w - child.getDrawWidth() * factor;
          float cbh = h - child.getDrawHeight() * factor;

          NlComponent childComponent = child.getNlComponent();
          Integer pos = (Integer)childComponent.getClientProperty(MotionLayoutSceneInteraction.MOTION_DRAG_KEYFRAME);
          Integer key_pos_type = (Integer)childComponent.getClientProperty(MotionLayoutSceneInteraction.MOTION_KEY_POS_TYPE);
          float[] key_pos_percent= (float[])childComponent.getClientProperty(MotionLayoutSceneInteraction.MOTION_KEY_POS_PERCENT);
          float percentX = (key_pos_percent == null)?Float.NaN:key_pos_percent[0];
          float percentY = (key_pos_percent == null)?Float.NaN:key_pos_percent[1];
          int selected_key = -1;
          if (pos != null) {
            selected_key = pos;
          }
          int keyFrameCount = helper.getKeyframePos(childComponent, keyFrameTypes, keyFramePos);
          DrawMotionPath.buildDisplayList(child.isSelected(), list, selected_key, (key_pos_type==null)?-1:key_pos_type , mPathBuffer,
                                          size * 2, keyFrameTypes, keyFramePos, keyFrameCount, x, y, w, h, cbx, cby, cbw, cbh,percentX,percentY);
        }
      }
    }
  }

  /**
   * This is used to extract the connection status from nlcomponents
   */
  static class ConnectionStatus {
    static String[] connectTypes = { // ordered to mach the order in ourDirections
      DecoratorUtilities.LEFT_CONNECTION,
      DecoratorUtilities.RIGHT_CONNECTION,
      DecoratorUtilities.TOP_CONNECTION,
      DecoratorUtilities.BOTTOM_CONNECTION,
      DecoratorUtilities.BASELINE_CONNECTION
    };
    public static final int DIRECTION_BASELINE = 4;

    int[] mPrevious = new int[connectTypes.length];
    int[] mCurrent = new int[connectTypes.length];
    long[] mStartTime = new long[connectTypes.length];

    DecoratorUtilities.ViewStates componentPrevState;
    DecoratorUtilities.ViewStates componentCurrentState;
    Long componentChangeStateTime;
    boolean mMyViewSelected = true;

    void getConnectionInfo(NlComponent c, boolean isSelected) {
      componentPrevState = DecoratorUtilities.getTimedChange_prev(c, DecoratorUtilities.VIEW);
      componentCurrentState = DecoratorUtilities.getTimedChange_value(c, DecoratorUtilities.VIEW);
      componentChangeStateTime = DecoratorUtilities.getTimedChange_time(c, "drawState");
      mMyViewSelected = isSelected;
      for (int i = 0; i < connectTypes.length; i++) {
        String type = connectTypes[i];
        DecoratorUtilities.ViewStates current, prev;
        if (componentCurrentState != DecoratorUtilities.ViewStates.SELECTED) { // selection fix
          // TODO we need to have a clear mechanism for selection event to propagate
          current = DecoratorUtilities.getTimedChange_value(c, type);
          if (current == DecoratorUtilities.ViewStates.SELECTED) { // we need to turn off
            long t = (componentChangeStateTime != null) ? componentChangeStateTime : System.nanoTime();
            DecoratorUtilities.setTimeChange(c, type, t, current, DecoratorUtilities.ViewStates.NORMAL);
          }
        }
        prev = DecoratorUtilities.getTimedChange_prev(c, type);

        current = DecoratorUtilities.getTimedChange_value(c, type);
        Long event = DecoratorUtilities.getTimedChange_time(c, type);
        if (event != null) {
          mStartTime[i] = event;
          mPrevious[i] = prev.getVal();
          mCurrent[i] = current.getVal();
        }
        else {
          mStartTime[i] = -1;
        }
      }
    }

    int getPreviousMode(int direction) {
      if (mStartTime[direction] == -1) {
        if (componentPrevState == DecoratorUtilities.ViewStates.NORMAL) {
          return DrawConnection.MODE_NORMAL;
        }
        return mCurrent[direction];
      }
      return mPrevious[direction];
    }

    /**
     * maps conditions to draw looks
     *
     * @param direction
     * @param thisConstraintSelected
     * @param fade
     * @param anyConstraintSelected
     * @param anyViewSelected
     * @param hoverConnection
     * @param onDelete
     * @return
     */
    int getCurrentMode(int direction,
                       boolean thisConstraintSelected,
                       boolean fade,
                       boolean anyConstraintSelected,
                       boolean anyViewSelected,
                       boolean hoverConnection,
                       boolean onDelete) {
      int ret = DrawConnection.MODE_NORMAL;
      int hoverFlag = hoverConnection ? DrawConnection.HOVER_FLAG : 0x0;

      if (onDelete) {
        return hoverFlag | DrawConnection.MODE_DELETING;
      }
      else if (thisConstraintSelected) {
        return hoverFlag | DrawConnection.MODE_CONSTRAINT_SELECTED;
      }
      else if (anyConstraintSelected) {
        return hoverFlag | DrawConnection.MODE_SUBDUED;
      }
      else if (anyViewSelected && !mMyViewSelected) {
        return hoverFlag | DrawConnection.MODE_SUBDUED;
      }

      if (mStartTime[direction] == -1) {
        ret = mCurrent[direction];
      }
      else {
        ret = mCurrent[direction];
      }
      if (ret == DrawConnection.MODE_NORMAL && fade) {
        ret = DrawConnection.MODE_SUBDUED;
      }
      return hoverFlag | ret;
    }

    long getTime(int direction) {
      if (mStartTime[direction] == -1) {
        return (componentChangeStateTime == null) ? 0 : componentChangeStateTime;
      }
      return mStartTime[direction];
    }
  }

  /**
   * This is used to build the display list of Constraints hanging off of each child.
   * This assume all children have been pre-processed to cache the connections to other SceneComponents
   */
  private static void buildListConnections(@NotNull DisplayList list,
                                           long time,
                                           @NotNull SceneContext sceneContext,
                                           @NotNull SceneComponent constraintComponent,
                                           @NotNull SceneComponent child) {
    Rectangle dest_rect = new Rectangle();
    Rectangle source_rect = new Rectangle();
    child.fillDrawRect(time, source_rect);
    convert(sceneContext, source_rect);
    ConnectionStatus connectStatus = new ConnectionStatus();
    List<NlComponent> selection = constraintComponent.getScene().getSelection();
    NlComponent component = child.getNlComponent();
    int hover = -1;
    Object hover_obj = component.getClientProperty(CONSTRAINT_HOVER);
    if (hover_obj != null && hover_obj instanceof SecondarySelector.Constraint) {
      SecondarySelector.Constraint constraint = ((SecondarySelector.Constraint)hover_obj);
      hover = constraint.ordinal();
    }
    // get the Secondary selection
    Object ss = constraintComponent.getScene().getSecondarySelection();
    int selectedDirection = -1;
    if (ss != null && ss instanceof SecondarySelector.Constraint) {
      SecondarySelector.Constraint constraint = ((SecondarySelector.Constraint)ss);
      selectedDirection = constraint.ordinal();
    }
    boolean constraintSelected = selectedDirection != -1;
    boolean anyViewSelected = selection != null && !selection.isEmpty();
    boolean fade = ConstraintLayoutHandler.getVisualProperty(ConstraintLayoutHandler.FADE_UNSELECTED_VIEWS);

    if (fade && selection.isEmpty()) { // nothing selected do not fade
      fade = false;
    }

    if (fade && selection.contains(component) && selection.size() == 1) { // only parent selected don't fade
      fade = false;
    }

    boolean viewSelected = selection.contains(child.getNlComponent());
    long changeStart;
    connectStatus.getConnectionInfo(child.getNlComponent(), viewSelected);

    // Extract Scene Components constraints from cache (Table speeds up next step)
    ConnectionType[] connectionTypes = new ConnectionType[ourDirections.length];
    SceneComponent[] connectionTo = new SceneComponent[ourDirections.length];

    String [][]marginNames =  ((constraintComponent.getScene().isInRTL()) ? MARGIN_ATTR_RTL : MARGIN_ATTR_LTR);
    String []marginValues = new String[marginNames.length];
    getMargins(constraintComponent, child, marginNames, marginValues);


    for (int i = 0; i < ourDirections.length; i++) {
      connectionTypes[i] = (ConnectionType)child.myCache.get(ourDirectionsType[i]);
      Object obj = child.myCache.get(ourDirections[i]);
      connectionTo[i] = (obj instanceof SceneComponent) ? (SceneComponent)obj : null;
    }

    for (int i = 0; i < ourDirections.length; i++) { // For each direction (not including baseline
      boolean selectedConnection = (selectedDirection == i && viewSelected);
      boolean hoverConnection = hover == i;
      ConnectionType type = connectionTypes[i];
      SceneComponent sc = connectionTo[i];
      int destType = DrawConnection.DEST_NORMAL;
      if (sc != null) {
        sc.fillDrawRect(time, dest_rect);  // get the destination rectangle
        convert(sceneContext, dest_rect);   // scale to screen space
        int connect = (type == ConnectionType.SAME) ? i : ourOppositeDirection[i];
        if (child.getParent().equals(sc)) { // flag a child connection
          destType = DrawConnection.DEST_PARENT;
        }
        else if (AndroidXConstants.CONSTRAINT_LAYOUT_GUIDELINE.isEqualsIgnoreCase(NlComponentHelperKt.getComponentClassName(sc.getNlComponent()))
                 ||
                 AndroidXConstants.CONSTRAINT_LAYOUT_BARRIER
                   .isEqualsIgnoreCase(NlComponentHelperKt.getComponentClassName(sc.getNlComponent()))) {
          destType = DrawConnection.DEST_GUIDELINE;
        }
        int connectType = DrawConnection.TYPE_NORMAL;

        if (connectionTo[ourOppositeDirection[i]] != null) { // opposite side is connected
          connectType = DrawConnection.TYPE_SPRING;
          if (connectionTo[ourOppositeDirection[i]] == sc && destType != DrawConnection.DEST_PARENT) { // center
            if (connectionTypes[ourOppositeDirection[i]] != type) {
              connectType = DrawConnection.TYPE_CENTER;
            }
            else {
              connectType = DrawConnection.TYPE_CENTER_WIDGET;
            }
          }
        }

        SceneComponent toComponentsTo = (SceneComponent)sc.myCache.get(ourDirections[connect]);
        // Chain detection
        if (type == ConnectionType.BACKWARD // this connection must be backward
            && toComponentsTo == child  // it must connect to some one who connects to me
            && sc.myCache.get(ourDirectionsType[connect]) == ConnectionType.BACKWARD) { // and that connection must be backward as well
          connectType = DrawConnection.TYPE_CHAIN;
          if (sc.myCache.containsKey(ourChainDirections[ourOppositeDirection[i]])) {
            continue; // no need to add element to display list chains only have to go one way
          }
        }
        int margin = 0;
        int marginDistance = 0;
        boolean isMarginReference = false;
        float bias = 0.5f;
        String marginString = marginValues[i]; //child.getAuthoritativeNlComponent().getLiveAttribute(SdkConstants.ANDROID_URI, margin_attr[0]);
        if (marginString != null) {
          if (marginString.startsWith("@")) {
            isMarginReference = true;
          }
          margin = ConstraintUtilities.getDpValue(child.getAuthoritativeNlComponent(), marginString);
          marginDistance = sceneContext.getSwingDimensionDip(margin);
        }
        String biasString = child.getAuthoritativeNlComponent().getLiveAttribute(SdkConstants.SHERPA_URI, BIAS_ATTR[i]);
        if (biasString != null) {
          try {
            bias = Float.parseFloat(biasString);
            if (FLIP_BIAS[i]) {
              bias = 1 - bias;
            }
          }
          catch (NumberFormatException e) {
          }
        }
        boolean shift = toComponentsTo != null;
        if (destType == DrawConnection.DEST_GUIDELINE) { // connections to guidelines are always Opposite
          connect = ourOppositeDirection[i];
        }
        AnchorTarget anchorTarget = AnchorTarget.findAnchorTarget(child, ourAnchorTypes[i]);
        boolean onDelete = anchorTarget != null && anchorTarget.canDisconnect() && anchorTarget.isMouseHovered();
        changeStart = connectStatus.getTime(i);
        int previousMode = connectStatus.getPreviousMode(i);
        int currentMode =
          connectStatus.getCurrentMode(i, selectedConnection, fade, constraintSelected, anyViewSelected, hoverConnection, onDelete);

        int x1 = getX(source_rect, i);
        int x2 = getX(dest_rect, connect);
        int y1 = getY(source_rect, i);
        int y2 = getY(dest_rect, connect);
        boolean overlap = (i != connect
                           && ((isLeftRight[i] && Math.abs(x1 - x2) < 4 && Math.abs(y1 - y2) < dest_rect.height / 2)
                               || (!isLeftRight[i] && Math.abs(y1 - y2) < 4 && Math.abs(x1 - x2) < dest_rect.width / 2)));
        if (overlap) {
          connectType = DrawConnection.TYPE_ADJACENT;
        }
        if (!ConstraintLayoutHandler.getVisualProperty(ConstraintLayoutHandler.SHOW_MARGINS_PREF_KEY)) {
          margin = 0;
          marginDistance = 0;
        }

        DrawConnection
          .buildDisplayList(list, SecondarySelector.get(child.getNlComponent(), SecondarySelector.Constraint.values()[i]), connectType,
                            source_rect, i, dest_rect, connect, destType, shift, margin, marginDistance,
                            isMarginReference, bias, previousMode, currentMode, changeStart);
        if (((anchorTarget != null && anchorTarget.isMouseHovered()) || hoverConnection) && viewSelected) {
          // When hovering the target or connection of a selected view, draw an animated frame around the target view.
          if (destType == DrawConnection.DEST_GUIDELINE) {
            int over_size_line = 3000;
            dest_rect.grow((connect < 2) ? 1 : over_size_line, (connect < 2) ? over_size_line : 1);
          }
          boolean drawFrameAsDelete = (currentMode & DrawConnection.HOVER_MASK) == DrawConnection.MODE_DELETING;
          DrawAnimatedFrame.add(list, dest_rect, connect, drawFrameAsDelete);
        }
      }
    }

    SceneComponent baseLineConnection = (SceneComponent)child.myCache.get("BASELINE");
    if (baseLineConnection != null) {
      baseLineConnection.fillDrawRect(time, dest_rect);  // get the destination rectangle
      convert(sceneContext, dest_rect);   // scale to screen space
      int dest_offset = sceneContext.getSwingDimensionDip(baseLineConnection.getBaseline());
      int source_offset = sceneContext.getSwingDimensionDip(child.getBaseline());
      source_rect.y += source_offset;
      source_rect.height = 0;
      dest_rect.y += dest_offset;
      dest_rect.height = 0;
      changeStart = connectStatus.getTime(ConnectionStatus.DIRECTION_BASELINE);
      AnchorTarget anchorTarget = AnchorTarget.findAnchorTarget(child, AnchorTarget.Type.BASELINE);
      boolean onDelete = anchorTarget != null && anchorTarget.canDisconnect() && anchorTarget.isMouseHovered();
      int previousMode = connectStatus.getPreviousMode(4);
      boolean selectedConnection = selectedDirection == SecondarySelector.Constraint.BASELINE.ordinal() && viewSelected;
      boolean hoverConnection = hover == SecondarySelector.Constraint.BASELINE.ordinal();
      int currentMode =
        connectStatus.getCurrentMode(4, selectedConnection, fade, constraintSelected, anyViewSelected, hoverConnection, onDelete);

      DrawConnection
        .buildDisplayList(list,
                          ourBlockSelection ? null : SecondarySelector.get(child.getNlComponent(), SecondarySelector.Constraint.BASELINE),
                          DrawConnection.TYPE_BASELINE, source_rect,
                          DrawConnection.TYPE_BASELINE, dest_rect,
                          DrawConnection.TYPE_BASELINE,
                          DrawConnection.DEST_NORMAL,
                          false, 0, 0, false,
                          0f, previousMode, currentMode, changeStart);
    }
  }

  private static int getX(Rectangle rectangle, int direction) {
    switch (direction) {
      case DrawConnection.DIR_LEFT:
        return rectangle.x;
      case DrawConnection.DIR_RIGHT:
        return rectangle.x + rectangle.width + 1;
      case DrawConnection.DIR_TOP:
        return rectangle.x + rectangle.width / 2;
      case DrawConnection.DIR_BOTTOM:
        return rectangle.x + rectangle.width / 2;
    }
    return 0;
  }

  private static int getY(Rectangle rectangle, int direction) {
    switch (direction) {
      case DrawConnection.DIR_LEFT:
        return rectangle.y + rectangle.height / 2;
      case DrawConnection.DIR_RIGHT:
        return rectangle.y + rectangle.height / 2;
      case DrawConnection.DIR_TOP:
        return rectangle.y;
      case DrawConnection.DIR_BOTTOM:
        return rectangle.y + rectangle.height + 1;
    }
    return 0;
  }
}
