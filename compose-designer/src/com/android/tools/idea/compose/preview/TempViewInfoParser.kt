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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.uibuilder.surface.NlDesignSurface

/**
 * Parse the viewObject for ComposeViewAdapter. For now we use reflection to parse these information without much structuring.
 * In the future we hope to change this.
 */
fun parseViewObject(handler: NlDesignSurface.NavigationHandler,
                    parent: Any): MutableList<ComposeBoundInfo>? {
  try {
    val viewObj = findComposeViewAdapter(parent) ?: return null
    val list = viewObj.javaClass.getMethod("getViewInfos").invoke(viewObj) as List<*>
    val listParsed = parseBounds(list)

    // Filter for:
    // 1) File name is something handler understands
    // 2) Bounds are not a duplicate (SceneComponent has a hard time understanding duplicated bounds).
    val flatBounds = ArrayList<ComposeBoundInfo>()
    listParsed.forEach { tree ->
      tree.stream().forEach {
        if (handler.fileNames.contains(it.fileName) && !boundsExist(it.bounds, flatBounds)) {
          flatBounds.add(it)
        }
      }
    }
    return flatBounds
  } catch (e: Exception) {
    //    println("Unable to parse the viewInfo. ${e.message}")
    return null
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

private fun parseBounds(list: List<*>): List<ComposeBoundInfoTree>{
  val toReturn = ArrayList<ComposeBoundInfoTree>()
  for (item in list) {
    val fileName = item!!.javaClass.getMethod("getFileName").invoke(item) as String
    val lineNumber = item.javaClass.getMethod("getLineNumber").invoke(item) as Int
    val bounds = getBound(item)

    val children = item.javaClass.getMethod("getChildren").invoke(item) as List<*>
    val info = ComposeBoundInfoTree(ComposeBoundInfo(fileName, lineNumber, bounds), parseBounds(children))
    toReturn.add(info)
  }
  return toReturn
}

private fun getData(viewInfo: Any): Array<Any> {
  val data = viewInfo.javaClass.getMethod("getData").invoke(viewInfo) as Array<*>
  return data as Array<Any>
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

  return PxBounds(Px(leftFloat), Px(topFloat), Px(rightFloat), Px(bottomFloat))
}

private fun getFloat(px: Any): Float {
  return px.javaClass.getMethod("getValue").invoke(px) as Float
}
