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
package com.android.tools.idea.uibuilder.handlers.compose.decorator

import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.compose.findComposeViewAdapter
import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger

internal fun ViewInfo.toDesignInfoList(): List<DesignInfo> {
  val viewObj = findComposeViewAdapter(this.viewObject) ?: return emptyList()

  val designInfoList = viewObj.javaClass.getDeclaredMethod("getDesignInfoList\$ui_tooling_release").invoke(viewObj) as? List<*>
                       ?: return emptyList()

  return designInfoList.mapNotNull{ designInfoJson ->
    try {
      parseDesignInfo(designInfoJson as String)
    } catch (e: Exception) {
      Logger.getInstance(DesignInfo::class.java).warn("Error while parsing", e)
      null
    }
  }
}

private fun parseDesignInfo(string: String) = Gson().fromJson(string, DesignInfo::class.java)

internal data class DesignInfo(
  val type: DesignInfoType,
  val version: Int,
  val content: Map<String, ViewDescription>
) {
  companion object {
    val EMPTY = DesignInfo(DesignInfoType.NONE, 0, emptyMap())
  }
}

internal data class ViewDescription(
  val viewId: String,
  val box: PxBounds,
  val isHelper: Boolean,
  val isRoot: Boolean,
  val helperReferences: List<String>,
  val constraints: List<ConstraintsInfo>
)

internal data class PxBounds(
  val left: Int,
  val top: Int,
  val right: Int,
  val bottom: Int) {

  val width get() = right - left
  val height get() = bottom - top
}

internal data class ConstraintsInfo(
  val originAnchor: Anchor,
  val targetAnchor: Anchor,
  val target: String,
  val margin: Int
)

internal enum class Anchor {
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

internal enum class DesignInfoType {
  CONSTRAINTS,
  NONE
}