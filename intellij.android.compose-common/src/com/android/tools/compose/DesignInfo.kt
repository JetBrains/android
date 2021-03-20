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
package com.android.tools.compose

/**
 * Key intended for the cache of SceneComponents when storing/reading a List containing [DesignInfo]s.
 */
const val DESIGN_INFO_LIST_KEY = "DesignInfoList"

/**
 * Represents information provided by Composables through the ComposeViewAdapter class.
 */
data class DesignInfo(
  val type: DesignInfoType,
  val version: Int,
  val content: Map<String, ViewDescription>
) {
  companion object {
    val EMPTY = DesignInfo(DesignInfoType.NONE, 0, emptyMap())
  }
}

data class ViewDescription(
  val viewId: String,
  val box: PxBounds,
  val isHelper: Boolean,
  val isRoot: Boolean,
  val helperReferences: List<String>,
  val constraints: List<ConstraintsInfo>
)

data class PxBounds(
  val left: Int,
  val top: Int,
  val right: Int,
  val bottom: Int) {

  val width get() = right - left
  val height get() = bottom - top
}

data class ConstraintsInfo(
  val originAnchor: Anchor,
  val targetAnchor: Anchor,
  val target: String,
  val margin: Int
)

enum class Anchor {
  NONE,
  LEFT,
  TOP,
  RIGHT,
  BOTTOM,
  BASELINE,
  CENTER,
  CENTER_X,
  CENTER_Y;
}

enum class DesignInfoType {
  CONSTRAINTS,
  NONE
}