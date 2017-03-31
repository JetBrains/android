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
package com.android.tools.idea.uibuilder.scene.decorator;

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.intellij.execution.ui.layout.View;

/**
 * Tools to be used by decorators for setting timed state transitions on NLComponents
 * Timed states have a name, time,  previous state, current state
 * and is to be thought of as this feature of a component changed from previous to next at that time
 */
public class DecoratorUtilities {
  public static final String VIEW = "view";
  public static final String TOP_CONNECTION = "north";
  public static final String LEFT_CONNECTION = "left";
  public static final String RIGHT_CONNECTION = "right";
  public static final String BOTTOM_CONNECTION = "bottom";
  public static final String BASELINE_CONNECTION = "baseline";

  /**
   * This is done to this way to ensure that the enums have a fixed value
   */
  public enum ViewStates {
    NORMAL(0),
    SUBDUED(1),
    SELECTED(2),
    HOVER(3),
    TARGETED(4),
    SECONDARY(5),
    WILL_DESTROY(6),
    INFERRED(7),
    DRAG(8);
    public static final int NORMAL_VALUE = 0;
    public static final int SUBDUED_VALUE = 1;
    public static final int SELECTED_VALUE = 2;
    public static final int HOVER_VALUE = 3;
    public static final int TARGETED_VALUE = 4;
    public static final int SECONDARY_VALUE = 5;
    public static final int WILL_DESTROY_VALUE = 6;
    public static final int INFERRED_VALUE = 7;
    public static final int DRAG_VALUE = 8;

    private final int val;

    private ViewStates(int v) {
      val = v;
    }

    public int getVal() {
      return val;
    }

  }

  public static ViewStates mapState(SceneComponent.DrawState sState) {
    switch (sState) {
      case SUBDUED:
        return ViewStates.SUBDUED;

      case HOVER:
        return ViewStates.HOVER;
      case SELECTED:
        return ViewStates.SELECTED;
      case DRAG:
        return ViewStates.DRAG;
      case NORMAL:
      default:
        return ViewStates.NORMAL;
    }
  }

  /**
   * This sets the current state and the previous state
   * You can use the previous state to say computed
   *
   * @param component
   * @param type
   * @param time
   * @param from
   * @param to
   */
  public static void setTimeChange(NlComponent component, String type, long time, ViewStates from, ViewStates to) {
    component.putClientProperty(type + "_mode", to);
    component.putClientProperty(type + "_prev", from);
    component.putClientProperty(type + "_time", time);
  }

  /**
   * This sets the current state and the previous state
   * You can use the previous state to say computed
   *
   * @param component
   * @param type
   * @param from
   * @param to
   */
  public static void setTimeChange(NlComponent component, String type, ViewStates from, ViewStates to) {
    long time = System.nanoTime();
    component.putClientProperty(type + "_mode", to);
    component.putClientProperty(type + "_prev", from);
    component.putClientProperty(type + "_time", time);
  }

  /**
   * This sets the view state and when it was issued
   * it computes the time and looks up the previous state
   *
   * @param component
   * @param type
   * @param to
   */
  public static void setTimeChange(NlComponent component, String type, ViewStates to) {
    long time = System.nanoTime();
    component.putClientProperty(type + "_mode", to);
    ViewStates from = (ViewStates)component.getClientProperty(type + "_mode");
    if (from == null) {
      from = ViewStates.NORMAL;
    }
    component.putClientProperty(type + "_prev", from);
    component.putClientProperty(type + "_time", time);
  }

  public static ViewStates getTimedChange_prev(NlComponent component, String type) {
    return (ViewStates)component.getClientProperty(type + "_prev");
  }

  public static ViewStates getTimedChange_value(NlComponent component, String type) {
    return (ViewStates)component.getClientProperty(type + "_mode");
  }

  public static Long getTimedChange_time(NlComponent component, String type) {

    return (Long)component.getClientProperty(type + "_time");
  }
}
