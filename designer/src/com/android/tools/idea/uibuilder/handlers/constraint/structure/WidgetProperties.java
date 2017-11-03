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
package com.android.tools.idea.uibuilder.handlers.constraint.structure;

import com.android.tools.idea.uibuilder.handlers.constraint.model.ConstraintAnchor;

import java.util.HashMap;

/**
 * This will contain information about widgets needed at design time
 */
public class WidgetProperties {
  HashMap<ConstraintAnchor, MarginProperty> mMarginsSource = new HashMap<>();
  String mGuidelineAttribute;
  String mDimensionRatio;

  class MarginProperty {
    String attribute;
    String attributeRtl;
  }

  public void clear() {
    mMarginsSource.clear();
    mGuidelineAttribute = null;
    mDimensionRatio = null;
  }

  public void setDimensionRatio(String attribute) {
    mDimensionRatio = attribute;
  }

  public String getDimensionRatio() {
    return mDimensionRatio;
  }

  public void setGuidelineAttribute(String attribute) {
    mGuidelineAttribute = attribute;
  }

  public String getGuidelineAttribute() {
    return mGuidelineAttribute;
  }

  public void setMarginReference(ConstraintAnchor anchor, String attribute) {
    MarginProperty property = mMarginsSource.get(anchor);
    if (property == null) {
      property = new MarginProperty();
    }
    property.attribute = attribute;
    mMarginsSource.put(anchor, property);
  }

  public boolean isMarginReference(ConstraintAnchor anchor) {
    if (mMarginsSource.containsKey(anchor) && mMarginsSource.get(anchor).attribute != null) {
      return mMarginsSource.get(anchor).attribute.startsWith("@");
    }
    return false;
  }

  public String getMarginValue(ConstraintAnchor anchor) {
    if (mMarginsSource.containsKey(anchor)) {
      return mMarginsSource.get(anchor).attribute;
    }
    return null;
  }

  // Let's duplicate things for Rtl... yay.

  public void setMarginRtlReference(ConstraintAnchor anchor, String attribute) {
    MarginProperty property = mMarginsSource.get(anchor);
    if (property == null) {
      property = new MarginProperty();
    }
    property.attributeRtl = attribute;
    mMarginsSource.put(anchor, property);
  }

  public boolean isMarginRtlReference(ConstraintAnchor anchor) {
    if (mMarginsSource.containsKey(anchor) && mMarginsSource.get(anchor).attributeRtl != null) {
      return mMarginsSource.get(anchor).attributeRtl.startsWith("@");
    }
    return false;
  }

  public String getMarginRtlValue(ConstraintAnchor anchor) {
    if (mMarginsSource.containsKey(anchor)) {
      return mMarginsSource.get(anchor).attributeRtl;
    }
    return null;
  }
}
