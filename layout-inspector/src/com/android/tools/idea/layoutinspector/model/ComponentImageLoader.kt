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
package com.android.tools.idea.layoutinspector.model

import com.android.tools.layoutinspector.SkiaViewNode

class ComponentImageLoader(private val nodeMap: Map<Long, ViewNode>, skiaRoot: SkiaViewNode) {
  private val nonImageSkiaNodes = skiaRoot.flatten().filter { it.image == null }.associateBy {  it.id }

  fun loadImages(drawChildren: ViewNode.() -> MutableList<DrawViewNode>) {
      for ((drawId, node) in nodeMap) {
        val remainingChildren = LinkedHashSet(node.children)
        val skiaNode = nonImageSkiaNodes[drawId]
        if (skiaNode != null) {
          for (childSkiaNode in skiaNode.children) {
            val image = childSkiaNode.image
            if (image != null) {
              node.drawChildren().add(DrawViewImage(image, node))
            }
            else {
              val viewForSkiaChild = nodeMap[childSkiaNode.id] ?: continue
              val actualChild = viewForSkiaChild.parentSequence.find { remainingChildren.contains(it) } ?: continue
              remainingChildren.remove(actualChild)
              node.drawChildren().add(DrawViewChild(actualChild))
            }
          }
        }
        remainingChildren.mapTo(node.drawChildren()) { DrawViewChild(it) }
      }
  }
}