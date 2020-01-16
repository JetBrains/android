/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.navigation

import com.android.tools.idea.compose.preview.COMPOSE_VIEW_ADAPTER

data class SourceLocation(val className: String, val methodName: String, val fileName: String, val lineNumber: Int) {
  /**
   * Returns true if there is no source location information
   */
  fun isEmpty() = lineNumber == -1 && className == ""
}
typealias LineNumberMapper = (SourceLocation) -> SourceLocation

private val identifyLineNumberMapper: LineNumberMapper = { sourceLocation -> sourceLocation }

/**
 * Parse the viewObject for ComposeViewAdapter. For now we use reflection to parse these information without much structuring.
 * In the future we hope to change this.
 */
fun parseViewInfo(rootView: Any,
                  isFileHandled: (String) -> Boolean = { true },
                  lineNumberMapper: LineNumberMapper = identifyLineNumberMapper): List<ComposeViewInfo> {
  try {
    val viewObj = findComposeViewAdapter(rootView) ?: return listOf()
    val viewInfoField = viewObj.javaClass.getDeclaredField("viewInfos").also {
      it.isAccessible = true
    }
    val rootViewInfo = viewInfoField.get(viewObj) as List<*>
    return parseBounds(rootViewInfo, isFileHandled, lineNumberMapper)
      .flatMap { it.allChildren() }
      .filter { !it.sourceLocation.isEmpty() }
  } catch (e: Exception) {
    return listOf()
  }
}

private fun findComposeViewAdapter(viewObj: Any): Any? {
  if (COMPOSE_VIEW_ADAPTER == viewObj.javaClass.name) {
    return viewObj
  }

  val childrenCount = viewObj.javaClass.getMethod("getChildCount").invoke(viewObj) as Int
  for (i in 0 until childrenCount) {
    val child = viewObj.javaClass.getMethod("getChildAt", Int::class.javaPrimitiveType).invoke(viewObj, i)
    return findComposeViewAdapter(child)
  }
  return null
}

private fun parseBounds(list: List<Any?>,
                        isFileHandled: (String) -> Boolean,
                        fileLocationMapper: LineNumberMapper): List<ComposeViewInfo> = list.mapNotNull { item ->
  try {
    val fileName = item!!.javaClass.getMethod("getFileName").invoke(item) as String
    if (!isFileHandled(fileName)) {
      return@mapNotNull null
    }

    val lineNumber = item.javaClass.getMethod("getLineNumber").invoke(item) as Int
    val method = try {
      item.javaClass.getMethod("getMethodName").invoke(item) as String
    }
    catch (_: Throwable) {
      ""
    }
    val bounds = getBound(item)
    val children = item.javaClass.getMethod("getChildren").invoke(item) as List<Any?>
    val sourceLocation = fileLocationMapper(
      SourceLocation(method.substringBeforeLast("."),
                     method.substringAfterLast("."),
                     fileName,
                     lineNumber))
    ComposeViewInfo(sourceLocation, bounds, parseBounds(children, isFileHandled, fileLocationMapper))
  }
  catch (t: Throwable) {
    null
  }
}

private fun getBound(viewInfo: Any): PxBounds {
  val bounds = viewInfo.javaClass.getMethod("getBounds").invoke(viewInfo)
  val topPx = bounds.javaClass.getMethod("getTop").invoke(bounds)
  val bottomPx = bounds.javaClass.getMethod("getBottom").invoke(bounds)
  val rightPx = bounds.javaClass.getMethod("getRight").invoke(bounds)
  val leftPx = bounds.javaClass.getMethod("getLeft").invoke(bounds)

  val topFloat = getFloat(topPx)
  val bottomFloat = getFloat(bottomPx)
  val rightFloat = getFloat(rightPx)
  val leftFloat = getFloat(leftPx)

  return PxBounds(
    Px(leftFloat),
    Px(topFloat),
    Px(rightFloat),
    Px(bottomFloat))
}

private fun getFloat(px: Any): Float {
  return px.javaClass.getMethod("getValue").invoke(px) as Float
}
