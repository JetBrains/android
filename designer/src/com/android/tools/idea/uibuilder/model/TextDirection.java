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
import org.jetbrains.annotations.Nullable;
import com.android.ide.common.resources.configuration.LayoutDirectionQualifier;
import com.android.resources.LayoutDirection;
import com.android.tools.idea.configurations.Configuration;

import static com.android.SdkConstants.*;

/**
 * RTL state for the designer editor. It provides helper methods to manage the RTL attributes.
 */
public enum TextDirection {
  LEFT_TO_RIGHT,
  RIGHT_TO_LEFT;

  /**
   * Returns the {@linkplain TextDirection} used in the passed {@link Configuration}. If the passed configuration is null, LTR is used.
   */
  public static TextDirection fromConfiguration(@Nullable Configuration configuration) {

    if (configuration == null) {
      return LEFT_TO_RIGHT;
    }
    LayoutDirectionQualifier qualifier = configuration.getFullConfig().getLayoutDirectionQualifier();
    return qualifier == null || qualifier.getValue() != LayoutDirection.RTL ? LEFT_TO_RIGHT : RIGHT_TO_LEFT;
  }

  /**
   * Returns the RTL SegmentType that corresponds to the LEFT side of the screen. This will be different depending if
   * the user has RTL preview enabled or not.
   */
  @NotNull
  public SegmentType getLeftSegment() {
    return this == LEFT_TO_RIGHT ? SegmentType.START : SegmentType.END;
  }

  /**
   * Returns the RTL SegmentType that corresponds to the RIGHT side of the screen. This will be different depending if
   * the user has RTL preview enabled or not.
   */
  @NotNull
  public SegmentType getRightSegment() {
    return this == LEFT_TO_RIGHT ? SegmentType.END : SegmentType.START;
  }

  /**
   * Returns whether the segment type is being seen on the left side of the screen. This is always the
   * SegmentType.LEFT but it could also be SegmentType.START (if LTR is on) or SegmentType.END (if RTL is on)
   *
   * @see #getLeftSegment
   */
  public boolean isLeftSegment(@Nullable SegmentType type) {
    return type == SegmentType.LEFT || type == getLeftSegment();
  }

  /**
   * Returns whether the segment type is being seen on the right side of the screen. This is always the
   * SegmentType.RIGHT but it could also be SegmentType.END (if LTR is on) or SegmentType.START (if RTL is on)
   *
   * @see #getRightSegment
   */
  public boolean isRightSegment(@Nullable SegmentType type) {
    return type == SegmentType.RIGHT || type == getRightSegment();
  }

  /**
   * Returns the RTL attribute name for aligning components left on screen.
   */
  @NotNull
  public String getAttrLeft() {
    return this == LEFT_TO_RIGHT ? ATTR_LAYOUT_ALIGN_START : ATTR_LAYOUT_ALIGN_END;
  }

  /**
   * Returns the RTL attribute name for aligning components left on screen.
   */
  @NotNull
  public String getAttrLeftOf() {
    return this == LEFT_TO_RIGHT ? ATTR_LAYOUT_TO_START_OF : ATTR_LAYOUT_TO_END_OF;
  }

  /**
   * Returns the RTL attribute name for aligning components right on screen.
   */
  @NotNull
  public String getAttrRight() {
    return this == LEFT_TO_RIGHT ? ATTR_LAYOUT_ALIGN_END : ATTR_LAYOUT_ALIGN_START;
  }

  /**
   * Returns the RTL attribute name for aligning components right-of on screen.
   */
  @NotNull
  public String getAttrRightOf() {
    return this == LEFT_TO_RIGHT ? ATTR_LAYOUT_TO_END_OF : ATTR_LAYOUT_TO_START_OF;
  }

  /**
   * Returns the RTL attribute name for aligning components parent-left on screen.
   */
  @NotNull
  public String getAttrAlignParentLeft() {
    return this == LEFT_TO_RIGHT ? ATTR_LAYOUT_ALIGN_PARENT_START : ATTR_LAYOUT_ALIGN_PARENT_END;
  }

  /**
   * Returns the RTL attribute name for aligning components parent-right on screen.
   */
  @NotNull
  public String getAttrAlignParentRight() {
    return this == LEFT_TO_RIGHT ? ATTR_LAYOUT_ALIGN_PARENT_END : ATTR_LAYOUT_ALIGN_PARENT_START;
  }

  /**
   * Returns the RTL attribute name to specify the margin on the left side of the component.
   */
  @NotNull
  public String getAttrMarginLeft() {
    return this == LEFT_TO_RIGHT ? ATTR_LAYOUT_MARGIN_START : ATTR_LAYOUT_MARGIN_END;
  }

  /**
   * Returns the RTL attribute name to specify the margin on the right side of the component.
   */
  @NotNull
  public String getAttrMarginRight() {
    return this == LEFT_TO_RIGHT ? ATTR_LAYOUT_MARGIN_END : ATTR_LAYOUT_MARGIN_START;
  }
}
