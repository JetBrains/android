/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.constraint.draw;

import com.android.SdkConstants;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.decorator.SceneDecorator;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintUtilities;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scene.decorator.DecoratorUtilities;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * This defines the decorator
 * TODO: move to the ConstraintLayout handler
 */
public class ConstraintLayoutDecorator extends SceneDecorator {
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

  private final static ConnectionType[] DIR_TABLE = {ConnectionType.SAME, ConnectionType.BACKWARD, ConnectionType.SAME, ConnectionType.BACKWARD};
  private final static String[] ourDirections = {"LEFT", "RIGHT", "TOP", "BOTTOM"};
  private final static String[] ourChainDirections = {"CHAIN_LEFT", "CHAIN_RIGHT", "CHAIN_TOP", "CHAIN_BOTTOM"};
  private final static String[] ourDirectionsType = {"LEFT_TYPE", "RIGHT_TYPE", "TOP_TYPE", "BOTTOM_TYPE"};
  private final static boolean[] isLeftRight = {true, true, false, false};
  private final static int[] ourOppositeDirection = {1, 0, 3, 2};

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

  /**
   * This caches connections on each child SceneComponent by accessing NLcomponent attributes
   *
   * @param component
   * @param child
   * @param atributes
   * @param dir
   * @param dirType
   */
  private static void getConnection(SceneComponent component, SceneComponent child, String[] atributes, String dir, String dirType) {
    String id = null;
    ConnectionType type = ConnectionType.SAME;
    for (int i = 0; i < atributes.length; i++) {
      id = child.getAuthoritativeNlComponent().getLiveAttribute(SdkConstants.SHERPA_URI, atributes[i]);
      type = DIR_TABLE[i];
      if (id != null) {
        break;
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

  @Override
  protected void addBackground(@NotNull DisplayList list,
                               @NotNull SceneContext sceneContext,
                               @NotNull SceneComponent component) {
    // no background
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
    if (!children.isEmpty()) {
      // Cache connections between children
      for (SceneComponent child : component.getChildren()) {
        gatherProperties(component, child);
      }
      Rectangle rect = new Rectangle();
      component.fillRect(rect);
      DisplayList.UNClip unClip = list.addClip(sceneContext, rect);
      Scene scene = component.getScene();

      boolean showAllConstraints = ConstraintLayoutHandler.getVisualProperty(ConstraintLayoutHandler.SHOW_CONSTRAINTS_PREF_KEY);
      List<NlComponent> selection = scene.getSelection();
      for (SceneComponent child : children) {
        child.buildDisplayList(time, list, sceneContext);
        if (sceneContext.showOnlySelection()) {
          continue;
        }
        if ((showAllConstraints && scene.getRoot() == component) || selection.contains(child.getNlComponent())) {
          buildListConnections(list, time, sceneContext, component, child); // draw child connections
        }
      }
      list.add(unClip);
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
    long[] time = new long[connectTypes.length];

    DecoratorUtilities.ViewStates componentPrevState;
    DecoratorUtilities.ViewStates componentCurrentState;
    Long componentChangeStateTime;
    boolean mSelected = true;

    void getConnectionInfo(NlComponent c, boolean isSelected) {
      componentPrevState = DecoratorUtilities.getTimedChange_prev(c, DecoratorUtilities.VIEW);
      componentCurrentState = DecoratorUtilities.getTimedChange_value(c, DecoratorUtilities.VIEW);
      componentChangeStateTime = DecoratorUtilities.getTimedChange_time(c, "drawState");
      mSelected = isSelected;
      for (int i = 0; i < connectTypes.length; i++) {
        String type = connectTypes[i];
        DecoratorUtilities.ViewStates current, prev;
        if (componentCurrentState != DecoratorUtilities.ViewStates.SELECTED) { // selection fix
          // TODO we need to have a clear mechinisem for selection event to propagate
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
          time[i] = event;
          mPrevious[i] = prev.getVal();
          mCurrent[i] = current.getVal();
        }
        else {
          time[i] = -1;
        }
      }
    }

    int getPreviousMode(int direction) {
      if (time[direction] == -1) {
        if (componentPrevState == DecoratorUtilities.ViewStates.NORMAL) {
          return DrawConnection.MODE_NORMAL;
        }
        return mCurrent[direction];
      }
      return mPrevious[direction];
    }

    int getCurrentMode(int direction) {
      if (time[direction] == -1) {
        if (mSelected) {
          return DrawConnection.MODE_SELECTED;
        }
        return mCurrent[direction];
      }
      return mCurrent[direction];
    }

    long getTime(int direction) {
      if (time[direction] == -1) {
        return (componentChangeStateTime == null) ? 0 : componentChangeStateTime;
      }
      return time[direction];
    }
  }

  /**
   * This is used to build the display list of Constraints hanging off of of each child.
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

    boolean fade =  ConstraintLayoutHandler.getVisualProperty(ConstraintLayoutHandler.FADE_UNSELECTED_VIEWS);

    if (fade && selection.isEmpty()) { // nothing selected do not fade
      fade = false;
    }

    if (fade && selection.contains(constraintComponent.getNlComponent()) && selection.size() == 1) { // only parent selected don't fade
      fade = false;
    }

    boolean viewSelected = selection.contains(child.getNlComponent());
    long changeStart;
    connectStatus.getConnectionInfo(child.getNlComponent(), viewSelected);

    // Extract Scene Components constraints from cache (Table speeds up next step)
    ConnectionType[] connectionTypes = new ConnectionType[ourDirections.length];
    SceneComponent[] connectionTo = new SceneComponent[ourDirections.length];
    for (int i = 0; i < ourDirections.length; i++) {
      connectionTypes[i] = (ConnectionType)child.myCache.get(ourDirectionsType[i]);
      Object obj = child.myCache.get(ourDirections[i]);
      connectionTo[i] = (obj instanceof SceneComponent) ? (SceneComponent)obj : null;
    }

    for (int i = 0; i < ourDirections.length; i++) { // For each direction (not including baseline

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
        else if (SdkConstants.CONSTRAINT_LAYOUT_GUIDELINE.isEqualsIgnoreCase(NlComponentHelperKt.getComponentClassName(sc.getNlComponent()))
              || SdkConstants.CONSTRAINT_LAYOUT_BARRIER.isEqualsIgnoreCase(NlComponentHelperKt.getComponentClassName(sc.getNlComponent()))) {
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
        boolean rtl = constraintComponent.getScene().isInRTL();
        String[] margin_attr = (rtl) ? MARGIN_ATTR_RTL[i] : MARGIN_ATTR_LTR[i];
        String marginString = child.getAuthoritativeNlComponent().getLiveAttribute(SdkConstants.NS_RESOURCES, margin_attr[0]);
        if (marginString == null && margin_attr.length > 1) {
          marginString = child.getAuthoritativeNlComponent().getLiveAttribute(SdkConstants.NS_RESOURCES, margin_attr[1]);
        }
        if (marginString == null) {
          if (i == 0) { // left check if it is start
            marginString =
              child.getAuthoritativeNlComponent().getLiveAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_START);
          }
          else if (i == 1) { // right check if it is end
            marginString =
              child.getAuthoritativeNlComponent().getLiveAttribute(SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_END);
          }
        }
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
        changeStart = connectStatus.getTime(i);
        int previousMode = connectStatus.getPreviousMode(i);
        int currentMode = connectStatus.getCurrentMode(i);
        if (currentMode == DrawConnection.MODE_NORMAL && fade) {
          currentMode = DrawConnection.MODE_SUBDUED;
        }
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
          .buildDisplayList(list, connectType, source_rect, i, dest_rect, connect, destType, shift, margin, marginDistance,
                            isMarginReference, bias, previousMode, currentMode, changeStart);
        if (currentMode == DrawConnection.MODE_WILL_DESTROY) {
          if (destType == DrawConnection.DEST_GUIDELINE) {
            int over_size_line = 3000;
            dest_rect.grow((connect < 2)?1:over_size_line,(connect < 2)?over_size_line:1);
          }
          DrawAnimatedFrame.add(list, dest_rect,  connect);
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
      int previousMode = connectStatus.getPreviousMode(4);
      int currentMode = connectStatus.getCurrentMode(4);
      if (currentMode == DrawConnection.MODE_NORMAL && fade) {
        currentMode = DrawConnection.MODE_SUBDUED;
      }
      DrawConnection
        .buildDisplayList(list,
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
