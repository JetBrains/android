/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.model;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Types of fill behavior that views can state that they prefer.
 */
public enum FillPolicy {
  /** This view does not want to fill */
  NONE,
  /** This view wants to always fill both horizontal and vertical */
  BOTH,
  /** This view wants to fill horizontally but not vertically */
  WIDTH,
  /** This view wants to fill vertically but not horizontally */
  HEIGHT,
  /**
   * This view wants to fill in the opposite dimension of the context, e.g. in a
   * vertical context it wants to fill horizontally, and vice versa
   */
  OPPOSITE,
  /** This view wants to fill horizontally, but only in a vertical context */
  WIDTH_IN_VERTICAL,
  /** This view wants to fill vertically, but only in a horizontal context */
  HEIGHT_IN_HORIZONTAL;

  static final Map<String, FillPolicy> ourNameToPolicy = new HashMap<String, FillPolicy>();
  static {
    for (FillPolicy pref : FillPolicy.values()) {
      ourNameToPolicy.put(pref.toString().toLowerCase(Locale.US), pref);
    }
  }

  /**
   * Returns true if this view wants to fill horizontally, if the context is
   * vertical or horizontal as indicated by the parameter.
   *
   * @param inVerticalContext If true, the context is vertical, otherwise it is
   *                          horizontal.
   * @return true if this view wants to fill horizontally
   */
  public boolean fillHorizontally(boolean inVerticalContext) {
    return (this == BOTH || this == WIDTH || (inVerticalContext && (this == OPPOSITE || this == WIDTH_IN_VERTICAL)));
  }

  /**
   * Returns true if this view wants to fill vertically, if the context is
   * vertical or horizontal as indicated by the parameter.
   *
   * @param inVerticalContext If true, the context is vertical, otherwise it is
   *                          horizontal.
   * @return true if this view wants to fill vertically
   */
  public boolean fillVertically(boolean inVerticalContext) {
    return (this == BOTH || this == HEIGHT || (!inVerticalContext && (this == OPPOSITE || this == HEIGHT_IN_HORIZONTAL)));
  }

  public static FillPolicy get(String fill) {
    FillPolicy fillPolicy = null;
    if (!fill.isEmpty()) {
      fillPolicy = ourNameToPolicy.get(fill);
    }
    if (fillPolicy == null) {
      fillPolicy = NONE;
    }

    return fillPolicy;
  }

  /**
   * Returns the {@link com.android.tools.idea.designer.ResizePolicy} for the given component
   *
   * @param component the component to look up a resize policy for
   * @return a suitable {@linkplain com.android.tools.idea.designer.ResizePolicy}
   */
  @NotNull
  public static FillPolicy getFillPreference(@NotNull NlComponent component) {
    return BOTH;
  }
}
