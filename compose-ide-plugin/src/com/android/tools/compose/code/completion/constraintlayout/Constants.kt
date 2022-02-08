/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.compose.code.completion.constraintlayout

internal object KeyWords {
  const val ConstraintSets = "ConstraintSets"
  const val Extends = "Extends"
}

/**
 * Common interface to simplify handling multiple enum Classes.
 *
 * [keyWord] is the case-sensitive string used in the syntax.
 */
internal interface ConstraintLayoutKeyWord {
  val keyWord: String
}

//region Constrain KeyWords
/**
 * The classic anchors used to constrain a widget.
 */
internal enum class StandardAnchor(override val keyWord: String): ConstraintLayoutKeyWord {
  Start("start"),
  Left("left"),
  End("end"),
  Right("right"),
  Top("top"),
  Bottom("bottom"),
  Baseline("baseline")
}

/**
 * Non-typical anchors.
 *
 * These implicitly apply multiple [StandardAnchor]s.
 */
internal enum class SpecialAnchor(override val keyWord: String): ConstraintLayoutKeyWord {
  Center("center"),
  CenterH("centerHorizontally"),
  CenterV("centerVertically")
}

/**
 * Supported keywords to define the dimension of a widget.
 */
internal enum class Dimension(override val keyWord: String): ConstraintLayoutKeyWord {
  Width("width"),
  Height("height")
}

/**
 * Keywords to apply rendering time transformations to a widget.
 */
internal enum class RenderTransform(override val keyWord: String): ConstraintLayoutKeyWord {
  Alpha("alpha"),
  ScaleX("scaleX"),
  ScaleY("scaleY"),
  RotationX("rotationX"),
  RotationY("rotationY"),
  RotationZ("rotationZ"),
  TranslationX("translationX"),
  TranslationY("translationY"),
  TranslationZ("translationZ"),
}
//endregion