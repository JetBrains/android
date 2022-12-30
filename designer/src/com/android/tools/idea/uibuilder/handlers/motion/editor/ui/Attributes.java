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
package com.android.tools.idea.uibuilder.handlers.motion.editor.ui;

/**
 * List of attributes used to copy tags to during conversion to Constraints
 */
public final class Attributes {
  static String[] LAYOUT = {
    "android:layout_width",
    "android:layout_height",
    "android:layout_marginStart",
    "android:layout_marginBottom",
    "android:layout_marginTop",
    "android:layout_marginEnd",
    "android:layout_marginLeft",
    "android:layout_marginRight",
    "layout_constraintCircle",
    "layout_constraintCircleRadius",
    "layout_constraintCircleAngle",
    "layout_constraintGuide_begin",
    "layout_constraintGuide_end",
    "layout_constraintGuide_percent",
    "layout_constraintLeft_toLeftOf",
    "layout_constraintLeft_toRightOf",
    "layout_constraintRight_toLeftOf",
    "layout_constraintRight_toRightOf",
    "layout_constraintTop_toTopOf",
    "layout_constraintTop_toBottomOf",
    "layout_constraintBottom_toTopOf",
    "layout_constraintBottom_toBottomOf",
    "layout_constraintBaseline_toBaselineOf",
    "layout_constraintStart_toEndOf",
    "layout_constraintStart_toStartOf",
    "layout_constraintEnd_toStartOf",
    "layout_constraintEnd_toEndOf",
    "layout_goneMarginLeft",
    "layout_goneMarginTop",
    "layout_goneMarginRight",
    "layout_goneMarginBottom",
    "layout_goneMarginStart",
    "layout_goneMarginEnd",
    "layout_constrainedWidth",
    "layout_constrainedHeight",
    "layout_constraintHorizontal_bias",
    "layout_constraintVertical_bias",
    "layout_constraintWidth_default",
    "layout_constraintHeight_default",
    "layout_constraintWidth_min",
    "layout_constraintWidth_max",
    "layout_constraintWidth_percent",
    "layout_constraintHeight_min",
    "layout_constraintHeight_max",
    "layout_constraintHeight_percent",
    "layout_constraintLeft_creator",
    "layout_constraintTop_creator",
    "layout_constraintRight_creator",
    "layout_constraintBottom_creator",
    "layout_constraintBaseline_creator",
    "layout_constraintDimensionRatio",
    "layout_constraintHorizontal_weight",
    "layout_constraintVertical_weight",
    "layout_constraintHorizontal_chainStyle",
    "layout_constraintVertical_chainStyle",
  };

  static String[] PROPERTY_SET = {
    "android:orientation",
    "android:visibility",
    "visibilityMode",
    "android:alpha",
    "android:elevation",
    "android:rotation",
    "android:rotationX",
    "android:rotationY",
    "android:scaleX",
    "android:scaleY",
  };
}
