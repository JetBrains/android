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

import com.android.ide.common.rendering.api.ViewInfo
import org.jetbrains.android.compose.COMPOSE_VIEW_ADAPTER_FQNS
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

interface SourceLocation {
  val className: String
  val methodName: String
  val fileName: String
  /** 1-indexed line number. */
  val lineNumber: Int

  /**
   * Returns true if there is no source location information
   */
  fun isEmpty() = lineNumber == -1 && className == ""
}

internal data class SourceLocationImpl(override val className: String,
                                       override val methodName: String,
                                       override val fileName: String,
                                       override val lineNumber: Int) : SourceLocation
typealias LineNumberMapper = (SourceLocation) -> SourceLocation

private val identifyLineNumberMapper: LineNumberMapper = { sourceLocation -> sourceLocation }

/**
 * Parse the viewObject for ComposeViewAdapter. For now we use reflection to parse these information without much structuring.
 * In the future we hope to change this.
 */
fun parseViewInfo(rootViewInfo: ViewInfo,
                  lineNumberMapper: LineNumberMapper = identifyLineNumberMapper): List<ComposeViewInfo> {
  try {
    val viewObj = findComposeViewAdapter(rootViewInfo.viewObject) ?: return listOf()
    val viewInfoField = viewObj::class.declaredMemberProperties
      .single { it.name == "viewInfos" }
      .getter.also {
      it.isAccessible = true
    }
    val composeViewInfos = viewInfoField.call(viewObj) as List<*>
    return parseBounds(composeViewInfos, lineNumberMapper)
  } catch (e: Exception) {
    return listOf()
  }
}

private fun findComposeViewAdapter(viewObj: Any): Any? {
  if (COMPOSE_VIEW_ADAPTER_FQNS.contains(viewObj.javaClass.name)) {
    return viewObj
  }

  val childrenCount = viewObj.javaClass.getMethod("getChildCount").invoke(viewObj) as Int
  for (i in 0 until childrenCount) {
    val child = viewObj.javaClass.getMethod("getChildAt", Int::class.javaPrimitiveType).invoke(viewObj, i)
    return findComposeViewAdapter(child)
  }
  return null
}

private fun parseBounds(elements: List<Any?>,
                        fileLocationMapper: LineNumberMapper): List<ComposeViewInfo> = elements.mapNotNull { item ->
  try {
    val fileName = item!!.javaClass.getMethod("getFileName").invoke(item) as String
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
      SourceLocationImpl(method.substringBeforeLast("."),
                         method.substringAfterLast("."),
                         fileName,
                         lineNumber))
    ComposeViewInfo(sourceLocation, bounds, parseBounds(children, fileLocationMapper))
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

  return PxBounds(
    left = getInt(leftPx),
    top = getInt(topPx),
    right = getInt(rightPx),
    bottom = getInt(bottomPx))
}

private fun getInt(px: Any): Int {
  // dev10 started using inline classes so we might have an Int already
  if (px is Int) return px

  val value = px.javaClass.getMethod("getValue").invoke(px)
  // In dev05, the type of Px changed from Float to Int. We need to handle both cases here for backwards compatibility
  return value as? Int ?: (value as Float).toInt()
}

