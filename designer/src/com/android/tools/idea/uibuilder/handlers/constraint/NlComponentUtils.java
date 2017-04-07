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
import com.android.tools.idea.uibuilder.model.NlComponent;

import java.util.HashMap;
import java.util.List;

/**
 * Utility class to operate efficiently on NLcomponents
 */
public class NlComponentUtils {

  private static final String ATT_LL = SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF;
  private static final String ATT_LR = SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF;
  private static final String ATT_RL = SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF;
  private static final String ATT_RR = SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF;
  private static final String ATT_TT = SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF;
  private static final String ATT_TB = SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF;
  private static final String ATT_BT = SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF;
  private static final String ATT_BB = SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF;
  private static final String[] LEFT = {ATT_LL, ATT_LR};
  private static final String[] RIGHT = {ATT_RR, ATT_RL};
  private static final String[] TOP = {ATT_LL, ATT_LR};
  private static final String[] BOTTOM = {ATT_RR, ATT_RL};

  private static HashMap<String, NlComponent> getMap(NlComponent nlComponent) {
    List<NlComponent> list = nlComponent.getModel().getComponents();
    HashMap<String, NlComponent> map = new HashMap<String, NlComponent>();
    for (NlComponent component : list) {
      String id = component.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);
      if (id != null) {
        map.put(id, component);
      }
    }
    return map;
  }

  private static NlComponent getDir(HashMap<String, NlComponent> map, NlComponent component, String[] dir) {
    if (component == null) {
      return null;
    }
    for (int i = 0; i < dir.length; i++) {
      String id = component.getAttribute(SdkConstants.SHERPA_URI, dir[i]);
      if (id != null) {
        return map.get(id);
      }
    }
    return null;
  }

  public static boolean isHorizontalChain(NlComponent component) {
    HashMap<String, NlComponent> map = getMap(component);
    if (getDir(map, getDir(map, component, LEFT), RIGHT) == component) {
      return true;
    }
    if (getDir(map, getDir(map, component, RIGHT), LEFT) == component) {
      return true;
    }
    return false;
  }

  public static boolean isVerticalChain(NlComponent component) {
    HashMap<String, NlComponent> map = getMap(component);
    if (getDir(map, getDir(map, component, TOP), BOTTOM) == component) {
      return true;
    }
    if (getDir(map, getDir(map, component, BOTTOM), TOP) == component) {
      return true;
    }
    return false;
  }

  public static NlComponent getLeftMostInChain(NlComponent component) {
    HashMap<String, NlComponent> map = getMap(component);
    NlComponent current = component;
    NlComponent leftComponent;
    do {
      leftComponent = current;
      current = getDir(map, leftComponent, LEFT);
      if (current == null) {
        return leftComponent;
      }
    }
    while (getDir(map, current, RIGHT) == leftComponent);
    return leftComponent;
  }

  public static NlComponent getTopMostInChain(NlComponent component) {
    HashMap<String, NlComponent> map = getMap(component);
    NlComponent current = component;
    NlComponent leftComponent;
    do {
      leftComponent = current;
      current = getDir(map, leftComponent, TOP);
      if (current == null) {
        return leftComponent;
      }
    }
    while (getDir(map, current, BOTTOM) == leftComponent);
    return leftComponent;
  }
}