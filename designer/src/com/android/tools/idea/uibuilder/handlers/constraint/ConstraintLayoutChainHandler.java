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
package com.android.tools.idea.uibuilder.handlers.constraint;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.SdkConstants.VALUE_VERTICAL;

/**
 * Handler for Chain helper
 */
public class ConstraintLayoutChainHandler extends ConstraintHelperHandler {

  @Override
  @NotNull
  public String getTitle(@NotNull NlComponent component) {
    String title = getSimpleTagName(component.getTagName());
    if (NlComponentHelperKt.isOrHasSuperclass(component, CLASS_CONSTRAINT_LAYOUT_CHAIN)) {
      boolean horizontal = true;
      String orientation = component.getLiveAttribute(ANDROID_URI, ATTR_ORIENTATION);
      if (orientation != null && orientation.equals(VALUE_VERTICAL)) {
        horizontal = false;
      }
      if (horizontal) {
        title = "Horizontal Chain";
      }
      else {
        title = "Vertical Chain";
      }
    }
    return title;
  }

  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(ATTR_ORIENTATION, ATTR_LAYOUT_CHAIN_HELPER_USE_RTL);
  }
}
