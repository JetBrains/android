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

import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.ATTR_FLOW_FIRST_HORIZONTAL_BIAS;
import static com.android.SdkConstants.ATTR_FLOW_FIRST_HORIZONTAL_STYLE;
import static com.android.SdkConstants.ATTR_FLOW_FIRST_VERTICAL_BIAS;
import static com.android.SdkConstants.ATTR_FLOW_FIRST_VERTICAL_STYLE;
import static com.android.SdkConstants.ATTR_FLOW_HORIZONTAL_ALIGN;
import static com.android.SdkConstants.ATTR_FLOW_HORIZONTAL_BIAS;
import static com.android.SdkConstants.ATTR_FLOW_HORIZONTAL_GAP;
import static com.android.SdkConstants.ATTR_FLOW_HORIZONTAL_STYLE;
import static com.android.SdkConstants.ATTR_FLOW_LAST_HORIZONTAL_BIAS;
import static com.android.SdkConstants.ATTR_FLOW_LAST_HORIZONTAL_STYLE;
import static com.android.SdkConstants.ATTR_FLOW_LAST_VERTICAL_BIAS;
import static com.android.SdkConstants.ATTR_FLOW_LAST_VERTICAL_STYLE;
import static com.android.SdkConstants.ATTR_FLOW_MAX_ELEMENTS_WRAP;
import static com.android.SdkConstants.ATTR_FLOW_VERTICAL_ALIGN;
import static com.android.SdkConstants.ATTR_FLOW_VERTICAL_BIAS;
import static com.android.SdkConstants.ATTR_FLOW_VERTICAL_GAP;
import static com.android.SdkConstants.ATTR_FLOW_VERTICAL_STYLE;
import static com.android.SdkConstants.ATTR_FLOW_WRAP_MODE;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.ATTR_VISIBILITY;

import com.android.tools.idea.common.model.NlComponent;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Handler for Flow helper
 */
public class ConstraintLayoutFlowHandler extends ConstraintHelperHandler {

  @Override
  @NotNull
  public String getTitle(@NotNull NlComponent component) {
    return "Flow";
  }

  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(ATTR_VISIBILITY, ATTR_BACKGROUND, ATTR_ORIENTATION,
                            ATTR_FLOW_WRAP_MODE, ATTR_FLOW_MAX_ELEMENTS_WRAP,
                            ATTR_FLOW_FIRST_HORIZONTAL_BIAS,
                            ATTR_FLOW_FIRST_HORIZONTAL_STYLE,
                            ATTR_FLOW_HORIZONTAL_BIAS,
                            ATTR_FLOW_HORIZONTAL_STYLE,
                            ATTR_FLOW_HORIZONTAL_ALIGN,
                            ATTR_FLOW_HORIZONTAL_GAP,
                            ATTR_FLOW_LAST_HORIZONTAL_BIAS,
                            ATTR_FLOW_LAST_HORIZONTAL_STYLE,
                            ATTR_FLOW_FIRST_VERTICAL_BIAS,
                            ATTR_FLOW_FIRST_VERTICAL_STYLE,
                            ATTR_FLOW_VERTICAL_BIAS,
                            ATTR_FLOW_VERTICAL_STYLE,
                            ATTR_FLOW_VERTICAL_ALIGN,
                            ATTR_FLOW_VERTICAL_GAP,
                            ATTR_FLOW_LAST_VERTICAL_BIAS,
                            ATTR_FLOW_LAST_VERTICAL_STYLE
    );
  }
}
