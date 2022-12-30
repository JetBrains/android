/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scout;

import static com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities.scoutConnect;
import static com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities.wouldCreateLoop;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities;
import java.util.List;

public final class ScoutConnectArrange {

  static int gap(NlComponent fromComponent, Direction fromDir, NlComponent toComponent, Direction toDir) {
    int fromY = 0;
    int fromX = 0;
    int marginDir = 1;

    switch (fromDir) {
      case TOP:
        fromY = ConstraintComponentUtilities.getDpY(fromComponent);
        marginDir = -1;
        break;
      case BOTTOM:
        fromY = ConstraintComponentUtilities.getDpY(fromComponent);
        fromY += ConstraintComponentUtilities.getDpHeight(fromComponent);
        break;
      case LEFT:
        marginDir = -1;
        fromX = ConstraintComponentUtilities.getDpX(fromComponent);
        break;
      case RIGHT:
        fromX = ConstraintComponentUtilities.getDpX(fromComponent);
        fromX += ConstraintComponentUtilities.getDpWidth(fromComponent);

        break;
      case BASELINE:
        return 0;
    }

    if (toComponent == null) {
      switch (toDir) {
        case TOP:
          fromY -= ConstraintComponentUtilities.getDpY(fromComponent.getParent());
          return fromY;
        case BOTTOM:
          fromY -= ConstraintComponentUtilities.getDpY(fromComponent.getParent());
          fromY -= ConstraintComponentUtilities.getDpHeight(fromComponent.getParent());
          return -fromY;
        case LEFT:
          fromX -= ConstraintComponentUtilities.getDpX(fromComponent.getParent());
          return fromX;
        case RIGHT:
          fromX -= ConstraintComponentUtilities.getDpX(fromComponent.getParent());
          fromX -= ConstraintComponentUtilities.getDpWidth(fromComponent.getParent());
          return -fromX;
        case BASELINE:
          return 0;
      }
    }

    switch (toDir) {
      case TOP:
        fromY = ConstraintComponentUtilities.getDpY(toComponent) - fromY;
        return fromY * marginDir;
      case BOTTOM:
        int toY = ConstraintComponentUtilities.getDpY(toComponent);
        toY += ConstraintComponentUtilities.getDpHeight(toComponent);

        toY -= fromY;
        return toY * marginDir;
      case LEFT:
        fromX = ConstraintComponentUtilities.getDpX(toComponent) - fromX;

        return fromX * marginDir;
      case RIGHT:
        int toX = ConstraintComponentUtilities.getDpX(toComponent);
        toX += ConstraintComponentUtilities.getDpWidth(toComponent);
        toX -= fromX;
        return toX * marginDir;
      case BASELINE:
        return 0;
    }
    return 0;
  }

  public static void connect(List<NlComponent> widgets, Scout.Connect action, boolean reverse, boolean useMargin) {
    int margin = 0;

    NlComponent componentFrom = widgets.get(0);
    NlComponent componentTo = widgets.size() > 1 ? widgets.get(1) : null;
    if (reverse) {
      NlComponent swap = componentTo;
      componentTo = componentFrom;
      componentFrom = swap;
    }
    switch (action) {
      case ConnectTopToTop:
        margin = gap(componentFrom, Direction.TOP, componentTo, Direction.TOP);
        scoutConnect(componentFrom, Direction.TOP, componentTo, Direction.TOP, margin);
        break;
      case ConnectTopToBottom:
        margin = gap(componentFrom, Direction.TOP, componentTo, Direction.BOTTOM);
        scoutConnect(componentFrom, Direction.TOP, componentTo, Direction.BOTTOM, margin);
        break;
      case ConnectBottomToTop:
        margin = gap(componentFrom, Direction.BOTTOM, componentTo, Direction.TOP);
        scoutConnect(componentFrom, Direction.BOTTOM, componentTo, Direction.TOP, margin);
        break;
      case ConnectBottomToBottom:
        margin = gap(componentFrom, Direction.BOTTOM, componentTo, Direction.BOTTOM);
        scoutConnect(componentFrom, Direction.BOTTOM, componentTo, Direction.BOTTOM, margin);
        break;
      case ConnectStartToStart:
        margin = gap(componentFrom, Direction.LEFT, componentTo, Direction.LEFT);
        scoutConnect(componentFrom, Direction.LEFT, componentTo, Direction.LEFT, margin);
        break;
      case ConnectStartToEnd:
        margin = gap(componentFrom, Direction.LEFT, componentTo, Direction.RIGHT);
        scoutConnect(componentFrom, Direction.LEFT, componentTo, Direction.RIGHT, margin);
        break;
      case ConnectEndToStart:
        margin = gap(componentFrom, Direction.RIGHT, componentTo, Direction.LEFT);
        scoutConnect(componentFrom, Direction.RIGHT, componentTo, Direction.LEFT, margin);
        break;
      case ConnectEndToEnd:
        margin = gap(componentFrom, Direction.RIGHT, componentTo, Direction.RIGHT);
        scoutConnect(componentFrom, Direction.RIGHT, componentTo, Direction.RIGHT, margin);
        break;
      case ConnectBaseLineToBaseLine:
        scoutConnect(componentFrom, Direction.BASELINE, componentTo, Direction.BASELINE, margin);
        break;
      case ConnectToParentTop:
        margin = gap(componentFrom, Direction.TOP, null, Direction.TOP);
        scoutConnect(componentFrom, Direction.TOP, componentFrom.getParent(), Direction.TOP, margin);
        break;
      case ConnectToParentBottom:
        margin = gap(componentFrom, Direction.BOTTOM, null, Direction.BOTTOM);
        scoutConnect(componentFrom, Direction.BOTTOM, componentFrom.getParent(), Direction.BOTTOM, margin);
        break;
      case ConnectToParentStart:
        margin = gap(componentFrom, Direction.LEFT, null, Direction.LEFT);
        scoutConnect(componentFrom, Direction.LEFT, componentFrom.getParent(), Direction.LEFT, margin);
        break;
      case ConnectToParentEnd:
        margin = gap(componentFrom, Direction.RIGHT, null, Direction.RIGHT);
        scoutConnect(componentFrom, Direction.RIGHT, componentFrom.getParent(), Direction.RIGHT, margin);
        break;
    }
    return;
  }

  public static boolean connectCheck(List<NlComponent> widgets, Scout.Connect test, boolean reverse) {
    if (widgets.size() == 0) {
      return false;
    }

    NlComponent componentFrom = widgets.get(0);
    NlComponent componentTo = widgets.size() > 1 ? widgets.get(1) : null;
    if (componentFrom == componentTo) {
      // Cannot connect to itself.
      return false;
    }
    if (reverse) {
      NlComponent swap = componentTo;
      componentTo = componentFrom;
      componentFrom = swap;
    }
    switch (test) {
      case ConnectTopToTop:
        return !wouldCreateLoop(componentFrom, Direction.TOP, componentTo);
      case ConnectTopToBottom:
        return !wouldCreateLoop(componentFrom, Direction.TOP, componentTo);
      case ConnectBottomToTop:
        return !wouldCreateLoop(componentFrom, Direction.BOTTOM, componentTo);
      case ConnectBottomToBottom:
        return !wouldCreateLoop(componentFrom, Direction.BOTTOM, componentTo);
      case ConnectStartToStart:
        return !wouldCreateLoop(componentFrom, Direction.LEFT, componentTo);
      case ConnectStartToEnd:
        return !wouldCreateLoop(componentFrom, Direction.LEFT, componentTo);
      case ConnectEndToStart:
        return !wouldCreateLoop(componentFrom, Direction.RIGHT, componentTo);
      case ConnectEndToEnd:
        return !wouldCreateLoop(componentFrom, Direction.RIGHT, componentTo);
      case ConnectBaseLineToBaseLine:
        return !wouldCreateLoop(componentFrom, Direction.BASELINE, componentTo);
      case ConnectToParentTop:
      case ConnectToParentBottom:
      case ConnectToParentStart:
      case ConnectToParentEnd:
        return widgets.size() == 1;
    }
    return false;
  }
}
