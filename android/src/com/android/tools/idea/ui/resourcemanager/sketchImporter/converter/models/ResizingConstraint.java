/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models;

import org.jetbrains.annotations.NotNull;

/**
 * Class that holds the information on the resizing properties that the shape has. INCOMPLETE!
 * Currently only considers the fixed width and fixed height constraints.
 * Should also consider the fixed margins constraints.
 */
public class ResizingConstraint {
  public static final boolean DEFAULT_WIDTH_CONSTRAINT = false;
  public static final boolean DEFAULT_HEIGHT_CONSTRAINT = false;

  private boolean myConstraintWidth;
  private boolean myConstraintHeight;

  public ResizingConstraint() {
    myConstraintWidth = DEFAULT_WIDTH_CONSTRAINT;
    myConstraintHeight = DEFAULT_HEIGHT_CONSTRAINT;
  }

  public ResizingConstraint(boolean constraintWidth, boolean constraintHeight) {
    myConstraintWidth = constraintWidth;
    myConstraintHeight = constraintHeight;
  }

  public boolean isConstraintWidth() {
    return myConstraintWidth;
  }

  public boolean isConstraintHeight() {
    return myConstraintHeight;
  }

  /**
   * Returns true if shape has fixed width but does not have a fixed height
   */
  public boolean isOnlyConstraintWidth() {
    return myConstraintWidth && !myConstraintHeight;
  }

  /**
   * Returns true if shape has fixed height but does not have a fixed width
   */
  public boolean isOnlyConstraintHeight() {
    return myConstraintHeight && !myConstraintWidth;
  }

  public boolean isNoConstraint() {
    return !myConstraintHeight && !myConstraintWidth;
  }

  /**
   * Updates this {@code ResizingConstraint} by adding new constraints from the upper levels.
   * If one group has a resizing constraint, then all its elements will have the same constraint.
   */
  public ResizingConstraint updateConstraint(@NotNull ResizingConstraint constraint) {
    return new ResizingConstraint(myConstraintWidth || constraint.isConstraintWidth(),
                                  myConstraintHeight || constraint.isConstraintHeight());
  }
}