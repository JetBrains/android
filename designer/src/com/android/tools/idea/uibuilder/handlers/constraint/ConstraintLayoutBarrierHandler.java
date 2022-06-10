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

import com.android.AndroidXConstants;
import com.android.tools.idea.common.model.NlComponent;
import com.google.common.collect.ImmutableList;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Handler for Barrier Helper
 */
public class ConstraintLayoutBarrierHandler extends ConstraintHelperHandler {
  @Override
  @NotNull
  public String getTitle(@NotNull NlComponent component) {
    String barrierDirection = component.resolveAttribute(SHERPA_URI, ATTR_BARRIER_DIRECTION);
    if (barrierDirection == null) {
      return "Barrier";
    }

    return isVertical(component)? "Vertical Barrier" : "Horizontal Barrier";
  }

  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(ATTR_BARRIER_DIRECTION);
  }

  @NotNull
  @Override
  public Icon getIcon(@NotNull NlComponent component) {
    if (!AndroidXConstants.CONSTRAINT_LAYOUT_BARRIER.isEquals(component.getTagName())) {
      return super.getIcon(component);
    }
    if (isVertical(component)) {
      return StudioIcons.LayoutEditor.Palette.BARRIER_VERTICAL;
    }
    else {
      return StudioIcons.LayoutEditor.Palette.BARRIER_HORIZONTAL;
    }
  }

  private static boolean isVertical(@NotNull NlComponent component) {
    String barrierDirection = component.resolveAttribute(SHERPA_URI, ATTR_BARRIER_DIRECTION);
    if (barrierDirection == null) {
      return false;
    }

    switch (barrierDirection) {
      case CONSTRAINT_BARRIER_START:
      case CONSTRAINT_BARRIER_END:
      case CONSTRAINT_BARRIER_LEFT:
      case CONSTRAINT_BARRIER_RIGHT:
        return true;
      default:
        return false;
    }
  }
}
